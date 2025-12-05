/**
 * 콘텐츠 수정 모달 컴포넌트
 *
 * ContentMetadataScreen과 동일한 구조의 수정 모달
 * - 제목 (필수, 200자 이내)
 * - 설명 (선택, 2000자 이내)
 * - 카테고리 선택
 * - 태그 입력 (최대 10개)
 * - 언어 선택
 *
 * 영상/사진은 수정 불가
 *
 * 백엔드 API: PATCH /api/v1/contents/{contentId}
 * 참조: grow-snap-backend/.../content/controller/ContentController.kt
 *
 * ContentUpdateRequest (백엔드):
 * - title: String? (max 200자)
 * - description: String? (max 2000자)
 * - category: Category?
 * - tags: List<String>?
 * - language: String?
 * - photoUrls: List<String>? (수정 불가로 처리)
 */

import React, { useState, useEffect } from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  TextInput,
  StyleSheet,
  Dimensions,
  ActivityIndicator,
  Alert,
  KeyboardAvoidingView,
  Platform,
  ScrollView,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { theme } from '@/theme';
import { updateContent } from '@/api/content.api';
import { getErrorMessage } from '@/utils/errorHandler';
import type { ContentUpdateRequest, Category } from '@/types/content.types';
import { CATEGORIES } from '@/types/content.types';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface ContentEditModalProps {
  visible: boolean;
  onClose: () => void;
  onSuccess?: () => void;
  contentId: string;
  initialTitle: string;
  initialDescription: string | null;
  initialCategory: Category;
  initialTags: string[];
  initialLanguage: string;
}

