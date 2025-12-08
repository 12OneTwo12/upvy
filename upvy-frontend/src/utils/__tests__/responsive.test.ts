import {
  scaleWidth,
  scaleHeight,
  scaleFontSize,
  moderateScale,
  responsive,
  getCurrentBreakpoint,
} from '../responsive';

describe('responsive utils', () => {
  describe('scaleWidth', () => {
    it('should scale width proportionally', () => {
      const result = scaleWidth(100);
      expect(result).toBeGreaterThan(0);
      expect(typeof result).toBe('number');
    });
  });

  describe('scaleHeight', () => {
    it('should scale height proportionally', () => {
      const result = scaleHeight(100);
      expect(result).toBeGreaterThan(0);
      expect(typeof result).toBe('number');
    });
  });

  describe('scaleFontSize', () => {
    it('should scale font size', () => {
      const result = scaleFontSize(16);
      expect(result).toBeGreaterThan(0);
      expect(typeof result).toBe('number');
    });
  });

  describe('moderateScale', () => {
    it('should moderately scale value', () => {
      const result = moderateScale(20);
      expect(result).toBeGreaterThan(0);
      expect(typeof result).toBe('number');
    });

    it('should accept custom factor', () => {
      const result = moderateScale(20, 0.7);
      expect(result).toBeGreaterThan(0);
    });
  });

  describe('getCurrentBreakpoint', () => {
    it('should return a valid breakpoint', () => {
      const breakpoint = getCurrentBreakpoint();
      expect(['xs', 'sm', 'md', 'lg', 'xl', 'xxl']).toContain(breakpoint);
    });
  });

  describe('responsive', () => {
    it('should return default value when no breakpoint matches', () => {
      const result = responsive({ default: 'default-value' });
      expect(result).toBe('default-value');
    });

    it('should return breakpoint-specific value when available', () => {
      const breakpoint = getCurrentBreakpoint();
      const result = responsive({
        [breakpoint]: 'breakpoint-value',
        default: 'default-value',
      });
      expect(result).toBe('breakpoint-value');
    });
  });
});
