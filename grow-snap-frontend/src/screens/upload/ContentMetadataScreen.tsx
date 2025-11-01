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
import { theme } from '@/theme';
import type { UploadStackParamList } from '@/types/navigation.types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { createContent } from '@/api/content.api';
import type { Category } from '@/types/content.types';
import { CATEGORIES } from '@/types/content.types';

type Props = NativeStackScreenProps<UploadStackParamList, 'ContentMetadata'>;

export default function ContentMetadataScreen({ navigation, route }: Props) {
  const { contentId, contentType, mediaInfo } = route.params;

  // 폼 상태
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<Category | null>(null);
  const [tags, setTags] = useState<string[]>([]);
  const [tagInput, setTagInput] = useState('');
  const [language, setLanguage] = useState('ko');

  // UI 상태
  const [showCategoryPicker, setShowCategoryPicker] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);

  const handleAddTag = () => {
    const trimmedTag = tagInput.trim();

    if (!trimmedTag) {
      return;
    }

    if (tags.length >= 10) {
      Alert.alert('알림', '태그는 최대 10개까지 추가할 수 있습니다.');
      return;
    }

    if (tags.includes(trimmedTag)) {
      Alert.alert('알림', '이미 추가된 태그입니다.');
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
      Alert.alert('알림', '제목을 입력해주세요.');
      return;
    }

    if (title.length > 200) {
      Alert.alert('알림', '제목은 200자 이하로 입력해주세요.');
      return;
    }

    if (!selectedCategory) {
      Alert.alert('알림', '카테고리를 선택해주세요.');
      return;
    }

    if (description && description.length > 2000) {
      Alert.alert('알림', '설명은 2000자 이하로 입력해주세요.');
      return;
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

      Alert.alert(
        '게시 완료',
        '콘텐츠가 성공적으로 게시되었습니다!',
        [
          {
            text: '확인',
            onPress: () => {
              // 프로필 화면으로 이동
              navigation.reset({
                index: 0,
                routes: [{ name: 'UploadMain' }],
              });
            },
          },
        ]
      );
    } catch (error) {
      console.error('Failed to publish content:', error);
      Alert.alert('오류', '콘텐츠 게시에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsPublishing(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerButton}>
          <Ionicons name="arrow-back" size={28} color={theme.colors.text.primary} />
        </TouchableOpacity>

        <Text style={styles.headerTitle}>새 게시물</Text>

        <TouchableOpacity
          onPress={handlePublish}
          disabled={isPublishing}
          style={styles.headerButton}
        >
          {isPublishing ? (
            <ActivityIndicator size="small" color={theme.colors.primary[500]} />
          ) : (
            <Text style={styles.publishButtonText}>게시</Text>
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
            제목 <Text style={styles.required}>*</Text>
          </Text>
          <TextInput
            style={styles.input}
            placeholder="제목을 입력하세요 (최대 200자)"
            placeholderTextColor={theme.colors.text.tertiary}
            value={title}
            onChangeText={setTitle}
            maxLength={200}
            multiline
          />
          <Text style={styles.counter}>{title.length}/200</Text>
        </View>

        {/* 설명 */}
        <View style={styles.section}>
          <Text style={styles.label}>설명</Text>
          <TextInput
            style={[styles.input, styles.textArea]}
            placeholder="설명을 입력하세요 (최대 2000자)"
            placeholderTextColor={theme.colors.text.tertiary}
            value={description}
            onChangeText={setDescription}
            maxLength={2000}
            multiline
            numberOfLines={5}
            textAlignVertical="top"
          />
          <Text style={styles.counter}>{description.length}/2000</Text>
        </View>

        {/* 카테고리 */}
        <View style={styles.section}>
          <Text style={styles.label}>
            카테고리 <Text style={styles.required}>*</Text>
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
                ? CATEGORIES.find((c) => c.value === selectedCategory)?.displayName
                : '카테고리를 선택하세요'}
            </Text>
            <Text style={styles.pickerArrow}>
              {showCategoryPicker ? '▲' : '▼'}
            </Text>
          </TouchableOpacity>

          {/* 카테고리 목록 */}
          {showCategoryPicker && (
            <View style={styles.categoryList}>
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
                  <View>
                    <Text style={styles.categoryName}>{category.displayName}</Text>
                    <Text style={styles.categoryDescription}>
                      {category.description}
                    </Text>
                  </View>
                  {selectedCategory === category.value && (
                    <Text style={styles.checkmark}>✓</Text>
                  )}
                </TouchableOpacity>
              ))}
            </View>
          )}
        </View>

        {/* 태그 */}
        <View style={styles.section}>
          <Text style={styles.label}>태그 (최대 10개)</Text>

          {/* 태그 입력 */}
          <View style={styles.tagInputContainer}>
            <TextInput
              style={styles.tagInput}
              placeholder="태그를 입력하세요"
              placeholderTextColor={theme.colors.text.tertiary}
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
                추가
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
          <Text style={styles.label}>언어</Text>
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
                한국어
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
                English
              </Text>
            </TouchableOpacity>
          </View>
        </View>

        {/* 안내 메시지 */}
        <View style={styles.infoSection}>
          <Text style={styles.infoTitle}>💡 게시 안내</Text>
          <Text style={styles.infoText}>
            • 제목과 카테고리는 필수 입력 항목입니다
          </Text>
          <Text style={styles.infoText}>
            • 태그는 콘텐츠 검색에 도움이 됩니다
          </Text>
          <Text style={styles.infoText}>
            • 게시된 콘텐츠는 프로필에서 관리할 수 있습니다
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
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
});
