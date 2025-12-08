/**
 * 비디오 편집 화면
 *
 * 인스타그램 스타일의 비디오 편집
 * - 비디오 미리보기 및 재생
 * - 타임라인 편집 (길이 조절)
 * - 썸네일 자동 생성
 */

import React, { useState, useRef, useEffect } from 'react';
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
  Platform,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useVideoPlayer, VideoView } from 'expo-video';
import { trim, isValidFile } from 'react-native-video-trim';
import * as MediaLibrary from 'expo-media-library';
import * as VideoThumbnails from 'expo-video-thumbnails';
import * as FileSystem from 'expo-file-system/legacy';
import { Ionicons } from '@expo/vector-icons';
import { useTranslation } from 'react-i18next';
import { theme } from '@/theme';
import { cleanIOSVideoUri } from '@/utils/videoUtils';
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
  const { t } = useTranslation(['upload', 'common']);
  const { asset } = route.params;

  const [isPlaying, setIsPlaying] = useState(false);
  const [duration, setDuration] = useState(0);
  const [position, setPosition] = useState(0);

  // 실제 비디오 파일 URI (ph:// -> file://)
  const [videoUri, setVideoUri] = useState<string>('');
  // 썸네일 생성용 URI (expo-video-thumbnails는 ph:// URI를 지원하지 않음)
  const [thumbnailUri, setThumbnailUri] = useState<string>('');
  const [isLoadingVideo, setIsLoadingVideo] = useState(true);

  // expo-video 플레이어 - ph:// URI를 직접 사용
  const player = useVideoPlayer(videoUri || null, (player) => {
    player.loop = false;
    player.muted = false;
  });

  // 트리밍 (초 단위)
  const [trimStart, setTrimStart] = useState(0);
  const [trimEnd, setTrimEnd] = useState(0);

  // 썸네일
  const [thumbnails, setThumbnails] = useState<string[]>([]);
  const [selectedThumbnail, setSelectedThumbnail] = useState<string>('');
  const [isGeneratingThumbnails, setIsGeneratingThumbnails] = useState(false);

  // 타임라인 프레임 썸네일 (편집용)
  const [timelineFrames, setTimelineFrames] = useState<string[]>([]);
  const [isGeneratingFrames, setIsGeneratingFrames] = useState(false);

  // 업로드 상태
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  // 드래그 상태
  const [isDraggingStart, setIsDraggingStart] = useState(false);
  const [isDraggingEnd, setIsDraggingEnd] = useState(false);

  // 트리밍 상태
  const [isTrimming, setIsTrimming] = useState(false);
  const [trimmingProgress, setTrimmingProgress] = useState(0);

  // 타임라인 실제 너비
  const [timelineWidth, setTimelineWidth] = useState(SCREEN_WIDTH - 32);

  // 드래그 중일 때 네비게이션 제스처 비활성화
  React.useEffect(() => {
    navigation.setOptions({
      gestureEnabled: !isDraggingStart && !isDraggingEnd,
    });
  }, [isDraggingStart, isDraggingEnd, navigation]);

  // expo-video 플레이어 이벤트 리스너
  useEffect(() => {
    if (!player) return;

    // 재생 상태 변경
    const playingSubscription = player.addListener('playingChange', ({ isPlaying: playing }) => {
      setIsPlaying(playing);
    });

    // 상태 변경 (로딩 완료 등)
    const statusSubscription = player.addListener('statusChange', ({ status }) => {
      console.log('📹 Player status:', status);
      if (status === 'readyToPlay') {
        const durationSec = player.duration;
        console.log('📹 Video loaded - duration:', durationSec.toFixed(2), 'seconds');
        setDuration(durationSec);
        setTrimEnd(Math.min(durationSec, MAX_VIDEO_DURATION));

        // 타임라인 프레임 생성 (thumbnailUri 사용)
        if (thumbnailUri) {
          generateTimelineFrames(thumbnailUri, durationSec);
        }
      }
    });

    // 재생 위치 업데이트 (인터벌 사용)
    const positionInterval = setInterval(() => {
      if (player && player.currentTime !== undefined) {
        const currentPos = player.currentTime;
        setPosition(currentPos);

        // 트리밍 끝에 도달하면 정지
        if (isPlaying && currentPos >= trimEnd) {
          player.pause();
          player.currentTime = trimStart;
        }
      }
    }, 100);

    return () => {
      playingSubscription.remove();
      statusSubscription.remove();
      clearInterval(positionInterval);
    };
  }, [player, thumbnailUri, isPlaying, trimStart, trimEnd]);

  // 트리밍 범위 변경 시 썸네일 재생성 (드래그 종료 후)
  const prevTrimRange = useRef({ start: 0, end: 0 });
  React.useEffect(() => {
    // 드래그 중이면 무시
    if (isDraggingStart || isDraggingEnd) return;
    // videoUri가 없거나 duration이 0이면 무시
    if (!videoUri || duration === 0) return;
    // 트리밍 범위가 변경되지 않았으면 무시
    if (prevTrimRange.current.start === trimStart && prevTrimRange.current.end === trimEnd) return;

    // 범위가 유의미하게 변경되었을 때만 썸네일 재생성 (0.5초 이상 차이)
    const startDiff = Math.abs(prevTrimRange.current.start - trimStart);
    const endDiff = Math.abs(prevTrimRange.current.end - trimEnd);
    if (startDiff > 0.5 || endDiff > 0.5) {
      console.log('🖼️ Trim range changed, regenerating thumbnails:', trimStart, '-', trimEnd);
      prevTrimRange.current = { start: trimStart, end: trimEnd };
      // thumbnailUri 사용 (expo-video-thumbnails는 ph:// URI 미지원)
      if (thumbnailUri) {
        generateThumbnailsInRange(thumbnailUri, trimStart, trimEnd);
      }
    }
  }, [isDraggingStart, isDraggingEnd, trimStart, trimEnd, thumbnailUri, duration]);

  // 실제 파일 URI 로드
  // expo-video는 ph:// URI를 직접 처리할 수 있음 (PHAsset 지원)
  // 하지만 expo-video-thumbnails는 ph:// URI를 지원하지 않으므로 localUri도 가져옴
  React.useEffect(() => {
    const loadVideoUri = async () => {
      try {
        setIsLoadingVideo(true);
        console.log('📹 Loading video URI for asset:', asset.id);
        console.log('📹 Asset URI:', asset.uri);

        // asset.uri가 file:// 형식이면 바로 사용 (카메라 촬영 등)
        if (asset.uri && asset.uri.startsWith('file://')) {
          const cleanUri = cleanIOSVideoUri(asset.uri);
          console.log('📹 Using file:// URI:', cleanUri);
          setVideoUri(cleanUri);
          setThumbnailUri(cleanUri); // 썸네일도 같은 URI 사용
          setIsLoadingVideo(false);
          return;
        }

        // ph:// URI인 경우
        if (asset.uri && asset.uri.startsWith('ph://')) {
          console.log('📹 Using ph:// URI directly for expo-video:', asset.uri);
          setVideoUri(asset.uri); // expo-video는 ph:// 지원

          // 썸네일 생성을 위해 localUri 가져오기
          try {
            const assetInfo = await MediaLibrary.getAssetInfoAsync(asset.id, {
              shouldDownloadFromNetwork: true,
            });
            if (assetInfo.localUri) {
              const cleanLocalUri = cleanIOSVideoUri(assetInfo.localUri);
              console.log('📹 Got localUri for thumbnails:', cleanLocalUri);
              setThumbnailUri(cleanLocalUri);
            } else {
              console.log('⚠️ No localUri available for thumbnails');
              setThumbnailUri(''); // 썸네일 생성 불가
            }
          } catch (e) {
            console.log('⚠️ Failed to get localUri for thumbnails:', e);
            setThumbnailUri('');
          }

          setIsLoadingVideo(false);
          return;
        }

        // 기타 경우
        if (asset.uri) {
          const cleanUri = cleanIOSVideoUri(asset.uri);
          console.log('📹 Using original URI:', cleanUri);
          setVideoUri(cleanUri);
          setThumbnailUri(cleanUri);
          setIsLoadingVideo(false);
          return;
        }

        throw new Error('Could not get video URI from asset');
      } catch (error) {
        console.error('❌ Failed to load video URI:', error);
        setIsLoadingVideo(false);
        Alert.alert(t('common:label.error', 'Error'), t('upload:edit.videoLoadError'));
        setTimeout(() => {
          navigation.goBack();
        }, 2000);
      }
    };
    loadVideoUri();
  }, [asset.id, asset.uri, navigation]);

  // thumbnailUri가 로드되고 duration이 있으면 초기 썸네일 생성
  // expo-video-thumbnails는 ph:// URI를 지원하지 않으므로 thumbnailUri(localUri) 사용
  React.useEffect(() => {
    if (thumbnailUri && duration > 0 && thumbnails.length === 0) {
      console.log('🖼️ Generating initial thumbnails - thumbnailUri:', thumbnailUri, 'range: 0 -', duration);
      generateThumbnailsInRange(thumbnailUri, 0, Math.min(duration, MAX_VIDEO_DURATION));
    }
  }, [thumbnailUri, duration]);

  // handleVideoLoad와 handlePlaybackStatusUpdate는 expo-video 이벤트 리스너로 대체됨

  // 트리밍 범위 내에서 썸네일 생성
  const generateThumbnailsInRange = async (uri: string, startSec: number, endSec: number) => {
    const rangeDuration = endSec - startSec;
    console.log('🖼️ generateThumbnails called - uri:', uri, 'range:', startSec, '-', endSec);
    setIsGeneratingThumbnails(true);
    try {
      // 트리밍 범위 내에서 5개의 타임스탬프 생성
      const times = [
        startSec + rangeDuration * 0.1,   // 트리밍 범위의 10%
        startSec + rangeDuration * 0.3,   // 트리밍 범위의 30%
        startSec + rangeDuration * 0.5,   // 트리밍 범위의 50%
        startSec + rangeDuration * 0.7,   // 트리밍 범위의 70%
        startSec + Math.min(rangeDuration * 0.9, rangeDuration - 0.5),  // 트리밍 범위의 90%
      ];

      console.log('🖼️ Generating thumbnails at times:', times);

      // expo-video-thumbnails로 실제 썸네일 생성
      const thumbnailResults = await Promise.all(
        times.map(async (time) => {
          try {
            const { uri: thumbnailUri } = await VideoThumbnails.getThumbnailAsync(uri, {
              time: Math.floor(time * 1000), // 밀리초 단위 (정수로 변환)
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
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:edit.thumbnailError'));
      // fallback: 비디오 자체를 썸네일로
      setThumbnails([uri]);
      setSelectedThumbnail(uri);
    } finally {
      setIsGeneratingThumbnails(false);
    }
  };

  // 타임라인용 프레임 생성 (10개)
  const generateTimelineFrames = async (uri: string, durationSec: number) => {
    console.log('🎬 Generating timeline frames');
    setIsGeneratingFrames(true);
    try {
      const frameCount = 10;
      const interval = durationSec / frameCount;

      const times = Array.from({ length: frameCount }, (_, i) => i * interval);

      const frameResults = await Promise.all(
        times.map(async (time) => {
          try {
            const { uri: frameUri } = await VideoThumbnails.getThumbnailAsync(uri, {
              time: Math.floor(time * 1000),
              quality: 0.5, // 타임라인용이므로 낮은 품질로
            });
            return frameUri;
          } catch (err) {
            console.error('Failed to generate frame at time', time, ':', err);
            return null;
          }
        })
      );

      const validFrames = frameResults.filter((uri): uri is string => uri !== null);
      console.log('🎬 Generated', validFrames.length, 'timeline frames');
      setTimelineFrames(validFrames);
    } catch (error) {
      console.error('Failed to generate timeline frames:', error);
    } finally {
      setIsGeneratingFrames(false);
    }
  };

  // react-native-video-trim을 사용한 비디오 트리밍 (Best Practice)
  // FFmpeg 기반으로 안정적인 트리밍 결과를 제공합니다.
  // iOS Photo Library 파일은 앱 샌드박스 외부에 있어 네이티브 모듈이 직접 접근 불가
  // 따라서 캐시 디렉토리로 복사 후 트리밍
  const trimVideoNative = async (inputUri: string, startTime: number, endTime: number): Promise<string> => {
    try {
      console.log('✂️ Starting video trim with react-native-video-trim');
      console.log('✂️ Input URI:', inputUri);

      // iOS URI에는 해시(#) 뒤에 메타데이터가 붙어있을 수 있음
      // iOS plist 메타데이터는 '#YnBsaXN0'(base64 시그니처)로 시작함
      // 파일명에 #이 있을 수 있으므로 iOS 메타데이터 패턴만 제거
      const cleanUri = inputUri.replace(/#YnBsaXN0[A-Za-z0-9+/=]*$/, '');
      console.log('✂️ Clean URI:', cleanUri);

      setIsTrimming(true);
      setTrimmingProgress(5);

      // iOS Photo Library 파일을 앱 캐시로 복사 (네이티브 모듈 접근 가능하도록)
      // /var/mobile/Media/ 경로는 앱 샌드박스 외부라 네이티브 모듈이 직접 접근 불가
      let trimSourceUri = cleanUri;
      if (cleanUri.includes('/var/mobile/Media/') || cleanUri.includes('/PhotoData/')) {
        console.log('✂️ Copying video to cache for native module access...');
        const cacheVideoPath = `${FileSystem.cacheDirectory}trim_source_${Date.now()}.mp4`;
        await FileSystem.copyAsync({
          from: cleanUri,
          to: cacheVideoPath,
        });
        trimSourceUri = cacheVideoPath;
        console.log('✂️ Video copied to:', trimSourceUri);
      }

      setTrimmingProgress(20);

      // 파일 유효성 검사
      const isValid = await isValidFile(trimSourceUri);
      if (!isValid) {
        throw new Error('Invalid video file');
      }

      setTrimmingProgress(30);
      console.log('✂️ Trim range:', startTime, '-', endTime, 'seconds');

      // react-native-video-trim의 trim() 함수 호출
      // startTime, endTime은 밀리초(ms) 단위
      const result = await trim(trimSourceUri, {
        startTime: Math.floor(startTime * 1000), // ms 단위
        endTime: Math.floor(endTime * 1000),     // ms 단위
      });

      if (!result.success) {
        throw new Error('Video trim failed');
      }

      setTrimmingProgress(100);
      console.log('✅ Video trimmed successfully');
      console.log('✂️ Result path:', result.outputPath);
      console.log('✂️ Duration:', result.duration, 'ms');

      // file:// 접두사 확인 및 추가
      const resultUri = result.outputPath.startsWith('file://')
        ? result.outputPath
        : `file://${result.outputPath}`;
      return resultUri;

    } catch (error) {
      console.error('❌ Video trim failed:', error);
      throw error;
    } finally {
      setIsTrimming(false);
      setTrimmingProgress(0);
    }
  };

  const handlePlayPause = () => {
    if (!player) return;

    if (isPlaying) {
      player.pause();
    } else {
      // 현재 위치가 트리밍 범위를 벗어났으면 시작 위치로 이동
      if (position >= trimEnd || position < trimStart) {
        console.log('▶️ Play from:', trimStart.toFixed(2), 'seconds');
        player.currentTime = trimStart;
      }
      player.play();
    }
  };

  // 드래그 시작 시 초기 위치 저장
  const initialTrimStart = useRef(0);
  const initialTrimEnd = useRef(0);

  // 최신 상태 값을 ref로 유지 (PanResponder 내에서 참조)
  const trimStartRef = useRef(trimStart);
  const trimEndRef = useRef(trimEnd);
  const durationRef = useRef(duration);
  const timelineWidthRef = useRef(timelineWidth);

  // player를 ref로 유지 (PanResponder에서 사용)
  const playerRef = useRef(player);
  useEffect(() => {
    playerRef.current = player;
  }, [player]);

  // seek throttle을 위한 ref
  const lastSeekTime = useRef(0);
  const SEEK_THROTTLE_MS = 50; // 50ms마다 한 번만 seek (더 부드러운 프리뷰)

  React.useEffect(() => {
    trimStartRef.current = trimStart;
  }, [trimStart]);

  React.useEffect(() => {
    trimEndRef.current = trimEnd;
  }, [trimEnd]);

  React.useEffect(() => {
    durationRef.current = duration;
  }, [duration]);

  React.useEffect(() => {
    timelineWidthRef.current = timelineWidth;
  }, [timelineWidth]);

  // 트리밍 시작 핸들 드래그 (의존성 없이 한 번만 생성)
  const trimStartPanResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_, gestureState) => {
        // 최소 1px만 움직여도 드래그로 인식
        return Math.abs(gestureState.dx) > 0 || Math.abs(gestureState.dy) > 0;
      },
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponderCapture: () => true,
      onPanResponderTerminationRequest: () => false,
      onShouldBlockNativeResponder: () => true,
      onPanResponderGrant: () => {
        console.log('🟢 Trim start handle - drag started');
        initialTrimStart.current = trimStartRef.current;
        setIsDraggingStart(true);

        // 드래그 시작 시 비디오 일시정지
        if (playerRef.current) {
          playerRef.current.pause();
        }
      },
      onPanResponderMove: (_, gestureState) => {
        if (durationRef.current === 0) return;

        const deltaTime = (gestureState.dx / timelineWidthRef.current) * durationRef.current;
        const newStart = Math.max(0, Math.min(trimEndRef.current - 1, initialTrimStart.current + deltaTime));

        setTrimStart(newStart);

        // Throttle: 50ms마다 한 번만 seek
        const now = Date.now();
        if (now - lastSeekTime.current > SEEK_THROTTLE_MS) {
          lastSeekTime.current = now;

          // 드래그 중 비디오를 해당 위치로 이동 (실시간 프리뷰)
          if (playerRef.current) {
            playerRef.current.currentTime = newStart;
          }
        }
      },
      onPanResponderRelease: () => {
        console.log('🟢 Trim start handle - drag released');
        setIsDraggingStart(false);
      },
    })
  ).current;

  // 트리밍 끝 핸들 드래그 (의존성 없이 한 번만 생성)
  const trimEndPanResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: (_, gestureState) => {
        // 최소 1px만 움직여도 드래그로 인식
        return Math.abs(gestureState.dx) > 0 || Math.abs(gestureState.dy) > 0;
      },
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponderCapture: () => true,
      onPanResponderTerminationRequest: () => false,
      onShouldBlockNativeResponder: () => true,
      onPanResponderGrant: () => {
        console.log('🔵 Trim end handle - drag started');
        initialTrimEnd.current = trimEndRef.current;
        setIsDraggingEnd(true);

        // 드래그 시작 시 비디오 일시정지
        if (playerRef.current) {
          playerRef.current.pause();
        }
      },
      onPanResponderMove: (_, gestureState) => {
        if (durationRef.current === 0) return;

        const deltaTime = (gestureState.dx / timelineWidthRef.current) * durationRef.current;
        const newEnd = Math.max(
          trimStartRef.current + 1,
          Math.min(durationRef.current, Math.min(trimStartRef.current + MAX_VIDEO_DURATION, initialTrimEnd.current + deltaTime))
        );

        setTrimEnd(newEnd);
        console.log('🔵 Dragging - dx:', gestureState.dx.toFixed(1), 'newEnd:', newEnd.toFixed(2));

        // Throttle: 50ms마다 한 번만 seek
        const now = Date.now();
        if (now - lastSeekTime.current > SEEK_THROTTLE_MS) {
          lastSeekTime.current = now;

          // 드래그 중 비디오를 해당 위치로 이동 (실시간 프리뷰)
          if (playerRef.current) {
            playerRef.current.currentTime = newEnd;
          }
        }
      },
      onPanResponderRelease: () => {
        console.log('🔵 Trim end handle - drag released');
        setIsDraggingEnd(false);
      },
    })
  ).current;

  const handleNext = async () => {
    if (!selectedThumbnail) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:edit.selectThumbnailRequired'));
      return;
    }

    const trimmedDuration = trimEnd - trimStart;
    if (trimmedDuration > MAX_VIDEO_DURATION) {
      Alert.alert(t('common:label.notice', 'Notice'), t('upload:edit.maxDurationExceeded', { maxDuration: MAX_VIDEO_DURATION }));
      return;
    }

    try {
      setIsUploading(true);

      // 트리밍과 업로드를 위해 file:// URI 필요 (ph:// 미지원)
      // thumbnailUri는 MediaLibrary에서 가져온 localUri (file://)
      if (!thumbnailUri) {
        Alert.alert('오류', '비디오 파일에 접근할 수 없습니다. 다시 시도해주세요.');
        setIsUploading(false);
        return;
      }

      let videoToUpload = thumbnailUri;

      // 트리밍이 필요한 경우 (시작이 0이 아니거나 끝이 전체 길이가 아닌 경우)
      const needsTrimming = trimStart > 0.1 || trimEnd < duration - 0.1;

      if (needsTrimming) {
        try {
          console.log('✂️ Trimming needed, starting trim process...');
          setUploadProgress(5);

          // 네이티브 모듈로 비디오 트리밍 (file:// URI 사용)
          videoToUpload = await trimVideoNative(thumbnailUri, trimStart, trimEnd);

          console.log('✅ Video trimmed successfully, new URI:', videoToUpload);
          setUploadProgress(20);
        } catch (trimError) {
          console.error('❌ Trim failed:', trimError);
          Alert.alert(
            t('upload:edit.trimFailed'),
            t('upload:edit.trimFailedMessage'),
            [
              { text: t('common:button.cancel'), style: 'cancel', onPress: () => { setIsUploading(false); return; } },
              { text: t('upload:edit.uploadOriginal'), onPress: () => { videoToUpload = thumbnailUri; } },
            ]
          );
          return;
        }
      } else {
        console.log('ℹ️ No trimming needed, uploading original video');
        setUploadProgress(10);
      }

      // 1. 비디오 업로드
      console.log('📤 Starting video upload...');
      
      // 파일 존재 여부 및 정보 확인
      const fileInfo = await FileSystem.getInfoAsync(videoToUpload);
      if (!fileInfo.exists) {
        throw new Error(`File does not exist at path: ${videoToUpload}`);
      }
      console.log('📄 File exists, size:', fileInfo.size);

      const videoResponse = await fetch(videoToUpload);
      const videoBlob = await videoResponse.blob();

      console.log('📤 Video blob size:', videoBlob.size, 'bytes');

      const videoUploadUrlResponse = await generateUploadUrl({
        contentType: 'VIDEO',
        fileName: asset.filename,
        fileSize: videoBlob.size,
      });

      await uploadFileToS3(
        videoUploadUrlResponse.uploadUrl,
        videoBlob,
        (progress) => {
          const uploadProgress = needsTrimming ? 20 + Math.floor(progress * 0.5) : 10 + Math.floor(progress * 0.6);
          setUploadProgress(uploadProgress);
        }
      );

      const videoS3Url = videoUploadUrlResponse.uploadUrl.split('?')[0];
      console.log('✅ Video uploaded to S3:', videoS3Url);

      // 2. 썸네일 업로드
      console.log('📤 Starting thumbnail upload...');
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
        (progress) => {
          const baseProgress = needsTrimming ? 70 : 70;
          setUploadProgress(Math.floor(baseProgress + progress * 0.3));
        }
      );

      const thumbnailS3Url = thumbnailUploadUrlResponse.uploadUrl.split('?')[0];
      console.log('✅ Thumbnail uploaded to S3:', thumbnailS3Url);

      setUploadProgress(100);

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
      console.error('❌ Upload failed:', error);
      Alert.alert(t('common:label.error', 'Error'), t('upload:edit.uploadFailed'));
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

        <Text style={styles.headerTitle}>{t('upload:edit.title')}</Text>

        <TouchableOpacity
          onPress={handleNext}
          disabled={isUploading || isTrimming}
          style={styles.headerButton}
        >
          <Text
            style={[
              styles.nextButtonText,
              (isUploading || isTrimming) && styles.disabledText,
            ]}
          >
            {isTrimming ? t('upload:edit.trimming') : isUploading ? t('upload:edit.uploading') : t('common:button.next')}
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
          {isLoadingVideo ? (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color={theme.colors.primary[500]} />
              <Text style={styles.loadingText}>{t('upload:edit.videoLoading')}</Text>
              <Text style={styles.loadingSubtext}>
                {asset.uri?.startsWith('ph://')
                  ? t('upload:edit.videoLoadingGallery')
                  : t('upload:edit.videoLoadingFile')}
              </Text>
            </View>
          ) : videoUri && player ? (
            <VideoView
              player={player}
              style={styles.video}
              contentFit="contain"
              nativeControls={false}
            />
          ) : (
            <View style={styles.loadingContainer}>
              <ActivityIndicator size="large" color={theme.colors.primary[500]} />
              <Text style={styles.loadingText}>{t('upload:edit.videoLoading')}</Text>
            </View>
          )}

          {/* 재생/일시정지 버튼 - 로딩 중이 아니고 비디오가 있을 때만 표시 */}
          {!isLoadingVideo && videoUri && (
            <>
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
            </>
          )}
        </View>

        {/* 타임라인 편집 - 인스타그램 스타일 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('upload:edit.timelineEdit')}</Text>
          <Text style={styles.sectionSubtitle}>
            {t('upload:edit.timelineInstruction', { maxDuration: MAX_VIDEO_DURATION })}
          </Text>

          {/* 타임라인 트리밍 UI */}
          <View
            style={styles.timelineContainer}
            onLayout={(event) => {
              const { width } = event.nativeEvent.layout;
              setTimelineWidth(width);
              console.log('📏 Timeline width measured:', width);
            }}
          >
            {/* 진행 바 */}
            <View style={styles.timelineTrack}>
              {/* 타임라인 프레임 이미지들 */}
              {timelineFrames.length > 0 && (
                <View style={styles.timelineFramesContainer}>
                  {timelineFrames.map((frameUri, index) => (
                    <Image
                      key={index}
                      source={{ uri: frameUri }}
                      style={styles.timelineFrame}
                      resizeMode="cover"
                    />
                  ))}
                </View>
              )}

              {/* 선택된 범위 오버레이 (어둡게) */}
              {duration > 0 && (
                <>
                  {/* 시작 전 어두운 영역 */}
                  <View
                    style={[
                      styles.timelineDimmed,
                      {
                        left: 0,
                        width: `${(trimStart / duration) * 100}%`,
                      },
                    ]}
                  />
                  {/* 끝 후 어두운 영역 */}
                  <View
                    style={[
                      styles.timelineDimmed,
                      {
                        left: `${(trimEnd / duration) * 100}%`,
                        right: 0,
                      },
                    ]}
                  />
                </>
              )}

              {/* 선택된 범위 테두리 */}
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
                <View
                  {...trimStartPanResponder.panHandlers}
                  style={[
                    styles.trimHandle,
                    {
                      left: `${(trimStart / duration) * 100}%`,
                      transform: [
                        { translateX: -40 }, // 핸들 중앙 정렬 (width 80의 절반)
                        ...(isDraggingStart ? [{ scale: 1.2 }] : []),
                      ],
                    },
                  ]}
                  pointerEvents="box-only"
                >
                  <View style={[
                    styles.trimHandleBar,
                    isDraggingStart && styles.trimHandleActive,
                  ]}>
                    <View style={styles.trimHandleGrip} />
                  </View>
                </View>
              )}

              {/* 트리밍 끝 핸들 */}
              {duration > 0 && (
                <View
                  {...trimEndPanResponder.panHandlers}
                  style={[
                    styles.trimHandle,
                    {
                      left: `${(trimEnd / duration) * 100}%`,
                      transform: [
                        { translateX: -40 }, // 핸들 중앙 정렬 (width 80의 절반)
                        ...(isDraggingEnd ? [{ scale: 1.2 }] : []),
                      ],
                    },
                  ]}
                  pointerEvents="box-only"
                >
                  <View style={[
                    styles.trimHandleBar,
                    isDraggingEnd && styles.trimHandleActive,
                  ]}>
                    <View style={styles.trimHandleGrip} />
                  </View>
                </View>
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
              <Text style={styles.trimInfoLabel}>{t('upload:edit.selectedLength')}</Text>
              <Text style={styles.trimInfoValue}>{formatTime(trimEnd - trimStart)}</Text>
            </View>
            <View style={styles.trimInfoItem}>
              <Text style={styles.trimInfoLabel}>{t('upload:edit.totalLength')}</Text>
              <Text style={styles.trimInfoValue}>{formatTime(duration)}</Text>
            </View>
          </View>
        </View>

        {/* 썸네일 선택 */}
        <View style={styles.section}>
          <Text style={styles.sectionTitle}>{t('upload:edit.thumbnailSelection')}</Text>
          <Text style={styles.sectionSubtitle}>
            {t('upload:edit.selectThumbnail')}
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

        {/* 업로드 및 편집 진행률 */}
        {(isUploading || isTrimming) && (
          <View style={styles.uploadProgressContainer}>
            <Text style={styles.uploadProgressText}>
              {isTrimming
                ? t('upload:edit.trimmingProgress', { progress: trimmingProgress })
                : t('upload:edit.uploadProgress', { progress: uploadProgress })}
            </Text>
            <View style={styles.progressBar}>
              <View
                style={[
                  styles.progressFill,
                  { width: isTrimming ? `${trimmingProgress}%` : `${uploadProgress}%` }
                ]}
              />
            </View>
            {isTrimming && (
              <Text style={[styles.uploadProgressText, { marginTop: 8, fontSize: 12 }]}>
                {t('upload:edit.trimmingWait')}
              </Text>
            )}
          </View>
        )}

        {/* 도움말 */}
        <View style={styles.helpSection}>
          <Text style={styles.helpText}>
            ✂️ {t('upload:edit.help.dragTimeline')}
          </Text>
          <Text style={styles.helpText}>
            ▶️ {t('upload:edit.help.playPreview')}
          </Text>
          <Text style={styles.helpText}>
            📌 {t('upload:edit.help.maxDuration', { seconds: MAX_VIDEO_DURATION })}
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
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: theme.colors.gray[900],
    padding: theme.spacing[6],
  },
  loadingText: {
    marginTop: theme.spacing[4],
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.inverse,
    textAlign: 'center',
  },
  loadingSubtext: {
    marginTop: theme.spacing[2],
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.gray[400],
    textAlign: 'center',
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
    backgroundColor: theme.colors.gray[900],
    borderRadius: theme.borderRadius.base,
    position: 'relative',
    overflow: 'hidden', // 프레임 이미지를 위해 hidden으로 변경
  },
  timelineFramesContainer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
  },
  timelineFrame: {
    flex: 1,
    height: '100%',
    borderRightWidth: 1,
    borderRightColor: 'rgba(0, 0, 0, 0.2)',
  },
  timelineDimmed: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
  },
  timelineSelected: {
    position: 'absolute',
    top: 0,
    bottom: 0,
    borderWidth: 2,
    borderColor: theme.colors.primary[500],
    backgroundColor: 'transparent',
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
    top: -10,
    bottom: -10,
    width: 80,
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 100,
  },
  trimHandleBar: {
    width: 20,
    height: '100%',
    backgroundColor: theme.colors.primary[500],
    borderRadius: 10,
    borderWidth: 4,
    borderColor: theme.colors.text.inverse,
    justifyContent: 'center',
    alignItems: 'center',
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.8,
    shadowRadius: 12,
    elevation: 15,
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
