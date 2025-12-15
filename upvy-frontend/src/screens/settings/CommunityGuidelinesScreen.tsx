import React from 'react';
import { View, Text, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'CommunityGuidelines'>;

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
  subsectionTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
    marginTop: theme.spacing[3],
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
  badge: {
    fontSize: theme.typography.fontSize.lg,
    marginRight: theme.spacing[2],
  },
  coreValues: {
    marginVertical: theme.spacing[3],
  },
  coreValue: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    marginBottom: theme.spacing[3],
  },
  coreValueText: {
    flex: 1,
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.base,
  },
  coreValueLabel: {
    fontWeight: theme.typography.fontWeight.bold,
  },
});

/**
 * Community Guidelines Screen
 * Upvy 커뮤니티 가이드라인
 */
export default function CommunityGuidelinesScreen() {
  const styles = useStyles();
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
            color={theme.colors.text.primary}
          />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{t('communityGuidelines.title')}</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <Text style={styles.lastUpdate}>{t('communityGuidelines.lastUpdate')}</Text>

          {/* Welcome */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('communityGuidelines.sections.welcome.title')}</Text>
            <Text style={styles.paragraph}>{t('communityGuidelines.sections.welcome.content')}</Text>

            <Text style={styles.subsectionTitle}>{t('communityGuidelines.sections.welcome.valuesTitle')}</Text>
            <View style={styles.coreValues}>
              {(t('communityGuidelines.sections.welcome.values', { returnObjects: true }) as Array<{icon: string, label: string, description: string}>).map((value, index) => (
                <View key={index} style={styles.coreValue}>
                  <Text style={styles.badge}>{value.icon}</Text>
                  <Text style={styles.coreValueText}>
                    <Text style={styles.coreValueLabel}>{value.label}</Text>: {value.description}
                  </Text>
                </View>
              ))}
            </View>
          </View>

          {/* Encouraged Content */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('communityGuidelines.sections.encouraged.title')}</Text>
            <Text style={styles.paragraph}>{t('communityGuidelines.sections.encouraged.intro')}</Text>
            {(t('communityGuidelines.sections.encouraged.items', { returnObjects: true }) as string[]).map((item, index) => (
              <Text key={index} style={styles.listItem}>• {item}</Text>
            ))}
          </View>

          {/* Prohibited Content */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('communityGuidelines.sections.prohibited.title')}</Text>
            <Text style={styles.paragraph}>{t('communityGuidelines.sections.prohibited.intro')}</Text>

            {(t('communityGuidelines.sections.prohibited.categories', { returnObjects: true }) as Array<{title: string, items: string[]}>).map((category, catIndex) => (
              <View key={catIndex}>
                <Text style={styles.subsectionTitle}>{category.title}</Text>
                {category.items.map((item, itemIndex) => (
                  <Text key={itemIndex} style={styles.listItem}>• {item}</Text>
                ))}
              </View>
            ))}
          </View>

          {/* Reporting */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('communityGuidelines.sections.reporting.title')}</Text>
            <Text style={styles.paragraph}>{t('communityGuidelines.sections.reporting.intro')}</Text>
            {(t('communityGuidelines.sections.reporting.steps', { returnObjects: true }) as string[]).map((step, index) => (
              <Text key={index} style={styles.listItem}>• {step}</Text>
            ))}
          </View>

          {/* Enforcement */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('communityGuidelines.sections.enforcement.title')}</Text>
            <Text style={styles.paragraph}>{t('communityGuidelines.sections.enforcement.intro')}</Text>

            {(t('communityGuidelines.sections.enforcement.actions', { returnObjects: true }) as Array<{title: string, description: string}>).map((action, index) => (
              <View key={index}>
                <Text style={styles.subsectionTitle}>{action.title}</Text>
                <Text style={styles.paragraph}>{action.description}</Text>
              </View>
            ))}
          </View>

          {/* Safety Tools */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('communityGuidelines.sections.safety.title')}</Text>
            <Text style={styles.paragraph}>{t('communityGuidelines.sections.safety.intro')}</Text>
            {(t('communityGuidelines.sections.safety.tools', { returnObjects: true }) as string[]).map((tool, index) => (
              <Text key={index} style={styles.listItem}>• {tool}</Text>
            ))}
          </View>

          {/* Contact */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('communityGuidelines.sections.contact.title')}</Text>
            <Text style={styles.paragraph}>{t('communityGuidelines.sections.contact.content')}</Text>
          </View>

          <View style={{ height: theme.spacing[8] }} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
