/**
 * 사진 편집 화면
 *
 * 인스타그램 스타일의 사진 편집
 * - 상단: 큰 미리보기 (스와이프 가능)
 * - 하단: 작은 사진 목록 (horizontal scroll)
 * - 비율 선택 오버레이
 */

import React, { useState } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  Alert,
  ScrollView,
  Image,
  FlatList,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Ionicons } from '@expo/vector-icons';
import * as MediaLibrary from 'expo-media-library';
import * as ImageManipulator from 'expo-image-manipulator';
import { useTranslation } from 'react-i18next';
import { useTheme } from '@/theme';
import { createStyleSheet } from '@/utils/styles';
import type { UploadStackParamList, MediaAsset } from '@/types/navigation.types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { generateUploadUrl, uploadFileToS3 } from '@/api/content.api';

type Props = NativeStackScreenProps<UploadStackParamList, 'PhotoEdit'>;

const SCREEN_WIDTH = Dimensions.get('window').width;
const SCREEN_HEIGHT = Dimensions.get('window').height;
const PREVIEW_HEIGHT = SCREEN_HEIGHT * 0.6;
const THUMBNAIL_SIZE = 60;

type AspectRatio = '1:1' | '4:5' | '16:9' | 'original';

/**
 * aspectRatio에 맞는 크롭 영역 계산
 *
 * @param width 원본 이미지 너비
 * @param height 원본 이미지 높이
 * @param ratio 선택된 비율
 * @returns crop 영역 { originX, originY, width, height }
 */
function calculateCropArea(
  width: number,
  height: number,
  ratio: AspectRatio
): { originX: number; originY: number; width: number; height: number } {
  let targetRatio: number;

  switch (ratio) {
    case '1:1':
      targetRatio = 1;
      break;
    case '4:5':
      targetRatio = 4 / 5;
      break;
    case '16:9':
      targetRatio = 16 / 9;
      break;
    default:
      return { originX: 0, originY: 0, width, height };
  }

  const currentRatio = width / height;

  let cropWidth: number;
  let cropHeight: number;
  let originX: number;
  let originY: number;

  if (currentRatio > targetRatio) {
    // 이미지가 더 넓음 → 좌우를 자름
    cropHeight = height;
    cropWidth = height * targetRatio;
    originX = (width - cropWidth) / 2;
    originY = 0;
  } else {
    // 이미지가 더 높음 → 상하를 자름
    cropWidth = width;
    cropHeight = width / targetRatio;
    originX = 0;
    originY = (height - cropHeight) / 2;
  }

  return {
    originX: Math.round(originX),
    originY: Math.round(originY),
    width: Math.round(cropWidth),
    height: Math.round(cropHeight),
  };
}

interface AspectRatioOption {
  labelKey: string;
  value: AspectRatio;
  icon: string;
}

const ASPECT_RATIOS: AspectRatioOption[] = [
  { labelKey: 'upload:edit.aspectRatio.original', value: 'original', icon: 'expand-outline' },
  { labelKey: 'upload:edit.aspectRatio.square', value: '1:1', icon: 'square-outline' },
  { labelKey: 'upload:edit.aspectRatio.portrait', value: '4:5', icon: 'crop' },
  { labelKey: 'upload:edit.aspectRatio.landscape', value: '16:9', icon: 'tablet-landscape-outline' },
];

