/**
 * 피드 아이템 컴포넌트
 *
 * 비디오 플레이어 + 오버레이를 결합한 완전한 피드 아이템
 */

import React, { useState, useRef, useCallback, useContext, useEffect } from 'react';
import { View, Dimensions, Animated, PanResponder, TouchableWithoutFeedback } from 'react-native';
import { LinearGradient } from 'expo-linear-gradient';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { BottomTabBarHeightContext } from '@react-navigation/bottom-tabs';
import { VideoPlayer, VideoPlayerRef } from './VideoPlayer';
import { PhotoGallery } from './PhotoGallery';
import { FeedOverlay } from './FeedOverlay';
import { LikeAnimation } from './LikeAnimation';
import { QuizOverlay } from '@/components/quiz';
import { useQuiz } from '@/hooks/useQuiz';
import { useQuizStore } from '@/stores/quizStore';
import type { FeedItem as FeedItemType } from '@/types/feed.types';

/**
 * Bottom Tab Bar Height를 안전하게 가져오는 Hook
 * Bottom Tab Navigator 외부에서 사용될 때 0 반환
 */
const useSafeBottomTabBarHeight = (): number => {
  const height = useContext(BottomTabBarHeightContext);
  return height ?? 0;
};

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

// 재생바 관련 상수
const PROGRESS_BAR_HEIGHT_NORMAL = 3;
const PROGRESS_BAR_HEIGHT_DRAGGING = 7;
const PROGRESS_BAR_BOTTOM_OFFSET = 8.2;
const PROGRESS_BAR_DEFAULT_BOTTOM = 24; // 탭바가 없을 때 하단 여백

interface FeedItemProps {
  item: FeedItemType;
  isFocused: boolean;
  shouldPreload?: boolean;
  hasBeenLoaded?: boolean; // 외부에서 관리되는 로드 상태
  onVideoLoaded?: () => void; // 로드 완료 콜백
  onLike?: () => void;
  onComment?: () => void;
  onSave?: () => void;
  onShare?: () => void;
  onFollow?: () => void;
  onCreatorPress?: () => void;
  onBlockSuccess?: () => void; // 차단 성공 시 호출
  onDeleteSuccess?: () => void; // 삭제 성공 시 호출
}

