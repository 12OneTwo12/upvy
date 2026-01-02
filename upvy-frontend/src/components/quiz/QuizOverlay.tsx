/**
 * 퀴즈 오버레이 컴포넌트
 *
 * Instagram Poll 스타일의 퀴즈 UI
 * - 퀴즈 질문과 보기 표시
 * - 실시간 통계 진행바
 * - 단일/복수 정답 지원
 * - 재시도, 건너뛰기, 비디오 보기 버튼
 */

import React, { useState, useEffect, useRef, useMemo, useCallback, useContext } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Animated,
  Easing,
  Dimensions,
  ScrollView,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
import { BottomTabBarHeightContext } from '@react-navigation/bottom-tabs';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import type {
  QuizResponse,
  QuizOptionResponse,
  QuizAttemptRequest,
  QuizAttemptResponse,
} from '@/types/quiz.types';

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

interface QuizOverlayProps {
  visible: boolean;
  onClose: () => void;
  quiz: QuizResponse | null;
  onSubmit: (request: QuizAttemptRequest) => Promise<QuizAttemptResponse>;
  attemptResult?: QuizAttemptResponse | null;
  isSubmitting?: boolean;
  isSubmitSuccess?: boolean;
  isAutoDisplayed?: boolean;
  onRetry?: () => void;
  onSkip?: () => void;
  onViewVideo?: () => void;
}