export default function PhotoEditScreen({ navigation, route }: Props) {
  const styles = useStyles();
  const dynamicTheme = useTheme();
  const { t } = useTranslation(['upload', 'common']);
  const { assets: initialAssets } = route.params;

  const [assets, setAssets] = useState<MediaAsset[]>(initialAssets);
  const [currentPhotoIndex, setCurrentPhotoIndex] = useState(0);
  const [aspectRatio, setAspectRatio] = useState<AspectRatio>('1:1');
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [showAspectRatioMenu, setShowAspectRatioMenu] = useState(false);

  const handleRemovePhoto = (index: number) => {
    if (assets.length === 1) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:edit.minPhotoRequired'));
      return;
    }

    Alert.alert(
      t('upload:edit.removePhoto'),
      t('upload:edit.removePhotoConfirm'),
      [
        { text: t('common:button.cancel'), style: 'cancel' },
        {
          text: t('common:button.delete'),
          style: 'destructive',
          onPress: () => {
            const newAssets = assets.filter((_, i) => i !== index);
            setAssets(newAssets);
            if (currentPhotoIndex >= newAssets.length) {
              setCurrentPhotoIndex(newAssets.length - 1);
            }
          },
        },
      ]
    );
  };

  const handleNext = async () => {
    try {
      setIsUploading(true);

      // 모든 사진을 업로드하고 S3 URL 수집
      const uploadedPhotoUrls: string[] = [];
      let firstContentId = '';

      for (let i = 0; i < assets.length; i++) {
        const asset = assets[i];

        // 1. MediaLibrary로 실제 파일 URI 얻기 (ph:// -> file://)
        const assetInfo = await MediaLibrary.getAssetInfoAsync(asset.id);
        let fileUri = assetInfo.localUri || assetInfo.uri;
        const originalFileName = asset.filename;
        const extension = originalFileName.split('.').pop()?.toLowerCase();

        let actualMimeType: string;

        // 2. aspectRatio에 따라 이미지 처리
        if (aspectRatio !== 'original') {
          // Crop이 필요한 경우 → 항상 JPEG로 변환
          // 2-1. 먼저 1080px로 리사이즈
          const resizedImage = await ImageManipulator.manipulateAsync(
            fileUri,
            [{ resize: { width: 1080 } }],
            { compress: 1, format: ImageManipulator.SaveFormat.JPEG }
          );

          // 2-2. 리사이즈된 이미지 크기로 크롭 영역 계산
          const cropArea = calculateCropArea(
            resizedImage.width,
            resizedImage.height,
            aspectRatio
          );

          // 2-3. 크롭 적용
          const croppedImage = await ImageManipulator.manipulateAsync(
            resizedImage.uri,
            [{ crop: cropArea }],
            { compress: 0.9, format: ImageManipulator.SaveFormat.JPEG }
          );

          fileUri = croppedImage.uri;
          actualMimeType = 'image/jpeg';
        } else {
          // Original 비율 → 원본 포맷 유지 (HEIC/HEIF만 JPEG로 변환)
          if (extension === 'heic' || extension === 'heif') {
            const convertedImage = await ImageManipulator.manipulateAsync(
              fileUri,
              [],
              { compress: 0.95, format: ImageManipulator.SaveFormat.JPEG }
            );
            fileUri = convertedImage.uri;
            actualMimeType = 'image/jpeg';
          } else if (extension === 'png') {
            actualMimeType = 'image/png';
          } else {
            actualMimeType = 'image/jpeg';
          }
        }

        // 3. blob 가져오기
        const response = await fetch(fileUri);
        let blob = await response.blob();

        // blob.type이 정확하지 않을 수 있으므로 명시적으로 설정
        if (blob.type !== actualMimeType) {
          blob = new Blob([blob], { type: actualMimeType });
        }

        // 4. Presigned URL 요청 - 실제 MIME 타입을 명시적으로 전달
        const uploadUrlResponse = await generateUploadUrl({
          contentType: 'PHOTO',
          fileName: originalFileName,
          fileSize: blob.size,
          mimeType: actualMimeType,
        });

        // 첫 번째 사진의 contentId를 저장 (PHOTO 타입은 여러 사진이지만 하나의 contentId 사용)
        if (i === 0) {
          firstContentId = uploadUrlResponse.contentId;
        }

        // 4. S3에 업로드
        await uploadFileToS3(
          uploadUrlResponse.uploadUrl,
          blob,
          (progress) => {
            const totalProgress = ((i + progress / 100) / assets.length) * 100;
            setUploadProgress(Math.floor(totalProgress));
          }
        );

        // 5. Presigned URL에서 실제 S3 URL 추출 (query string 제거)
        const s3Url = uploadUrlResponse.uploadUrl.split('?')[0];
        uploadedPhotoUrls.push(s3Url);
      }

      // 5. 메타데이터 입력 화면으로 이동
      navigation.navigate('ContentMetadata', {
        contentId: firstContentId,
        contentType: 'PHOTO',
        mediaInfo: {
          uri: uploadedPhotoUrls,
          thumbnailUrl: uploadedPhotoUrls[0],
          width: 1080,
          height: 1080,
        },
      });
    } catch (error) {
      console.error('Upload failed:', error);
      Alert.alert(t('common:label.error', 'Error'), t('upload:edit.uploadFailed'));
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerButton}>
          <Ionicons name="arrow-back" size={28} color={dynamicTheme.colors.text.primary} />
        </TouchableOpacity>

        <Text style={styles.headerTitle}>{t('upload:edit.title')}</Text>

        <TouchableOpacity onPress={handleNext} disabled={isUploading} style={styles.headerButton}>
          <Text
            style={[
              styles.nextButtonText,
              isUploading && styles.disabledText,
            ]}
          >
            {isUploading ? t('upload:edit.uploading') : t('common:button.next')}
          </Text>
        </TouchableOpacity>
      </View>

      {/* 큰 미리보기 - 스와이프 가능 */}
      <View style={styles.previewContainer}>
        <FlatList
          data={assets}
          renderItem={({ item }) => (
            <View style={styles.previewImageContainer}>
              <Image
                source={{ uri: item.uri }}
                style={[
                  styles.previewImage,
                  aspectRatio === '1:1' && { aspectRatio: 1 },
                  aspectRatio === '4:5' && { aspectRatio: 4 / 5 },
                  aspectRatio === '16:9' && { aspectRatio: 16 / 9 },
                ]}
                resizeMode="cover"
              />
            </View>
          )}
          keyExtractor={(item, index) => `${item.id}-${index}`}
          horizontal
          pagingEnabled
          showsHorizontalScrollIndicator={false}
          onViewableItemsChanged={({ viewableItems }) => {
            if (viewableItems.length > 0 && viewableItems[0].index !== null) {
              setCurrentPhotoIndex(viewableItems[0].index);
            }
          }}
          viewabilityConfig={{
            itemVisiblePercentThreshold: 50,
          }}
        />

        {/* 비율 선택 버튼 (오버레이) */}
        <TouchableOpacity
          style={styles.aspectRatioButton}
          onPress={() => setShowAspectRatioMenu(!showAspectRatioMenu)}
        >
          <Ionicons name="expand-outline" size={24} color="#fff" />
        </TouchableOpacity>

        {/* 비율 선택 메뉴 */}
        {showAspectRatioMenu && (
          <View style={styles.aspectRatioMenu}>
            {ASPECT_RATIOS.map((option) => (
              <TouchableOpacity
                key={option.value}
                style={[
                  styles.aspectRatioMenuItem,
                  aspectRatio === option.value && styles.aspectRatioMenuItemActive,
                ]}
                onPress={() => {
                  setAspectRatio(option.value);
                  setShowAspectRatioMenu(false);
                }}
              >
                <Ionicons
                  name={option.icon as any}
                  size={20}
                  color={aspectRatio === option.value ? dynamicTheme.colors.primary[500] : dynamicTheme.colors.text.secondary}
                />
                <Text
                  style={[
                    styles.aspectRatioMenuItemText,
                    aspectRatio === option.value && styles.aspectRatioMenuItemTextActive,
                  ]}
                >
                  {t(option.labelKey)}
                </Text>
              </TouchableOpacity>
            ))}
          </View>
        )}

        {/* 사진 카운터 */}
        {assets.length > 1 && (
          <View style={styles.photoCounter}>
            <Text style={styles.photoCounterText}>
              {t('upload:edit.photoCounter', { current: currentPhotoIndex + 1, total: assets.length })}
            </Text>
          </View>
        )}

        {/* 인디케이터 */}
        {assets.length > 1 && (
          <View style={styles.previewIndicator}>
            {assets.map((_, index) => (
              <View
                key={index}
                style={[
                  styles.indicatorDot,
                  index === currentPhotoIndex && styles.indicatorDotActive,
                ]}
              />
            ))}
          </View>
        )}
      </View>

      {/* 하단 사진 목록 */}
      <View style={styles.bottomContainer}>
        <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.thumbnailScroll}>
          {assets.map((asset, index) => (
            <TouchableOpacity
              key={`${asset.id}-${index}`}
              style={[
                styles.thumbnailItem,
                index === currentPhotoIndex && styles.thumbnailItemActive,
              ]}
              onPress={() => {
                // 해당 인덱스로 스크롤하는 로직은 ref로 구현 가능하지만 간단히 하기 위해 생략
                setCurrentPhotoIndex(index);
              }}
            >
              <Image source={{ uri: asset.uri }} style={styles.thumbnailImage} />
              {index === currentPhotoIndex && <View style={styles.thumbnailOverlay} />}

              {/* 삭제 버튼 */}
              {assets.length > 1 && (
                <TouchableOpacity
                  style={styles.thumbnailDelete}
                  onPress={(e) => {
                    e.stopPropagation();
                    handleRemovePhoto(index);
                  }}
                >
                  <Ionicons name="close-circle" size={20} color="#fff" />
                </TouchableOpacity>
              )}
            </TouchableOpacity>
          ))}
        </ScrollView>

        {/* 업로드 진행률 */}
        {isUploading && (
          <View style={styles.uploadProgressContainer}>
            <Text style={styles.uploadProgressText}>
              {t('upload:edit.uploadProgress', { progress: uploadProgress })}
            </Text>
            <View style={styles.progressBar}>
              <View
                style={[styles.progressFill, { width: `${uploadProgress}%` }]}
              />
            </View>
          </View>
        )}

        {/* 도움말 */}
        <View style={styles.helpSection}>
          <Text style={styles.helpText}>
            💡 {t('upload:edit.help.thumbnail')}
          </Text>
          <Text style={styles.helpText}>
            ✨ {t('upload:edit.help.swipe')}
          </Text>
        </View>
      </View>
    </SafeAreaView>
  );
}

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
  nextButtonText: {
    fontSize: theme.typography.fontSize.base,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.primary[500],
    textAlign: 'right',
  },
  disabledText: {
    color: theme.colors.text.tertiary,
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  previewContainer: {
    width: SCREEN_WIDTH,
    height: PREVIEW_HEIGHT,
    backgroundColor: theme.colors.gray[100],
    position: 'relative',
  },
  previewImageContainer: {
    width: SCREEN_WIDTH,
    height: PREVIEW_HEIGHT,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.colors.gray[900],
  },
  previewImage: {
    width: SCREEN_WIDTH,
  },
  aspectRatioButton: {
    position: 'absolute',
    left: theme.spacing[3],
    bottom: theme.spacing[3],
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  aspectRatioMenu: {
    position: 'absolute',
    left: theme.spacing[3],
    bottom: theme.spacing[16],
    backgroundColor: 'rgba(0, 0, 0, 0.9)',
    borderRadius: theme.borderRadius.base,
    padding: theme.spacing[2],
    minWidth: 120,
  },
  aspectRatioMenuItem: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: theme.spacing[2],
    paddingHorizontal: theme.spacing[3],
    gap: theme.spacing[2],
  },
  aspectRatioMenuItemActive: {
    backgroundColor: 'rgba(34, 197, 94, 0.2)',
    borderRadius: theme.borderRadius.sm,
  },
  aspectRatioMenuItemText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.inverse,
  },
  aspectRatioMenuItemTextActive: {
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.semibold,
  },
  photoCounter: {
    position: 'absolute',
    top: theme.spacing[3],
    right: theme.spacing[3],
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    paddingHorizontal: theme.spacing[2],
    paddingVertical: theme.spacing[1],
    borderRadius: theme.borderRadius.full,
  },
  photoCounterText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
  },
  previewIndicator: {
    position: 'absolute',
    bottom: theme.spacing[3],
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: theme.spacing[1],
  },
  indicatorDot: {
    width: 6,
    height: 6,
    borderRadius: 3,
    backgroundColor: 'rgba(255, 255, 255, 0.5)',
  },
  indicatorDotActive: {
    backgroundColor: '#fff',
  },
  bottomContainer: {
    flex: 1,
    backgroundColor: theme.colors.background.primary,
  },
  thumbnailScroll: {
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[3],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  thumbnailItem: {
    width: THUMBNAIL_SIZE,
    height: THUMBNAIL_SIZE,
    marginRight: theme.spacing[2],
    borderRadius: theme.borderRadius.sm,
    overflow: 'hidden',
    position: 'relative',
    borderWidth: 2,
    borderColor: 'transparent',
  },
  thumbnailItemActive: {
    borderColor: theme.colors.primary[500],
  },
  thumbnailImage: {
    width: '100%',
    height: '100%',
  },
  thumbnailOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(34, 197, 94, 0.3)',
  },
  thumbnailDelete: {
    position: 'absolute',
    top: -6,
    right: -6,
    backgroundColor: theme.colors.error,
    borderRadius: 10,
  },
  uploadProgressContainer: {
    padding: theme.spacing[4],
  },
  uploadProgressText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[2],
    textAlign: 'center',
  },
  progressBar: {
    height: 4,
    backgroundColor: theme.colors.gray[200],
    borderRadius: theme.borderRadius.sm,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: theme.colors.primary[500],
  },
  helpSection: {
    padding: theme.spacing[4],
    backgroundColor: theme.colors.gray[50],
    marginHorizontal: theme.spacing[4],
    marginTop: theme.spacing[2],
    borderRadius: theme.borderRadius.base,
  },
  helpText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[1],
  },
}));
