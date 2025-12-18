/**
 * Firebase Analytics Service
 *
 * Fire-and-Forget 패턴을 사용한 Analytics 서비스
 * - 모든 메서드는 await 없이 호출 (비동기 but 결과를 기다리지 않음)
 * - Firebase SDK 내부에서 큐잉 및 배치 처리
 * - 에러가 발생해도 앱 동작에 영향 없음
 *
 * Best Practices:
 * 1. API 레이어에서는 Analytics 호출 금지 (순수 HTTP 통신만)
 * 2. Custom Hooks의 onSuccess 콜백에서 호출
 * 3. 인증 관련은 auth hooks/store에서 호출
 * 4. await 사용하지 않음 (Fire-and-Forget)
 */

import analytics from '@react-native-firebase/analytics';
import Constants from 'expo-constants';
import {
  AnalyticsEvents,
  type AuthMethod,
  type CommentParams,
  type ContentType,
  type ContentUploadCompleteParams,
  type ContentUploadFailedParams,
  type ContentUploadStartParams,
  type ContentViewParams,
  type ErrorParams,
  type FollowParams,
  type LikeParams,
  type ProfileViewParams,
  type SaveParams,
  type ScreenName,
  type SearchParams,
  type ShareMethod,
  type ShareParams,
  type UnfollowParams,
  type UnlikeParams,
  type UnsaveParams,
  type UserProperties,
  type VideoCompleteParams,
  type VideoPauseParams,
  type VideoPlayParams,
} from './types';

class AnalyticsService {
  private enabled: boolean = false;
  private environment: 'development' | 'production' = 'development';

  /**
   * Analytics 초기화
   * 모든 환경에서 활성화하되, environment 파라미터로 구분
   */
  initialize(): void {
    const firebaseAnalyticsEnabled =
      Constants.expoConfig?.extra?.firebaseAnalyticsEnabled ?? false;
    this.environment = (Constants.expoConfig?.extra?.environment ?? 'development') as 'development' | 'production';

    // Firebase Analytics가 활성화된 경우에만 작동
    this.enabled = firebaseAnalyticsEnabled;

    if (__DEV__) {
      console.log(
        `[Analytics] Initialized - Enabled: ${this.enabled}, Environment: ${this.environment}`
      );
    }
  }

  /**
   * Private 헬퍼 메서드: 공통 이벤트 로깅 로직을 처리
   * @param eventName 이벤트 이름
   * @param params 이벤트 파라미터
   * @param logMessage 개발 환경 로그 메시지
   */
  private logCustomEvent(eventName: string, params: object, logMessage: string): void {
    if (!this.enabled) return;

    try {
      const fullParams = { ...params, environment: this.environment };
      analytics().logEvent(eventName, fullParams);

      if (__DEV__) {
        console.log(`[Analytics] ${logMessage}:`, fullParams);
      }
    } catch (error) {
      console.error(`[Analytics] Failed to log ${eventName}:`, error);
    }
  }

  // ===== 인증 이벤트 =====

  /**
   * 로그인 이벤트
   * @param method 로그인 방법 (google, apple, email)
   */
  logLogin(method: AuthMethod): void {
    if (!this.enabled) return;

    try {
      // Firebase 표준 이벤트는 environment 파라미터를 받지 않음
      analytics().logLogin({ method });

      if (__DEV__) {
        console.log('[Analytics] Login:', { method, environment: this.environment });
      }
    } catch (error) {
      console.error('[Analytics] Failed to log login:', error);
    }
  }

  /**
   * 회원가입 이벤트
   * @param method 회원가입 방법 (google, apple, email)
   */
  logSignUp(method: AuthMethod): void {
    if (!this.enabled) return;

    try {
      // Firebase 표준 이벤트는 environment 파라미터를 받지 않음
      analytics().logSignUp({ method });

      if (__DEV__) {
        console.log('[Analytics] Sign up:', { method, environment: this.environment });
      }
    } catch (error) {
      console.error('[Analytics] Failed to log sign up:', error);
    }
  }

  /**
   * 로그아웃 이벤트
   */
  logLogout(): void {
    this.logCustomEvent(AnalyticsEvents.LOGOUT, {}, 'Logout');
  }

  /**
   * 사용자 ID 설정
   * @param userId 사용자 ID (로그아웃 시 null)
   */
  setUserId(userId: string | null): void {
    if (!this.enabled) return;

    try {
      analytics().setUserId(userId);

      if (__DEV__) {
        console.log('[Analytics] Set user ID:', userId);
      }
    } catch (error) {
      console.error('[Analytics] Failed to set user ID:', error);
    }
  }

  // ===== 콘텐츠 상호작용 이벤트 =====

