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
import { useTranslation } from 'react-i18next';
import { theme } from '@/theme';
import { blockUser, blockContent } from '@/api/block.api';
import { getErrorMessage } from '@/utils/errorHandler';
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
  const { t } = useTranslation('interactions');
  const { t: tCommon } = useTranslation('common');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const isUserBlock = blockType === 'user';

  const handleConfirm = async () => {
    setIsSubmitting(true);

    try {
      if (isUserBlock) {
        await blockUser(targetId);
        Alert.alert(
          t('block.user.successTitle'),
          t('block.user.successMessage'),
          [{ text: tCommon('button.confirm'), onPress: () => {
            onSuccess?.();
            onClose();
          }}]
        );
      } else {
        await blockContent(targetId);
        Alert.alert(
          t('block.content.successTitle'),
          t('block.content.successMessage'),
          [{ text: tCommon('button.confirm'), onPress: () => {
            onSuccess?.();
            onClose();
          }}]
        );
      }
    } catch (error: any) {
      setIsSubmitting(false);

      // errorHandler를 통해 적절한 에러 메시지 추출
      const errorMessage = getErrorMessage(error);

      // 409 에러 = 이미 차단한 경우
      if (error?.response?.status === 409) {
        Alert.alert(
          t('block.alreadyBlockedTitle'),
          errorMessage,
          [{ text: tCommon('button.confirm'), onPress: onClose }]
        );
      } else {
        // 그 외 에러 - errorCode 기반 메시지 표시
        Alert.alert(
          t('block.errorTitle'),
          errorMessage
        );
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
              ? t('block.user.title', { name: `@${targetName || '사용자'}` })
              : t('block.content.title')}
          </Text>

          {/* 설명 */}
          <Text style={styles.description}>
            {isUserBlock
              ? t('block.user.description')
              : t('block.content.description')}
          </Text>

          {/* 버튼 */}
          <View style={styles.buttonContainer}>
            <TouchableOpacity
              style={[styles.button, styles.cancelButton]}
              onPress={handleClose}
              disabled={isSubmitting}
            >
              <Text style={styles.cancelButtonText}>{tCommon('button.cancel')}</Text>
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
                  {isUserBlock ? t('block.user.confirmButton') : t('block.content.confirmButton')}
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
