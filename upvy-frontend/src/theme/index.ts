import { scaleFontSize, scaleSpacing } from '@/utils/responsive';

/**
 * 색상 팔레트
 */
export const colors = {
  // Primary (Green)
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

  // Gray Scale
  gray: {
    50: '#fafafa',
    100: '#f5f5f5',
    200: '#e5e5e5',
    300: '#d4d4d4',
    400: '#a3a3a3',
    500: '#737373',
    600: '#525252',
    700: '#404040',
    800: '#262626',
    900: '#171717',
  },

  // Semantic Colors
  success: '#22c55e',
  error: '#ef4444',
  warning: '#f59e0b',
  info: '#3b82f6',

  // Background
  background: {
    primary: '#ffffff',
    secondary: '#fafafa',
    tertiary: '#f5f5f5',
  },

  // Text
  text: {
    primary: '#171717',
    secondary: '#525252',
    tertiary: '#a3a3a3',
    inverse: '#ffffff',
  },

  // Border
  border: {
    light: '#e5e5e5',
    medium: '#d4d4d4',
    dark: '#a3a3a3',
  },

  // Overlay
  overlay: 'rgba(0, 0, 0, 0.5)',
} as const;

/**
 * 타이포그래피
 */
export const typography = {
  // 폰트 크기
  fontSize: {
    xs: scaleFontSize(12),
    sm: scaleFontSize(14),
    base: scaleFontSize(16),
    lg: scaleFontSize(18),
    xl: scaleFontSize(20),
    '2xl': scaleFontSize(24),
    '3xl': scaleFontSize(30),
    '4xl': scaleFontSize(36),
    '5xl': scaleFontSize(48),
  },

  // 줄 높이
  lineHeight: {
    tight: 1.25,
    normal: 1.5,
    relaxed: 1.75,
  },

  // 폰트 두께
  fontWeight: {
    normal: '400' as const,
    medium: '500' as const,
    semibold: '600' as const,
    bold: '700' as const,
  },
} as const;

/**
 * 간격 (Spacing)
 */
export const spacing = {
  0: 0,
  1: scaleSpacing(4),
  2: scaleSpacing(8),
  3: scaleSpacing(12),
  4: scaleSpacing(16),
  5: scaleSpacing(20),
  6: scaleSpacing(24),
  8: scaleSpacing(32),
  10: scaleSpacing(40),
  12: scaleSpacing(48),
  16: scaleSpacing(64),
  20: scaleSpacing(80),
  24: scaleSpacing(96),
} as const;

/**
 * 둥근 모서리 (Border Radius)
 */
export const borderRadius = {
  none: 0,
  sm: scaleSpacing(4),
  base: scaleSpacing(8),
  md: scaleSpacing(12),
  lg: scaleSpacing(16),
  xl: scaleSpacing(24),
  full: 9999,
} as const;

/**
 * 그림자 (Shadow)
 */
export const shadows = {
  sm: {
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 1,
    },
    shadowOpacity: 0.05,
    shadowRadius: 2,
    elevation: 2,
  },
  base: {
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 2,
    },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 4,
  },
  md: {
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 4,
    },
    shadowOpacity: 0.15,
    shadowRadius: 8,
    elevation: 8,
  },
  lg: {
    shadowColor: '#000',
    shadowOffset: {
      width: 0,
      height: 8,
    },
    shadowOpacity: 0.2,
    shadowRadius: 16,
    elevation: 16,
  },
} as const;

/**
 * 애니메이션 지속시간
 */
export const duration = {
  fast: 150,
  base: 300,
  slow: 500,
} as const;

/**
 * Z-Index
 */
export const zIndex = {
  base: 0,
  dropdown: 1000,
  sticky: 1020,
  fixed: 1030,
  modalBackdrop: 1040,
  modal: 1050,
  popover: 1060,
  tooltip: 1070,
} as const;

/**
 * 레이아웃
 */
export const layout = {
  containerPadding: scaleSpacing(16),
  maxContentWidth: 1200,
  headerHeight: scaleSpacing(56),
  tabBarHeight: scaleSpacing(64),
} as const;

/**
 * Theme 객체
 */
export const theme = {
  colors,
  typography,
  spacing,
  borderRadius,
  shadows,
  duration,
  zIndex,
  layout,
} as const;

export type Theme = typeof theme;
export type Colors = typeof colors;
export type Typography = typeof typography;
export type Spacing = typeof spacing;
