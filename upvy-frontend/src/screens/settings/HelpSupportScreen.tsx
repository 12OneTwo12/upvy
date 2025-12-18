import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, Linking, Alert, Clipboard } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { useTranslation } from 'react-i18next';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'HelpSupport'>;

const SUPPORT_EMAIL = 'app.upvy@gmail.com';

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
  intro: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.base,
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
  helpItem: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingVertical: theme.spacing[3],
    paddingHorizontal: theme.spacing[4],
    backgroundColor: theme.colors.background.secondary,
    borderRadius: theme.borderRadius.base,
    marginBottom: theme.spacing[3],
  },
  helpIcon: {
    marginRight: theme.spacing[3],
    marginTop: 2,
  },
  helpContent: {
    flex: 1,
  },
  helpTitle: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  helpDescription: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.sm,
  },
  contactButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.primary[500],
    paddingVertical: theme.spacing[4],
    borderRadius: theme.borderRadius.base,
    marginTop: theme.spacing[4],
  },
  contactButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.inverse,
    marginLeft: theme.spacing[2],
  },
  emailButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: theme.colors.background.secondary,
    borderWidth: 1,
    borderColor: theme.colors.border.light,
    paddingVertical: theme.spacing[4],
    borderRadius: theme.borderRadius.base,
    marginTop: theme.spacing[3],
  },
  emailButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginLeft: theme.spacing[2],
  },
  emailInfo: {
    marginTop: theme.spacing[3],
    padding: theme.spacing[3],
    backgroundColor: theme.colors.background.secondary,
    borderRadius: theme.borderRadius.base,
  },
  emailLabel: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[1],
  },
  emailAddress: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.medium,
    color: theme.colors.primary[500],
  },
  faqItem: {
    marginBottom: theme.spacing[4],
    paddingBottom: theme.spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  question: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[2],
  },
  answer: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    lineHeight: theme.typography.lineHeight.relaxed * theme.typography.fontSize.sm,
  },
  emphasis: {
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
}));

/**
 * Help & Support Screen
 * Upvy 도움말 및 지원
 */
export default function HelpSupportScreen() {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const navigation = useNavigation<NavigationProp>();
  const { t } = useTranslation('legal');

  const handleContactSupport = () => {
    const url = 'https://github.com/12OneTwo12/upvy/issues';
    Linking.openURL(url).catch(() => {
      Alert.alert(t('helpSupport.contact.email.error'), t('helpSupport.contact.email.errorMessage'));
    });
  };

  const handleEmailContact = () => {
    const mailtoUrl = `mailto:${SUPPORT_EMAIL}`;
    Linking.openURL(mailtoUrl).catch(() => {
      // 이메일 클라이언트가 없으면 이메일 주소 복사
      Clipboard.setString(SUPPORT_EMAIL);
      Alert.alert(
        t('helpSupport.contact.email.copied'),
        `${SUPPORT_EMAIL}\n\n${t('helpSupport.contact.email.copiedMessage')}`,
        [{ text: t('common:button.confirm') }]
      );
    });
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
        <Text style={styles.headerTitle}>{t('helpSupport.title')}</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <Text style={styles.intro}>{t('helpSupport.intro')}</Text>

          {/* Main Features */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('helpSupport.features.title')}</Text>

            <View style={styles.helpItem}>
              <Ionicons
                name="play-circle"
                size={24}
                color={dynamicTheme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>{t('helpSupport.features.smartFeed.title')}</Text>
                <Text style={styles.helpDescription}>{t('helpSupport.features.smartFeed.description')}</Text>
              </View>
            </View>

            <View style={styles.helpItem}>
              <Ionicons
                name="search"
                size={24}
                color={dynamicTheme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>{t('helpSupport.features.search.title')}</Text>
                <Text style={styles.helpDescription}>{t('helpSupport.features.search.description')}</Text>
              </View>
            </View>

            <View style={styles.helpItem}>
              <Ionicons
                name="cloud-upload"
                size={24}
                color={dynamicTheme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>{t('helpSupport.features.upload.title')}</Text>
                <Text style={styles.helpDescription}>{t('helpSupport.features.upload.description')}</Text>
              </View>
            </View>

            <View style={styles.helpItem}>
              <Ionicons
                name="people"
                size={24}
                color={dynamicTheme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>{t('helpSupport.features.social.title')}</Text>
                <Text style={styles.helpDescription}>{t('helpSupport.features.social.description')}</Text>
              </View>
            </View>
          </View>

          {/* FAQ */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('helpSupport.faq.title')}</Text>

            <View style={styles.faqItem}>
              <Text style={styles.question}>{t('helpSupport.faq.q1.question')}</Text>
              <Text style={styles.answer}>{t('helpSupport.faq.q1.answer')}</Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>{t('helpSupport.faq.q2.question')}</Text>
              <Text style={styles.answer}>{t('helpSupport.faq.q2.answer')}</Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>{t('helpSupport.faq.q3.question')}</Text>
              <Text style={styles.answer}>{t('helpSupport.faq.q3.answer')}</Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>{t('helpSupport.faq.q4.question')}</Text>
              <Text style={styles.answer}>{t('helpSupport.faq.q4.answer')}</Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>{t('helpSupport.faq.q5.question')}</Text>
              <Text style={styles.answer}>{t('helpSupport.faq.q5.answer')}</Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>{t('helpSupport.faq.q6.question')}</Text>
              <Text style={styles.answer}>{t('helpSupport.faq.q6.answer')}</Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>{t('helpSupport.faq.q7.question')}</Text>
              <Text style={styles.answer}>{t('helpSupport.faq.q7.answer')}</Text>
            </View>
          </View>

          {/* Contact */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>{t('helpSupport.contact.title')}</Text>
            <Text style={styles.answer}>{t('helpSupport.contact.intro')}</Text>

            {/* Email Contact */}
            <TouchableOpacity
              style={styles.emailButton}
              onPress={handleEmailContact}
            >
              <Ionicons name="mail-outline" size={20} color={dynamicTheme.colors.text.primary} />
              <Text style={styles.emailButtonText}>{t('helpSupport.contact.email.button')}</Text>
            </TouchableOpacity>

            <View style={styles.emailInfo}>
              <Text style={styles.emailLabel}>{t('helpSupport.contact.email.label')}</Text>
              <Text style={styles.emailAddress}>{SUPPORT_EMAIL}</Text>
            </View>

            {/* GitHub Issues */}
            <TouchableOpacity
              style={styles.contactButton}
              onPress={handleContactSupport}
            >
              <Ionicons name="logo-github" size={20} color={dynamicTheme.colors.text.inverse} />
              <Text style={styles.contactButtonText}>{t('helpSupport.contact.github.button')}</Text>
            </TouchableOpacity>
          </View>

          <View style={{ height: dynamicTheme.spacing[8] }} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
