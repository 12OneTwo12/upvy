import React from 'react';
import { View, Text, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'PrivacyPolicy'>;

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
});

/**
 * Privacy Policy Screen
 * GrowSnap 개인정보 보호정책
 */
export default function PrivacyPolicyScreen() {
  const styles = useStyles();
  const navigation = useNavigation<NavigationProp>();

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
        <Text style={styles.headerTitle}>개인정보 보호정책</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <Text style={styles.lastUpdate}>최종 업데이트: 2025년 11월 10일</Text>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>1. 개인정보 수집 항목</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 소셜 로그인(OAuth 2.0)을 통해 다음 정보를 수집합니다:
            </Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>이메일 주소</Text> (필수)</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>프로필 정보</Text> (닉네임, 프로필 사진 - 선택)</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>소셜 로그인 제공자</Text> (Google, Naver, Kakao)</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>서비스 이용 기록</Text> (콘텐츠 조회, 좋아요, 댓글, 저장)</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>기기 정보</Text> (OS 버전, 앱 버전, 기기 식별자)</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>2. 개인정보 이용 목적</Text>
            <Text style={styles.paragraph}>수집된 정보는 다음 목적으로만 사용됩니다:</Text>
            <Text style={styles.listItem}>• 회원 가입 및 로그인 인증</Text>
            <Text style={styles.listItem}>• 서비스 제공 및 맞춤형 콘텐츠 추천</Text>
            <Text style={styles.listItem}>• 서비스 개선 및 신규 기능 개발</Text>
            <Text style={styles.listItem}>• 문의 응대 및 고객 지원</Text>
            <Text style={styles.listItem}>• 부정 이용 방지 및 서비스 보안</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>3. 개인정보 보관 기간</Text>
            <View style={styles.table}>
              <View style={[styles.tableRow, styles.tableHeader]}>
                <View style={styles.tableCell}>
                  <Text style={[styles.tableCellText, styles.tableCellHeader]}>정보 항목</Text>
                </View>
                <View style={styles.tableCell}>
                  <Text style={[styles.tableCellText, styles.tableCellHeader]}>보관 기간</Text>
                </View>
              </View>
              <View style={styles.tableRow}>
                <View style={styles.tableCell}>
                  <Text style={styles.tableCellText}>계정 정보</Text>
                </View>
                <View style={styles.tableCell}>
                  <Text style={styles.tableCellText}>회원 탈퇴 시까지</Text>
                </View>
              </View>
              <View style={styles.tableRow}>
                <View style={styles.tableCell}>
                  <Text style={styles.tableCellText}>서비스 이용 기록</Text>
                </View>
                <View style={styles.tableCell}>
                  <Text style={styles.tableCellText}>3년</Text>
                </View>
              </View>
              <View style={[styles.tableRow, { borderBottomWidth: 0 }]}>
                <View style={styles.tableCell}>
                  <Text style={styles.tableCellText}>로그 데이터</Text>
                </View>
                <View style={styles.tableCell}>
                  <Text style={styles.tableCellText}>1년</Text>
                </View>
              </View>
            </View>
            <Text style={styles.paragraph}>
              회원 탈퇴 시 모든 개인정보는 즉시 삭제되며, 법령에서 요구하는 경우를 제외하고는 보관하지 않습니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>4. 개인정보 제3자 제공</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 사용자의 동의 없이 개인정보를 제3자에게 제공하지 않습니다. 다만, 다음의 경우는 예외입니다:
            </Text>
            <Text style={styles.listItem}>• 법률에 의해 요구되는 경우</Text>
            <Text style={styles.listItem}>• 수사 목적으로 법령에 정해진 절차에 따라 요구받는 경우</Text>
            <Text style={styles.listItem}>• 서비스 제공을 위해 필요한 경우 (AWS, Kafka 등 인프라 제공자)</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>5. OAuth 소셜 로그인</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 비밀번호를 저장하지 않으며, Google, Naver, Kakao의 OAuth 2.0 프로토콜을 사용합니다.
            </Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>Google:</Text> 이메일 주소, 프로필 정보</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>Naver:</Text> 이메일 주소, 닉네임, 프로필 사진</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>Kakao:</Text> 이메일 주소, 닉네임, 프로필 사진</Text>
            <Text style={styles.paragraph}>
              각 소셜 로그인 제공자의 개인정보 처리방침은 해당 서비스의 정책을 따릅니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>6. 쿠키 및 추적 기술</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 다음과 같은 기술을 사용합니다:
            </Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>JWT 토큰:</Text> 로그인 상태 유지 (Access Token 1시간, Refresh Token 14일)</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>로컬 저장소:</Text> 앱 설정 및 캐시 데이터</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>행동 추적:</Text> 콘텐츠 추천을 위한 시청 패턴 분석 (익명화)</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>7. 아동의 개인정보 보호</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 만 14세 미만 아동의 개인정보를 수집하지 않습니다. 만약 부모님 또는 보호자께서
              자녀가 개인정보를 제공했다고 판단되시면 즉시 연락주시기 바랍니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>8. 사용자 권리</Text>
            <Text style={styles.paragraph}>사용자는 다음 권리를 행사할 수 있습니다:</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>열람:</Text> 자신의 개인정보 확인</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>수정:</Text> 프로필 정보 변경</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>삭제:</Text> 회원 탈퇴 (설정 &gt; 회원 탈퇴)</Text>
            <Text style={styles.listItem}>• <Text style={styles.emphasis}>처리 정지:</Text> 계정 일시 정지 요청</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>9. 보안</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 개인정보를 안전하게 보호하기 위해 다음 조치를 취합니다:
            </Text>
            <Text style={styles.listItem}>• TLS 1.3 암호화 통신</Text>
            <Text style={styles.listItem}>• 비밀번호 bcrypt 해싱 (OAuth 사용으로 비밀번호 저장 안 함)</Text>
            <Text style={styles.listItem}>• AWS 보안 인프라 활용</Text>
            <Text style={styles.listItem}>• 정기적인 보안 점검 및 업데이트</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>10. 정책 변경</Text>
            <Text style={styles.paragraph}>
              본 개인정보 보호정책은 법령 또는 서비스 변경에 따라 수정될 수 있습니다. 중요한 변경사항은
              서비스 내 공지를 통해 최소 30일 전에 안내합니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>11. 문의</Text>
            <Text style={styles.paragraph}>
              개인정보 보호와 관련한 문의사항은:
            </Text>
            <Text style={styles.listItem}>• GitHub Issues: https://github.com/12OneTwo12/grow-snap</Text>
            <Text style={styles.listItem}>• 앱 내: 설정 &gt; 도움말 및 지원</Text>
          </View>

          <View style={{ height: theme.spacing[8] }} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
