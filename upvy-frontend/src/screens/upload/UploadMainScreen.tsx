/**
 * 크리에이터 스튜디오 - 업로드 메인 화면
 *
 * 단순 버튼 스타일 UI (Google Play 정책 준수)
 * - 카메라로 촬영
 * - 갤러리에서 선택 (시스템 Photo Picker 사용)
 *
 * Note: READ_MEDIA_IMAGES/VIDEO 권한 대신 Photo Picker API 사용
 * Google Play 정책: https://support.google.com/googleplay/android-developer/answer/14115180
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  Alert,
  ActivityIndicator,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as ImagePicker from 'expo-image-picker';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import type { UploadStackParamList, MediaAsset } from '@/types/navigation.types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type Props = NativeStackScreenProps<UploadStackParamList, 'UploadMain'>;

const useStyles = createStyleSheet((theme) => ({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  headerButton: {
    padding: theme.spacing[1],
    minWidth: 60,
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[6],
  },
  buttonContainer: {
    width: '100%',
    gap: theme.spacing[4],
  },
  actionButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing[3],
    backgroundColor: theme.colors.background.secondary,
    paddingVertical: theme.spacing[5],
    paddingHorizontal: theme.spacing[6],
    borderRadius: theme.borderRadius.xl,
    borderWidth: 1,
    borderColor: theme.colors.border.light,
  },
  actionButtonPrimary: {
    backgroundColor: theme.colors.primary[500],
    borderColor: theme.colors.primary[500],
  },
  actionButtonText: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  actionButtonTextPrimary: {
    color: theme.colors.text.inverse,
  },
  actionButtonSubtext: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginTop: theme.spacing[1],
  },
  actionButtonSubtextPrimary: {
    color: 'rgba(255, 255, 255, 0.8)',
  },
  buttonContent: {
    alignItems: 'center',
  },
  divider: {
    flexDirection: 'row',
    alignItems: 'center',
    marginVertical: theme.spacing[4],
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: theme.colors.border.light,
  },
  dividerText: {
    marginHorizontal: theme.spacing[3],
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
  },
  helpSection: {
    position: 'absolute',
    bottom: theme.spacing[8],
    left: theme.spacing[6],
    right: theme.spacing[6],
    padding: theme.spacing[4],
    backgroundColor: theme.colors.gray[50],
    borderRadius: theme.borderRadius.base,
  },
  helpText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    textAlign: 'center',
    lineHeight: 20,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
}));

export default function UploadMainScreen({ navigation }: Props) {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const { t } = useTranslation(['upload', 'common']);
  const [isLoading, setIsLoading] = useState(false);

  const handleCameraCapture = async (mediaType: 'photo' | 'video') => {
    try {
      setIsLoading(true);

      const { status } = await ImagePicker.requestCameraPermissionsAsync();

      if (status !== 'granted') {
        Alert.alert(t('upload:main.permissionRequired'), t('upload:main.cameraPermissionMessage'));
        return;
      }

      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: mediaType === 'photo' ? 'images' as any : 'videos' as any,
        allowsEditing: false,
        quality: 1,
        videoMaxDuration: 180,
      });

      if (!result.canceled && result.assets[0]) {
        const asset = result.assets[0];
        const mediaAsset: MediaAsset = {
          id: Date.now().toString(),
          uri: asset.uri,
          mediaType: asset.type === 'video' ? 'video' : 'photo',
          duration: asset.duration || 0,
          width: asset.width || 1080,
          height: asset.height || 1920,
          filename: `capture_${Date.now()}.${asset.type === 'video' ? 'mp4' : 'jpg'}`,
        };

        if (mediaType === 'video') {
          navigation.navigate('VideoEdit', {
            asset: mediaAsset,
            type: 'video',
          });
        } else {
          navigation.navigate('PhotoEdit', {
            assets: [mediaAsset],
            type: 'photo',
          });
        }
      }
    } catch (error: any) {
      console.error('Camera capture failed:', error);

      if (error?.code === 'ERR_CAMERA_UNAVAILABLE_ON_SIMULATOR') {
        Alert.alert(
          t('upload:main.simulatorLimitation'),
          t('upload:main.simulatorCameraMessage')
        );
      } else {
        Alert.alert(t('common:label.error', 'Error'), t('upload:main.cameraError'));
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleGallerySelect = async (mediaType: 'photo' | 'video') => {
    try {
      setIsLoading(true);

      // Photo Picker API 사용 - READ_MEDIA_* 권한 불필요
      // Android 13+에서 시스템 Photo Picker가 자동으로 사용됨
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: mediaType === 'photo' ? 'images' as any : 'videos' as any,
        allowsMultipleSelection: mediaType === 'photo',
        selectionLimit: mediaType === 'photo' ? 10 : 1,
        quality: 1,
        videoMaxDuration: 180,
      });

      if (!result.canceled && result.assets.length > 0) {
        if (mediaType === 'video') {
          const asset = result.assets[0];

          // 비디오 길이 체크 (최대 3분 = 180초)
          if (asset.duration && asset.duration > 180) {
            Alert.alert(
              t('upload:main.videoLengthExceeded'),
              t('upload:main.videoLengthMessage')
            );
          }

          const mediaAsset: MediaAsset = {
            id: Date.now().toString(),
            uri: asset.uri,
            mediaType: 'video',
            duration: asset.duration || 0,
            width: asset.width || 1080,
            height: asset.height || 1920,
            filename: asset.fileName || `video_${Date.now()}.mp4`,
          };

          navigation.navigate('VideoEdit', {
            asset: mediaAsset,
            type: 'video',
          });
        } else {
          const mediaAssets: MediaAsset[] = result.assets.map((asset, index) => ({
            id: `${Date.now()}_${index}`,
            uri: asset.uri,
            mediaType: 'photo' as const,
            duration: 0,
            width: asset.width || 1080,
            height: asset.height || 1080,
            filename: asset.fileName || `photo_${Date.now()}_${index}.jpg`,
          }));

          navigation.navigate('PhotoEdit', {
            assets: mediaAssets,
            type: 'photo',
          });
        }
      }
    } catch (error) {
      console.error('Gallery select failed:', error);
      Alert.alert(t('common:label.error', 'Error'), t('upload:main.loadError'));
    } finally {
      setIsLoading(false);
    }
  };

  if (isLoading) {
    return (
      <SafeAreaView style={styles.container} edges={['top']}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerButton}>
            <Ionicons name="close" size={28} color={dynamicTheme.colors.text.primary} />
          </TouchableOpacity>
          <Text style={styles.headerTitle}>{t('upload:main.title')}</Text>
          <View style={styles.headerButton} />
        </View>
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={dynamicTheme.colors.primary[500]} />
        </View>
      </SafeAreaView>
    );
  }

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerButton}>
          <Ionicons name="close" size={28} color={dynamicTheme.colors.text.primary} />
        </TouchableOpacity>
        <Text style={styles.headerTitle}>{t('upload:main.title')}</Text>
        <View style={styles.headerButton} />
      </View>

      {/* 메인 콘텐츠 */}
      <View style={styles.content}>
        <View style={styles.buttonContainer}>
          {/* 비디오 업로드 */}
          <TouchableOpacity
            style={[styles.actionButton, styles.actionButtonPrimary]}
            onPress={() => handleGallerySelect('video')}
            activeOpacity={0.8}
          >
            <Ionicons name="videocam" size={32} color="#fff" />
            <View style={styles.buttonContent}>
              <Text style={[styles.actionButtonText, styles.actionButtonTextPrimary]}>
                {t('upload:main.selectVideo')}
              </Text>
              <Text style={[styles.actionButtonSubtext, styles.actionButtonSubtextPrimary]}>
                {t('upload:main.maxDuration')}
              </Text>
            </View>
          </TouchableOpacity>

          {/* 사진 업로드 */}
          <TouchableOpacity
            style={styles.actionButton}
            onPress={() => handleGallerySelect('photo')}
            activeOpacity={0.8}
          >
            <Ionicons name="images" size={32} color={dynamicTheme.colors.text.primary} />
            <View style={styles.buttonContent}>
              <Text style={styles.actionButtonText}>
                {t('upload:main.selectPhotos')}
              </Text>
              <Text style={styles.actionButtonSubtext}>
                {t('upload:main.maxPhotos')}
              </Text>
            </View>
          </TouchableOpacity>

          {/* 구분선 */}
          <View style={styles.divider}>
            <View style={styles.dividerLine} />
            <Text style={styles.dividerText}>{t('upload:main.or')}</Text>
            <View style={styles.dividerLine} />
          </View>

          {/* 카메라로 촬영 */}
          <TouchableOpacity
            style={styles.actionButton}
            onPress={() => handleCameraCapture('video')}
            activeOpacity={0.8}
          >
            <Ionicons name="camera" size={32} color={dynamicTheme.colors.text.primary} />
            <View style={styles.buttonContent}>
              <Text style={styles.actionButtonText}>
                {t('upload:main.camera')}
              </Text>
              <Text style={styles.actionButtonSubtext}>
                {t('upload:main.recordNew')}
              </Text>
            </View>
          </TouchableOpacity>
        </View>

        {/* 도움말 */}
        <View style={styles.helpSection}>
          <Text style={styles.helpText}>
            {t('upload:main.helpText')}
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}
