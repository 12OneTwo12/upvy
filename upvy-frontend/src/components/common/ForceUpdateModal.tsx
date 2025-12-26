/**
 * 강제 업데이트 모달 컴포넌트
 *
 * 앱 버전이 최소 지원 버전보다 낮을 경우 표시됩니다.
 * 사용자는 업데이트를 완료하기 전까지 앱을 사용할 수 없습니다.
 */

import React from 'react';
import {
  Modal,
  View,
  Text,
  TouchableOpacity,
  Linking,
  StyleSheet,
  Alert,
} from 'react-native';
import type { AppVersionCheckResponse } from '@/types/app-version.types';

interface ForceUpdateModalProps {
  /** 모달 표시 여부 */
  visible: boolean;
  /** 버전 정보 */
  versionInfo: AppVersionCheckResponse | null;
}

export const ForceUpdateModal: React.FC<ForceUpdateModalProps> = ({
  visible,
  versionInfo,
}) => {
  const handleUpdate = async () => {
    if (!versionInfo?.storeUrl) return;

    try {
      const supported = await Linking.canOpenURL(versionInfo.storeUrl);
      if (supported) {
        await Linking.openURL(versionInfo.storeUrl);
      }
    } catch (error) {
      console.error('[ForceUpdateModal] Failed to open store URL:', error);
      Alert.alert(
        '업데이트 실패',
        '스토어로 이동하는 데 실패했습니다. 앱 스토어 또는 플레이 스토어에서 직접 앱을 업데이트해 주세요.'
      );
    }
  };

  if (!visible || !versionInfo) return null;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      statusBarTranslucent
      // 뒤로 가기/dismiss 불가능
      onRequestClose={() => {}}
    >
      <View style={styles.overlay}>
        <View style={styles.container}>
          {/* 제목 */}
          <Text style={styles.title}>업데이트가 필요합니다</Text>

          {/* 설명 */}
          <Text style={styles.description}>
            더 나은 서비스 제공을 위해{'\n'}
            최신 버전으로 업데이트해 주세요.
          </Text>

          {/* 버전 정보 */}
          <View style={styles.versionInfo}>
            <Text style={styles.versionLabel}>최신 버전</Text>
            <Text style={styles.versionText}>{versionInfo.latestVersion}</Text>
          </View>

          {/* 업데이트 버튼 */}
          <TouchableOpacity
            style={styles.updateButton}
            onPress={handleUpdate}
            activeOpacity={0.8}
          >
            <Text style={styles.updateButtonText}>업데이트하기</Text>
          </TouchableOpacity>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  container: {
    backgroundColor: '#1C1C1E',
    borderRadius: 16,
    padding: 24,
    width: '100%',
    maxWidth: 340,
    alignItems: 'center',
  },
  title: {
    fontSize: 20,
    fontWeight: '700',
    color: '#FFFFFF',
    marginBottom: 12,
    textAlign: 'center',
  },
  description: {
    fontSize: 14,
    color: '#ACACAC',
    textAlign: 'center',
    lineHeight: 20,
    marginBottom: 24,
  },
  versionInfo: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 24,
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: '#2C2C2E',
    borderRadius: 8,
    width: '100%',
    justifyContent: 'space-between',
  },
  versionLabel: {
    fontSize: 14,
    color: '#ACACAC',
  },
  versionText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
  },
  updateButton: {
    backgroundColor: '#00A3FF',
    borderRadius: 12,
    paddingVertical: 16,
    paddingHorizontal: 32,
    width: '100%',
    alignItems: 'center',
  },
  updateButtonText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#FFFFFF',
  },
});
