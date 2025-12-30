/**
 * 퀴즈 오버레이 컴포넌트
 *
 * Instagram Poll 스타일의 퀴즈 UI
 * - 퀴즈 질문과 보기 표시
 * - 실시간 통계 진행바
 * - 단일/복수 정답 지원
 * - 재시도, 건너뛰기, 비디오 보기 버튼
 */

import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  Animated,
  Easing,
  Dimensions,
  ScrollView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { LinearGradient } from 'expo-linear-gradient';
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
  onRetry?: () => void;
  onSkip?: () => void;
  onViewVideo?: () => void;
}

export const QuizOverlay: React.FC<QuizOverlayProps> = ({
  visible,
  onClose,
  quiz,
  onSubmit,
  onRetry,
  onSkip,
  onViewVideo,
}) => {
  const styles = useStyles();
  const theme = useTheme();

  // Animation refs
  const fadeAnim = useRef(new Animated.Value(0)).current;
  const slideAnim = useRef(new Animated.Value(SCREEN_HEIGHT)).current;

  // Selection state
  const [selectedOptionIds, setSelectedOptionIds] = useState<string[]>([]);
  const [isSubmitted, setIsSubmitted] = useState(false);
  const [attemptResult, setAttemptResult] = useState<QuizAttemptResponse | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Reset state when quiz changes or modal closes
  useEffect(() => {
    if (!visible) {
      setSelectedOptionIds([]);
      setIsSubmitted(false);
      setAttemptResult(null);
      setIsSubmitting(false);
    }
  }, [visible, quiz?.id]);

  // Check if user has already attempted (정답이 공개된 경우)
  const hasAttempted = useMemo(() => {
    return quiz?.options.some(option => option.isCorrect !== null) ?? false;
  }, [quiz]);

  // Set isSubmitted if user has already attempted
  useEffect(() => {
    if (hasAttempted && quiz) {
      setIsSubmitted(true);
      // Show user's previous selections if available
      const correctOptions = quiz.options.filter(opt => opt.isCorrect === true);
      if (correctOptions.length > 0) {
        setSelectedOptionIds(correctOptions.map(opt => opt.id));
      }
    }
  }, [hasAttempted, quiz]);

  // Animation
  useEffect(() => {
    if (visible) {
      // Open animation
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 1,
          duration: 300,
          useNativeDriver: true,
        }),
        Animated.spring(slideAnim, {
          toValue: 0,
          tension: 65,
          friction: 11,
          useNativeDriver: true,
        }),
      ]).start();
    } else {
      // Close animation
      Animated.parallel([
        Animated.timing(fadeAnim, {
          toValue: 0,
          duration: 200,
          useNativeDriver: true,
        }),
        Animated.timing(slideAnim, {
          toValue: SCREEN_HEIGHT,
          duration: 200,
          easing: Easing.in(Easing.ease),
          useNativeDriver: true,
        }),
      ]).start();
    }
  }, [visible, fadeAnim, slideAnim]);

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
      setAttemptResult(result);
      setIsSubmitted(true);
    } catch (error) {
      console.error('Failed to submit quiz attempt:', error);
      // TODO: Show error toast
    } finally {
      setIsSubmitting(false);
    }
  }, [quiz, selectedOptionIds, isSubmitting, onSubmit]);

  // Handle retry
  const handleRetry = useCallback(() => {
    setSelectedOptionIds([]);
    setIsSubmitted(false);
    setAttemptResult(null);
    if (onRetry) {
      onRetry();
    }
  }, [onRetry]);

  // Get option display data (either from attempt result or original quiz)
  const displayOptions = useMemo(() => {
    if (attemptResult) {
      return attemptResult.options;
    }
    return quiz?.options ?? [];
  }, [attemptResult, quiz]);

  // Calculate max percentage for scaling progress bars
  const maxPercentage = useMemo(() => {
    const percentages = displayOptions.map(opt => opt.selectionPercentage);
    return Math.max(...percentages, 1); // Minimum 1 to avoid division by zero
  }, [displayOptions]);

  if (!quiz) return null;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="none"
      onRequestClose={onClose}
    >
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
          styles.modalContainer,
          {
            transform: [{ translateY: slideAnim }],
          },
        ]}
      >
        <View style={styles.modal}>
          {/* Handle Bar */}
          <View style={styles.handleBar} />

          {/* Close Button */}
          <TouchableOpacity style={styles.closeButton} onPress={onClose}>
            <Ionicons name="close" size={24} color={theme.colors.text.secondary} />
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
                  복수 정답 선택 가능
                </Text>
              )}
            </View>

            {/* Options */}
            <View style={styles.optionsContainer}>
              {displayOptions.map((option, index) => {
                const isSelected = selectedOptionIds.includes(option.id);
                const isCorrect = option.isCorrect === true;
                const isWrong = isSubmitted && isSelected && option.isCorrect === false;
                const showProgress = isSubmitted;
                const progressWidth = (option.selectionPercentage / maxPercentage) * 100;

                return (
                  <TouchableOpacity
                    key={option.id}
                    style={[
                      styles.optionButton,
                      isSelected && !isSubmitted && styles.optionButtonSelected,
                      isCorrect && isSubmitted && styles.optionButtonCorrect,
                      isWrong && styles.optionButtonWrong,
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
                              ? theme.colors.success + '30'
                              : theme.colors.gray[200],
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
                          isSelected && styles.optionCircleSelected,
                          isCorrect && isSubmitted && styles.optionCircleCorrect,
                          isWrong && styles.optionCircleWrong,
                        ]}
                      >
                        {isSelected && (
                          <Ionicons
                            name="checkmark"
                            size={16}
                            color={
                              isSubmitted
                                ? isCorrect
                                  ? theme.colors.success
                                  : theme.colors.error
                                : theme.colors.primary
                            }
                          />
                        )}
                      </View>

                      {/* Option Text */}
                      <Text
                        style={[
                          styles.optionText,
                          isSelected && !isSubmitted && styles.optionTextSelected,
                          isCorrect && isSubmitted && styles.optionTextCorrect,
                          isWrong && styles.optionTextWrong,
                        ]}
                      >
                        {option.optionText}
                      </Text>

                      {/* Percentage (shown after submission) */}
                      {showProgress && (
                        <Text style={styles.percentageText}>
                          {option.selectionPercentage.toFixed(1)}%
                        </Text>
                      )}

                      {/* Correct/Wrong Icon */}
                      {isSubmitted && isSelected && (
                        <Ionicons
                          name={isCorrect ? "checkmark-circle" : "close-circle"}
                          size={20}
                          color={isCorrect ? theme.colors.success : theme.colors.error}
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
                  {attemptResult.isCorrect ? '정답입니다!' : '오답입니다!'}
                </Text>
                <Text style={styles.attemptCountText}>
                  {attemptResult.attemptNumber}번째 시도
                </Text>
              </View>
            )}

            {/* Statistics */}
            {isSubmitted && (
              <View style={styles.statsContainer}>
                <Text style={styles.statsText}>
                  총 {quiz.totalAttempts}명 참여
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
                >
                  <Text style={styles.submitButtonText}>
                    {isSubmitting ? '제출 중...' : '제출하기'}
                  </Text>
                </TouchableOpacity>
              ) : (
                // Post-submission buttons
                <>
                  <TouchableOpacity
                    style={styles.retryButton}
                    onPress={handleRetry}
                  >
                    <Ionicons name="refresh" size={20} color={theme.colors.primary} />
                    <Text style={styles.retryButtonText}>다시 풀기</Text>
                  </TouchableOpacity>

                  {onViewVideo && (
                    <TouchableOpacity
                      style={styles.viewVideoButton}
                      onPress={() => {
                        onViewVideo();
                        onClose();
                      }}
                    >
                      <Ionicons name="play-circle" size={20} color={theme.colors.text.primary} />
                      <Text style={styles.viewVideoButtonText}>비디오 보기</Text>
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
                >
                  <Text style={styles.skipButtonText}>건너뛰기</Text>
                </TouchableOpacity>
              )}
            </View>
          </ScrollView>
        </View>
      </Animated.View>
    </Modal>
  );
};

const useStyles = createStyleSheet((theme) => ({
  backdrop: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  backdropTouchable: {
    flex: 1,
  },
  modalContainer: {
    position: 'absolute',
    bottom: 0,
    left: 0,
    right: 0,
    maxHeight: SCREEN_HEIGHT * 0.85,
  },
  modal: {
    backgroundColor: theme.colors.background.primary,
    borderTopLeftRadius: theme.borderRadius.xl,
    borderTopRightRadius: theme.borderRadius.xl,
    paddingBottom: theme.spacing[6],
  },
  handleBar: {
    width: 40,
    height: 4,
    backgroundColor: theme.colors.gray[300],
    borderRadius: 2,
    alignSelf: 'center',
    marginTop: theme.spacing[2],
    marginBottom: theme.spacing[2],
  },
  closeButton: {
    position: 'absolute',
    top: theme.spacing[2],
    right: theme.spacing[4],
    zIndex: 10,
    padding: theme.spacing[2],
  },
  scrollView: {
    maxHeight: SCREEN_HEIGHT * 0.75,
  },
  scrollContent: {
    paddingHorizontal: theme.spacing[4],
    paddingBottom: theme.spacing[4],
  },
  questionContainer: {
    marginTop: theme.spacing[4],
    marginBottom: theme.spacing[6],
  },
  questionText: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    lineHeight: theme.typography.fontSize.lg * 1.5,
  },
  multipleAnswerHint: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[2],
  },
  optionsContainer: {
    marginBottom: theme.spacing[4],
  },
  optionButton: {
    position: 'relative',
    borderWidth: 2,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    marginBottom: theme.spacing[3],
    overflow: 'hidden',
  },
  optionButtonSelected: {
    borderColor: theme.colors.primary,
    backgroundColor: theme.colors.primary + '10',
  },
  optionButtonCorrect: {
    borderColor: theme.colors.success,
    backgroundColor: theme.colors.success + '10',
  },
  optionButtonWrong: {
    borderColor: theme.colors.error,
    backgroundColor: theme.colors.error + '10',
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
    borderColor: theme.colors.gray[400],
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: theme.spacing[3],
  },
  optionCircleSelected: {
    borderColor: theme.colors.primary,
    backgroundColor: theme.colors.primary + '20',
  },
  optionCircleCorrect: {
    borderColor: theme.colors.success,
    backgroundColor: theme.colors.success + '20',
  },
  optionCircleWrong: {
    borderColor: theme.colors.error,
    backgroundColor: theme.colors.error + '20',
  },
  optionText: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    fontWeight: theme.typography.fontWeight.medium,
  },
  optionTextSelected: {
    color: theme.colors.primary,
    fontWeight: theme.typography.fontWeight.semibold,
  },
  optionTextCorrect: {
    color: theme.colors.success,
    fontWeight: theme.typography.fontWeight.semibold,
  },
  optionTextWrong: {
    color: theme.colors.error,
  },
  percentageText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    fontWeight: theme.typography.fontWeight.semibold,
    marginRight: theme.spacing[2],
  },
  resultContainer: {
    alignItems: 'center',
    paddingVertical: theme.spacing[4],
    marginVertical: theme.spacing[4],
    backgroundColor: theme.colors.background.secondary,
    borderRadius: theme.borderRadius.md,
  },
  resultText: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginTop: theme.spacing[2],
  },
  attemptCountText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[1],
  },
  statsContainer: {
    alignItems: 'center',
    paddingVertical: theme.spacing[2],
    marginBottom: theme.spacing[4],
  },
  statsText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },
  actionsContainer: {
    gap: theme.spacing[2],
  },
  submitButton: {
    backgroundColor: theme.colors.primary,
    paddingVertical: theme.spacing[4],
    borderRadius: theme.borderRadius.md,
    alignItems: 'center',
  },
  submitButtonDisabled: {
    backgroundColor: theme.colors.gray[300],
  },
  submitButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.white,
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
