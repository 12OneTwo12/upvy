/**
 * 비디오 편집 화면
 *
 * 인스타그램 스타일의 비디오 편집
 * - 비디오 미리보기 및 재생
 * - 타임라인 트리밍
 * - 썸네일 자동 생성
 */

import React, { useState, useRef } from 'react';
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
  PanResponder,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Video, ResizeMode, AVPlaybackStatus } from 'expo-av';
import * as MediaLibrary from 'expo-media-library';
import * as VideoThumbnails from 'expo-video-thumbnails';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '@/theme';
import type { UploadStackParamList, MediaAsset } from '@/types/navigation.types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { generateUploadUrl, uploadFileToS3 } from '@/api/content.api';

type Props = NativeStackScreenProps<UploadStackParamList, 'VideoEdit'>;

const SCREEN_WIDTH = Dimensions.get('window').width;
const SCREEN_HEIGHT = Dimensions.get('window').height;
const VIDEO_HEIGHT = SCREEN_HEIGHT * 0.6;

// 최대 비디오 길이 (초)
const MAX_VIDEO_DURATION = 60;

export default function VideoEditScreen({ navigation, route }: Props) {
  const { asset } = route.params;

  const videoRef = useRef<Video>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);

  // 실제 비디오 파일 URI (ph:// -> file://)
  const [videoUri, setVideoUri] = useState<string>('');

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

  // 드래그 상태
  const [isDraggingStart, setIsDraggingStart] = useState(false);
  const [isDraggingEnd, setIsDraggingEnd] = useState(false);

  // 실제 파일 URI 로드
  React.useEffect(() => {
    const loadVideoUri = async () => {
      try {
        console.log('📹 Loading video URI for asset:', asset.id);
        const assetInfo = await MediaLibrary.getAssetInfoAsync(asset.id);
        const uri = assetInfo.localUri || assetInfo.uri;
        console.log('📹 Video URI loaded:', uri);
        setVideoUri(uri);
      } catch (error) {
        console.error('Failed to load video URI:', error);
        Alert.alert('오류', '비디오를 불러올 수 없습니다.');
      }
    };
    loadVideoUri();
  }, [asset.id]);

  // videoUri가 로드되고 duration이 있으면 썸네일 생성
  React.useEffect(() => {
    if (videoUri && duration > 0 && thumbnails.length === 0) {
      console.log('🖼️ Generating thumbnails - videoUri:', videoUri, 'duration:', duration);
      generateThumbnails(videoUri, duration);
    }
  }, [videoUri, duration]);

  const handleVideoLoad = (status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      const durationMs = status.durationMillis || 0;
      const durationSec = durationMs / 1000;
      console.log('📹 Video loaded - duration:', durationSec.toFixed(2), 'seconds');
      setDuration(durationSec);
      setTrimEnd(Math.min(durationSec, MAX_VIDEO_DURATION));

      // 자동으로 썸네일 생성 (videoUri가 있을 때만)
      if (videoUri) {
        generateThumbnails(videoUri, durationSec);
      }
    }
  };

  const handlePlaybackStatusUpdate = (status: AVPlaybackStatus) => {
    if (status.isLoaded) {
      const currentPosition = status.positionMillis / 1000;
      setPosition(currentPosition);
      setIsPlaying(status.isPlaying);

      // 트리밍 끝에 도달하면 정지하고 시작 위치로 이동
      if (currentPosition >= trimEnd) {
        videoRef.current?.pauseAsync();
        videoRef.current?.setPositionAsync(trimStart * 1000);
      }

      // 트리밍 시작 이전이면 시작 위치로 이동
      if (currentPosition < trimStart) {
        videoRef.current?.setPositionAsync(trimStart * 1000);
      }
    }
  };

  const generateThumbnails = async (uri: string, durationSec: number) => {
    console.log('🖼️ generateThumbnails called - uri:', uri, 'duration:', durationSec);
    setIsGeneratingThumbnails(true);
    try {
      // 3개의 타임스탬프에서 썸네일 생성
      const times = [
        Math.max(0, durationSec * 0.1),
        durationSec * 0.5,
        Math.min(durationSec * 0.9, durationSec - 1),
      ];

      console.log('🖼️ Generating thumbnails at times:', times);

      // expo-video-thumbnails로 실제 썸네일 생성
      const thumbnailResults = await Promise.all(
        times.map(async (time) => {
          try {
            const { uri: thumbnailUri } = await VideoThumbnails.getThumbnailAsync(uri, {
              time: time * 1000, // 밀리초 단위
              quality: 0.8,
            });
            console.log('🖼️ Thumbnail generated for time', time.toFixed(2), ':', thumbnailUri);
            return thumbnailUri;
          } catch (err) {
            console.error('Failed to generate thumbnail at time', time, ':', err);
            return null;
          }
        })
      );

      // null이 아닌 썸네일만 필터링
      const validThumbnails = thumbnailResults.filter((uri): uri is string => uri !== null);

      if (validThumbnails.length > 0) {
        console.log('🖼️ Successfully generated', validThumbnails.length, 'thumbnails');
        setThumbnails(validThumbnails);
        setSelectedThumbnail(validThumbnails[0]);
      } else {
        throw new Error('No thumbnails generated');
      }
    } catch (error) {
      console.error('Failed to generate thumbnails:', error);
      Alert.alert('알림', '썸네일 생성에 실패했습니다. 기본 썸네일을 사용합니다.');
      // fallback: 비디오 자체를 썸네일로
      setThumbnails([uri]);
      setSelectedThumbnail(uri);
    } finally {
      setIsGeneratingThumbnails(false);
    }
  };

  const handlePlayPause = async () => {
    if (isPlaying) {
      await videoRef.current?.pauseAsync();
    } else {
      // 현재 위치가 트리밍 범위를 벗어나면 시작 위치로 이동
      if (position >= trimEnd || position < trimStart) {
        await videoRef.current?.setPositionAsync(trimStart * 1000);
      }
      await videoRef.current?.playAsync();
    }
  };

  // 드래그 시작 시 초기 위치 저장
  const initialTrimStart = useRef(0);
  const initialTrimEnd = useRef(0);

  // 트리밍 시작 핸들 드래그
  const trimStartPanResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponderCapture: () => true,
      onPanResponderGrant: () => {
        console.log('🟢 Trim start handle - drag started');
        initialTrimStart.current = trimStart;
        setIsDraggingStart(true);
      },
      onPanResponderMove: (_, gestureState) => {
        if (duration === 0) return;

        // 타임라인 너비 기준으로 계산
        const timelineWidth = SCREEN_WIDTH - 32; // padding 제외
        const deltaTime = (gestureState.dx / timelineWidth) * duration;
        const newStart = Math.max(0, Math.min(trimEnd - 1, initialTrimStart.current + deltaTime));

        console.log('🟢 Trim start dragging:', newStart.toFixed(2));
        setTrimStart(newStart);
      },
      onPanResponderRelease: async () => {
        setIsDraggingStart(false);
        // 현재 재생 위치가 범위를 벗어나면 시작 위치로 이동
        if (position < trimStart || position >= trimEnd) {
          await videoRef.current?.setPositionAsync(trimStart * 1000);
        }
      },
    })
  ).current;

  // 트리밍 끝 핸들 드래그
  const trimEndPanResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponderCapture: () => true,
      onPanResponderGrant: () => {
        console.log('🔵 Trim end handle - drag started');
        initialTrimEnd.current = trimEnd;
        setIsDraggingEnd(true);
      },
      onPanResponderMove: (_, gestureState) => {
        if (duration === 0) return;

        const timelineWidth = SCREEN_WIDTH - 32;
        const deltaTime = (gestureState.dx / timelineWidth) * duration;
        const newEnd = Math.max(
          trimStart + 1,
          Math.min(duration, Math.min(trimStart + MAX_VIDEO_DURATION, initialTrimEnd.current + deltaTime))
        );

        console.log('🔵 Trim end dragging:', newEnd.toFixed(2));
        setTrimEnd(newEnd);
      },
      onPanResponderRelease: async () => {
        setIsDraggingEnd(false);
        // 현재 재생 위치가 범위를 벗어나면 시작 위치로 이동
        if (position < trimStart || position >= trimEnd) {
          await videoRef.current?.setPositionAsync(trimStart * 1000);
        }
      },
    })
  ).current;

  const handleNext = async () => {
    if (!selectedThumbnail) {
      Alert.alert('알림', '썸네일을 선택해주세요.');
      return;
    }

    const trimmedDuration = trimEnd - trimStart;
    if (trimmedDuration > MAX_VIDEO_DURATION) {
      Alert.alert('알림', `비디오는 최대 ${MAX_VIDEO_DURATION}초까지 업로드할 수 있습니다.`);
      return;
    }

    try {
      setIsUploading(true);

      if (!videoUri) {
        Alert.alert('오류', '비디오를 불러올 수 없습니다.');
        return;
      }

      // 2. 비디오 업로드
      const videoResponse = await fetch(videoUri);
      const videoBlob = await videoResponse.blob();

      const videoUploadUrlResponse = await generateUploadUrl({
        contentType: 'VIDEO',
        fileName: asset.filename,
        fileSize: videoBlob.size,
      });

      await uploadFileToS3(
        videoUploadUrlResponse.uploadUrl,
        videoBlob,
        (progress) => setUploadProgress(Math.floor(progress * 0.7))
      );

      const videoS3Url = videoUploadUrlResponse.uploadUrl.split('?')[0];

      // 2. 썸네일 업로드 (임시로 비디오 썸네일 스크린샷 사용)
      // TODO: expo-video-thumbnails로 실제 썸네일 생성
      const thumbnailResponse = await fetch(selectedThumbnail);
      const thumbnailBlob = await thumbnailResponse.blob();

      const thumbnailUploadUrlResponse = await generateUploadUrl({
        contentType: 'PHOTO',
        fileName: `thumbnail_${Date.now()}.jpg`,
        fileSize: thumbnailBlob.size,
        mimeType: 'image/jpeg',
      });

      await uploadFileToS3(
        thumbnailUploadUrlResponse.uploadUrl,
        thumbnailBlob,
        (progress) => setUploadProgress(Math.floor(70 + progress * 0.3))
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
          width: 1080,
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
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 - PhotoEditScreen 스타일 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerButton}>
          <Ionicons name="arrow-back" size={28} color={theme.colors.text.primary} />
        </TouchableOpacity>

        <Text style={styles.headerTitle}>편집</Text>

        <TouchableOpacity onPress={handleNext} disabled={isUploading} style={styles.headerButton}>
          <Text
            style={[
              styles.nextButtonText,
              isUploading && styles.disabledText,
            ]}
          >
            {isUploading ? '업로드 중...' : '다음'}
          </Text>
        </TouchableOpacity>
      </View>

      <ScrollView
        style={styles.content}
        showsVerticalScrollIndicator={false}
        scrollEnabled={!isDraggingStart && !isDraggingEnd}
      >
        {/* 비디오 미리보기 */}
        <View style={styles.videoContainer}>
          {videoUri ? (
            <Video
              ref={videoRef}
              source={{ uri: videoUri }}
              style={styles.video}
              resizeMode={ResizeMode.CONTAIN}
              isLooping={false}
              shouldPlay={false}
              onLoad={handleVideoLoad}
              onPlaybackStatusUpdate={handlePlaybackStatusUpdate}
            />
          ) : (
            <ActivityIndicator size="large" color={theme.colors.primary[500]} style={{ marginTop: 100 }} />
          )}

          {/* 재생/일시정지 버튼 */}
          <TouchableOpacity
            style={styles.playButton}
            onPress={handlePlayPause}
            activeOpacity={0.7}
          >
            <Ionicons
              name={isPlaying ? 'pause' : 'play'}
              size={32}
              color="#fff"
            />
          </TouchableOpacity>

          {/* 재생 시간 */}
          <View style={styles.timeIndicator}>
            <Text style={styles.timeText}>
              {formatTime(position)} / {formatTime(duration)}
            </Text>
          </View>

          {/* 트리밍 범위 표시 */}
          <View style={styles.trimRangeIndicator}>
            <Text style={styles.trimRangeText}>
              {formatTime(trimStart)} - {formatTime(trimEnd)} ({formatTime(trimEnd - trimStart)})
            </Text>
          </View>
        </View>

        {/* 타임라인 트리밍 - 인스타그램 스타일 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>트리밍</Text>
          <Text style={styles.sectionSubtitle}>
            타임라인을 드래그하여 {MAX_VIDEO_DURATION}초 이내로 선택하세요
          </Text>

          {/* 타임라인 트리밍 UI */}
          <View style={styles.timelineContainer}>
            {/* 진행 바 */}
            <View style={styles.timelineTrack}>
              {/* 선택된 범위 */}
              <View
                style={[
                  styles.timelineSelected,
                  {
                    left: `${(trimStart / duration) * 100}%`,
                    width: `${((trimEnd - trimStart) / duration) * 100}%`,
                  },
                ]}
              />

              {/* 현재 재생 위치 */}
              {duration > 0 && (
                <View
                  style={[
                    styles.playheadIndicator,
                    { left: `${(position / duration) * 100}%` },
                  ]}
                />
              )}

              {/* 트리밍 시작 핸들 */}
              {duration > 0 && (
                <TouchableOpacity
                  activeOpacity={1}
                  {...trimStartPanResponder.panHandlers}
                  style={[
                    styles.trimHandle,
                    styles.trimHandleLeft,
                    { left: `${(trimStart / duration) * 100}%` },
                    isDraggingStart && { transform: [{ scale: 1.3 }] },
                  ]}
                  hitSlop={{ top: 30, bottom: 30, left: 30, right: 30 }}
                >
                  <View style={[
                    styles.trimHandleBar,
                    isDraggingStart && styles.trimHandleActive,
                  ]}>
                    <View style={styles.trimHandleGrip} />
                  </View>
                </TouchableOpacity>
              )}

              {/* 트리밍 끝 핸들 */}
              {duration > 0 && (
                <TouchableOpacity
                  activeOpacity={1}
                  {...trimEndPanResponder.panHandlers}
                  style={[
                    styles.trimHandle,
                    styles.trimHandleRight,
                    { left: `${(trimEnd / duration) * 100}%` },
                    isDraggingEnd && { transform: [{ scale: 1.3 }] },
                  ]}
                  hitSlop={{ top: 30, bottom: 30, left: 30, right: 30 }}
                >
                  <View style={[
                    styles.trimHandleBar,
                    isDraggingEnd && styles.trimHandleActive,
                  ]}>
                    <View style={styles.trimHandleGrip} />
                  </View>
                </TouchableOpacity>
              )}
            </View>

            {/* 시간 레이블 */}
            <View style={styles.timeLabels}>
              <Text style={styles.timeLabel}>0:00</Text>
              <Text style={styles.timeLabel}>{formatTime(duration)}</Text>
            </View>
          </View>

          {/* 트리밍 정보 */}
          <View style={styles.trimInfo}>
            <View style={styles.trimInfoItem}>
              <Text style={styles.trimInfoLabel}>선택한 길이</Text>
              <Text style={styles.trimInfoValue}>{formatTime(trimEnd - trimStart)}</Text>
            </View>
            <View style={styles.trimInfoItem}>
              <Text style={styles.trimInfoLabel}>전체 길이</Text>
              <Text style={styles.trimInfoValue}>{formatTime(duration)}</Text>
            </View>
          </View>
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
                  onPress={() => {
                    console.log('📸 Thumbnail selected:', index);
                    setSelectedThumbnail(thumbnail);
                  }}
                >
                  <Image
                    source={{ uri: thumbnail }}
                    style={styles.thumbnailImage}
                    resizeMode="cover"
                  />
                  {selectedThumbnail === thumbnail && (
                    <View style={styles.thumbnailCheckmark}>
                      <Ionicons name="checkmark" size={16} color="#fff" />
                    </View>
                  )}
                </TouchableOpacity>
              ))}
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

        {/* 도움말 */}
        <View style={styles.helpSection}>
          <Text style={styles.helpText}>
            ✂️ 타임라인 핸들을 드래그하여 원하는 구간을 선택하세요
          </Text>
          <Text style={styles.helpText}>
            ▶️ 재생 버튼을 눌러 선택한 구간을 미리보기하세요
          </Text>
          <Text style={styles.helpText}>
            📌 최대 {MAX_VIDEO_DURATION}초까지 선택 가능합니다
          </Text>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

