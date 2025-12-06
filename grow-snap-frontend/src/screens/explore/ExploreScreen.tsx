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
  StatusBar,
  useWindowDimensions,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
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

export default function ExploreScreen() {
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
    <View style={{ flex: 1, backgroundColor: '#FFFFFF' }}>
      <StatusBar barStyle="dark-content" backgroundColor="#FFFFFF" translucent />

      {/* 헤더 */}
      <View style={{
        paddingTop: insets.top + 16,
        paddingHorizontal: 16,
        paddingBottom: 16,
      }}>
        <Text style={{
          color: '#000000',
          fontSize: 28 * adjustedFontScale,
          fontWeight: '700',
        }}>
          {t('explore.title')}
        </Text>
        <Text style={{
          color: '#666666',
          fontSize: 14 * adjustedFontScale,
          marginTop: 4,
        }}>
          {t('explore.selectCategory')}
        </Text>
      </View>

      {/* 카테고리 그리드 */}
      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: 16,
          paddingBottom: insets.bottom + 80,
        }}
        showsVerticalScrollIndicator={false}
      >
        <View style={{
          flexDirection: 'row',
          flexWrap: 'wrap',
          gap: 16,
        }}>
          {CATEGORIES.map((categoryInfo) => (
            <TouchableOpacity
              key={categoryInfo.value}
              onPress={() => handleCategoryPress(categoryInfo.value)}
              activeOpacity={0.7}
              style={{
                width: CARD_WIDTH,
                aspectRatio: 1,
                backgroundColor: '#F8F9FA',
                borderRadius: 12,
                padding: 16,
                justifyContent: 'space-between',
                borderWidth: 1,
                borderColor: '#E9ECEF',
              }}
            >
              {/* 아이콘 */}
              <View>
                <Ionicons
                  name={CATEGORY_ICON_MAP[categoryInfo.value] || 'grid'}
                  size={32}
                  color="#22c55e"
                />
              </View>

              {/* 카테고리 정보 */}
              <View>
                <Text style={{
                  color: '#000000',
                  fontSize: 17 * adjustedFontScale,
                  fontWeight: '700',
                  marginBottom: 4,
                }}>
                  {t(`category.${categoryInfo.value}.name`, categoryInfo.displayName)}
                </Text>
                <Text style={{
                  color: '#6C757D',
                  fontSize: 13 * adjustedFontScale,
                }}
                numberOfLines={2}
                >
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
