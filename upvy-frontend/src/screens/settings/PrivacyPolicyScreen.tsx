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

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'PrivacyPolicy'>;

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
  table: {
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    borderRadius: theme.borderRadius.base,
    marginBottom: theme.spacing[3],
  },
  tableRow: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  tableHeader: {
    backgroundColor: theme.colors.background.secondary,
  },
  tableCell: {
    flex: 1,
    padding: theme.spacing[3],
  },
  tableCellText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.primary,
  },
  tableCellHeader: {
    fontWeight: theme.typography.fontWeight.semibold,
  },
}));

/**
 * Privacy Policy Screen
 * Upvy 개인정보 보호정책
 */
export default function PrivacyPolicyScreen() {
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
        <Text style={styles.headerTitle}>{t('privacyPolicy.title')}</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <Text style={styles.lastUpdate}>{t('privacyPolicy.lastUpdate')}</Text>

          {/* Section 1: Collection */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.collection.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.collection.intro')}</Text>
            {(t('privacyPolicy.sections.collection.items', { returnObjects: true }) as string[]).map((item, index) => (
              <Text key={index} style={styles.listItem}>• <Text style={styles.emphasis}>{item}</Text></Text>
            ))}
          </View>

          {/* Section 2: Purpose */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.purpose.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.purpose.intro')}</Text>
            {(t('privacyPolicy.sections.purpose.items', { returnObjects: true }) as string[]).map((item, index) => (
              <Text key={index} style={styles.listItem}>• {item}</Text>
            ))}
          </View>

          {/* Section 3: Retention */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.retention.title')}</Text>
            <View style={styles.table}>
              <View style={[styles.tableRow, styles.tableHeader]}>
                <View style={styles.tableCell}>
                  <Text style={[styles.tableCellText, styles.tableCellHeader]}>
                    {t('privacyPolicy.sections.retention.table.header.item')}
                  </Text>
                </View>
                <View style={styles.tableCell}>
                  <Text style={[styles.tableCellText, styles.tableCellHeader]}>
                    {t('privacyPolicy.sections.retention.table.header.period')}
                  </Text>
                </View>
              </View>
              {(t('privacyPolicy.sections.retention.table.rows', { returnObjects: true }) as Array<{item: string, period: string}>).map((row, index, arr) => (
                <View key={index} style={[styles.tableRow, index === arr.length - 1 && { borderBottomWidth: 0 }]}>
                  <View style={styles.tableCell}>
                    <Text style={styles.tableCellText}>{row.item}</Text>
                  </View>
                  <View style={styles.tableCell}>
                    <Text style={styles.tableCellText}>{row.period}</Text>
                  </View>
                </View>
              ))}
            </View>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.retention.note')}</Text>
          </View>

          {/* Section 4: Third Party */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.thirdParty.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.thirdParty.intro')}</Text>
            {(t('privacyPolicy.sections.thirdParty.items', { returnObjects: true }) as string[]).map((item, index) => (
              <Text key={index} style={styles.listItem}>• {item}</Text>
            ))}
          </View>

          {/* Section 5: OAuth */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.oauth.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.oauth.intro')}</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.oauth.google')}</Text></Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.oauth.naver')}</Text></Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.oauth.kakao')}</Text></Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.oauth.note')}</Text>
          </View>

          {/* Section 6: Cookies */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.cookies.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.cookies.intro')}</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.cookies.jwt')}</Text></Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.cookies.storage')}</Text></Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.cookies.tracking')}</Text></Text>
          </View>

          {/* Section 7: Children */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.children.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.children.content')}</Text>
          </View>

          {/* Section 8: Rights */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.rights.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.rights.intro')}</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.rights.view')}</Text></Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.rights.edit')}</Text></Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.rights.delete')}</Text></Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>{t('privacyPolicy.sections.rights.stop')}</Text></Text>
          </View>

          {/* Section 9: Security */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.security.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.security.intro')}</Text>
            {(t('privacyPolicy.sections.security.items', { returnObjects: true }) as string[]).map((item, index) => (
              <Text key={index} style={styles.listItem}>• {item}</Text>
            ))}
          </View>

          {/* Section 10: Policy Changes */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.policyChanges.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.policyChanges.content')}</Text>
          </View>

          {/* Section 11: Contact */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('privacyPolicy.sections.contactInfo.title')}</Text>
            <Text style={styles.paragraph}>{t('privacyPolicy.sections.contactInfo.intro')}</Text>
            <Text style={styles.listItem}>• {t('privacyPolicy.sections.contactInfo.github')}</Text>
            <Text style={styles.listItem}>• {t('privacyPolicy.sections.contactInfo.app')}</Text>
          </View>

          <View style={{ height: dynamicTheme.spacing[8] }} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
