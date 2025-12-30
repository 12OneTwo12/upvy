/**
 * 콘텐츠 메타데이터 입력 화면
 *
 * 인스타그램 스타일의 메타데이터 입력
 * - 제목 (필수, 200자 이내)
 * - 설명 (선택, 2000자 이내)
 * - 카테고리 선택 (필수)
 * - 태그 입력 (최대 10개)
 * - 언어 선택
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Alert,
  ScrollView,
  TextInput,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import { useLanguageStore } from '@/stores/languageStore';
import type { UploadStackParamList } from '@/types/navigation.types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { createContent } from '@/api/content.api';
import { createQuiz } from '@/api/quiz.api';
import type { Category } from '@/types/content.types';
import { CATEGORIES } from '@/types/content.types';

type Props = NativeStackScreenProps<UploadStackParamList, 'ContentMetadata'>;

export default function ContentMetadataScreen({ navigation, route }: Props) {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const { t } = useTranslation(['upload', 'common', 'search']);
  const { contentId, contentType, mediaInfo } = route.params;
  const queryClient = useQueryClient();
  const currentLanguage = useLanguageStore((state) => state.currentLanguage);

  // 폼 상태
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [language, setLanguage] = useState(currentLanguage);

  // 퀴즈 상태
  const [addQuiz, setAddQuiz] = useState(false);
  const [quizQuestion, setQuizQuestion] = useState('');
  const [quizOptions, setQuizOptions] = useState(['', '', '', '']);
  const [allowMultipleAnswers, setAllowMultipleAnswers] = useState(false);
  const [correctOptionIndices, setCorrectOptionIndices] = useState<number[]>([]);

  // UI 상태
  const [showCategoryPicker, setShowCategoryPicker] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);

  /**
   * FUN 카테고리 선택 시 가이드라인 표시
   */
  const handleCategorySelect = (categoryValue: Category) => {
    if (categoryValue === 'FUN') {
      Alert.alert(
        t('upload:metadata.funCategoryGuideline.title'),
        t('upload:metadata.funCategoryGuideline.message'),
        [
          { text: t('common:button.cancel'), style: 'cancel' },
          {
            text: t('common:button.confirm'),
            onPress: () => {
              setSelectedCategory(categoryValue);
              setShowCategoryPicker(false);
            },
          },
        ]
      );
    } else {
      setSelectedCategory(categoryValue);
      setShowCategoryPicker(false);
    }
  };

  const handleAddTag = () => {
    const trimmedTag = tagInput.trim();

    if (!trimmedTag) {
      return;
    }

    if (tags.length >= 10) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:metadata.validation.maxTagsReached'));
      return;
    }

    if (tags.includes(trimmedTag)) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:metadata.validation.tagAlreadyExists'));
      return;
    }

    setTags([...tags, trimmedTag]);
    setTagInput('');
  };

  const handleRemoveTag = (tagToRemove: string) => {
    setTags(tags.filter((tag) => tag !== tagToRemove));
  };

  const handlePublish = async () => {
    // 유효성 검사
    if (!title.trim()) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:metadata.validation.titleRequired'));
      return;
    }

    if (title.length > 200) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:metadata.validation.titleTooLong'));
      return;
    }

    if (!selectedCategory) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:metadata.validation.categoryRequired'));
      return;
    }

    if (description && description.length > 2000) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:metadata.validation.descriptionTooLong'));
      return;
    }

    // 퀴즈 유효성 검사
    if (addQuiz) {
      if (!quizQuestion.trim()) {
        Alert.alert(t('common:label.notice', 'Notice'), '퀴즈 질문을 입력해주세요.');
        return;
      }

      const validOptions = quizOptions.filter(opt => opt.trim());
      if (validOptions.length < 2) {
        Alert.alert(t('common:label.notice', 'Notice'), '최소 2개의 선택지를 입력해주세요.');
        return;
      }

      if (correctOptionIndices.length === 0) {
        Alert.alert(t('common:label.notice', 'Notice'), '최소 1개의 정답을 선택해주세요.');
        return;
      }

      if (!allowMultipleAnswers && correctOptionIndices.length > 1) {
        Alert.alert(t('common:label.notice', 'Notice'), '단일 정답 모드에서는 1개의 정답만 선택 가능합니다.');
        return;
      }
    }

    try {
      setIsPublishing(true);

      // 콘텐츠 생성 요청
      await createContent({
        contentId,
        title: title.trim(),
        description: description.trim() || undefined,
        category: selectedCategory,
        tags,
        language,
        photoUrls: Array.isArray(mediaInfo.uri) ? mediaInfo.uri : undefined,
        thumbnailUrl: mediaInfo.thumbnailUrl,
        duration: mediaInfo.duration,
        width: mediaInfo.width,
        height: mediaInfo.height,
      });

      // 퀴즈 생성
      if (addQuiz && quizQuestion.trim()) {
        const validOptions = quizOptions.filter(opt => opt.trim());
        await createQuiz(contentId, {
          question: quizQuestion.trim(),
          allowMultipleAnswers,
          options: validOptions.map((optionText, index) => ({
            optionText,
            displayOrder: index + 1,
            isCorrect: correctOptionIndices.includes(quizOptions.indexOf(optionText)),
          })),
        });
      }

      // Profile 화면의 내 콘텐츠 목록 자동 새로고침
      queryClient.invalidateQueries({ queryKey: ['myContents'] });

      Alert.alert(
        t('upload:metadata.publishSuccess'),
        t('upload:metadata.publishSuccessMessage'),
        [
          {
            text: t('common:button.confirm'),
            onPress: () => {
              // Upload 스택을 초기 화면으로 리셋
              navigation.reset({
                index: 0,
                routes: [{ name: 'UploadMain' }],
              });

              // 메인 탭의 프로필 화면으로 이동
              navigation.getParent()?.getParent()?.navigate('Main', {
                screen: 'Profile',
              });
            },
          },
        ]
      );
    } catch (error) {
      console.error('Failed to publish content:', error);
      Alert.alert(t('common:label.error', 'Error'), t('upload:metadata.publishFailed'));
    } finally {
      setIsPublishing(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerButton}>
          <Ionicons name="arrow-back" size={28} color={dynamicTheme.colors.text.primary} />
        </TouchableOpacity>

        <Text style={styles.headerTitle}>{t('upload:metadata.title')}</Text>

        <TouchableOpacity
          onPress={handlePublish}
          disabled={isPublishing}
          style={styles.headerButton}
        >
          {isPublishing ? (
            <ActivityIndicator size="small" color={dynamicTheme.colors.primary[500]} />
          ) : (
            <Text style={styles.publishButtonText}>{t('upload:metadata.publish')}</Text>
          )}
        </TouchableOpacity>
      </View>

      <ScrollView
        style={styles.content}
        showsVerticalScrollIndicator={false}
        keyboardShouldPersistTaps="handled"
      >
        {/* 제목 */}
        <View style={styles.section}>
          <Text style={styles.label}>
            {t('upload:metadata.caption')} <Text style={styles.required}>{t('upload:metadata.required')}</Text>
          </Text>
          <TextInput
            style={styles.input}
            placeholder={t('upload:metadata.captionPlaceholder')}
            placeholderTextColor={dynamicTheme.colors.text.tertiary}
            value={title}
            onChangeText={setTitle}
            maxLength={200}
            multiline
          />
          <Text style={styles.counter}>{t('upload:metadata.characterCount', { count: title.length, max: 200 })}</Text>
        </View>

        {/* 설명 */}
        <View style={styles.section}>
          <Text style={styles.label}>{t('upload:metadata.description')}</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            placeholder={t('upload:metadata.descriptionPlaceholder')}
            placeholderTextColor={dynamicTheme.colors.text.tertiary}
            value={description}
            onChangeText={setDescription}
            maxLength={2000}
            multiline
            numberOfLines={5}
            textAlignVertical="top"
          />
          <Text style={styles.counter}>{t('upload:metadata.characterCount', { count: description.length, max: 2000 })}</Text>
        </View>

        {/* 카테고리 */}
        <View style={styles.section}>
          <Text style={styles.label}>
            {t('upload:metadata.category')} <Text style={styles.required}>{t('upload:metadata.required')}</Text>
          </Text>
          <TouchableOpacity
            style={styles.pickerButton}
            onPress={() => setShowCategoryPicker(!showCategoryPicker)}
          >
            <Text
              style={[
                styles.pickerButtonText,
                !selectedCategory && styles.placeholderText,
              ]}
            >
              {selectedCategory
                ? t(`search:category.${selectedCategory}.name`, CATEGORIES.find((c) => c.value === selectedCategory)?.displayName)
                : t('upload:metadata.categoryPlaceholder')}
            </Text>
            <Text style={styles.pickerArrow}>
              {showCategoryPicker ? '▲' : '▼'}
            </Text>
          </TouchableOpacity>

          {/* 카테고리 목록 */}
          {showCategoryPicker && (
            <ScrollView
              style={styles.categoryList}
              nestedScrollEnabled
              showsVerticalScrollIndicator
            >
              {CATEGORIES.map((category) => (
                <TouchableOpacity
                  key={category.value}
                  style={[
                    styles.categoryItem,
                    selectedCategory === category.value && styles.categoryItemSelected,
                  ]}
                  onPress={() => handleCategorySelect(category.value)}
                >
                  <View style={styles.categoryContent}>
                    <Text style={styles.categoryName}>{t(`search:category.${category.value}.name`, category.displayName)}</Text>
                    <Text style={styles.categoryDescription}>
                      {t(`search:category.${category.value}.desc`, category.description)}
                    </Text>
                  </View>
                  {selectedCategory === category.value && (
                    <Text style={styles.checkmark}>✓</Text>
                  )}
                </TouchableOpacity>
              ))}
            </ScrollView>
          )}
        </View>

        {/* 태그 */}
        <View style={styles.section}>
          <Text style={styles.label}>{t('upload:metadata.maxTags')}</Text>

          {/* 태그 입력 */}
          <View style={styles.tagInputContainer}>
            <TextInput
              style={styles.tagInput}
              placeholder={t('upload:metadata.tagsPlaceholder')}
              placeholderTextColor={dynamicTheme.colors.text.tertiary}
              value={tagInput}
              onChangeText={setTagInput}
              onSubmitEditing={handleAddTag}
              returnKeyType="done"
            />
            <TouchableOpacity
              style={styles.addTagButton}
              onPress={handleAddTag}
              disabled={!tagInput.trim()}
            >
              <Text
                style={[
                  styles.addTagButtonText,
                  !tagInput.trim() && styles.disabledText,
                ]}
              >
                {t('upload:metadata.addTag')}
              </Text>
            </TouchableOpacity>
          </View>

          {/* 태그 목록 */}
          {tags.length > 0 && (
            <View style={styles.tagList}>
              {tags.map((tag, index) => (
                <View key={`${tag}-${index}`} style={styles.tagItem}>
                  <Text style={styles.tagText}>#{tag}</Text>
                  <TouchableOpacity
                    onPress={() => handleRemoveTag(tag)}
                    hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
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

            <TouchableOpacity
              style={[
                styles.languageButton,
                language === 'ja' && styles.languageButtonActive,
              ]}
              onPress={() => setLanguage('ja')}
            >
              <Text
                style={[
                  styles.languageButtonText,
                  language === 'ja' && styles.languageButtonTextActive,
                ]}
              >
                {t('upload:metadata.japanese')}
              </Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* 퀴즈 */}
        <View style={styles.section}>
          <TouchableOpacity
            style={styles.checkboxRow}
            onPress={() => setAddQuiz(!addQuiz)}
          >
            <Ionicons
              name={addQuiz ? 'checkbox' : 'square-outline'}
              size={24}
              color={dynamicTheme.colors.primary[500]}
            />
            <Text style={styles.checkboxLabel}>퀴즈 추가</Text>
          </TouchableOpacity>

          {addQuiz && (
            <View style={styles.quizForm}>
              {/* 질문 */}
              <View style={styles.quizSection}>
                <Text style={styles.label}>
                  질문 <Text style={styles.required}>{t('upload:metadata.required')}</Text>
                </Text>
                <TextInput
                  style={styles.input}
                  placeholder="퀴즈 질문을 입력하세요"
                  placeholderTextColor={dynamicTheme.colors.text.tertiary}
                  value={quizQuestion}
                  onChangeText={setQuizQuestion}
                  maxLength={500}
                  multiline
                />
                <Text style={styles.counter}>{quizQuestion.length}/500</Text>
              </View>

              {/* 선택지 */}
              <View style={styles.quizSection}>
                <Text style={styles.label}>선택지 (정답 체크)</Text>
                {quizOptions.map((option, index) => (
                  <View key={index} style={styles.optionRow}>
                    <TouchableOpacity
                      onPress={() => {
                        if (allowMultipleAnswers) {
                          setCorrectOptionIndices(prev =>
                            prev.includes(index) ? prev.filter(i => i !== index) : [...prev, index]
                          );
                        } else {
                          setCorrectOptionIndices([index]);
                        }
                      }}
                      style={styles.optionCheckbox}
                    >
                      <Ionicons
                        name={correctOptionIndices.includes(index) ? 'checkbox' : 'square-outline'}
                        size={20}
                        color={correctOptionIndices.includes(index) ? dynamicTheme.colors.success[500] : dynamicTheme.colors.text.tertiary}
                      />
                    </TouchableOpacity>
                    <TextInput
                      style={styles.optionInput}
                      placeholder={`선택지 ${index + 1}`}
                      placeholderTextColor={dynamicTheme.colors.text.tertiary}
                      value={option}
                      onChangeText={(text) => {
                        const newOptions = [...quizOptions];
                        newOptions[index] = text;
                        setQuizOptions(newOptions);
                      }}
                      maxLength={200}
                    />
                  </View>
                ))}
              </View>

              {/* 복수 정답 허용 */}
              <TouchableOpacity
                style={styles.checkboxRow}
                onPress={() => {
                  setAllowMultipleAnswers(!allowMultipleAnswers);
                  // 모드 전환 시 정답 선택 초기화
                  if (!allowMultipleAnswers && correctOptionIndices.length > 1) {
                    setCorrectOptionIndices([correctOptionIndices[0]]);
                  }
                }}
              >
                <Ionicons
                  name={allowMultipleAnswers ? 'checkbox' : 'square-outline'}
                  size={20}
                  color={dynamicTheme.colors.primary[500]}
                />
                <Text style={styles.checkboxLabel}>복수 정답 허용</Text>
              </TouchableOpacity>
            </View>
          )}
        </View>

        {/* 안내 메시지 */}
        <View style={styles.infoSection}>
          <Text style={styles.infoTitle}>💡 {t('upload:metadata.info.title')}</Text>
          <Text style={styles.infoText}>
            • {t('upload:metadata.info.requiredFields')}
          </Text>
          <Text style={styles.infoText}>
            • {t('upload:metadata.info.tagsHelp')}
          </Text>
          <Text style={styles.infoText}>
            • {t('upload:metadata.info.manageContent')}
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const useStyles = createStyleSheet((theme) => ({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  headerButton: {
    padding: theme.spacing[1],
    minWidth: 60,
  },
  publishButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary[500],
    textAlign: 'right',
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  content: {
    flex: 1,
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
    borderRadius: theme.borderRadius.base,
    backgroundColor: theme.colors.background.primary,
  },
  textArea: {
    minHeight: 120,
  },
  counter: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    marginTop: theme.spacing[1],
    textAlign: 'right',
  },
  pickerButton: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: theme.spacing[3],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.base,
    backgroundColor: theme.colors.background.primary,
  },
  pickerButtonText: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },
  placeholderText: {
    color: theme.colors.text.tertiary,
  },
  pickerArrow: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
  },
  categoryList: {
    marginTop: theme.spacing[2],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.base,
    maxHeight: 300,
    overflow: 'hidden',
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
  categoryContent: {
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
  checkmark: {
    fontSize: theme.typography.fontSize.lg,
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.bold,
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
    borderRadius: theme.borderRadius.base,
    backgroundColor: theme.colors.background.primary,
  },
  addTagButton: {
    paddingHorizontal: theme.spacing[4],
    justifyContent: 'center',
    borderRadius: theme.borderRadius.base,
    backgroundColor: theme.colors.primary[500],
  },
  addTagButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.inverse,
  },
  disabledText: {
    opacity: 0.5,
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
    borderRadius: theme.borderRadius.base,
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
  infoSection: {
    padding: theme.spacing[4],
    backgroundColor: theme.colors.gray[50],
    margin: theme.spacing[4],
    borderRadius: theme.borderRadius.base,
  },
  infoTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  infoText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[1],
  },
  checkboxRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing[2],
    paddingVertical: theme.spacing[2],
  },
  checkboxLabel: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.text.primary,
  },
  quizForm: {
    marginTop: theme.spacing[3],
    padding: theme.spacing[4],
    backgroundColor: theme.colors.gray[50],
    borderRadius: theme.borderRadius.base,
  },
  quizSection: {
    marginBottom: theme.spacing[4],
  },
  optionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing[2],
    marginBottom: theme.spacing[2],
  },
  optionCheckbox: {
    padding: theme.spacing[1],
  },
  optionInput: {
    flex: 1,
    paddingVertical: theme.spacing[3],
    paddingHorizontal: theme.spacing[4],
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.base,
    backgroundColor: theme.colors.background.primary,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },
}));
