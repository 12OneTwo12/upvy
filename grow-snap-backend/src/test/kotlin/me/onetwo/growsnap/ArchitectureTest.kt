package me.onetwo.growsnap

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.RestController

/**
 * 아키텍처 규칙 검증 테스트
 *
 * ArchUnit을 사용하여 코드 레벨에서 아키텍처 규칙을 자동으로 검증합니다.
 * AI와의 협업 과정에서 아키텍처를 손상하는 일이 없도록 필수 규칙을 정의합니다.
 *
 * ### 검증 규칙
 * 1. 레이어 아키텍처 검증
 * 2. 패키지 규칙 검증 (순환 의존성, 도메인 격리)
 * 3. 네이밍 컨벤션 검증
 * 4. 어노테이션 규칙 검증
 */
@AnalyzeClasses(
    packages = ["me.onetwo.growsnap"],
    importOptions = [ImportOption.DoNotIncludeTests::class]
)
class ArchitectureTest {

    /**
     * 레이어 아키텍처 규칙 검증
     *
     * ### 레이어 구조
     * - Controller: HTTP 요청/응답 처리
     * - Service: 비즈니스 로직 처리
     * - Repository: 데이터베이스 접근
     * - Model: 도메인 엔티티
     * - DTO: 데이터 전송 객체
     * - Event: 도메인 이벤트
     * - Infrastructure: 공통 기능 및 설정
     *
     * ### 의존성 규칙
     * - Controller → Service, DTO만 의존 가능
     * - Service → Repository, 다른 Service, DTO, Infrastructure만 의존 가능
     * - Repository → Model, DTO, JOOQ만 의존 가능 (복잡한 쿼리 결과 매핑 시 DTO 반환 허용)
     * - Event → Repository, Service, DTO 의존 가능 (비동기 이벤트 처리)
     * - Infrastructure → Service, Model 의존 가능 (예: OAuth2가 UserService 의존)
     */
    @ArchTest
    val layerDependencyRule = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Controller").definedBy("..domain..controller..")
        .layer("Service").definedBy("..domain..service..")
        .layer("Repository").definedBy("..domain..repository..")
        .layer("Model").definedBy("..domain..model..")
        .layer("DTO").definedBy("..domain..dto..")
        .layer("Event").definedBy("..domain..event..")
        .layer("Infrastructure").definedBy("..infrastructure..")

        .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
        .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Service", "Event", "Infrastructure")
        .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
        .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository", "Event", "DTO")
        .whereLayer("Model").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository", "DTO", "Infrastructure")

    /**
     * 순환 의존성 금지 규칙 - Service와 Repository 레이어
     *
     * 도메인 간 순환 의존성을 금지하여 아키텍처의 건전성을 유지합니다.
     *
     * 참고:
     * - Event 핸들러는 비동기 이벤트 처리를 위해 다른 도메인의 서비스를 호출할 수 있습니다.
     *   이로 인해 content ↔ analytics 간 순환 의존성이 발생하지만, 이는 의도된 설계입니다.
     *   (ContentEventHandler → ContentInteractionService → ContentInteraction)
     * - Event 레이어를 제외한 Service와 Repository 레이어만 순환 의존성을 검사합니다.
     */
    @ArchTest
    val noCyclicDependencyRule = slices()
        .matching("me.onetwo.growsnap.domain.(*)..(service|repository|model|dto)..")
        .should()
        .beFreeOfCycles()

    /**
     * Controller 네이밍 컨벤션 규칙
     *
     * Controller 클래스는 반드시 'Controller'로 끝나야 합니다.
     */
    @ArchTest
    val controllerNamingRule = classes()
        .that().resideInAPackage("..controller..")
        .and().areAnnotatedWith(RestController::class.java)
        .should().haveSimpleNameEndingWith("Controller")
        .because("Controller 클래스는 'Controller'로 끝나야 합니다")

