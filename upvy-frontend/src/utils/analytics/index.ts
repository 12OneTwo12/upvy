/**
 * Firebase Analytics 모듈
 *
 * 사용 예시:
 * ```typescript
 * import { Analytics } from '@/utils/analytics';
 *
 * // 초기화 (App.tsx)
 * Analytics.initialize();
 *
 * // 이벤트 로깅 (Fire-and-Forget - await 없이 호출)
 * Analytics.logLogin('google');
 * Analytics.logLike(contentId, 'video');
 * Analytics.logSearch(query, resultCount);
 * ```
 */

export { Analytics } from './Analytics';
export * from './types';
