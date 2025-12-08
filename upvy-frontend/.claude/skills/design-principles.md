---
description: Upvy 디자인 원칙 및 스타일 가이드
---

# Upvy Design System

당신은 Upvy 프로젝트의 디자인 시스템을 따라 개발합니다.

## 디자인 철학

### 핵심 가치
- **인스타그램 스타일**: 깔끔하고 미니멀, 전문적
- **틱톡 스타일 지양**: 과도한 컬러나 화려함 배제
- **교육 중심**: 콘텐츠가 주인공, UI는 보조
- **성장 상징**: 그린 컬러로 성장과 발전 표현

## 컬러 시스템

### Primary: Green
```typescript
primary: {
  500: '#22c55e',  // Main color
  600: '#16a34a',  // Hover/Active
}
```

### Background & Text
```typescript
background: {
  primary: '#ffffff',
  secondary: '#fafafa',
  tertiary: '#f5f5f5',
}

text: {
  primary: '#171717',
  secondary: '#525252',
  tertiary: '#a3a3a3',
}
```

## 반응형 브레이크포인트

```typescript
xs: 320px   // 작은 폰
sm: 375px   // iPhone SE
md: 390px   // iPhone 14 (기준)
lg: 428px   // iPhone 14 Pro Max
xl: 768px   // 태블릿
```

## 타이포그래피

```typescript
fontSize: {
  xs: 12px,   // 캡션
  sm: 14px,   // 보조 텍스트
  base: 16px, // 본문 (기준)
  lg: 18px,   // 부제목
  xl: 20px,
  '2xl': 24px,
  '3xl': 30px,
}

fontWeight: {
  normal: '400',
  medium: '500',
  semibold: '600',
  bold: '700',
}
```

## 컴포넌트 사용 원칙

### Button
- Primary: 주요 액션 (그린 배경)
- Outline: 부가 액션 (그린 테두리)
- Ghost: 텍스트 버튼
- 크기: sm(36px), md(48px), lg(56px)

### Input
- Border: 2px solid #e5e5e5
- Focus: 2px solid #22c55e
- Height: 48px
- Label: 항상 입력 필드 위에 배치

### 스타일링
- **항상 theme 사용**: `theme.colors.primary[500]`
- **반응형 함수 활용**: `responsive()`, `scaleWidth()`
- **하드코딩 금지**: 색상, 크기 직접 입력 금지

## 화면 디자인 패턴

### 로그인/인증 화면
- 세로 센터 정렬
- 로고 상단 중앙 배치
- CTA 버튼 하단 고정
- 여백 충분히 확보

### 폼 화면
- 명확한 레이블
- 실시간 검증 피드백
- 에러 메시지 빨간색 표시
- 성공 메시지 그린 표시

## 금지 사항

❌ 과도한 그라데이션
❌ 화려한 네온 컬러
❌ 복잡한 패턴 배경
❌ 12px 미만 폰트
❌ 하드코딩된 색상/크기

## 참고 파일

- `/src/theme/index.ts` - 모든 디자인 토큰
- `/src/utils/responsive.ts` - 반응형 유틸리티
- `/src/components/common/` - 재사용 컴포넌트
