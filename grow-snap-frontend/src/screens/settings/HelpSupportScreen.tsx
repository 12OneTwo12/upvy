import React from 'react';
import { View, Text, ScrollView, TouchableOpacity, Linking, Alert, Clipboard } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'HelpSupport'>;

const SUPPORT_EMAIL = 'app.grow.snap@gmail.com';

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
});

/**
 * Help & Support Screen
 * GrowSnap 도움말 및 지원
 */
export default function HelpSupportScreen() {
  const styles = useStyles();
  const navigation = useNavigation<NavigationProp>();

  const handleContactSupport = () => {
    const url = 'https://github.com/12OneTwo12/grow-snap/issues';
    Linking.openURL(url).catch(() => {
      Alert.alert('오류', 'GitHub Issues를 열 수 없습니다.');
    });
  };

  const handleEmailContact = () => {
    const mailtoUrl = `mailto:${SUPPORT_EMAIL}`;
    Linking.openURL(mailtoUrl).catch(() => {
      // 이메일 클라이언트가 없으면 이메일 주소 복사
      Clipboard.setString(SUPPORT_EMAIL);
      Alert.alert(
        '이메일 주소 복사됨',
        `${SUPPORT_EMAIL}\n\n이메일 주소가 클립보드에 복사되었습니다.`,
        [{ text: '확인' }]
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
            color={theme.colors.text.primary}
          />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>도움말 및 지원</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <Text style={styles.intro}>
            GrowSnap 이용에 도움이 필요하신가요? 자주 묻는 질문과 주요 기능 안내를 확인해보세요.
          </Text>

          {/* Main Features */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>주요 기능</Text>

            <View style={styles.helpItem}>
              <Ionicons
                name="play-circle"
                size={24}
                color={theme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>스마트 피드</Text>
                <Text style={styles.helpDescription}>
                  개인화된 숏폼 콘텐츠를 스와이프하며 자연스럽게 학습하세요. AI 추천 알고리즘이 당신의 관심사에 맞는 콘텐츠를 제공합니다.
                </Text>
              </View>
            </View>

            <View style={styles.helpItem}>
              <Ionicons
                name="search"
                size={24}
                color={theme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>콘텐츠 검색</Text>
                <Text style={styles.helpDescription}>
                  키워드, 크리에이터, 카테고리별로 원하는 콘텐츠를 찾아보세요. 학문, 비즈니스, 자기계발 등 다양한 주제가 준비되어 있습니다.
                </Text>
              </View>
            </View>

            <View style={styles.helpItem}>
              <Ionicons
                name="cloud-upload"
                size={24}
                color={theme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>콘텐츠 제작</Text>
                <Text style={styles.helpDescription}>
                  누구나 크리에이터가 될 수 있습니다. 비디오나 이미지를 업로드하고 지식을 공유해보세요.
                </Text>
              </View>
            </View>

            <View style={styles.helpItem}>
              <Ionicons
                name="people"
                size={24}
                color={theme.colors.primary[500]}
                style={styles.helpIcon}
              />
              <View style={styles.helpContent}>
                <Text style={styles.helpTitle}>소셜 기능</Text>
                <Text style={styles.helpDescription}>
                  좋아요, 댓글, 저장, 공유로 다른 사용자와 소통하고, 관심있는 크리에이터를 팔로우하세요.
                </Text>
              </View>
            </View>
          </View>

          {/* FAQ */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>자주 묻는 질문</Text>

            <View style={styles.faqItem}>
              <Text style={styles.question}>Q. GrowSnap은 어떤 서비스인가요?</Text>
              <Text style={styles.answer}>
                GrowSnap은 <Text style={styles.emphasis}>"스크롤 시간을 성장 시간으로"</Text> 만드는 숏폼 학습 플랫폼입니다.
                재미있게 콘텐츠를 소비하면서 자연스럽게 새로운 지식과 인사이트를 얻을 수 있습니다.
              </Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>Q. 회원가입은 어떻게 하나요?</Text>
              <Text style={styles.answer}>
                Google, Naver, Kakao 계정으로 간편하게 소셜 로그인할 수 있습니다. 별도의 비밀번호 설정이 필요 없습니다.
              </Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>Q. 콘텐츠는 무료인가요?</Text>
              <Text style={styles.answer}>
                네, 현재 모든 콘텐츠는 무료로 이용하실 수 있습니다. 추후 프리미엄 콘텐츠가 추가될 수 있습니다.
              </Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>Q. 콘텐츠를 업로드하려면 어떻게 해야 하나요?</Text>
              <Text style={styles.answer}>
                하단의 업로드 탭(+)을 선택하여 비디오 또는 이미지를 업로드할 수 있습니다. 최대 500MB, 1분 이내의 영상을 지원합니다.
              </Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>Q. AI로 생성된 콘텐츠는 무엇인가요?</Text>
              <Text style={styles.answer}>
                초기 콘텐츠 확보를 위해 YouTube CC 라이선스 영상을 AI로 편집하여 제공합니다. 모든 콘텐츠에는 원저작자 출처가 명시됩니다.
              </Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>Q. 추천 알고리즘은 어떻게 작동하나요?</Text>
              <Text style={styles.answer}>
                사용자의 시청 기록, 좋아요, 저장 등의 행동 데이터를 분석하여 맞춤형 콘텐츠를 추천합니다.
                인기 콘텐츠(30%), 신규 콘텐츠(20%), 랜덤(10%), 개인화(40%) 비율로 제공됩니다.
              </Text>
            </View>

            <View style={styles.faqItem}>
              <Text style={styles.question}>Q. 계정을 삭제하고 싶어요</Text>
              <Text style={styles.answer}>
                설정 &gt; 회원 탈퇴에서 계정을 삭제할 수 있습니다. 삭제된 데이터는 복구할 수 없으니 신중하게 결정해주세요.
              </Text>
            </View>
          </View>

          {/* Contact */}
          <View style={styles.section}>
            <Text style={styles.sectionTitle}>문의하기</Text>
            <Text style={styles.answer}>
              추가 문의사항이 있거나 버그를 발견하셨다면 아래 방법으로 연락해주세요.
            </Text>

            {/* Email Contact */}
            <TouchableOpacity
              style={styles.emailButton}
              onPress={handleEmailContact}
            >
              <Ionicons name="mail-outline" size={20} color={theme.colors.text.primary} />
              <Text style={styles.emailButtonText}>이메일로 문의하기</Text>
            </TouchableOpacity>

            <View style={styles.emailInfo}>
              <Text style={styles.emailLabel}>이메일 주소</Text>
              <Text style={styles.emailAddress}>{SUPPORT_EMAIL}</Text>
            </View>

            {/* GitHub Issues */}
            <TouchableOpacity
              style={styles.contactButton}
              onPress={handleContactSupport}
            >
              <Ionicons name="logo-github" size={20} color={theme.colors.text.inverse} />
              <Text style={styles.contactButtonText}>GitHub Issues 열기</Text>
            </TouchableOpacity>
          </View>

          <View style={{ height: theme.spacing[8] }} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
