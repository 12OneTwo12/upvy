/**
 * 비디오 편집 화면
 *
 * 인스타그램 스타일의 비디오 편집
 * - 비디오 미리보기
 * - 타임라인 트리밍
 * - 썸네일 선택 (자동 생성 3개 또는 수동 선택)
 */

import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Dimensions,
  Alert,
  ScrollView,
  Image,
  ActivityIndicator,
} from 'react-native';
import { Video, ResizeMode } from 'expo-av';
import * as MediaLibrary from 'expo-media-library';
import { theme } from '@/theme';
import type { UploadStackParamList, MediaAsset } from '@/types/navigation.types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { generateUploadUrl, uploadFileToS3 } from '@/api/content.api';

type Props = NativeStackScreenProps<UploadStackParamList, 'VideoEdit'>;

const SCREEN_WIDTH = Dimensions.get('window').width;
const VIDEO_HEIGHT = SCREEN_WIDTH * (16 / 9); // 16:9 비율

export default function VideoEditScreen({ navigation, route }: Props) {
  const { asset } = route.params;

  const videoRef = useRef<Video>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);

  // 트리밍 (초 단위)
  const [trimStart, setTrimStart] = useState(0);
  const [trimEnd, setTrimEnd] = useState(0);

  // 썸네일
  const [thumbnails, setThumbnails] = useState<string[]>([]);
  const [selectedThumbnail, setSelectedThumbnail] = useState<string>('');
  const [isGeneratingThumbnails, setIsGeneratingThumbnails] = useState(false);

  // 업로드 상태
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  const handleVideoLoad = (status: any) => {
    if (status.isLoaded) {
      const durationMs = status.durationMillis || 0;
      const durationSec = durationMs / 1000;
      setDuration(durationSec);
      setTrimEnd(Math.min(durationSec, 60)); // 최대 60초

      // 자동으로 썸네일 3개 생성
      generateThumbnails(durationSec);
    }
  };

  const handlePlaybackStatusUpdate = (status: any) => {
    if (status.isLoaded) {
      setPosition(status.positionMillis / 1000);
      setIsPlaying(status.isPlaying);

      // 트리밍 끝에 도달하면 정지
      if (status.positionMillis / 1000 >= trimEnd) {
        videoRef.current?.pauseAsync();
        videoRef.current?.setPositionAsync(trimStart * 1000);
      }
    }
  };

  const generateThumbnails = async (durationSec: number) => {
    setIsGeneratingThumbnails(true);
    try {
      // 실제 구현에서는 expo-video-thumbnails 사용
      // 여기서는 UI만 구현
      const times = [
        durationSec * 0.2,
        durationSec * 0.5,
        durationSec * 0.8,
      ];

      // TODO: 실제 썸네일 생성 로직
      // import * as VideoThumbnails from 'expo-video-thumbnails';
      // const thumbnailUris = await Promise.all(
      //   times.map(time =>
      //     VideoThumbnails.getThumbnailAsync(uri, { time: time * 1000 })
      //   )
      // );

      // 임시로 빈 배열 설정
      setThumbnails(['', '', '']);
      setSelectedThumbnail('');
    } catch (error) {
      console.error('Failed to generate thumbnails:', error);
    } finally {
      setIsGeneratingThumbnails(false);
    }
  };

  const handlePlayPause = async () => {
    if (isPlaying) {
      await videoRef.current?.pauseAsync();
    } else {
      await videoRef.current?.playAsync();
    }
  };

  const handleNext = async () => {
    if (!selectedThumbnail) {
      Alert.alert('알림', '썸네일을 선택해주세요.');
      return;
    }

    const trimmedDuration = trimEnd - trimStart;
    if (trimmedDuration > 60) {
      Alert.alert('알림', '비디오는 최대 60초까지 업로드할 수 있습니다.');
      return;
    }

    try {
      setIsUploading(true);

      // 1. MediaLibrary로 실제 파일 URI 얻기 (ph:// -> file://)
      const assetInfo = await MediaLibrary.getAssetInfoAsync(asset.id);
      const videoFileUri = assetInfo.localUri || assetInfo.uri;

      // 2. 비디오 업로드
      const videoResponse = await fetch(videoFileUri);
      const videoBlob = await videoResponse.blob();

      const videoUploadUrlResponse = await generateUploadUrl({
        contentType: 'VIDEO',
        fileName: asset.filename,
        fileSize: videoBlob.size,
      });

      await uploadFileToS3(
        videoUploadUrlResponse.uploadUrl,
        videoBlob,
        (progress) => setUploadProgress(progress * 0.7) // 비디오 업로드 70%
      );

      // Presigned URL에서 실제 S3 URL 추출
      const videoS3Url = videoUploadUrlResponse.uploadUrl.split('?')[0];

      // 2. 썸네일 업로드
      const thumbnailResponse = await fetch(selectedThumbnail);
      const thumbnailBlob = await thumbnailResponse.blob();

      const thumbnailUploadUrlResponse = await generateUploadUrl({
        contentType: 'PHOTO',
        fileName: `thumbnail_${Date.now()}.jpg`,
        fileSize: thumbnailBlob.size,
      });

      await uploadFileToS3(
        thumbnailUploadUrlResponse.uploadUrl,
        thumbnailBlob,
        (progress) => setUploadProgress(70 + progress * 0.3) // 썸네일 업로드 30%
      );

      const thumbnailS3Url = thumbnailUploadUrlResponse.uploadUrl.split('?')[0];

      // 3. 메타데이터 입력 화면으로 이동
      navigation.navigate('ContentMetadata', {
        contentId: videoUploadUrlResponse.contentId,
        contentType: 'VIDEO',
        mediaInfo: {
          uri: videoS3Url,
          thumbnailUrl: thumbnailS3Url,
          duration: Math.floor(trimmedDuration),
          width: 1080, // TODO: 실제 비디오 해상도
          height: 1920,
        },
      });
    } catch (error) {
      console.error('Upload failed:', error);
      Alert.alert('오류', '업로드에 실패했습니다. 다시 시도해주세요.');
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <View style={styles.container}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()}>
          <Text style={styles.headerButton}>뒤로</Text>
        </TouchableOpacity>

        <Text style={styles.headerTitle}>비디오 편집</Text>

        <TouchableOpacity onPress={handleNext} disabled={isUploading}>
          <Text
            style={[
              styles.headerButton,
              styles.nextButton,
              isUploading && styles.disabledButton,
            ]}
          >
            {isUploading ? '업로드 중...' : '다음'}
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.content} showsVerticalScrollIndicator={false}>
        {/* 비디오 미리보기 */}
        <View style={styles.videoContainer}>
          <Video
            ref={videoRef}
            source={{ uri: asset.uri }}
            style={styles.video}
            resizeMode={ResizeMode.CONTAIN}
            isLooping={false}
            onLoad={handleVideoLoad}
            onPlaybackStatusUpdate={handlePlaybackStatusUpdate}
          />

          {/* 재생/일시정지 버튼 */}
          <TouchableOpacity
            style={styles.playButton}
            onPress={handlePlayPause}
            activeOpacity={0.7}
          >
            <Text style={styles.playButtonText}>{isPlaying ? '⏸' : '▶️'}</Text>
          </TouchableOpacity>

          {/* 재생 시간 */}
          <View style={styles.timeIndicator}>
            <Text style={styles.timeText}>
              {Math.floor(position)}s / {Math.floor(duration)}s
            </Text>
          </View>
        </View>

        {/* 트리밍 섹션 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>트리밍</Text>
          <Text style={styles.sectionSubtitle}>
            최대 60초까지 선택할 수 있습니다
          </Text>

          <View style={styles.trimInfo}>
            <Text style={styles.trimText}>
              시작: {Math.floor(trimStart)}초
            </Text>
            <Text style={styles.trimText}>
              끝: {Math.floor(trimEnd)}초
            </Text>
            <Text style={styles.trimText}>
              길이: {Math.floor(trimEnd - trimStart)}초
            </Text>
          </View>

          {/* TODO: 실제 트리밍 UI 구현 (슬라이더 등) */}
          <Text style={styles.todoText}>
            타임라인 트리밍 UI (슬라이더 구현 필요)
          </Text>
        </View>

        {/* 썸네일 선택 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>썸네일 선택</Text>
          <Text style={styles.sectionSubtitle}>
            비디오의 대표 이미지를 선택하세요
          </Text>

          {isGeneratingThumbnails ? (
            <ActivityIndicator
              size="large"
              color={theme.colors.primary[500]}
              style={styles.loader}
            />
          ) : (
            <ScrollView
              horizontal
              showsHorizontalScrollIndicator={false}
              style={styles.thumbnailScroll}
            >
              {thumbnails.map((thumbnail, index) => (
                <TouchableOpacity
                  key={index}
                  style={[
                    styles.thumbnailItem,
                    selectedThumbnail === thumbnail && styles.thumbnailSelected,
                  ]}
                  onPress={() => setSelectedThumbnail(thumbnail)}
                >
                  <View style={styles.thumbnailPlaceholder}>
                    {thumbnail ? (
                      <Image source={{ uri: thumbnail }} style={styles.thumbnailImage} />
                    ) : (
                      <Text style={styles.thumbnailPlaceholderText}>
                        썸네일 {index + 1}
                      </Text>
                    )}
                  </View>
                  {selectedThumbnail === thumbnail && (
                    <View style={styles.thumbnailCheckmark}>
                      <Text style={styles.checkmarkText}>✓</Text>
                    </View>
                  )}
                </TouchableOpacity>
              ))}

              {/* 수동 선택 버튼 */}
              <TouchableOpacity style={styles.thumbnailItem}>
                <View style={styles.thumbnailPlaceholder}>
                  <Text style={styles.thumbnailPlaceholderText}>+ 직접 선택</Text>
                </View>
              </TouchableOpacity>
            </ScrollView>
          )}
        </View>

        {/* 업로드 진행률 */}
        {isUploading && (
          <View style={styles.uploadProgressContainer}>
            <Text style={styles.uploadProgressText}>
              업로드 중... {uploadProgress}%
            </Text>
            <View style={styles.progressBar}>
              <View
                style={[styles.progressFill, { width: `${uploadProgress}%` }]}
              />
            </View>
          </View>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
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
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.primary,
  },
  nextButton: {
    color: theme.colors.primary[500],
    fontWeight: theme.typography.fontWeight.semibold,
  },
  disabledButton: {
    color: theme.colors.text.tertiary,
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
  },
  content: {
    flex: 1,
  },
  videoContainer: {
    width: SCREEN_WIDTH,
    height: VIDEO_HEIGHT,
    backgroundColor: theme.colors.gray[900],
    position: 'relative',
  },
  video: {
    width: '100%',
    height: '100%',
  },
  playButton: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: [{ translateX: -30 }, { translateY: -30 }],
    width: 60,
    height: 60,
    borderRadius: 30,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    alignItems: 'center',
  },
  playButtonText: {
    fontSize: 24,
  },
  timeIndicator: {
    position: 'absolute',
    bottom: theme.spacing[2],
    right: theme.spacing[2],
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    paddingHorizontal: theme.spacing[2],
    paddingVertical: theme.spacing[1],
    borderRadius: theme.borderRadius.sm,
  },
  timeText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.medium,
  },
  section: {
    padding: theme.spacing[4],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  sectionTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
    marginBottom: theme.spacing[1],
  },
  sectionSubtitle: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[3],
  },
  trimInfo: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: theme.spacing[3],
  },
  trimText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.primary,
  },
  todoText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    fontStyle: 'italic',
    textAlign: 'center',
    paddingVertical: theme.spacing[4],
  },
  loader: {
    marginVertical: theme.spacing[6],
  },
  thumbnailScroll: {
    marginTop: theme.spacing[2],
  },
  thumbnailItem: {
    width: 120,
    height: 160,
    marginRight: theme.spacing[2],
    borderRadius: theme.borderRadius.base,
    overflow: 'hidden',
    position: 'relative',
  },
  thumbnailSelected: {
    borderWidth: 3,
    borderColor: theme.colors.primary[500],
  },
  thumbnailPlaceholder: {
    width: '100%',
    height: '100%',
    backgroundColor: theme.colors.gray[200],
    justifyContent: 'center',
    alignItems: 'center',
  },
  thumbnailPlaceholderText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.tertiary,
    textAlign: 'center',
  },
  thumbnailImage: {
    width: '100%',
    height: '100%',
  },
  thumbnailCheckmark: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: theme.colors.primary[500],
    justifyContent: 'center',
    alignItems: 'center',
  },
  checkmarkText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.bold,
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
});
