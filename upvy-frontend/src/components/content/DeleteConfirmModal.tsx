/**
 * 콘텐츠 삭제 확인 모달 컴포넌트
 *
 * BlockConfirmModal 스타일 참조
 * - 중앙 모달 형태
 * - 삭제 경고 메시지
 * - 취소/삭제 버튼
 *
 * 백엔드 API: DELETE /api/v1/contents/{contentId}
 * 참조: upvy-backend/.../content/controller/ContentController.kt
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
import { deleteContent } from '@/api/content.api';
import { getErrorMessage } from '@/utils/errorHandler';

interface DeleteConfirmModalProps {
  visible: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  contentId: string;
  contentTitle?: string;
}

export const DeleteConfirmModal: React.FC<DeleteConfirmModalProps> = ({
  visible,
  onClose,
  onSuccess,
  contentId,
  contentTitle,
}) => {
  const { t } = useTranslation('feed');
  const { t: tCommon } = useTranslation('common');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleConfirm = async () => {
    setIsSubmitting(true);

    try {
      await deleteContent(contentId);
      Alert.alert(
        t('deleteContent.successTitle'),
        t('deleteContent.successMessage'),
        [{
          text: tCommon('button.confirm'),
          onPress: () => {
            onSuccess?.();
            onClose();
          }
        }]
      );
    } catch (error: any) {
      setIsSubmitting(false);
      const errorMessage = getErrorMessage(error);

      if (error?.response?.status === 403) {
        Alert.alert(
          t('deleteContent.errorTitle'),
          t('deleteContent.forbiddenMessage')
        );
      } else if (error?.response?.status === 404) {
        Alert.alert(
          t('deleteContent.errorTitle'),
          t('deleteContent.notFoundMessage'),
          [{ text: tCommon('button.confirm'), onPress: onClose }]
        );
      } else {
        Alert.alert(t('deleteContent.errorTitle'), errorMessage);
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
              name="trash-outline"
              size={48}
              color={theme.colors.error}
            />
          </View>

          {/* 제목 */}
          <Text style={styles.title}>
            {t('deleteContent.title')}
          </Text>

          {/* 콘텐츠 제목 표시 */}
          {contentTitle && (
            <Text style={styles.contentTitle} numberOfLines={1}>
              "{contentTitle}"
            </Text>
          )}

          {/* 설명 */}
          <Text style={styles.description}>
            {t('deleteContent.description')}
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
                  {t('deleteContent.confirmButton')}
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
    backgroundColor: theme.colors.error + '15',
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
  contentTitle: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    textAlign: 'center',
    marginBottom: theme.spacing[2],
    paddingHorizontal: theme.spacing[2],
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