export const ContentEditModal: React.FC<ContentEditModalProps> = ({
  visible,
  onClose,
  onSuccess,
  contentId,
  initialTitle,
  initialDescription,
  initialCategory,
  initialTags,
  initialLanguage,
}) => {
  const { t } = useTranslation(['feed', 'upload', 'common', 'search']);
  const [title, setTitle] = useState(initialTitle);
  const [description, setDescription] = useState(initialDescription || '');
  const [selectedCategory, setSelectedCategory] = useState<Category>(initialCategory);
  const [tags, setTags] = useState<string[]>(initialTags);
  const [tagInput, setTagInput] = useState('');
  const [language, setLanguage] = useState(initialLanguage);
  const [showCategoryPicker, setShowCategoryPicker] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  // 모달이 열릴 때 초기값 설정
  useEffect(() => {
    if (visible) {
      setTitle(initialTitle);
      setDescription(initialDescription || '');
      setSelectedCategory(initialCategory);
      setTags(initialTags);
      setLanguage(initialLanguage);
      setTagInput('');
      setShowCategoryPicker(false);
      setIsSubmitting(false); // 모달 열릴 때 로딩 상태 초기화
    }
  }, [visible, initialTitle, initialDescription, initialCategory, initialTags, initialLanguage]);

  const hasChanges =
    title !== initialTitle ||
    description !== (initialDescription || '') ||
    selectedCategory !== initialCategory ||
    JSON.stringify(tags) !== JSON.stringify(initialTags) ||
    language !== initialLanguage;

  const isValid = title.trim().length > 0 && title.length <= 200 && description.length <= 2000;

  const handleAddTag = () => {
    const trimmedTag = tagInput.trim();

    if (!trimmedTag) return;

    if (tags.length >= 10) {
      Alert.alert(t('common:label.notice'), t('upload:metadata.validation.maxTagsReached'));
      return;
    }

    if (tags.includes(trimmedTag)) {
      Alert.alert(t('common:label.notice'), t('upload:metadata.validation.tagAlreadyExists'));
      return;
    }

    setTags([...tags, trimmedTag]);
    setTagInput('');
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((tag) => tag !== tagToRemove));
  };

  const handleSubmit = async () => {
    if (!isValid || !hasChanges) return;

    setIsSubmitting(true);

    try {
      const request: ContentUpdateRequest = {};

      // 변경된 필드만 전송
      if (title !== initialTitle) {
        request.title = title.trim();
      }
      if (description !== (initialDescription || '')) {
        request.description = description.trim() || undefined;
      }
      if (selectedCategory !== initialCategory) {
        request.category = selectedCategory;
      }
      if (JSON.stringify(tags) !== JSON.stringify(initialTags)) {
        request.tags = tags;
      }
      if (language !== initialLanguage) {
        request.language = language;
      }

      await updateContent(contentId, request);

      Alert.alert(
        t('feed:editContent.successTitle'),
        t('feed:editContent.successMessage'),
        [{
          text: t('common:button.confirm'),
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
          t('feed:editContent.errorTitle'),
          t('feed:editContent.forbiddenMessage')
        );
      } else if (error?.response?.status === 404) {
        Alert.alert(
          t('feed:editContent.errorTitle'),
          t('feed:editContent.notFoundMessage'),
          [{ text: t('common:button.confirm'), onPress: onClose }]
        );
      } else {
        Alert.alert(t('feed:editContent.errorTitle'), errorMessage);
      }
    }
  };

  const handleClose = () => {
    if (isSubmitting) return;

    if (hasChanges) {
      Alert.alert(
        t('feed:editContent.discardTitle'),
        t('feed:editContent.discardMessage'),
        [
          { text: t('common:button.cancel'), style: 'cancel' },
          {
            text: t('feed:editContent.discardButton'),
            style: 'destructive',
            onPress: onClose,
          },
        ]
      );
    } else {
      onClose();
    }
  };

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={handleClose}
    >
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.container}
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
              <TouchableOpacity
                onPress={handleClose}
                disabled={isSubmitting}
                style={styles.headerButton}
              >
                <Text style={styles.cancelText}>{t('common:button.cancel')}</Text>
              </TouchableOpacity>

              <Text style={styles.headerTitle}>{t('feed:editContent.title')}</Text>

              <TouchableOpacity
                onPress={handleSubmit}
                disabled={isSubmitting || !isValid || !hasChanges}
                style={styles.headerButton}
              >
                {isSubmitting ? (
                  <ActivityIndicator size="small" color={theme.colors.primary[500]} />
                ) : (
                  <Text
                    style={[
                      styles.saveText,
                      (!isValid || !hasChanges) && styles.saveTextDisabled,
                    ]}
                  >
                    {t('common:button.save')}
                  </Text>
                )}
              </TouchableOpacity>
            </View>

            {/* 폼 */}
            <ScrollView
              style={styles.content}
              keyboardShouldPersistTaps="handled"
              showsVerticalScrollIndicator={false}
            >
              {/* 제목 */}
              <View style={styles.section}>
                <Text style={styles.label}>
                  {t('upload:metadata.caption')} <Text style={styles.required}>{t('upload:metadata.required')}</Text>
                </Text>
                <TextInput
                  style={styles.input}
                  placeholder={t('upload:metadata.captionPlaceholder')}
                  placeholderTextColor={theme.colors.text.tertiary}
                  value={title}
                  onChangeText={setTitle}
                  maxLength={200}
                  multiline
                  editable={!isSubmitting}
                />
                <Text style={styles.charCount}>
                  {title.length}/200
                </Text>
              </View>

              {/* 설명 */}
              <View style={styles.section}>
                <Text style={styles.label}>{t('upload:metadata.description')}</Text>
                <TextInput
                  style={[styles.input, styles.textArea]}
                  placeholder={t('upload:metadata.descriptionPlaceholder')}
                  placeholderTextColor={theme.colors.text.tertiary}
                  value={description}
                  onChangeText={setDescription}
                  maxLength={2000}
                  multiline
                  numberOfLines={5}
                  textAlignVertical="top"
                  editable={!isSubmitting}
                />
                <Text style={styles.charCount}>
                  {description.length}/2000
                </Text>
              </View>

              {/* 카테고리 */}
              <View style={styles.section}>
                <Text style={styles.label}>
                  {t('upload:metadata.category')} <Text style={styles.required}>{t('upload:metadata.required')}</Text>
                </Text>
                <TouchableOpacity
                  style={styles.pickerButton}
                  onPress={() => setShowCategoryPicker(!showCategoryPicker)}
                  disabled={isSubmitting}
                >
                  <Text style={styles.pickerButtonText}>
                    {t(`search:category.${selectedCategory}.name`, CATEGORIES.find((c) => c.value === selectedCategory)?.displayName)}
                  </Text>
                  <Text style={styles.pickerArrow}>
                    {showCategoryPicker ? '▲' : '▼'}
                  </Text>
                </TouchableOpacity>

                {showCategoryPicker && (
                  <View style={styles.categoryList}>
                    <ScrollView style={styles.categoryScroll} nestedScrollEnabled>
                      {CATEGORIES.map((category) => (
                        <TouchableOpacity
                          key={category.value}
                          style={[
                            styles.categoryItem,
                            selectedCategory === category.value && styles.categoryItemSelected,
                          ]}
                          onPress={() => {
                            setSelectedCategory(category.value);
                            setShowCategoryPicker(false);
                          }}
                        >
                          <View style={styles.categoryTextContainer}>
                            <Text style={styles.categoryName}>
                              {t(`search:category.${category.value}.name`, category.displayName)}
                            </Text>
                            <Text style={styles.categoryDescription}>
                              {t(`search:category.${category.value}.desc`, category.description)}
                            </Text>
                          </View>
                          {selectedCategory === category.value && (
                            <Ionicons name="checkmark" size={20} color={theme.colors.primary[500]} />
                          )}
                        </TouchableOpacity>
                      ))}
                    </ScrollView>
                  </View>
                )}
              </View>

              {/* 태그 */}
              <View style={styles.section}>
                <Text style={styles.label}>{t('upload:metadata.maxTags')}</Text>

                <View style={styles.tagInputContainer}>
                  <TextInput
                    style={styles.tagInput}
                    placeholder={t('upload:metadata.tagsPlaceholder')}
                    placeholderTextColor={theme.colors.text.tertiary}
                    value={tagInput}
                    onChangeText={setTagInput}
                    onSubmitEditing={handleAddTag}
                    returnKeyType="done"
                    editable={!isSubmitting}
                  />
                  <TouchableOpacity
                    style={[styles.addTagButton, !tagInput.trim() && styles.addTagButtonDisabled]}
                    onPress={handleAddTag}
                    disabled={!tagInput.trim() || isSubmitting}
                  >
                    <Text style={styles.addTagButtonText}>{t('upload:metadata.addTag')}</Text>
                  </TouchableOpacity>
                </View>

                {tags.length > 0 && (
                  <View style={styles.tagList}>
                    {tags.map((tag, index) => (
                      <View key={`${tag}-${index}`} style={styles.tagItem}>
                        <Text style={styles.tagText}>#{tag}</Text>
                        <TouchableOpacity
                          onPress={() => handleRemoveTag(tag)}
                          hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                          disabled={isSubmitting}
                        >
                          <Text style={styles.tagRemove}>×</Text>
                        </TouchableOpacity>
                      </View>
                    ))}
                  </View>
                )}
              </View>

              {/* 언어 */}
              <View style={styles.section}>
                <Text style={styles.label}>{t('upload:metadata.language')}</Text>
                <View style={styles.languageButtons}>
                  <TouchableOpacity
                    style={[
                      styles.languageButton,
                      language === 'ko' && styles.languageButtonActive,
                    ]}
                    onPress={() => setLanguage('ko')}
                    disabled={isSubmitting}
                  >
                    <Text
                      style={[
                        styles.languageButtonText,
                        language === 'ko' && styles.languageButtonTextActive,
                      ]}
                    >
                      {t('upload:metadata.korean')}
                    </Text>
                  </TouchableOpacity>

                  <TouchableOpacity
                    style={[
                      styles.languageButton,
                      language === 'en' && styles.languageButtonActive,
                    ]}
                    onPress={() => setLanguage('en')}
                    disabled={isSubmitting}
                  >
                    <Text
                      style={[
                        styles.languageButtonText,
                        language === 'en' && styles.languageButtonTextActive,
                      ]}
                    >
                      {t('upload:metadata.english')}
                    </Text>
                  </TouchableOpacity>
                </View>
              </View>

              {/* 하단 여백 */}
              <View style={styles.bottomSpacer} />
            </ScrollView>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'flex-end',
  },
  overlayTouchable: {
    flex: 1,
  },
  modalContainer: {
    backgroundColor: theme.colors.background.primary,
    borderTopLeftRadius: theme.borderRadius.xl,
    borderTopRightRadius: theme.borderRadius.xl,
    maxHeight: SCREEN_HEIGHT * 0.9,
    ...theme.shadows.lg,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  headerButton: {
    minWidth: 60,
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  cancelText: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.secondary,
  },
  saveText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary[500],
  },
  saveTextDisabled: {
    color: theme.colors.text.tertiary,
  },
  content: {
    maxHeight: SCREEN_HEIGHT * 0.75,
  },
  section: {
    padding: theme.spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  label: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  required: {
    color: theme.colors.error,
  },
  input: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    padding: theme.spacing[3],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    backgroundColor: theme.colors.background.primary,
  },
  textArea: {
    minHeight: 100,
  },
  charCount: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.tertiary,
    textAlign: 'right',
    marginTop: theme.spacing[1],
  },
  pickerButton: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: theme.spacing[3],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    backgroundColor: theme.colors.background.primary,
  },
  pickerButtonText: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },
  pickerArrow: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
  },
  categoryList: {
    marginTop: theme.spacing[2],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    overflow: 'hidden',
  },
  categoryScroll: {
    maxHeight: 200,
  },
  categoryItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  categoryItemSelected: {
    backgroundColor: theme.colors.primary[50],
  },
  categoryTextContainer: {
    flex: 1,
    marginRight: theme.spacing[2],
  },
  categoryName: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.primary,
    marginBottom: 2,
  },
  categoryDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
  },
  tagInputContainer: {
    flexDirection: 'row',
    gap: theme.spacing[2],
  },
  tagInput: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    padding: theme.spacing[3],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    backgroundColor: theme.colors.background.primary,
  },
  addTagButton: {
    paddingHorizontal: theme.spacing[4],
    justifyContent: 'center',
    borderRadius: theme.borderRadius.md,
    backgroundColor: theme.colors.primary[500],
  },
  addTagButtonDisabled: {
    backgroundColor: theme.colors.gray[300],
  },
  addTagButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.inverse,
  },
  tagList: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: theme.spacing[2],
    marginTop: theme.spacing[3],
  },
  tagItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing[1],
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    backgroundColor: theme.colors.primary[100],
    borderRadius: theme.borderRadius.full,
  },
  tagText: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.primary[700],
  },
  tagRemove: {
    fontSize: 18,
    color: theme.colors.primary[700],
    fontWeight: theme.typography.fontWeight.bold,
    lineHeight: 18,
  },
  languageButtons: {
    flexDirection: 'row',
    gap: theme.spacing[2],
  },
  languageButton: {
    flex: 1,
    paddingVertical: theme.spacing[3],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.md,
    alignItems: 'center',
  },
  languageButtonActive: {
    backgroundColor: theme.colors.primary[500],
    borderColor: theme.colors.primary[500],
  },
  languageButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.primary,
  },
  languageButtonTextActive: {
    color: theme.colors.text.inverse,
  },
  bottomSpacer: {
    height: theme.spacing[10],
  },
});
