/**
 * 차단 확인 모달 컴포넌트
 *
 * Instagram/Twitter 스타일의 확인 모달
 * - 중앙 모달 형태
 * - 차단 경고 메시지
 * - 취소/차단 버튼
 */

import React, { useState } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '@/theme';
import { blockUser, blockContent } from '@/api/block.api';
import type { BlockType } from '@/types/block.types';

interface BlockConfirmModalProps {
  visible: boolean;
  onClose: () => void;
  onSuccess?: () => void; // 차단 성공 시 호출되는 콜백
  blockType: BlockType;
  targetId: string;
  targetName?: string; // 사용자 닉네임 또는 콘텐츠 제목
}

export const BlockConfirmModal: React.FC<BlockConfirmModalProps> = ({
  visible,
  onClose,
  onSuccess,
  blockType,
  targetId,
  targetName,
}) => {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isUserBlock = blockType === 'user';

  const handleConfirm = async () => {
    setIsSubmitting(true);

    try {
      if (isUserBlock) {
        await blockUser(targetId);
        Alert.alert('사용자를 차단했어요', '이 사용자의 콘텐츠가 더 이상 표시되지 않아요.', [
          { text: '확인', onPress: () => {
            onSuccess?.();
            onClose();
          }},
        ]);
      } else {
        await blockContent(targetId);
        Alert.alert('콘텐츠를 숨겼어요', '이 콘텐츠가 피드에 더 이상 표시되지 않아요.', [
          { text: '확인', onPress: () => {
            onSuccess?.();
            onClose();
          }},
        ]);
      }
    } catch (error: any) {
      setIsSubmitting(false);

      // 409 에러 = 이미 차단한 경우
      if (error?.response?.status === 409) {
        Alert.alert(
          '이미 차단했어요',
          isUserBlock ? '이 사용자는 이미 차단되어 있어요.' : '이 콘텐츠는 이미 숨겨져 있어요.',
          [{ text: '확인', onPress: onClose }]
        );
      } else {
        // 그 외 에러
        Alert.alert('앗, 문제가 생겼어요', '잠시 후 다시 시도해주세요.');
      }
    }
  };

  const handleClose = () => {
    if (!isSubmitting) {
      onClose();
    }
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      onRequestClose={handleClose}
    >
      <View style={styles.overlay}>
        <TouchableOpacity
          style={styles.overlayTouchable}
          activeOpacity={1}
          onPress={handleClose}
        />

        <View style={styles.modalContainer}>
          {/* 아이콘 */}
          <View style={styles.iconContainer}>
            <Ionicons
              name={isUserBlock ? 'person-remove' : 'eye-off'}
              size={48}
              color={theme.colors.error}
            />
          </View>

          {/* 제목 */}
          <Text style={styles.title}>
            {isUserBlock
              ? `@${targetName || '사용자'}님을 차단하시겠어요?`
              : '이 콘텐츠를 숨기시겠어요?'}
          </Text>

          {/* 설명 */}
          <Text style={styles.description}>
            {isUserBlock
              ? '이 사용자의 모든 콘텐츠가 피드에 표시되지 않으며, 상호 팔로우가 해제됩니다.'
              : '이 콘텐츠가 피드에 더 이상 표시되지 않습니다.'}
          </Text>

          {/* 버튼 */}
          <View style={styles.buttonContainer}>
            <TouchableOpacity
              style={[styles.button, styles.cancelButton]}
              onPress={handleClose}
              disabled={isSubmitting}
            >
              <Text style={styles.cancelButtonText}>취소</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[
                styles.button,
                styles.confirmButton,
                isSubmitting && styles.confirmButtonDisabled,
              ]}
              onPress={handleConfirm}
              disabled={isSubmitting}
            >
              {isSubmitting ? (
                <ActivityIndicator color="#FFFFFF" />
              ) : (
                <Text style={styles.confirmButtonText}>
                  {isUserBlock ? '차단하기' : '숨기기'}
                </Text>
              )}
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: theme.spacing[4],
  },
  overlayTouchable: {
    ...StyleSheet.absoluteFillObject,
  },
  modalContainer: {
    backgroundColor: theme.colors.background.primary,
    borderRadius: theme.borderRadius.xl,
    width: '100%',
    maxWidth: 400,
    padding: theme.spacing[6],
    alignItems: 'center',
    ...theme.shadows.lg,
  },
  iconContainer: {
    width: 80,
    height: 80,
    borderRadius: 40,
    backgroundColor: theme.colors.error + '15', // 15% 투명도
    justifyContent: 'center',
    alignItems: 'center',
    marginBottom: theme.spacing[4],
  },
  title: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    textAlign: 'center',
    marginBottom: theme.spacing[2],
  },
  description: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    textAlign: 'center',
    lineHeight: theme.typography.fontSize.sm * 1.5,
    marginBottom: theme.spacing[5],
  },
  buttonContainer: {
    flexDirection: 'row',
    width: '100%',
    gap: theme.spacing[2],
  },
  button: {
    flex: 1,
    paddingVertical: theme.spacing[3],
    borderRadius: theme.borderRadius.md,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
  },
  cancelButton: {
    backgroundColor: theme.colors.background.secondary,
    borderWidth: 1,
    borderColor: theme.colors.border.light,
  },
  cancelButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  confirmButton: {
    backgroundColor: theme.colors.error,
  },
  confirmButtonDisabled: {
    opacity: 0.6,
  },
  confirmButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.bold,
    color: '#FFFFFF',
  },
});
