import React from 'react';
import {
  View,
  Text,
  ScrollView,
  TouchableOpacity,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { theme, useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import { useThemeStore, ThemeMode } from '@/stores/themeStore';

const themeModes: ThemeMode[] = ['light', 'dark', 'system'];

const themeIcons: Record<ThemeMode, keyof typeof Ionicons.glyphMap> = {
  light: 'sunny-outline',
  dark: 'moon-outline',
  system: 'phone-portrait-outline',
};

const useStyles = createStyleSheet({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  backButton: {
    width: 40,
    height: 40,
    justifyContent: 'center',
    alignItems: 'center',
    marginRight: theme.spacing[3],
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.xl,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
  },
  scrollView: {
    flex: 1,
  },
  themeItem: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[4],
    backgroundColor: theme.colors.background.primary,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  themeLeft: {
    flexDirection: 'row',
    alignItems: 'center',
    flex: 1,
  },
  themeIcon: {
    marginRight: theme.spacing[3],
  },
  themeInfo: {
    flex: 1,
  },
  themeName: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    fontWeight: theme.typography.fontWeight.medium,
  },
  themeDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[1],
  },
  checkIcon: {
    marginLeft: theme.spacing[3],
  },
});

export default function ThemeSelectorScreen() {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const navigation = useNavigation();
  const { t } = useTranslation('settings');
  const { theme: currentTheme, setTheme } = useThemeStore();

  const getThemeDescription = (mode: ThemeMode): string => {
    switch (mode) {
      case 'light':
        return t('theme.lightDescription') || '밝은 화면으로 표시';
      case 'dark':
        return t('theme.darkDescription') || '어두운 화면으로 표시';
      case 'system':
        return t('theme.systemDescription') || '기기 설정에 따라 자동 변경';
    }
  };

  const handleSelectTheme = async (themeMode: ThemeMode) => {
    await setTheme(themeMode);
    // Go back after theme is changed
    navigation.goBack();
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* Header */}
      <View style={styles.header}>
        <TouchableOpacity
          onPress={() => navigation.goBack()}
          style={styles.backButton}
        >
          <Ionicons
            name="arrow-back"
            size={24}
            color={dynamicTheme.colors.text.primary}
          />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{t('general.theme')}</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        {themeModes.map((mode) => (
          <TouchableOpacity
            key={mode}
            style={styles.themeItem}
            onPress={() => handleSelectTheme(mode)}
          >
            <View style={styles.themeLeft}>
              <Ionicons
                name={themeIcons[mode]}
                size={24}
                color={dynamicTheme.colors.text.secondary}
                style={styles.themeIcon}
              />
              <View style={styles.themeInfo}>
                <Text style={styles.themeName}>{t(`theme.${mode}`)}</Text>
                <Text style={styles.themeDescription}>{getThemeDescription(mode)}</Text>
              </View>
            </View>
            {currentTheme === mode && (
              <Ionicons
                name="checkmark"
                size={24}
                color={dynamicTheme.colors.primary[500]}
                style={styles.checkIcon}
              />
            )}
          </TouchableOpacity>
        ))}
      </ScrollView>
    </SafeAreaView>
  );
}