// 시간 포맷팅 헬퍼 함수
function formatTime(seconds: number): string {
  const mins = Math.floor(seconds / 60);
  const secs = Math.floor(seconds % 60);
  return `${mins}:${secs.toString().padStart(2, '0')}`;
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
    transform: [{ translateX: -35 }, { translateY: -35 }],
    width: 70,
    height: 70,
    borderRadius: 35,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 3,
    borderColor: 'rgba(255, 255, 255, 0.8)',
  },
  timeIndicator: {
    position: 'absolute',
    bottom: theme.spacing[2],
    right: theme.spacing[2],
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    borderRadius: theme.borderRadius.base,
  },
  timeText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
  },
  trimRangeIndicator: {
    position: 'absolute',
    top: theme.spacing[2],
    left: theme.spacing[2],
    backgroundColor: 'rgba(34, 197, 94, 0.9)',
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[2],
    borderRadius: theme.borderRadius.base,
  },
  trimRangeText: {
    color: theme.colors.text.inverse,
    fontSize: theme.typography.fontSize.sm,
    fontWeight: theme.typography.fontWeight.semibold,
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
    marginBottom: theme.spacing[4],
  },
  timelineContainer: {
    marginVertical: theme.spacing[4],
  },
  timelineTrack: {
    height: 80,
    backgroundColor: theme.colors.gray[200],
    borderRadius: theme.borderRadius.base,
    position: 'relative',
    overflow: 'visible',
  },
  timelineSelected: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    backgroundColor: theme.colors.primary[500],
    opacity: 0.3,
  },
  playheadIndicator: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    width: 3,
    backgroundColor: theme.colors.error,
    zIndex: 10,
  },
  trimHandle: {
    position: 'absolute',
    top: -15,
    bottom: -15,
    width: 60,
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 100,
  },
  trimHandleLeft: {
    marginLeft: -30,
  },
  trimHandleRight: {
    marginLeft: -30,
  },
  trimHandleBar: {
    width: 16,
    height: '100%',
    backgroundColor: theme.colors.primary[500],
    borderRadius: 8,
    borderWidth: 3,
    borderColor: theme.colors.text.inverse,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.5,
    shadowRadius: 8,
    elevation: 10,
  },
  trimHandleActive: {
    backgroundColor: theme.colors.primary[600],
    borderWidth: 3,
    shadowOpacity: 0.5,
    shadowRadius: 6,
    elevation: 8,
  },
  trimHandleGrip: {
    width: 4,
    height: 40,
    backgroundColor: theme.colors.text.inverse,
    borderRadius: 2,
  },
  timeLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: theme.spacing[2],
  },
  timeLabel: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.secondary,
  },
  trimInfo: {
    flexDirection: 'row',
    justifyContent: 'space-around',
    marginTop: theme.spacing[2],
    padding: theme.spacing[3],
    backgroundColor: theme.colors.gray[50],
    borderRadius: theme.borderRadius.base,
  },
  trimInfoItem: {
    alignItems: 'center',
  },
  trimInfoLabel: {
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[1],
  },
  trimInfoValue: {
    fontSize: theme.typography.fontSize.lg,
    color: theme.colors.text.primary,
    fontWeight: theme.typography.fontWeight.bold,
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
    marginRight: theme.spacing[3],
    borderRadius: theme.borderRadius.base,
    overflow: 'hidden',
    position: 'relative',
    borderWidth: 3,
    borderColor: 'transparent',
  },
  thumbnailSelected: {
    borderColor: theme.colors.primary[500],
  },
  thumbnailImage: {
    width: '100%',
    height: '100%',
  },
  thumbnailCheckmark: {
    position: 'absolute',
    top: 8,
    right: 8,
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: theme.colors.primary[500],
    justifyContent: 'center',
    alignItems: 'center',
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
    marginBottom: theme.spacing[6],
    borderRadius: theme.borderRadius.base,
  },
  helpText: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    marginBottom: theme.spacing[1],
  },
});
