import java.time.Duration

plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("kapt") version "1.9.23"
    kotlin("plugin.spring") version "1.9.23"
    id("org.springframework.boot") version "3.5.4"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.asciidoctor.jvm.convert") version "3.3.2"

    id("jacoco")
    id ("io.gitlab.arturbosch.detekt") version "1.23.6"

    id("nu.studer.jooq") version "8.2"
}

group = "me.onetwo"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

extra["snippetsDir"] = file("build/generated-snippets")
val jooqVersion = "3.17.23"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.restdocs:spring-restdocs-webtestclient")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.projectreactor:reactor-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // MockK for Kotlin testing
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // Testcontainers for integration testing with Redis and MySQL
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers:1.19.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
    testImplementation("org.testcontainers:mysql:1.19.3")
    testImplementation("org.testcontainers:r2dbc:1.19.3")
    // MySQL JDBC driver for Testcontainers init script execution
    testRuntimeOnly("com.mysql:mysql-connector-j:8.2.0")

    // ArchUnit for architecture testing
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")

    // Awaitility for async testing (replaces Thread.sleep)
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")

    // OAuth2 Client
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

    // OAuth2 Resource Server (for JWT authentication)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")

    // Redis Reactive
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // Spring Session for WebFlux (OAuth2 authorization request storage)
    implementation("org.springframework.session:spring-session-data-redis")

    // BCrypt
    implementation("org.springframework.security:spring-security-crypto")

    // Spring Mail for Email Verification
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // AWS S3 for image upload (Spring Cloud AWS)
    implementation(platform("io.awspring.cloud:spring-cloud-aws-dependencies:3.0.3"))
    implementation("io.awspring.cloud:spring-cloud-aws-starter-s3")

    // Image processing (Thumbnailator for resizing)
    implementation("net.coobird:thumbnailator:0.4.20")

    // R2DBC for Reactive Database Access
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("io.r2dbc:r2dbc-pool")
    runtimeOnly("io.asyncer:r2dbc-mysql:1.0.5")

    // JOOQ 기본 런타임 라이브러리
    implementation("org.jooq:jooq:$jooqVersion")
    implementation("org.springframework.boot:spring-boot-starter-jooq") // jooq, Spring과 통합하는 경우 추가

    // JOOQ 코드 생성 관련 라이브러리
    jooqGenerator("org.jooq:jooq-meta:$jooqVersion")
    jooqGenerator("org.jooq:jooq-codegen:$jooqVersion")
    // DDLDatabase 지원 라이브러리 추가
    jooqGenerator("org.jooq:jooq-meta-extensions:$jooqVersion")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // 전체 테스트 suite 최대 타임아웃: 20분
    // MockK mocking 실수로 인한 무한 대기 방지 (최후의 방어선)
    // 개별 테스트 메서드는 BaseReactiveTest에서 10초 타임아웃 적용
    // 전체 테스트 실행 시간: ~10-11분 (CI/CD 변동성 고려하여 20분 설정)
    // @see <a href="https://github.com/12OneTwo12/upvy/issues/177">ISSUE-177</a>
    timeout.set(Duration.ofMinutes(20))

    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.test {
    outputs.dir(project.extra["snippetsDir"]!!)
    finalizedBy("jacocoTestReport")
}

tasks.asciidoctor {
    inputs.dir(project.extra["snippetsDir"]!!)
    dependsOn(tasks.test)
}


tasks.register<Copy>("copyDocument") {
    dependsOn(tasks.asciidoctor)
    from(file("build/docs/asciidoc"))
    into(file("src/main/resources/static/docs"))
}

tasks.register<Copy>("buildDocument") {
    dependsOn("copyDocument")
    from(file("src/main/resources/static/docs"))
    into(file("build/resources/main/static/docs"))
}

tasks.named("jar") {
    dependsOn("buildDocument")
}

tasks.named("bootJar") {
    dependsOn("buildDocument")
}

tasks.named("resolveMainClassName") {
    dependsOn("buildDocument")
}


// jacoco test 커버리지
extensions.configure<JacocoPluginExtension> {
    toolVersion = "0.8.11"
    reportsDirectory.set(layout.buildDirectory.dir("reports/jacoco"))
}

val excludedClasses = listOf(
    "**/UpvyBackendApplication.class",
    "**/filter/**",
    "**/dto/**",
    "**/exception/**",
    "**/config/**",
    "**/generated/**",
    "**/docs/**",
    "**/test/**"
)

val jacocoClassDirectories = layout.buildDirectory.dir("classes").map { dir ->
    fileTree(dir) {
        include("**/*.class")
        exclude(excludedClasses)
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.test)

    reports {
        html.required.set(true)
        xml.required.set(true)
    }

    classDirectories.setFrom(jacocoClassDirectories)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.test)

    classDirectories.setFrom(jacocoClassDirectories)

    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.1".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.1".toBigDecimal()
            }
        }
    }
}

val detektConfigFile = file("$rootDir/config/detekt/detekt.yml")

detekt {
    toolVersion = "1.23.6"
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file(detektConfigFile)) // Detekt에서 제공된 yml에서 Rule 설정 On/Off 가능
    ignoreFailures = true // detekt 빌드시 실패 ignore 처리
}

// JOOQ 설정
// Note: JOOQ Gradle 플러그인의 XML 스키마 검증 경고는 무시해도 됩니다.
// 이는 플러그인의 알려진 이슈이며 실제 코드 생성과 빌드에는 영향이 없습니다.
// https://github.com/etiennestuder/gradle-jooq-plugin/issues
jooq {
    version.set(jooqVersion)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                generator.apply {
                    name = "org.jooq.codegen.KotlinGenerator"

                    database.apply {
                        name = "org.jooq.meta.extensions.ddl.DDLDatabase" // DDL 파일 사용 설정
                        properties.add(
                            org.jooq.meta.jaxb.Property().apply {
                                key = "scripts"
                                value = "src/main/resources/sql/create-table-sql.sql" // DDL 파일 경로
                            }
                        )
                        properties.add(
                            org.jooq.meta.jaxb.Property().apply {
                                key = "sort"
                                value = "semantic" // 테이블 참조 관계 정렬
                            }
                        )
                        properties.add(
                            org.jooq.meta.jaxb.Property().apply {
                                key = "unqualifiedSchema"
                                value = "true" // 스키마 이름 제거
                            }
                        )

                        // DATETIME을 Instant로 강제 매핑 (UTC 기준)
                        // MySQL DATETIME(6) best practice for global apps with UTC
                        forcedTypes.add(
                            org.jooq.meta.jaxb.ForcedType().apply {
                                userType = "java.time.Instant"
                                converter = "me.onetwo.upvy.config.jooq.InstantConverter"
                                includeTypes = "TIMESTAMP.*|LOCALDATETIME.*"
                            }
                        )
                    }

                    generate.apply {
                        // 도메인 모델을 별도로 관리하므로 JOOQ POJO/DAO는 생성하지 않음
                        isPojos = false
                        isDaos = false
                        isRecords = true
                        isFluentSetters = false
                        isJavaTimeTypes = true
                    }

                    target.apply {
                        packageName = "me.onetwo.upvy.jooq.generated" // JOOQ 코드 저장 경로
                        directory = "src/main/generated"
                    }
                }
            }
        }
    }
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/generated") // JOOQ 코드가 있는 폴더 소스 코드로 추가
        }
    }
}