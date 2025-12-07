package me.onetwo.growsnap.domain.content.model

/**
 * 콘텐츠 카테고리
 *
 * 요구사항 명세서 섹션 2.2.2에 정의된 콘텐츠 카테고리입니다.
 */
enum class Category(val displayName: String, val description: String) {
    // 학문 & 교육
    LANGUAGE("언어", "외국어, 한국어 등"),
    SCIENCE("과학", "물리, 화학, 생물 등"),
    HISTORY("역사", "세계사, 한국사 등"),
    MATHEMATICS("수학", "수학, 통계 등"),
    ART("예술", "미술, 음악, 문학 등"),

    // 비즈니스 & 커리어
    STARTUP("스타트업", "창업, 경영 등"),
    MARKETING("마케팅", "디지털 마케팅, 브랜딩 등"),
    PROGRAMMING("프로그래밍", "개발, 코딩 등"),
    DESIGN("디자인", "UI/UX, 그래픽 디자인 등"),

    // 자기계발
    PRODUCTIVITY("생산성", "시간관리, 업무 효율 등"),
    PSYCHOLOGY("심리학", "인간 심리, 행동 등"),
    FINANCE("재테크", "투자, 저축 등"),
    HEALTH("건강", "운동, 식단, 정신건강 등"),
    MOTIVATION("동기부여", "동기부여, 성공 마인드셋, 자기계발 등"),

    // 라이프스타일
    PARENTING("육아", "자녀 교육, 육아 팁 등"),
    COOKING("요리", "레시피, 요리 기술 등"),
    TRAVEL("여행", "여행지, 여행 팁 등"),
    HOBBY("취미", "독서, 음악, 운동 등"),

    // 트렌드 & 인사이트
    TREND("트렌드", "최신 트렌드, 인사이트 등"),
    OTHER("기타", "기타 카테고리"),

    // 재미 & 가벼운 콘텐츠
    FUN("재미", "재미있거나 가벼운 콘텐츠 (학습 증진을 위해 노출 빈도가 낮음)")
}
