/**
 * 탐색 메인 화면
 *
 * 카테고리 선택 그리드를 보여주는 화면
 * 사용자가 카테고리를 선택하면 해당 카테고리의 피드로 이동
 */

import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  ScrollView,
  useWindowDimensions,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import { CATEGORIES, type Category } from '@/types/content.types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { ExploreStackParamList } from '@/types/navigation.types';

// 카테고리 아이콘 매핑 (Ionicons)
const CATEGORY_ICON_MAP: Record<Category, keyof typeof Ionicons.glyphMap> = {
  LANGUAGE: 'language',
  SCIENCE: 'flask',
  HISTORY: 'book',
  MATHEMATICS: 'calculator',
  ART: 'color-palette',
  STARTUP: 'rocket',
  MARKETING: 'trending-up',
  PROGRAMMING: 'code-slash',
  DESIGN: 'brush',
  PRODUCTIVITY: 'flash',
  PSYCHOLOGY: 'pulse',
  FINANCE: 'wallet',
  HEALTH: 'fitness',
  MOTIVATION: 'trophy',
  PARENTING: 'people',
  COOKING: 'restaurant',
  TRAVEL: 'airplane',
  HOBBY: 'game-controller',
  TREND: 'flame',
  OTHER: 'grid',
  FUN: 'happy',
};

type ExploreScreenNavigationProp = NativeStackNavigationProp<
  ExploreStackParamList,
  'ExploreMain'
>;

const useStyles = createStyleSheet((theme) => ({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    paddingHorizontal: theme.spacing[4],
    paddingBottom: theme.spacing[4],
  },
  title: {
    color: theme.colors.text.primary,
    fontWeight: '700' as const,
  },
  subtitle: {
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[1],
  },
  scrollContent: {
    paddingHorizontal: theme.spacing[4],
  },
  gridContainer: {
    flexDirection: 'row' as const,
    flexWrap: 'wrap' as const,
    gap: theme.spacing[4],
  },
  categoryCard: {
    aspectRatio: 1,
    backgroundColor: theme.colors.background.secondary,
    borderRadius: theme.borderRadius.lg,
    padding: theme.spacing[4],
    justifyContent: 'space-between' as const,
    borderWidth: 1,
    borderColor: theme.colors.border.light,
  },
  categoryName: {
    color: theme.colors.text.primary,
    fontWeight: '700' as const,
    marginBottom: theme.spacing[1],
  },
  categoryDesc: {
    color: theme.colors.text.tertiary,
  },
}));

export default function ExploreScreen() {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<ExploreScreenNavigationProp>();
  const { width: screenWidth, fontScale } = useWindowDimensions();
  const { t } = useTranslation('search');

  // 글자 크기 스케일 제한 (최대 1.3배까지만)
  const adjustedFontScale = Math.min(fontScale, 1.3);

  // 카드 너비 계산 (2개씩 배치)
  const CARD_WIDTH = (screenWidth - 48) / 2; // 48 = paddingHorizontal (16*2) + gap (16)

  const handleCategoryPress = (category: Category) => {
    navigation.navigate('CategoryFeed', { category });
  };

  return (
    <View style={styles.container}>
      {/* 헤더 */}
      <View style={[styles.header, { paddingTop: insets.top + 16 }]}>
        <Text style={[styles.title, { fontSize: 28 * adjustedFontScale }]}>
          {t('explore.title')}
        </Text>
        <Text style={[styles.subtitle, { fontSize: 14 * adjustedFontScale }]}>
          {t('explore.selectCategory')}
        </Text>
      </View>

      {/* 카테고리 그리드 */}
      <ScrollView
        contentContainerStyle={[styles.scrollContent, { paddingBottom: insets.bottom + 80 }]}
        showsVerticalScrollIndicator={false}
      >
        <View style={styles.gridContainer}>
          {CATEGORIES.map((categoryInfo) => (
            <TouchableOpacity
              key={categoryInfo.value}
              onPress={() => handleCategoryPress(categoryInfo.value)}
              activeOpacity={0.7}
              style={[styles.categoryCard, { width: CARD_WIDTH }]}
            >
              {/* 아이콘 */}
              <View>
                <Ionicons
                  name={CATEGORY_ICON_MAP[categoryInfo.value] || 'grid'}
                  size={32}
                  color={dynamicTheme.colors.primary[500]}
                />
              </View>

              {/* 카테고리 정보 */}
              <View>
                <Text style={[styles.categoryName, { fontSize: 17 * adjustedFontScale }]}>
                  {t(`category.${categoryInfo.value}.name`, categoryInfo.displayName)}
                </Text>
                <Text style={[styles.categoryDesc, { fontSize: 13 * adjustedFontScale }]} numberOfLines={2}>
                  {t(`category.${categoryInfo.value}.desc`, categoryInfo.description)}
                </Text>
              </View>
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}
