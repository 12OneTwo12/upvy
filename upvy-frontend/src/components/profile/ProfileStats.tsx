import React from 'react';
import { View, Text, TouchableOpacity } from 'react-native';
import { createStyleSheet } from '@/utils/styles';

interface StatItem {
  label: string;
  value: number;
  onPress?: () => void;
}

interface ProfileStatsProps {
  stats: StatItem[];
}

/**
 * 프로필 통계 컴포넌트
 * 인스타그램 스타일의 팔로워/팔로잉 등 통계 표시
 */
export default function ProfileStats({ stats }: ProfileStatsProps) {
  const styles = useStyles();

  const formatNumber = (num: number): string => {
    if (num >= 1000000) {
      return `${(num / 1000000).toFixed(1)}M`;
    }
    if (num >= 1000) {
      return `${(num / 1000).toFixed(1)}K`;
    }
    return num.toString();
  };

  return (
    <View style={styles.container}>
      {stats.map((stat, index) => {
        const StatContent = (
          <View key={index} style={styles.statItem}>
            <Text style={styles.statValue}>{formatNumber(stat.value)}</Text>
            <Text style={styles.statLabel}>{stat.label}</Text>
          </View>
        );

        if (stat.onPress) {
          return (
            <TouchableOpacity key={index} onPress={stat.onPress} activeOpacity={0.7}>
              {StatContent}
            </TouchableOpacity>
          );
        }

        return StatContent;
      })}
    </View>
  );
}

const useStyles = createStyleSheet((theme) => ({
  container: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    alignItems: 'center',
    paddingVertical: theme.spacing[4],
  },
  statItem: {
    alignItems: 'center',
    minWidth: 70,
  },
  statValue: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  statLabel: {
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.normal,
    color: theme.colors.text.secondary,
  },
}));
