/**
 * 신고 모달 컴포넌트
 *
 * Instagram 스타일의 신고 모달
 * - 하단 시트 형태
 * - 신고 사유 선택
 * - 상세 설명 입력 (선택 사항)
 */

import React, { useState } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  TextInput,
  StyleSheet,
  Dimensions,
  ActivityIndicator,
  Alert,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { theme } from '@/theme';
import { reportTarget } from '@/api/report.api';
import { ReportType, TargetType } from '@/types/report.types';
import type { Category } from '@/types/content.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface ReportModalProps {
  visible: boolean;
  onClose: () => void;
  targetType: TargetType;
  targetId: string;
  targetName?: string; // 신고 대상 이름 (콘텐츠 제목 또는 사용자 닉네임)
  contentCategory?: Category; // 콘텐츠 카테고리 (FUN 카테고리 + OFF_TOPIC 신고 시 안내 표시용)
}

export const ReportModal: React.FC<ReportModalProps> = ({
  visible,
  onClose,
  targetType,
  targetId,
  targetName,
  contentCategory,
}) => {
  const { t } = useTranslation('interactions');
  const { t: tCommon } = useTranslation('common');
  const [selectedReason, setSelectedReason] = useState<ReportType | null>(null);
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  // Report reasons list
  const REPORT_REASONS: ReportType[] = [
    'OFF_TOPIC',
    'SPAM',
    'INAPPROPRIATE_CONTENT',
    'HARASSMENT',
    'HATE_SPEECH',
    'MISINFORMATION',
    'COPYRIGHT',
    'OTHER',
  ];

  const handleClose = () => {
    setSelectedReason(null);
    setDescription('');
    setIsSubmitting(false);
    onClose();
  };

  /**
   * FUN 카테고리 + OFF_TOPIC 신고 시 확인 알림 표시
   */
  const handleReasonSelect = (reason: ReportType) => {
    if (contentCategory === 'FUN' && reason === 'OFF_TOPIC') {
      Alert.alert(
        t('report.funCategoryAlert.title'),
        t('report.funCategoryAlert.message'),
        [
          { text: tCommon('button.cancel'), style: 'cancel' },
          {
            text: t('report.funCategoryAlert.confirm'),
            onPress: () => setSelectedReason(reason),
            style: 'destructive',
          },
        ]
      );
    } else {
      setSelectedReason(reason);
    }
  };

  const handleSubmit = async () => {
    if (!selectedReason) {
      Alert.alert(t('report.title'), t('report.selectReason'));
      return;
    }

    setIsSubmitting(true);

    try {
      await reportTarget(targetType, targetId, {
        reportType: selectedReason,
        description: description.trim() || undefined,
      });

      // 성공
      Alert.alert(t('report.successTitle'), t('report.successMessage'), [
        { text: tCommon('button.confirm'), onPress: handleClose },
      ]);
    } catch (error: any) {
      // 409 에러 = 이미 신고한 경우
      if (error?.response?.status === 409) {
        Alert.alert(t('report.alreadyReportedTitle'), t('report.alreadyReportedMessage'), [
          { text: tCommon('button.confirm'), onPress: handleClose },
        ]);
      } else {
        // 그 외 에러 - 모달은 닫지 않음
        setIsSubmitting(false);
        Alert.alert(t('report.errorTitle'), t('report.errorMessage'));
      }
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

          {/* 헤더 */}
          <View style={styles.header}>
            <Text style={styles.title}>{t('report.title')}</Text>
            <TouchableOpacity onPress={handleClose} style={styles.closeButton}>
              <Ionicons name="close" size={24} color={theme.colors.text.primary} />
            </TouchableOpacity>
          </View>

          {/* 신고 대상 정보 */}
          {targetName && (
            <View style={styles.targetInfo}>
              <Text style={styles.targetInfoText}>
                {t(`report.targetInfo.${targetType}`)}: {targetName}
              </Text>
            </View>
          )}

          {/* 신고 사유 목록 */}
          <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
            <Text style={styles.sectionTitle}>{t('report.subtitle')}</Text>

            {REPORT_REASONS.map((reason) => (
              <TouchableOpacity
                key={reason}
                style={[
                  styles.reasonItem,
                  selectedReason === reason && styles.reasonItemSelected,
                ]}
                onPress={() => handleReasonSelect(reason)}
              >
                <View style={styles.reasonContent}>
                  <View style={styles.reasonTextContainer}>
                    <Text
                      style={[
                        styles.reasonLabel,
                        selectedReason === reason && styles.reasonLabelSelected,
                      ]}
                    >
                      {t(`report.reasons.${reason}.label`)}
                    </Text>
                    <Text style={styles.reasonDescription}>
                      {t(`report.reasons.${reason}.description`)}
                    </Text>
                  </View>
                  {selectedReason === reason && (
                    <Ionicons
                      name="checkmark-circle"
                      size={24}
                      color={theme.colors.primary[500]}
                    />
                  )}
                </View>
              </TouchableOpacity>
            ))}

            {/* 상세 설명 (선택 사항) */}
            <View style={styles.descriptionSection}>
              <Text style={styles.sectionTitle}>{t('report.details')}</Text>
              <TextInput
                style={styles.descriptionInput}
                placeholder={t('report.detailsPlaceholder')}
                placeholderTextColor={theme.colors.text.tertiary}
                value={description}
                onChangeText={setDescription}
                multiline
                maxLength={500}
                textAlignVertical="top"
              />
              <Text style={styles.characterCount}>
                {t('report.characterCount', { count: description.length })}
              </Text>
            </View>
          </ScrollView>

          {/* 제출 버튼 */}
          <View style={styles.footer}>
            <TouchableOpacity
              style={[
                styles.submitButton,
                (!selectedReason || isSubmitting) && styles.submitButtonDisabled,
              ]}
              onPress={handleSubmit}
              disabled={!selectedReason || isSubmitting}
            >
              {isSubmitting ? (
                <ActivityIndicator color="#FFFFFF" />
              ) : (
                <Text style={styles.submitButtonText}>{t('report.submit')}</Text>
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
    maxWidth: 500,
    maxHeight: SCREEN_HEIGHT * 0.85,
    overflow: 'hidden',
    ...theme.shadows.lg,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing[5],
    paddingVertical: theme.spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  title: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  closeButton: {
    padding: theme.spacing[1],
  },
  targetInfo: {
    paddingHorizontal: theme.spacing[5],
    paddingVertical: theme.spacing[3],
    backgroundColor: theme.colors.background.secondary,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  targetInfoText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },
  content: {
    maxHeight: SCREEN_HEIGHT * 0.5,
    paddingHorizontal: theme.spacing[5],
  },
  sectionTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginTop: theme.spacing[4],
    marginBottom: theme.spacing[3],
  },
  reasonItem: {
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    marginBottom: theme.spacing[2],
    padding: theme.spacing[4],
  },
  reasonItemSelected: {
    borderColor: theme.colors.primary[500],
    backgroundColor: theme.colors.primary[50],
  },
  reasonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  reasonTextContainer: {
    flex: 1,
    marginRight: theme.spacing[2],
  },
  reasonLabel: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  reasonLabelSelected: {
    color: theme.colors.primary[600],
  },
  reasonDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: theme.typography.fontSize.sm * 1.4,
  },
  descriptionSection: {
    marginTop: theme.spacing[2],
    marginBottom: theme.spacing[4],
  },
  descriptionInput: {
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    padding: theme.spacing[3],
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    minHeight: 100,
  },
  characterCount: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'right',
    marginTop: theme.spacing[1],
  },
  footer: {
    paddingHorizontal: theme.spacing[5],
    paddingTop: theme.spacing[4],
    paddingBottom: theme.spacing[4],
    borderTopWidth: 1,
    borderTopColor: theme.colors.border.light,
  },
  submitButton: {
    backgroundColor: theme.colors.primary[500],
    borderRadius: theme.borderRadius.md,
    paddingVertical: theme.spacing[4],
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
  },
  submitButtonDisabled: {
    backgroundColor: theme.colors.gray[300],
  },
  submitButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.bold,
    color: '#FFFFFF',
  },
});
