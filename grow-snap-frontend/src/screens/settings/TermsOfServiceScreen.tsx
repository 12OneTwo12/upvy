import React from 'react';
import { View, Text, ScrollView, TouchableOpacity } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { RootStackParamList } from '@/types/navigation.types';
import { theme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';

type NavigationProp = NativeStackNavigationProp<RootStackParamList, 'TermsOfService'>;

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
});

/**
 * Terms of Service Screen
 * GrowSnap 서비스 이용약관
 */
export default function TermsOfServiceScreen() {
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
        <Text style={styles.headerTitle}>서비스 약관</Text>
      </View>

      <ScrollView style={styles.scrollView} showsVerticalScrollIndicator={false}>
        <View style={styles.content}>
          <Text style={styles.lastUpdate}>최종 업데이트: 2025년 11월 10일</Text>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>1. 서비스 소개</Text>
            <Text style={styles.paragraph}>
              GrowSnap(이하 "서비스")은 <Text style={styles.emphasis}>"스크롤 시간을 성장 시간으로"</Text>를
              슬로건으로 하는 숏폼 학습 플랫폼입니다. 사용자는 재미있게 스크롤하며 자연스럽게 새로운 지식과
              인사이트를 얻을 수 있습니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>2. 서비스 이용</Text>
            <Text style={styles.paragraph}>
              본 약관에 동의함으로써 귀하는 다음 사항에 동의합니다:
            </Text>
            <Text style={styles.listItem}>• 만 14세 이상의 사용자만 서비스를 이용할 수 있습니다</Text>
            <Text style={styles.listItem}>• 소셜 로그인(Google, Naver, Kakao)을 통해 계정을 생성합니다</Text>
            <Text style={styles.listItem}>• 정확한 정보를 제공하고 계정 보안을 유지할 책임이 있습니다</Text>
            <Text style={styles.listItem}>• 타인의 권리를 침해하거나 불법적인 활동을 하지 않습니다</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>3. 콘텐츠 및 저작권</Text>
            <Text style={styles.paragraph}>
              <Text style={styles.emphasis}>사용자가 업로드한 콘텐츠:</Text> 사용자는 자신이 업로드한 콘텐츠에 대한
              저작권을 보유하며, GrowSnap에 해당 콘텐츠를 사용, 배포, 수정할 수 있는 비독점적 라이선스를 부여합니다.
            </Text>
            <Text style={styles.paragraph}>
              <Text style={styles.emphasis}>AI 생성 콘텐츠:</Text> 초기 콘텐츠 확보를 위해 YouTube CC 라이선스
              영상을 AI로 편집하여 제공합니다. 모든 콘텐츠에는 원저작자 출처가 명시됩니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>4. 금지 행위</Text>
            <Text style={styles.paragraph}>다음 행위는 금지됩니다:</Text>
            <Text style={styles.listItem}>• 타인의 저작권, 상표권 등 지적재산권 침해</Text>
            <Text style={styles.listItem}>• 허위 정보 유포 또는 사기 행위</Text>
            <Text style={styles.listItem}>• 욕설, 혐오 표현, 차별적 발언</Text>
            <Text style={styles.listItem}>• 서비스 운영 방해 또는 시스템 해킹 시도</Text>
            <Text style={styles.listItem}>• 스팸, 광고성 콘텐츠 무분별한 게시</Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>5. 서비스 변경 및 중단</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 사전 통지 없이 서비스의 일부 또는 전체를 변경, 중단, 종료할 수 있습니다.
              단, 중요한 변경사항은 최소 30일 전에 공지합니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>6. 면책사항</Text>
            <Text style={styles.paragraph}>
              GrowSnap은 서비스를 "있는 그대로" 제공하며, 서비스의 정확성, 완전성, 신뢰성에 대해
              보증하지 않습니다. 사용자의 서비스 이용으로 인한 손해에 대해 책임지지 않습니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>7. 계정 해지</Text>
            <Text style={styles.paragraph}>
              사용자는 언제든지 계정을 삭제할 수 있습니다. 계정 삭제 시 모든 데이터는 복구할 수 없습니다.
              GrowSnap은 약관 위반 시 사전 통지 없이 계정을 정지하거나 삭제할 수 있습니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>8. 약관 변경</Text>
            <Text style={styles.paragraph}>
              본 약관은 필요에 따라 변경될 수 있으며, 변경 사항은 서비스 내 공지를 통해 안내됩니다.
              변경된 약관에 동의하지 않을 경우 서비스 이용을 중단해야 합니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>9. 준거법 및 관할</Text>
            <Text style={styles.paragraph}>
              본 약관은 대한민국 법률에 따라 해석되며, 서비스 이용과 관련한 분쟁은 대한민국 법원의
              관할에 따릅니다.
            </Text>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionTitle}>10. 문의</Text>
            <Text style={styles.paragraph}>
              서비스 약관에 대한 문의사항은 GitHub Issues 또는 설정 &gt; 도움말 및 지원을 통해 문의해주세요.
            </Text>
          </View>

          <View style={{ height: theme.spacing[8] }} />
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
