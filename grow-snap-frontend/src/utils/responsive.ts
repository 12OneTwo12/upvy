import { Dimensions, PixelRatio, Platform } from 'react-native';

/**
 * 화면 크기
 */
const { width: SCREEN_WIDTH, height: SCREEN_HEIGHT } = Dimensions.get('window');

/**
 * 기준 디자인 크기 (iPhone 14 기준)
 */
const DESIGN_WIDTH = 390;
const DESIGN_HEIGHT = 844;

/**
 * 브레이크포인트 정의
 */
export const BREAKPOINTS = {
  xs: 320, // 작은 폰
  sm: 375, // iPhone SE, 작은 iPhone
  md: 390, // iPhone 14, 중간 크기
  lg: 428, // iPhone 14 Pro Max, 큰 폰
  xl: 768, // 태블릿
  xxl: 1024, // 큰 태블릿
} as const;

/**
 * 현재 브레이크포인트 가져오기
 */
export const getCurrentBreakpoint = (): keyof typeof BREAKPOINTS => {
  if (SCREEN_WIDTH >= BREAKPOINTS.xxl) return 'xxl';
  if (SCREEN_WIDTH >= BREAKPOINTS.xl) return 'xl';
  if (SCREEN_WIDTH >= BREAKPOINTS.lg) return 'lg';
  if (SCREEN_WIDTH >= BREAKPOINTS.md) return 'md';
  if (SCREEN_WIDTH >= BREAKPOINTS.sm) return 'sm';
  return 'xs';
};

/**
 * 너비 기반 스케일
 * 디자인 가이드의 크기를 현재 화면 크기에 맞게 조정
 */
export const scaleWidth = (size: number): number => {
  const scale = SCREEN_WIDTH / DESIGN_WIDTH;
  return Math.round(size * scale);
};

/**
 * 높이 기반 스케일
 */
export const scaleHeight = (size: number): number => {
  const scale = SCREEN_HEIGHT / DESIGN_HEIGHT;
  return Math.round(size * scale);
};

/**
 * 폰트 스케일
 * 화면 크기에 비례하되, 최소/최대값을 제한
 */
export const scaleFontSize = (size: number): number => {
  const scale = SCREEN_WIDTH / DESIGN_WIDTH;
  const newSize = size * scale;

  // 픽셀 밀도를 고려한 조정
  if (Platform.OS === 'ios') {
    return Math.round(PixelRatio.roundToNearestPixel(newSize));
  }

  // Android는 좀 더 유연하게
  return Math.round(newSize);
};

/**
 * 적당한 스케일 (moderate scale)
 * 너무 크거나 작아지지 않도록 조정
 */
export const moderateScale = (size: number, factor = 0.5): number => {
  const scaled = scaleWidth(size);
  return Math.round(size + (scaled - size) * factor);
};

/**
 * 간격 스케일 (padding, margin 등)
 */
export const scaleSpacing = (size: number): number => {
  return moderateScale(size, 0.3);
};

/**
 * 디바이스 타입 체크
 */
export const isSmallDevice = (): boolean => SCREEN_WIDTH < BREAKPOINTS.sm;
export const isMediumDevice = (): boolean => SCREEN_WIDTH >= BREAKPOINTS.sm && SCREEN_WIDTH < BREAKPOINTS.lg;
export const isLargeDevice = (): boolean => SCREEN_WIDTH >= BREAKPOINTS.lg && SCREEN_WIDTH < BREAKPOINTS.xl;
export const isTablet = (): boolean => SCREEN_WIDTH >= BREAKPOINTS.xl;

/**
 * 반응형 값 선택
 * 브레이크포인트에 따라 다른 값 반환
 */
export const responsive = <T>(values: {
  xs?: T;
  sm?: T;
  md?: T;
  lg?: T;
  xl?: T;
  xxl?: T;
  default: T;
}): T => {
  const breakpoint = getCurrentBreakpoint();
  return values[breakpoint] ?? values.default;
};

/**
 * 화면 크기 정보
 */
export const screenInfo = {
  width: SCREEN_WIDTH,
  height: SCREEN_HEIGHT,
  aspectRatio: SCREEN_WIDTH / SCREEN_HEIGHT,
  isPortrait: SCREEN_HEIGHT > SCREEN_WIDTH,
  isLandscape: SCREEN_WIDTH > SCREEN_HEIGHT,
  pixelRatio: PixelRatio.get(),
  fontScale: PixelRatio.getFontScale(),
  breakpoint: getCurrentBreakpoint(),
};

/**
 * 안전한 영역 계산 (노치, 상태바 등 고려)
 * useSafeAreaInsets와 함께 사용
 */
export const getSafeAreaPadding = (insets: {
  top: number;
  bottom: number;
  left: number;
  right: number;
}) => ({
  paddingTop: Math.max(insets.top, scaleSpacing(16)),
  paddingBottom: Math.max(insets.bottom, scaleSpacing(16)),
  paddingLeft: Math.max(insets.left, scaleSpacing(16)),
  paddingRight: Math.max(insets.right, scaleSpacing(16)),
});

/**
 * 퍼센트를 픽셀로 변환
 */
export const widthPercentageToDP = (percentage: number): number => {
  return (SCREEN_WIDTH * percentage) / 100;
};

export const heightPercentageToDP = (percentage: number): number => {
  return (SCREEN_HEIGHT * percentage) / 100;
};

// 간단한 별칭
export const wp = widthPercentageToDP;
export const hp = heightPercentageToDP;
