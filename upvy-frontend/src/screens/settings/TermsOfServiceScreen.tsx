import React from 'react';
import { View, Text, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'TermsOfService'>;

const useStyles = createStyleSheet((theme) => ({
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
  content: {
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[6],
  },
  lastUpdate: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[6],
  },
  section: {
    marginBottom: theme.spacing[6],
  },
  sectionTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.bold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[3],
  },
  paragraph: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.base,
    marginBottom: theme.spacing[3],
  },
  listItem: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.base,
    marginBottom: theme.spacing[2],
    paddingLeft: theme.spacing[4],
  },
  emphasis: {
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
}));

/**
 * Terms of Service Screen
 * Upvy 서비스 이용약관
 */
export default function TermsOfServiceScreen() {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const navigation = useNavigation<NavigationProp>();
  const { t } = useTranslation('legal');

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
        <Text style={styles.headerTitle}>{t('termsOfService.title')}</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <Text style={styles.lastUpdate}>{t('termsOfService.lastUpdate')}</Text>

          {/* Section 1: Intro */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.intro.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.intro.content')}</Text>
          </View>

          {/* Section 2: Usage */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.usage.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.usage.intro')}</Text>
            {(t('termsOfService.sections.usage.items', { returnObjects: true }) as string[]).map((item, index) => (
              <Text key={index} style={styles.listItem}>• {item}</Text>
            ))}
          </View>

          {/* Section 3: Content */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.content.title')}</Text>
            <Text style={styles.paragraph}>
              <Text style={styles.emphasis}>{t('termsOfService.sections.content.userContentLabel')}</Text> {t('termsOfService.sections.content.userContent')}
            </Text>
            <Text style={styles.paragraph}>
              <Text style={styles.emphasis}>{t('termsOfService.sections.content.aiContentLabel')}</Text> {t('termsOfService.sections.content.aiContent')}
            </Text>
          </View>

          {/* Section 4: Prohibited */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.prohibited.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.prohibited.intro')}</Text>
            {(t('termsOfService.sections.prohibited.items', { returnObjects: true }) as string[]).map((item, index) => (
              <Text key={index} style={styles.listItem}>• {item}</Text>
            ))}
          </View>

          {/* Section 5: Changes */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.changes.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.changes.content')}</Text>
          </View>

          {/* Section 6: Disclaimer */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.disclaimer.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.disclaimer.content')}</Text>
          </View>

          {/* Section 7: Termination */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.termination.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.termination.content')}</Text>
          </View>

          {/* Section 8: Modifications */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.modifications.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.modifications.content')}</Text>
          </View>

          {/* Section 9: Jurisdiction */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.jurisdiction.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.jurisdiction.content')}</Text>
          </View>

          {/* Section 10: Contact */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('termsOfService.sections.contact.title')}</Text>
            <Text style={styles.paragraph}>{t('termsOfService.sections.contact.content')}</Text>
          </View>

          <View style={{ height: dynamicTheme.spacing[8] }} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