export const QuizOverlay: React.FC<QuizOverlayProps> = ({
  visible,
  onClose,
  quiz,
  onSubmit,
  attemptResult: externalAttemptResult,
  isSubmitting: externalIsSubmitting,
  isSubmitSuccess: externalIsSubmitSuccess,
  isAutoDisplayed = false,
  onRetry,
  onSkip,
  onViewVideo,
}) => {
  const { t } = useTranslation('quiz');
  const tabBarHeight = useContext(BottomTabBarHeightContext) ?? 0;
  const styles = useStyles();
  const theme = useTheme();

  // Calculate modal container style with tab bar adjustment
  const modalContainerStyle = useMemo(() => ({
    ...styles.modalContainer,
    paddingTop: tabBarHeight > 0 ? tabBarHeight / 2 : 0,
  }), [styles.modalContainer, tabBarHeight]);

  // Animation refs
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const hasAppliedExternalResult = useRef(false);

  // Internal state to control actual rendering (for smooth animation)
  const [isRendered, setIsRendered] = useState(false);

  // Selection state
  const [selectedOptionIds, setSelectedOptionIds] = useState<string[]>([]);
  const [submittedOptionIds, setSubmittedOptionIds] = useState<string[]>([]); // 제출한 옵션 ID 저장
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [isRetrying, setIsRetrying] = useState(false); // 다시 풀기 모드
  const [attemptResult, setAttemptResult] = useState<QuizAttemptResponse | null>(externalAttemptResult || null);
  const [isSubmitting, setIsSubmitting] = useState(externalIsSubmitting || false);

  // Handle visible prop changes with smooth animation
  useEffect(() => {
    if (visible) {
      // Show modal: render first, then animate in
      setIsRendered(true);
    } else {
      // Hide modal: animate out first, then unmount
      Animated.timing(fadeAnim, {
        toValue: 0,
        duration: 200,
        useNativeDriver: true,
      }).start(() => {
        setIsRendered(false);
        // Reset state after animation completes
        setSelectedOptionIds([]);
        setSubmittedOptionIds([]);
        setIsSubmitted(false);
        setIsRetrying(false);
        setAttemptResult(null);
        setIsSubmitting(false);
        hasAppliedExternalResult.current = false;
      });
    }
  }, [visible, fadeAnim]);

  // Reset state when quiz changes
  useEffect(() => {
    if (visible && quiz?.id) {
      setSelectedOptionIds([]);
      setSubmittedOptionIds([]);
      setIsSubmitted(false);
      setIsRetrying(false);
      setAttemptResult(null);
      setIsSubmitting(false);
      hasAppliedExternalResult.current = false;
    }
  }, [quiz?.id, visible]);

  // Check if user has already attempted (정답이 공개된 경우)
  const hasAttempted = useMemo(() => {
    return quiz?.options.some(option => option.isCorrect !== null) ?? false;
  }, [quiz]);

  // Set isSubmitted if user has already attempted
  useEffect(() => {
    if (hasAttempted && quiz) {
      setIsSubmitted(true);
      // Show user's previous selections if available
      const userSelectedOptions = quiz.options.filter(opt => opt.isSelected === true);
      if (userSelectedOptions.length > 0) {
        setSelectedOptionIds(userSelectedOptions.map(opt => opt.id));
        setSubmittedOptionIds(userSelectedOptions.map(opt => opt.id));
      }
    }
  }, [hasAttempted, quiz]);

  // 외부에서 전달받은 attemptResult가 있으면 결과 화면 표시 (한 번만, 다시 풀기 모드가 아닐 때만)
  useEffect(() => {
    if (externalAttemptResult && visible && !hasAppliedExternalResult.current && !isRetrying) {
      hasAppliedExternalResult.current = true;
      setAttemptResult(externalAttemptResult);
      setIsSubmitted(true);

      // 사용자가 선택한 옵션들 설정
      if (externalAttemptResult.options && Array.isArray(externalAttemptResult.options)) {
        const selectedIds = externalAttemptResult.options
          .filter(opt => opt.isSelected)
          .map(opt => opt.id);

        setSelectedOptionIds(selectedIds);
        setSubmittedOptionIds(selectedIds);
      }
    }
  }, [externalAttemptResult, visible, quiz]);

  // isRetrying 상태 변경 시 외부 결과 재적용 방지
  useEffect(() => {
    if (isRetrying) {
      hasAppliedExternalResult.current = true;
    }
  }, [isRetrying]);

  // Fade in animation when rendered
  useEffect(() => {
    if (isRendered) {
      Animated.timing(fadeAnim, {
        toValue: 1,
        duration: 300,
        useNativeDriver: true,
      }).start();
    }
  }, [isRendered, fadeAnim]);

  // 정답일 경우 2초 후 자동으로 모달 닫기 (자동 표시된 경우만)
  useEffect(() => {
    if (!isSubmitted || !visible || !isAutoDisplayed) return;

    // 새로 제출한 경우: attemptResult 확인
    const isCorrectFromResult = attemptResult?.isCorrect;

    // 이미 푼 퀴즈인 경우: 모든 정답 옵션이 선택되고 모든 오답 옵션이 선택되지 않았으면 정답
    const wasCorrectBefore = hasAttempted && quiz?.options.every(opt => {
      const isCorrectOption = opt.isCorrect === true;
      const isSelectedOption = opt.isSelected === true;

      if (isCorrectOption) {
        return isSelectedOption; // 정답은 선택되어야 함
      } else {
        return !isSelectedOption; // 오답은 선택되면 안 됨
      }
    });

    if (isCorrectFromResult || wasCorrectBefore) {
      const timer = setTimeout(() => {
        // fadeOut 애니메이션 시작
        Animated.timing(fadeAnim, {
          toValue: 0,
          duration: 300,
          useNativeDriver: true,
        }).start(() => {
          // fadeOut 완료 후 onClose 호출 (비디오 재생은 이 시점에 시작됨)
          onClose();
        });
      }, 2000);

      return () => clearTimeout(timer);
    }
  }, [isSubmitted, attemptResult?.isCorrect, hasAttempted, quiz, visible, isAutoDisplayed, onClose, fadeAnim]);

  // Handle option selection
  const handleOptionPress = useCallback((optionId: string) => {
    if (isSubmitted) return; // Cannot change selection after submission

    if (quiz?.allowMultipleAnswers) {
      // Multiple selection
      setSelectedOptionIds(prev =>
        prev.includes(optionId)
          ? prev.filter(id => id !== optionId)
          : [...prev, optionId]
      );
    } else {
      // Single selection
      setSelectedOptionIds([optionId]);
    }
  }, [quiz?.allowMultipleAnswers, isSubmitted]);

  // Handle submit
  const handleSubmit = useCallback(async () => {
    if (!quiz || selectedOptionIds.length === 0 || isSubmitting) return;

    setIsSubmitting(true);
    try {
      const result = await onSubmit({ selectedOptionIds });
      setSubmittedOptionIds([...selectedOptionIds]); // 제출한 옵션 ID 저장
      setAttemptResult(result);
      setIsSubmitted(true);
      setIsRetrying(false); // 다시 풀기 모드 종료
      hasAppliedExternalResult.current = true; // 외부 결과 재적용 방지
    } catch (error) {
      console.error('Failed to submit quiz attempt:', error);
      Alert.alert(t('overlay.submitError'));
    } finally {
      setIsSubmitting(false);
    }
  }, [quiz, selectedOptionIds, isSubmitting, onSubmit, t]);

  // Handle retry
  const handleRetry = useCallback(() => {
    setIsRetrying(true);
    setSelectedOptionIds([]);
    setSubmittedOptionIds([]);
    setIsSubmitted(false);
    setAttemptResult(null);
    if (onRetry) {
      onRetry();
    }
  }, [onRetry]);

  // Get option display data (either from attempt result or original quiz)
  const displayOptions = useMemo(() => {
    // 다시 풀기 모드면 항상 원본 퀴즈 옵션 사용
    if (isRetrying) {
      return quiz?.options ?? [];
    }
    // 제출 결과가 있으면 결과 옵션 사용
    if (attemptResult && attemptResult.options) {
      return attemptResult.options;
    }
    return quiz?.options ?? [];
  }, [attemptResult, quiz, isRetrying]);


  if (!quiz) return null;

  if (!isRendered) return null;

  return (
    <>
      {/* Backdrop */}
      <Animated.View
        style={[
          styles.backdrop,
          {
            opacity: fadeAnim,
          },
        ]}
      >
        <TouchableOpacity
          style={styles.backdropTouchable}
          activeOpacity={1}
          onPress={onClose}
        />
      </Animated.View>

      {/* Quiz Modal */}
      <Animated.View
        style={[
          modalContainerStyle,
          {
            opacity: fadeAnim,
          },
        ]}
        pointerEvents="box-none"
      >
        <TouchableOpacity
          style={styles.modalBackdrop}
          activeOpacity={1}
          onPress={onClose}
        />
        <View style={styles.modal}>
          {/* Handle Bar */}
          <View style={styles.handleBar} />

          {/* Close Button */}
          <TouchableOpacity style={styles.closeButton} onPress={onClose}>
            <Ionicons name="close" size={28} color="#FFFFFF" />
          </TouchableOpacity>

          <ScrollView
            style={styles.scrollView}
            contentContainerStyle={styles.scrollContent}
            showsVerticalScrollIndicator={false}
          >
            {/* Quiz Question */}
            <View style={styles.questionContainer}>
              <Text style={styles.questionText}>{quiz.question}</Text>
              {quiz.allowMultipleAnswers && (
                <Text style={styles.multipleAnswerHint}>
                  {t('overlay.multipleAnswerHint')}
                </Text>
              )}
            </View>

            {/* Options */}
            <View style={styles.optionsContainer}>
              {displayOptions.map((option, index) => {
                const isSelected = selectedOptionIds.includes(option.id);
                const wasSubmitted = submittedOptionIds.includes(option.id);
                const isCorrect = option.isCorrect === true;
                const isWrong = isSubmitted && wasSubmitted && option.isCorrect === false;
                const showProgress = isSubmitted;
                const progressWidth = option.selectionPercentage || 0;

                return (
                  <TouchableOpacity
                    key={option.id}
                    style={[
                      styles.optionButton,
                      isSelected && !isSubmitted && styles.optionButtonSelected,
                      isSubmitted && isCorrect && styles.optionButtonCorrect,
                      isSubmitted && isWrong && styles.optionButtonWrong,
                    ]}
                    onPress={() => handleOptionPress(option.id)}
                    disabled={isSubmitted}
                    activeOpacity={0.7}
                  >
                    {/* Progress Bar (shown after submission) */}
                    {showProgress && (
                      <Animated.View
                        style={[
                          styles.progressBar,
                          {
                            width: `${progressWidth}%`,
                            backgroundColor: isCorrect
                              ? 'rgba(76, 175, 80, 0.5)'
                              : isWrong
                              ? 'rgba(244, 67, 54, 0.5)'
                              : 'rgba(150, 150, 150, 0.3)',
                          },
                        ]}
                      />
                    )}

                    {/* Option Content */}
                    <View style={styles.optionContent}>
                      {/* Checkbox/Radio Circle */}
                      <View
                        style={[
                          styles.optionCircle,
                          isSelected && !isSubmitted && styles.optionCircleSelected,
                          isSubmitted && isCorrect && styles.optionCircleCorrect,
                          isSubmitted && isWrong && styles.optionCircleWrong,
                        ]}
                      >
                        {/* 제출 전: 선택한 것만 체크마크 */}
                        {/* 제출 후: 내가 고른 것만 체크마크 (정답이든 오답이든) */}
                        {((!isSubmitted && isSelected) || (isSubmitted && wasSubmitted)) && (
                          <Ionicons
                            name="checkmark"
                            size={16}
                            color={
                              isSubmitted
                                ? isWrong
                                  ? '#F44336'
                                  : '#4CAF50'
                                : '#FFFFFF'
                            }
                          />
                        )}
                      </View>

                      {/* Option Text */}
                      <Text
                        style={[
                          styles.optionText,
                          isSelected && !isSubmitted && styles.optionTextSelected,
                          isSubmitted && isCorrect && styles.optionTextCorrect,
                          isSubmitted && isWrong && styles.optionTextWrong,
                        ]}
                      >
                        {option.optionText}
                      </Text>

                      {/* Percentage (shown after submission) */}
                      {showProgress && (
                        <Text style={[
                          styles.percentageText,
                          {
                            color: isCorrect
                              ? '#4CAF50'
                              : isWrong
                              ? '#F44336'
                              : '#AAAAAA'
                          }
                        ]}>
                          {option.selectionPercentage.toFixed(1)}%
                        </Text>
                      )}

                      {/* Correct/Wrong Icon */}
                      {isSubmitted && wasSubmitted && (
                        <Ionicons
                          name={isCorrect ? "checkmark-circle" : "close-circle"}
                          size={20}
                          color={isCorrect ? '#4CAF50' : '#F44336'}
                        />
                      )}
                    </View>
                  </TouchableOpacity>
                );
              })}
            </View>

            {/* Result Message */}
            {attemptResult && (
              <View style={styles.resultContainer}>
                <Ionicons
                  name={attemptResult.isCorrect ? "checkmark-circle" : "close-circle"}
                  size={48}
                  color={attemptResult.isCorrect ? theme.colors.success : theme.colors.error}
                />
                <Text style={styles.resultText}>
                  {attemptResult.isCorrect ? t('overlay.resultCorrect') : t('overlay.resultIncorrect')}
                </Text>
                <Text style={styles.attemptCountText}>
                  {t('overlay.attemptCount', { count: attemptResult.attemptNumber })}
                </Text>
              </View>
            )}

            {/* Statistics */}
            {isSubmitted && (
              <View style={styles.statsContainer}>
                <Text style={styles.statsText}>
                  {t('overlay.totalParticipants', { count: quiz.totalAttempts })}
                </Text>
              </View>
            )}

            {/* Action Buttons */}
            <View style={styles.actionsContainer}>
              {!isSubmitted ? (
                // Submit Button
                <TouchableOpacity
                  style={[
                    styles.submitButton,
                    selectedOptionIds.length === 0 && styles.submitButtonDisabled,
                  ]}
                  onPress={handleSubmit}
                  disabled={selectedOptionIds.length === 0 || isSubmitting}
                  accessibilityLabel={t('overlay.submit')}
                  accessibilityRole="button"
                  accessibilityState={{ disabled: selectedOptionIds.length === 0 || isSubmitting }}
                >
                  <Text style={[
                    styles.submitButtonText,
                    selectedOptionIds.length === 0 && styles.submitButtonTextDisabled,
                  ]}>
                    {isSubmitting ? t('overlay.submitting') : t('overlay.submit')}
                  </Text>
                </TouchableOpacity>
              ) : (
                // Post-submission buttons
                <>
                  <TouchableOpacity
                    style={styles.retryButton}
                    onPress={handleRetry}
                    accessibilityLabel={t('overlay.retry')}
                    accessibilityRole="button"
                  >
                    <Ionicons name="refresh" size={20} color={theme.colors.primary} />
                    <Text style={styles.retryButtonText}>{t('overlay.retry')}</Text>
                  </TouchableOpacity>

                  {onViewVideo && (
                    <TouchableOpacity
                      style={styles.viewVideoButton}
                      onPress={() => {
                        onViewVideo();
                        onClose();
                      }}
                      accessibilityLabel={t('overlay.viewVideo')}
                      accessibilityRole="button"
                    >
                      <Ionicons name="play-circle" size={20} color={theme.colors.text.primary} />
                      <Text style={styles.viewVideoButtonText}>{t('overlay.viewVideo')}</Text>
                    </TouchableOpacity>
                  )}
                </>
              )}

              {/* Skip Button */}
              {onSkip && !isSubmitted && (
                <TouchableOpacity
                  style={styles.skipButton}
                  onPress={() => {
                    onSkip();
                    onClose();
                  }}
                  accessibilityLabel={t('overlay.skip')}
                  accessibilityRole="button"
                >
                  <Text style={styles.skipButtonText}>{t('overlay.skip')}</Text>
                </TouchableOpacity>
              )}
            </View>
          </ScrollView>
        </View>
      </Animated.View>
    </>
  );
};

