/**
 * Dark Mode Color Palette
 * 다크 모드 색상 팔레트 정의
 */
export const darkColors = {
  // Primary (Green) - 동일하게 유지 (그린 컬러는 다크모드에서도 유지)
  primary: {
    50: '#f0fdf4',
    100: '#dcfce7',
    200: '#bbf7d0',
    300: '#86efac',
    400: '#4ade80',
    500: '#22c55e', // Main
    600: '#16a34a',
    700: '#15803d',
    800: '#166534',
    900: '#14532d',
  },

  // Gray Scale (다크 모드용으로 반전)
  gray: {
    50: '#18181b', // 어두운 배경
    100: '#27272a',
    200: '#3f3f46',
    300: '#52525b',
    400: '#71717a',
    500: '#a1a1aa',
    600: '#d4d4d8',
    700: '#e4e4e7',
    800: '#f4f4f5',
    900: '#fafafa',
  },

  // Semantic Colors (다크 모드에 최적화)
  success: '#22c55e',
  error: '#f87171', // 밝은 레드 (가독성 개선)
  warning: '#fbbf24', // 밝은 옐로우 (가독성 개선)
  info: '#60a5fa', // 밝은 블루 (가독성 개선)

  // Background
  background: {
    primary: '#09090b', // 매우 어두운 배경
    secondary: '#18181b', // 어두운 배경
    tertiary: '#27272a', // 덜 어두운 배경
  },

  // Text (다크 모드에서는 밝은 텍스트)
  text: {
    primary: '#fafafa', // 매우 밝은 텍스트
    secondary: '#a1a1aa', // 회색 텍스트
    tertiary: '#71717a', // 더 어두운 회색
    inverse: '#ffffff', // 버튼 등에서 사용하는 흰색 텍스트
  },

  // Border
  border: {
    light: '#27272a', // 어두운 보더
    medium: '#3f3f46',
    dark: '#52525b',
  },

  // Overlay (다크 모드에서는 더 밝은 오버레이)
  overlay: 'rgba(0, 0, 0, 0.7)',
} as const;