  /**
   * 콘텐츠 조회 이벤트
   * @param contentId 콘텐츠 ID
   * @param contentType 콘텐츠 타입
   * @param additionalParams 추가 파라미터 (카테고리, 제작자 등)
   */
  logContentView(
    contentId: string,
    contentType: ContentType,
    additionalParams?: Partial<ContentViewParams>
  ): void {
    if (!this.enabled) return;

    try {
      const params: ContentViewParams = {
        content_id: contentId,
        content_type: contentType,
        environment: this.environment,
        ...additionalParams,
      };

      analytics().logEvent(AnalyticsEvents.CONTENT_VIEW, params);

      if (__DEV__) {
        console.log('[Analytics] Content view:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log content view:', error);
    }
  }

  /**
   * 좋아요 이벤트
   * @param contentId 콘텐츠 ID
   * @param contentType 콘텐츠 타입
   * @param creatorId 제작자 ID (선택)
   */
  logLike(contentId: string, contentType: ContentType, creatorId?: string): void {
    if (!this.enabled) return;

    try {
      const params: LikeParams = {
        content_id: contentId,
        content_type: contentType,
        creator_id: creatorId,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.LIKE, params);

      if (__DEV__) {
        console.log('[Analytics] Like:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log like:', error);
    }
  }

  /**
   * 좋아요 취소 이벤트
   * @param contentId 콘텐츠 ID
   * @param contentType 콘텐츠 타입
   */
  logUnlike(contentId: string, contentType: ContentType): void {
    const params: UnlikeParams = {
      content_id: contentId,
      content_type: contentType,
    };

    this.logCustomEvent(AnalyticsEvents.UNLIKE, params, 'Unlike');
  }

  /**
   * 저장 이벤트
   * @param contentId 콘텐츠 ID
   * @param contentType 콘텐츠 타입
   */
  logSave(contentId: string, contentType: ContentType): void {
    const params: SaveParams = {
      content_id: contentId,
      content_type: contentType,
    };

    this.logCustomEvent(AnalyticsEvents.SAVE, params, 'Save');
  }

  /**
   * 저장 취소 이벤트
   * @param contentId 콘텐츠 ID
   * @param contentType 콘텐츠 타입
   */
  logUnsave(contentId: string, contentType: ContentType): void {
    const params: UnsaveParams = {
      content_id: contentId,
      content_type: contentType,
    };

    this.logCustomEvent(AnalyticsEvents.UNSAVE, params, 'Unsave');
  }

  /**
   * 댓글 이벤트
   * @param contentId 콘텐츠 ID
   * @param commentLength 댓글 길이
   */
  logComment(contentId: string, commentLength: number): void {
    if (!this.enabled) return;

    try {
      const params: CommentParams = {
        content_id: contentId,
        comment_length: commentLength,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.COMMENT, params);

      if (__DEV__) {
        console.log('[Analytics] Comment:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log comment:', error);
    }
  }

  /**
   * 공유 이벤트
   * @param contentId 콘텐츠 ID
   * @param contentType 콘텐츠 타입
   * @param method 공유 방법
   */
  logShare(contentId: string, contentType: ContentType, method: ShareMethod): void {
    if (!this.enabled) return;

    try {
      const params: ShareParams = {
        content_id: contentId,
        content_type: contentType,
        method,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.SHARE, params);

      if (__DEV__) {
        console.log('[Analytics] Share:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log share:', error);
    }
  }

  // ===== 사용자 행동 이벤트 =====

  /**
   * 검색 이벤트
   * @param searchTerm 검색어
   * @param resultCount 검색 결과 수
   * @param category 카테고리 (선택)
   */
  logSearch(searchTerm: string, resultCount: number, category?: string): void {
    if (!this.enabled) return;

    try {
      // Firebase 표준 이벤트는 environment 파라미터를 받지 않음
      const params = {
        search_term: searchTerm,
        result_count: resultCount,
        category,
      };

      analytics().logSearch(params);

      if (__DEV__) {
        console.log('[Analytics] Search:', { ...params, environment: this.environment });
      }
    } catch (error) {
      console.error('[Analytics] Failed to log search:', error);
    }
  }

  /**
   * 프로필 조회 이벤트
   * @param userId 사용자 ID
   * @param isSelf 본인 프로필 여부
   */
  logProfileView(userId: string, isSelf: boolean): void {
    if (!this.enabled) return;

    try {
      const params: ProfileViewParams = {
        user_id: userId,
        is_self: isSelf,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.PROFILE_VIEW, params);

      if (__DEV__) {
        console.log('[Analytics] Profile view:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log profile view:', error);
    }
  }

  /**
   * 팔로우 이벤트
   * @param userId 팔로우할 사용자 ID
   */
  logFollow(userId: string): void {
    const params: FollowParams = {
      user_id: userId,
    };

    this.logCustomEvent(AnalyticsEvents.FOLLOW, params, 'Follow');
  }

  /**
   * 언팔로우 이벤트
   * @param userId 언팔로우할 사용자 ID
   */
  logUnfollow(userId: string): void {
    const params: UnfollowParams = {
      user_id: userId,
    };

    this.logCustomEvent(AnalyticsEvents.UNFOLLOW, params, 'Unfollow');
  }

  // ===== 콘텐츠 업로드 이벤트 =====

  /**
   * 콘텐츠 업로드 시작 이벤트
   * @param contentType 콘텐츠 타입
   * @param category 카테고리 (선택)
   */
  logContentUploadStart(contentType: ContentType, category?: string): void {
    if (!this.enabled) return;

    try {
      const params: ContentUploadStartParams = {
        content_type: contentType,
        category,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.CONTENT_UPLOAD_START, params);

      if (__DEV__) {
        console.log('[Analytics] Content upload start:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log content upload start:', error);
    }
  }

  /**
   * 콘텐츠 업로드 완료 이벤트
   * @param contentType 콘텐츠 타입
   * @param category 카테고리 (선택)
   * @param additionalParams 추가 파라미터
   */
  logContentUploadComplete(
    contentType: ContentType,
    category?: string,
    additionalParams?: Partial<ContentUploadCompleteParams>
  ): void {
    if (!this.enabled) return;

    try {
      const params: ContentUploadCompleteParams = {
        content_type: contentType,
        category,
        environment: this.environment,
        ...additionalParams,
      };

      analytics().logEvent(AnalyticsEvents.CONTENT_UPLOAD_COMPLETE, params);

      if (__DEV__) {
        console.log('[Analytics] Content upload complete:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log content upload complete:', error);
    }
  }

  /**
   * 콘텐츠 업로드 실패 이벤트
   * @param errorMessage 에러 메시지
   */
  logContentUploadFailed(errorMessage: string): void {
    if (!this.enabled) return;

    try {
      const params: ContentUploadFailedParams = {
        error_message: errorMessage,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.CONTENT_UPLOAD_FAILED, params);

      if (__DEV__) {
        console.log('[Analytics] Content upload failed:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log content upload failed:', error);
    }
  }

  // ===== 비디오 재생 이벤트 =====

  /**
   * 비디오 재생 이벤트
   * @param contentId 콘텐츠 ID
   * @param position 재생 위치 (초)
   * @param duration 총 길이 (초)
   */
  logVideoPlay(contentId: string, position: number, duration: number): void {
    if (!this.enabled) return;

    try {
      const params: VideoPlayParams = {
        content_id: contentId,
        position,
        duration,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.VIDEO_PLAY, params);

      if (__DEV__) {
        console.log('[Analytics] Video play:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log video play:', error);
    }
  }

  /**
   * 비디오 일시정지 이벤트
   * @param contentId 콘텐츠 ID
   * @param position 일시정지 위치 (초)
   */
  logVideoPause(contentId: string, position: number): void {
    if (!this.enabled) return;

    try {
      const params: VideoPauseParams = {
        content_id: contentId,
        position,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.VIDEO_PAUSE, params);

      if (__DEV__) {
        console.log('[Analytics] Video pause:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log video pause:', error);
    }
  }

  /**
   * 비디오 완료 이벤트
   * @param contentId 콘텐츠 ID
   * @param watchTime 시청 시간 (초)
   * @param duration 총 길이 (초)
   */
  logVideoComplete(contentId: string, watchTime: number, duration: number): void {
    if (!this.enabled) return;

    try {
      const params: VideoCompleteParams = {
        content_id: contentId,
        watch_time: watchTime,
        duration,
        environment: this.environment,
      };

      analytics().logEvent(AnalyticsEvents.VIDEO_COMPLETE, params);

      if (__DEV__) {
        console.log('[Analytics] Video complete:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log video complete:', error);
    }
  }

  // ===== 화면 추적 이벤트 =====

  /**
   * 화면 조회 이벤트
   * @param screenName 화면 이름
   */
  logScreenView(screenName: ScreenName): void {
    if (!this.enabled) return;

    try {
      // Firebase 표준 이벤트는 environment 파라미터를 받지 않음
      analytics().logScreenView({
        screen_name: screenName,
        screen_class: screenName,
      });

      if (__DEV__) {
        console.log('[Analytics] Screen view:', { screen_name: screenName, environment: this.environment });
      }
    } catch (error) {
      console.error('[Analytics] Failed to log screen view:', error);
    }
  }

  // ===== 에러 추적 =====

  /**
   * 에러 이벤트
   * @param errorMessage 에러 메시지
   * @param additionalParams 추가 파라미터
   */
  logError(errorMessage: string, additionalParams?: Partial<ErrorParams>): void {
    if (!this.enabled) return;

    try {
      const params: ErrorParams = {
        error_message: errorMessage,
        environment: this.environment,
        ...additionalParams,
      };

      analytics().logEvent(AnalyticsEvents.ERROR, params);

      if (__DEV__) {
        console.log('[Analytics] Error:', params);
      }
    } catch (error) {
      console.error('[Analytics] Failed to log error:', error);
    }
  }

  // ===== 사용자 속성 =====

  /**
   * 사용자 속성 설정
   * @param properties 사용자 속성
   */
  setUserProperties(properties: UserProperties): void {
    if (!this.enabled) return;

    try {
      Object.entries(properties).forEach(([key, value]) => {
        if (value !== undefined) {
          analytics().setUserProperty(
            key,
            Array.isArray(value) ? value.join(',') : String(value)
          );
        }
      });

      if (__DEV__) {
        console.log('[Analytics] Set user properties:', properties);
      }
    } catch (error) {
      console.error('[Analytics] Failed to set user properties:', error);
    }
  }
}

// Singleton instance
export const Analytics = new AnalyticsService();