    /**
     * Service 구현체 네이밍 컨벤션 규칙
     *
     * Service 구현체 클래스는 반드시 'ServiceImpl'로 끝나야 합니다.
     */
    @ArchTest
    val serviceImplNamingRule = classes()
        .that().resideInAPackage("..service..")
        .and().areAnnotatedWith(Service::class.java)
        .and().areNotInterfaces()
        .should().haveSimpleNameEndingWith("ServiceImpl")
        .orShould().haveSimpleNameEndingWith("Service")
        .because("Service 구현체는 'ServiceImpl' 또는 'Service'로 끝나야 합니다")

    /**
     * Repository 구현체 네이밍 컨벤션 규칙
     *
     * Repository 구현체 클래스는 반드시 'RepositoryImpl', 'Repository', 또는 'QueryRepository'로 끝나야 합니다.
     * 단, Kotlin 내부 클래스 및 람다는 제외합니다.
     */
    @ArchTest
    val repositoryNamingRule = classes()
        .that().resideInAPackage("..repository..")
        .and().areNotInterfaces()
        .and().areTopLevelClasses()
        .should().haveSimpleNameEndingWith("RepositoryImpl")
        .orShould().haveSimpleNameEndingWith("Repository")
        .orShould().haveSimpleNameEndingWith("QueryRepository")
        .because("Repository 클래스는 'RepositoryImpl', 'Repository', 또는 'QueryRepository'로 끝나야 합니다")

    /**
     * @RestController 어노테이션 위치 규칙
     *
     * @RestController는 반드시 controller 패키지에만 존재해야 합니다.
     */
    @ArchTest
    val restControllerLocationRule = classes()
        .that().areAnnotatedWith(RestController::class.java)
        .should().resideInAPackage("..controller..")
        .because("@RestController는 controller 패키지에만 존재해야 합니다")

    /**
     * @Service 어노테이션 위치 규칙
     *
     * @Service는 반드시 service 패키지에만 존재해야 합니다.
     * 단, Infrastructure 계층의 서비스는 예외로 허용합니다.
     */
    @ArchTest
    val serviceLocationRule = classes()
        .that().areAnnotatedWith(Service::class.java)
        .and().resideOutsideOfPackage("..infrastructure..")
        .should().resideInAPackage("..service..")
        .because("@Service는 service 패키지에만 존재해야 합니다 (Infrastructure 제외)")

    /**
     * @Repository 어노테이션 위치 규칙
     *
     * @Repository는 반드시 repository 패키지에만 존재해야 합니다.
     * 단, Infrastructure 계층의 Repository는 예외로 허용합니다.
     */
    @ArchTest
    val repositoryLocationRule = classes()
        .that().areAnnotatedWith(Repository::class.java)
        .and().resideOutsideOfPackage("..infrastructure..")
        .should().resideInAPackage("..repository..")
        .because("@Repository는 repository 패키지에만 존재해야 합니다 (Infrastructure 제외)")

    /**
     * @Transactional 사용 규칙
     *
     * @Transactional은 Service 계층에만 사용해야 합니다.
     * Controller나 Repository에서는 사용할 수 없습니다.
     */
    @ArchTest
    val transactionalLocationRule = noClasses()
        .that().resideInAPackage("..controller..")
        .or().resideInAPackage("..repository..")
        .should().beAnnotatedWith(Transactional::class.java)
        .because("@Transactional은 Service 계층에만 사용해야 합니다")

    /**
     * DSLContext 사용 규칙
     *
     * DSLContext(JOOQ)는 Repository 계층에서만 사용해야 합니다.
     * Service나 Controller에서는 직접 데이터베이스에 접근할 수 없습니다.
     */
    @ArchTest
    val dslContextUsageRule = noClasses()
        .that().resideOutsideOfPackage("..repository..")
        .and().resideInAPackage("..domain..")
        .should().dependOnClassesThat().haveFullyQualifiedName("org.jooq.DSLContext")
        .because("DSLContext는 Repository 계층에서만 사용해야 합니다")
}
