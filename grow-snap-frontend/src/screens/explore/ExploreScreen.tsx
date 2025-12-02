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
  Dimensions,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { CATEGORIES, type Category } from '@/types/content.types';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type { ExploreStackParamList } from '@/types/navigation.types';

const { width: SCREEN_WIDTH } = Dimensions.get('window');
const CARD_MARGIN = 8;
const CARD_WIDTH = (SCREEN_WIDTH - 48 - CARD_MARGIN) / 2; // 48 = padding 32 + gap 16

type ExploreScreenNavigationProp = NativeStackNavigationProp<
  ExploreStackParamList,
  'ExploreMain'
>;

export default function ExploreScreen() {
  const insets = useSafeAreaInsets();
  const navigation = useNavigation<ExploreScreenNavigationProp>();

  const handleCategoryPress = (category: Category) => {
    navigation.navigate('CategoryFeed', { category });
  };

  // 카테고리 아이콘 이모지 매핑 (간단한 아이콘 대신 사용)
  const getCategoryEmoji = (category: Category): string => {
    const emojiMap: Record<Category, string> = {
      LANGUAGE: '🗣️',
      SCIENCE: '🔬',
      HISTORY: '📜',
      MATHEMATICS: '🔢',
      ART: '🎨',
      STARTUP: '🚀',
      MARKETING: '📈',
      PROGRAMMING: '💻',
      DESIGN: '✨',
      PRODUCTIVITY: '⚡',
      PSYCHOLOGY: '🧠',
      FINANCE: '💰',
      HEALTH: '💪',
      PARENTING: '👨‍👩‍👧',
      COOKING: '🍳',
      TRAVEL: '✈️',
      HOBBY: '🎯',
      TREND: '🔥',
      OTHER: '📦',
    };
    return emojiMap[category] || '📦';
  };

  return (
    <View style={{ flex: 1, backgroundColor: '#000000' }}>
      <StatusBar barStyle="light-content" backgroundColor="#000000" translucent />

      {/* 헤더 */}
      <View style={{
        paddingTop: insets.top + 16,
        paddingHorizontal: 16,
        paddingBottom: 16,
      }}>
        <Text style={{
          color: '#FFFFFF',
          fontSize: 28,
          fontWeight: '700',
        }}>
          탐색
        </Text>
        <Text style={{
          color: '#666666',
          fontSize: 14,
          marginTop: 4,
        }}>
          관심있는 카테고리를 선택하세요
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
                backgroundColor: '#0a0a0a',
                borderRadius: 12,
                padding: 16,
                justifyContent: 'space-between',
                borderWidth: 1,
                borderColor: '#1a1a1a',
              }}
            >
              {/* 이모지 아이콘 */}
              <View>
                <Text style={{ fontSize: 40 }}>
                  {getCategoryEmoji(categoryInfo.value)}
                </Text>
              </View>

              {/* 카테고리 정보 */}
              <View>
                <Text style={{
                  color: '#FFFFFF',
                  fontSize: 16,
                  fontWeight: '700',
                  marginBottom: 4,
                }}>
                  {categoryInfo.displayName}
                </Text>
                <Text style={{
                  color: '#666666',
                  fontSize: 12,
                }}
                numberOfLines={2}
                >
                  {categoryInfo.description}
                </Text>
              </View>
            </TouchableOpacity>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}