const useStyles = createStyleSheet((theme) => ({
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    zIndex: 9999,
  },
  backdropTouchable: {
    flex: 1,
  },
  modalContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    zIndex: 10000,
  },
  modalBackdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
  modal: {
    backgroundColor: 'rgba(50, 50, 50, 0.95)',
    borderRadius: 24,
    width: '100%',
    maxWidth: 500,
    maxHeight: SCREEN_HEIGHT * 0.8,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
    elevation: 8,
  },
  handleBar: {
    width: 40,
    height: 4,
    backgroundColor: theme.colors.gray[300],
    borderRadius: 2,
    alignSelf: 'center',
    marginTop: theme.spacing[3],
    marginBottom: theme.spacing[2],
  },
  closeButton: {
    position: 'absolute',
    top: theme.spacing[3],
    right: theme.spacing[3],
    zIndex: 10,
    padding: theme.spacing[1], // 댓글 모달과 동일
  },
  scrollView: {
    maxHeight: SCREEN_HEIGHT * 0.7,
  },
  scrollContent: {
    paddingHorizontal: theme.spacing[5],
    paddingTop: theme.spacing[2],
    paddingBottom: theme.spacing[8],
  },
  questionContainer: {
    marginTop: theme.spacing[5], // X 버튼과 여유 공간
    marginBottom: theme.spacing[5],
  },
  questionText: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: '#FFFFFF',
    lineHeight: theme.typography.fontSize.xl * 1.4,
  },
  multipleAnswerHint: {
    fontSize: theme.typography.fontSize.sm,
    color: '#AAAAAA',
    marginTop: theme.spacing[2],
  },
  optionsContainer: {
    marginBottom: theme.spacing[4],
  },
  optionButton: {
    position: 'relative',
    borderWidth: 2,
    borderColor: 'rgba(255, 255, 255, 0.3)',
    borderRadius: 16,
    marginBottom: theme.spacing[3],
    overflow: 'hidden',
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
  },
  optionButtonSelected: {
    borderColor: '#FFFFFF',
    backgroundColor: 'rgba(255, 255, 255, 0.25)',
    borderWidth: 2.5,
  },
  optionButtonCorrect: {
    borderColor: '#4CAF50',
    backgroundColor: 'rgba(76, 175, 80, 0.3)',
    borderWidth: 2.5,
  },
  optionButtonWrong: {
    borderColor: '#F44336',
    backgroundColor: 'rgba(244, 67, 54, 0.3)',
    borderWidth: 2.5,
  },
  progressBar: {
    position: 'absolute',
    left: 0,
    top: 0,
    bottom: 0,
    borderRadius: theme.borderRadius.md,
  },
  optionContent: {
    flexDirection: 'row',
    alignItems: 'center',
    padding: theme.spacing[4],
    position: 'relative',
    zIndex: 1,
  },
  optionCircle: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 2,
    borderColor: 'rgba(255, 255, 255, 0.5)',
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: theme.spacing[3],
  },
  optionCircleSelected: {
    borderColor: '#FFFFFF',
    backgroundColor: 'rgba(255, 255, 255, 0.4)',
  },
  optionCircleCorrect: {
    borderColor: '#4CAF50',
    backgroundColor: 'rgba(76, 175, 80, 0.3)',
  },
  optionCircleWrong: {
    borderColor: '#F44336',
    backgroundColor: 'rgba(244, 67, 54, 0.3)',
  },
  optionText: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: '#FFFFFF',
    fontWeight: theme.typography.fontWeight.medium,
  },
  optionTextSelected: {
    color: '#FFFFFF',
    fontWeight: theme.typography.fontWeight.bold,
  },
  optionTextCorrect: {
    color: '#FFFFFF',
    fontWeight: theme.typography.fontWeight.bold,
  },
  optionTextWrong: {
    color: '#FFFFFF',
    fontWeight: theme.typography.fontWeight.bold,
  },
  percentageText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
    marginRight: theme.spacing[2],
  },
  resultContainer: {
    alignItems: 'center',
    paddingVertical: theme.spacing[4],
    marginVertical: theme.spacing[4],
    backgroundColor: 'rgba(255, 255, 255, 0.1)',
    borderRadius: theme.borderRadius.md,
  },
  resultText: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: '#FFFFFF',
    marginTop: theme.spacing[2],
  },
  attemptCountText: {
    fontSize: theme.typography.fontSize.sm,
    color: '#AAAAAA',
    marginTop: theme.spacing[1],
  },
  statsContainer: {
    alignItems: 'center',
    paddingVertical: theme.spacing[2],
    marginBottom: theme.spacing[4],
  },
  statsText: {
    fontSize: theme.typography.fontSize.sm,
    color: '#AAAAAA',
  },
  actionsContainer: {
    gap: theme.spacing[2],
  },
  submitButton: {
    backgroundColor: '#FFFFFF',
    paddingVertical: theme.spacing[4],
    borderRadius: theme.borderRadius.lg,
    alignItems: 'center',
    marginTop: theme.spacing[2],
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.3,
    shadowRadius: 4,
    elevation: 4,
  },
  submitButtonDisabled: {
    backgroundColor: 'rgba(255, 255, 255, 0.3)',
    shadowOpacity: 0,
    elevation: 0,
  },
  submitButtonText: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: '#000000',
  },
  submitButtonTextDisabled: {
    color: 'rgba(255, 255, 255, 0.5)',
  },
  retryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.background.secondary,
    paddingVertical: theme.spacing[4],
    borderRadius: theme.borderRadius.md,
    borderWidth: 1,
    borderColor: theme.colors.primary,
    gap: theme.spacing[2],
  },
  retryButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary,
  },
  viewVideoButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.background.secondary,
    paddingVertical: theme.spacing[4],
    borderRadius: theme.borderRadius.md,
    gap: theme.spacing[2],
  },
  viewVideoButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  skipButton: {
    paddingVertical: theme.spacing[3],
    alignItems: 'center',
  },
  skipButtonText: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
  },
}));