export const FeedItem: React.FC<FeedItemProps> = ({
  item,
  isFocused,
  shouldPreload = false,
  hasBeenLoaded = false,
  onVideoLoaded,
  onLike,
  onComment,
  onSave,
  onShare,
  onFollow,
  onCreatorPress,
  onBlockSuccess,
  onDeleteSuccess,
}) => {
  const tabBarHeight = useSafeBottomTabBarHeight();
  const [isExpanded, setIsExpanded] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const [isDragging, setIsDragging] = useState(false);
  const [showLikeAnimation, setShowLikeAnimation] = useState(false);
  const [isLongPressing, setIsLongPressing] = useState(false);
  const progressAnim = useRef(new Animated.Value(0)).current;
  const overlayOpacity = useRef(new Animated.Value(1)).current;
  const videoPlayerRef = useRef<VideoPlayerRef>(null);

  // 퀴즈 상태
  const [quizVisible, setQuizVisible] = useState(false);
  const { isQuizAutoDisplayEnabled } = useQuizStore();
  const [hasAutoShownQuiz, setHasAutoShownQuiz] = useState(false);
  const [wasAutoOpened, setWasAutoOpened] = useState(false); // 현재 열린 퀴즈가 자동으로 열린 것인지
  const wasPlayingBeforeQuiz = useRef(false); // 퀴즈 열기 전 재생 상태 저장

  // 퀴즈 로직
  const {
    quiz,
    isLoadingQuiz,
    submitAttemptAsync,
    attemptResult,
    isSubmitting,
    isSubmitSuccess,
  } = useQuiz(
    item.contentId,
    {
      onSuccess: () => {
        // 퀴즈 제출 성공 시 처리
      },
    }
  );

  // 퀴즈 핸들러
  const handleQuizPress = useCallback(() => {
    setQuizVisible(true);
    setWasAutoOpened(false); // 수동으로 열었으므로 false
  }, []);

  const handleQuizClose = useCallback(() => {
    setQuizVisible(false);
    setWasAutoOpened(false); // 닫을 때 리셋
  }, []);

  // 아이템 변경 시 자동 표시 상태 리셋
  useEffect(() => {
    setHasAutoShownQuiz(false);
    setQuizVisible(false);
  }, [item.contentId]);

  // 퀴즈 모달 열림/닫힘 시 비디오 제어
  useEffect(() => {
    // VIDEO 타입일 때만 비디오 제어
    if (item.contentType !== 'VIDEO') return;

    const controlVideoForQuiz = async () => {
      if (!videoPlayerRef.current) return;

      if (quizVisible) {
        // 퀴즈 열림 → 현재 재생 상태 저장 후 일시정지
        try {
          const isPlaying = await videoPlayerRef.current.getIsPlaying();
          wasPlayingBeforeQuiz.current = isPlaying;
          await videoPlayerRef.current.pauseAsync();
        } catch (error) {
          // VideoPlayer가 아직 초기화 중일 수 있음 - 무시
          console.log('Video not ready yet, skipping pause');
        }
      } else {
        // 퀴즈 닫힘 → 기본값은 재생 (단, 사용자가 명시적으로 일시정지했으면 제외)
        const userPaused = videoPlayerRef.current.getUserPaused();
        if (!userPaused) {
          try {
            await videoPlayerRef.current.playAsync();
          } catch (error) {
            console.log('Video not ready yet, skipping play');
          }
        }
      }
    };

    controlVideoForQuiz();
  }, [quizVisible, item.contentType]);

  // 퀴즈 자동 표시 로직
  useEffect(() => {
    if (!isFocused || !isQuizAutoDisplayEnabled) return;
    if (hasAutoShownQuiz || quizVisible) return;
    if (isLoadingQuiz) return;

    if (quiz && item.quiz) {
      // VideoPlayer 초기화 시간을 위해 약간 지연 (재마운트 시 타이밍 이슈 방지)
      const timer = setTimeout(() => {
        setQuizVisible(true);
        setHasAutoShownQuiz(true);
        setWasAutoOpened(true); // 자동으로 열었으므로 true
      }, 300);

      return () => clearTimeout(timer);
    }
  }, [isFocused, isQuizAutoDisplayEnabled, hasAutoShownQuiz, quizVisible, quiz, isLoadingQuiz, item.quiz, item.contentId]);

  // 컨텐츠 탭 시 더보기 또는 퀴즈 닫기
  const handleContentTap = useCallback(() => {
    if (quizVisible) {
      setQuizVisible(false);
      return true;
    }
    if (isExpanded) {
      setIsExpanded(false);
      return true;
    }
    return false;
  }, [isExpanded, quizVisible]);

  // 좋아요 핸들러 (하트 애니메이션 포함)
  const handleLike = useCallback(() => {
    // 좋아요가 안 되어있을 때만 하트 애니메이션 표시
    if (!item.interactions.isLiked) {
      setShowLikeAnimation(true);
    }
    onLike?.();
  }, [item.interactions.isLiked, onLike]);

  // 애니메이션 완료 핸들러
  const handleAnimationComplete = useCallback(() => {
    setShowLikeAnimation(false);
  }, []);

  // 롱프레스 상태에 따라 오버레이 페이드 아웃/인
  useEffect(() => {
    Animated.timing(overlayOpacity, {
      toValue: isLongPressing ? 0 : 1,
      duration: 200,
      useNativeDriver: true,
    }).start();
  }, [isLongPressing, overlayOpacity]);

  // 진행률 업데이트 받기
  const handleProgressUpdate = (prog: number, dur: number, dragging: boolean) => {
    // duration은 항상 업데이트
    if (dur > 0 && dur !== duration) {
      setDuration(dur);
    }

    // 드래그 중이 아닐 때만 progress 업데이트
    if (!dragging) {
      setProgress(prog);
      Animated.timing(progressAnim, {
        toValue: prog,
        duration: 100,
        useNativeDriver: false,
      }).start();
    }
  };

  // 재생바 드래그
  const dragStartDuration = useRef<number>(0);
  const lastSeekTime = useRef<number>(0);
  const isSeeking = useRef<boolean>(false);

  // progress 계산 헬퍼 함수
  const calculateProgress = (pageX: number): number => {
    return Math.max(0, Math.min(1, pageX / SCREEN_WIDTH));
  };

  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (event) => {
        setIsDragging(true);
        // VideoPlayer에서 직접 duration 가져오기
        const videoDuration = videoPlayerRef.current?.getDuration() || 0;
        dragStartDuration.current = videoDuration;
        lastSeekTime.current = 0;
        isSeeking.current = false;
      },
      onPanResponderMove: async (event, gestureState) => {
        // pageX: 화면 전체 기준 X 좌표 사용
        const pageX = event.nativeEvent.pageX;
        const newProgress = calculateProgress(pageX);

        // 애니메이션 업데이트 (항상)
        progressAnim.setValue(newProgress);

        // Throttle: 100ms마다 한 번씩만 seek (성능 최적화)
        const now = Date.now();
        const timeSinceLastSeek = now - lastSeekTime.current;
        const SEEK_THROTTLE_MS = 100;

        if (timeSinceLastSeek >= SEEK_THROTTLE_MS && !isSeeking.current) {
          lastSeekTime.current = now;
          isSeeking.current = true;

          const seekDuration = dragStartDuration.current;
          if (videoPlayerRef.current && seekDuration > 0) {
            try {
              await videoPlayerRef.current.seek(newProgress, seekDuration);
              setProgress(newProgress);
            } catch (error) {
              // 드래그 중 seek 에러는 무시 (성능상 일부 프레임 스킵 가능)
            } finally {
              isSeeking.current = false;
            }
          } else {
            isSeeking.current = false;
          }
        }
      },
      onPanResponderRelease: async (event, gestureState) => {
        // pageX: 화면 전체 기준 X 좌표 사용
        const pageX = event.nativeEvent.pageX;
        const newProgress = calculateProgress(pageX);
        const seekDuration = dragStartDuration.current;

        // 진행 중인 seek가 완료될 때까지 대기
        while (isSeeking.current) {
          await new Promise(resolve => setTimeout(resolve, 10));
        }

        // 최종 위치로 seek
        if (videoPlayerRef.current && seekDuration > 0) {
          try {
            await videoPlayerRef.current.seek(newProgress, seekDuration);
            setProgress(newProgress);
          } catch (error) {
            console.error('Seek failed:', error);
            // seek 실패 시 원래 progress로 복원
            progressAnim.setValue(progress);
          }
        }

        // 드래그 종료
        setIsDragging(false);
      },
    })
  ).current;

  return (
    <View style={{ height: SCREEN_HEIGHT }} className="relative">
      {/* 콘텐츠 렌더링 - VIDEO, PHOTO, QUIZ */}
      {item.contentType === 'QUIZ' ? (
        // QUIZ 전용 콘텐츠: 그라데이션 배경
        <TouchableWithoutFeedback onPress={handleContentTap}>
          <LinearGradient
            colors={['#667eea', '#764ba2', '#f093fb']}
            start={{ x: 0, y: 0 }}
            end={{ x: 1, y: 1 }}
            style={{
              width: SCREEN_WIDTH,
              height: SCREEN_HEIGHT,
            }}
          />
        </TouchableWithoutFeedback>
      ) : item.contentType === 'VIDEO' ? (
        <VideoPlayer
          ref={videoPlayerRef}
          uri={item.url}
          isFocused={isFocused}
          shouldPreload={shouldPreload}
          hasBeenLoaded={hasBeenLoaded}
          isDragging={isDragging}
          isExpanded={isExpanded}
          onVideoLoaded={onVideoLoaded}
          onDoubleTap={handleLike}
          onTap={handleContentTap}
          onProgressUpdate={handleProgressUpdate}
          onLongPressChange={setIsLongPressing}
        />
      ) : item.contentType === 'PHOTO' && item.photoUrls && item.photoUrls.length > 0 ? (
        <PhotoGallery
          photoUrls={item.photoUrls}
          width={SCREEN_WIDTH}
          height={SCREEN_HEIGHT}
          isExpanded={isExpanded}
          onDoubleTap={handleLike}
          onTap={handleContentTap}
        />
      ) : null}

      {/* 정보 오버레이 - 롱프레스 중에는 페이드 아웃 */}
      <Animated.View
        style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          opacity: overlayOpacity,
        }}
        pointerEvents={isLongPressing ? 'none' : 'box-none'}
      >
        <FeedOverlay
          creator={item.creator}
          title={item.title}
          description={item.description}
          interactions={item.interactions}
          contentId={item.contentId}
          category={item.category}
          tags={item.tags}
          quiz={item.quiz}
          onLike={onLike}
          onComment={onComment}
          onSave={onSave}
          onShare={onShare}
          onFollow={onFollow}
          onCreatorPress={onCreatorPress}
          onQuizPress={handleQuizPress}
          onBlockSuccess={onBlockSuccess}
          onDeleteSuccess={onDeleteSuccess}
          isExpanded={isExpanded}
          setIsExpanded={setIsExpanded}
          tabBarHeight={tabBarHeight}
        />
      </Animated.View>

      {/* 퀴즈 오버레이 - 최상위 레이어 */}
      {quiz && submitAttemptAsync && (
        <QuizOverlay
          visible={quizVisible}
          onClose={handleQuizClose}
          quiz={quiz}
          onSubmit={submitAttemptAsync}
          attemptResult={attemptResult}
          isSubmitting={isSubmitting}
          isSubmitSuccess={isSubmitSuccess}
          isAutoDisplayed={wasAutoOpened}
        />
      )}

      {/* 비디오 진행률 바 - 탭바 바로 위 */}
      {item.contentType === 'VIDEO' && item.url && (
        <View
          {...panResponder.panHandlers}
          style={{
            position: 'absolute',
            bottom: tabBarHeight > 0
              ? tabBarHeight - PROGRESS_BAR_BOTTOM_OFFSET
              : PROGRESS_BAR_DEFAULT_BOTTOM,
            left: 0,
            right: 0,
            height: 20,
            justifyContent: 'center',
            zIndex: 100,
          }}
        >
          <Animated.View style={{
            height: isDragging ? PROGRESS_BAR_HEIGHT_DRAGGING : PROGRESS_BAR_HEIGHT_NORMAL,
            backgroundColor: 'rgba(255, 255, 255, 0.3)',
          }}>
            <Animated.View
              style={{
                height: '100%',
                width: progressAnim.interpolate({
                  inputRange: [0, 1],
                  outputRange: ['0%', '100%'],
                }),
                backgroundColor: '#FFFFFF',
              }}
            />
          </Animated.View>
        </View>
      )}

      {/* 좋아요 애니메이션 - 모든 오버레이 위에 표시 */}
      {showLikeAnimation && (
        <View
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            justifyContent: 'center',
            alignItems: 'center',
            pointerEvents: 'none',
            zIndex: 9999,
          }}
        >
          <LikeAnimation show={showLikeAnimation} onComplete={handleAnimationComplete} />
        </View>
      )}
    </View>
  );
};
