/**
 * 크리에이터 스튜디오 - 업로드 메인 화면
 *
 * 인스타그램 스타일의 미디어 선택 화면
 * - 상단: 선택된 미디어 큰 미리보기
 * - 하단: 작은 그리드로 미디어 목록
 * - 카메라 버튼이 그리드 첫 번째 위치
 */

import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  FlatList,
  Image,
  StyleSheet,
  Alert,
  Dimensions,
  ActivityIndicator,
  RefreshControl,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import * as ImagePicker from 'expo-image-picker';
import * as MediaLibrary from 'expo-media-library';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '@/theme';
import type { UploadStackParamList, MediaAsset } from '@/types/navigation.types';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

type Props = NativeStackScreenProps<UploadStackParamList, 'UploadMain'>;

const SCREEN_WIDTH = Dimensions.get('window').width;
const SCREEN_HEIGHT = Dimensions.get('window').height;
const GRID_COLUMNS = 4;
const GRID_IMAGE_SIZE = SCREEN_WIDTH / GRID_COLUMNS;
const PREVIEW_HEIGHT = SCREEN_HEIGHT * 0.5;

export default function UploadMainScreen({ navigation }: Props) {
  const [hasPermission, setHasPermission] = useState<boolean>(false);
  const [mediaAssets, setMediaAssets] = useState<MediaAsset[]>([]);
  const [selectedAssets, setSelectedAssets] = useState<MediaAsset[]>([]);
  const [contentType, setContentType] = useState<'photo' | 'video'>('video');
  const [isLoading, setIsLoading] = useState(true);
  const [currentPreviewIndex, setCurrentPreviewIndex] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const isInitialFocus = useRef(true);

  useEffect(() => {
    requestPermissions();
  }, []);

  useEffect(() => {
    if (hasPermission) {
      loadMediaAssets();
    }
  }, [hasPermission, contentType]);

  // 화면 포커스 시 미디어 라이브러리 자동 새로고침
  // 첫 마운트 시에는 실행하지 않아 useEffect와의 중복 호출 방지
  useFocusEffect(
    useCallback(() => {
      if (isInitialFocus.current) {
        isInitialFocus.current = false;
        return;
      }

      if (hasPermission) {
        loadMediaAssets();
      }
    }, [hasPermission, contentType])
  );

  const requestPermissions = async () => {
    try {
      const { status } = await MediaLibrary.requestPermissionsAsync();
      setHasPermission(status === 'granted');

      if (status !== 'granted') {
        Alert.alert(
          '권한 필요',
          '갤러리 접근 권한이 필요합니다. 설정에서 권한을 허용해주세요.'
        );
      }
    } catch (error) {
      console.error('Permission request failed:', error);
    }
  };

  const loadMediaAssets = async () => {
    try {
      setIsLoading(true);
      const { assets } = await MediaLibrary.getAssetsAsync({
        first: 100,
        mediaType: contentType === 'photo' ? 'photo' : 'video',
        sortBy: [[MediaLibrary.SortBy.creationTime, false]],
      });

      const formattedAssets: MediaAsset[] = assets.map((asset: any) => ({
        id: asset.id,
        uri: asset.uri,
        mediaType: asset.mediaType === 'video' ? 'video' : 'photo',
        duration: asset.duration || 0,
        width: asset.width,
        height: asset.height,
        filename: asset.filename,
      }));

      setMediaAssets(formattedAssets);

      // 첫 번째 아이템 자동 선택
      if (formattedAssets.length > 0 && selectedAssets.length === 0) {
        setSelectedAssets([formattedAssets[0]]);
      }
    } catch (error) {
      console.error('Failed to load media assets:', error);
      Alert.alert('오류', '미디어를 불러오는데 실패했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleAssetSelect = (asset: MediaAsset) => {
    if (contentType === 'video') {
      // 비디오는 단일 선택
      setSelectedAssets([asset]);
      setCurrentPreviewIndex(0);
    } else {
      // 사진은 다중 선택
      const index = selectedAssets.findIndex((a) => a.id === asset.id);
      if (index !== -1) {
        // 이미 선택된 경우 - 선택 해제
        const newSelected = selectedAssets.filter((a) => a.id !== asset.id);
        setSelectedAssets(newSelected);
        if (currentPreviewIndex >= newSelected.length) {
          setCurrentPreviewIndex(Math.max(0, newSelected.length - 1));
        }
      } else {
        // 새로 선택
        if (selectedAssets.length >= 10) {
          Alert.alert('알림', '사진은 최대 10개까지 선택할 수 있습니다.');
          return;
        }
        setSelectedAssets([...selectedAssets, asset]);
      }
    }
  };

  const handleNext = async () => {
    if (selectedAssets.length === 0) {
      Alert.alert('알림', '미디어를 선택해주세요.');
      return;
    }

    if (contentType === 'video') {
      const asset = selectedAssets[0];

      // 비디오 길이 체크 (최대 1분 = 60초)
      if (asset.duration > 60) {
        Alert.alert(
          '비디오 길이 초과',
          '비디오는 최대 1분(60초)까지 업로드할 수 있습니다. 편집 화면에서 트리밍해주세요.'
        );
      }

      // ph:// URI인 경우 localUri를 미리 가져오기
      let assetWithLocalUri = asset;
      if (asset.uri && asset.uri.startsWith('ph://')) {
        try {
          console.log('📹 Getting localUri for video asset...');
          const assetInfo = await MediaLibrary.getAssetInfoAsync(asset.id);

          if (assetInfo.localUri) {
            console.log('✅ Got localUri:', assetInfo.localUri);
            assetWithLocalUri = {
              ...asset,
              uri: assetInfo.localUri, // localUri로 교체
            };
          }
        } catch (error) {
          console.error('Failed to get localUri:', error);
          // 실패해도 계속 진행 (VideoEditScreen에서 재시도)
        }
      }

      navigation.navigate('VideoEdit', {
        asset: assetWithLocalUri,
        type: 'video',
      });
    } else {
      navigation.navigate('PhotoEdit', {
        assets: selectedAssets,
        type: 'photo',
      });
    }
  };

  const onRefresh = async () => {
    setRefreshing(true);
    await loadMediaAssets();
    setRefreshing(false);
  };

  const handleCameraCapture = async () => {
    try {
      const { status } = await ImagePicker.requestCameraPermissionsAsync();

      if (status !== 'granted') {
        Alert.alert('권한 필요', '카메라 접근 권한이 필요합니다.');
        return;
      }

      const result = await ImagePicker.launchCameraAsync({
        mediaTypes: contentType === 'photo'
          ? 'images' as any
          : 'videos' as any,
        allowsEditing: false,
        quality: 1,
        videoMaxDuration: 60,
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

        if (contentType === 'video') {
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

      // 시뮬레이터에서 카메라 사용 불가 에러는 무시
      if (error?.code === 'ERR_CAMERA_UNAVAILABLE_ON_SIMULATOR') {
        Alert.alert(
          '시뮬레이터 제한',
          '시뮬레이터에서는 카메라를 사용할 수 없습니다. 실제 기기에서 테스트해주세요.'
        );
      } else {
        Alert.alert('오류', '카메라 촬영에 실패했습니다.');
      }
    }
  };

  const renderGridItem = ({ item, index }: { item: MediaAsset; index: number }) => {
    const isSelected = selectedAssets.find((a) => a.id === item.id);
    const selectionOrder = selectedAssets.findIndex((a) => a.id === item.id) + 1;

    return (
      <TouchableOpacity
        style={styles.gridItem}
        onPress={() => handleAssetSelect(item)}
        activeOpacity={0.8}
      >
        <Image source={{ uri: item.uri }} style={styles.gridImage} />

        {/* 비디오 길이 표시 */}
        {item.mediaType === 'video' && (
          <View style={styles.durationBadge}>
            <Text style={styles.durationText}>
              {Math.floor(item.duration / 60)}:{String(Math.floor(item.duration % 60)).padStart(2, '0')}
            </Text>
          </View>
        )}

        {/* 선택 표시 */}
        {isSelected && (
          <>
            <View style={styles.selectedOverlay} />
            <View style={styles.selectedBadge}>
              <Text style={styles.selectedText}>
                {contentType === 'photo' ? selectionOrder : '✓'}
              </Text>
            </View>
          </>
        )}
      </TouchableOpacity>
    );
  };

  const currentPreview = selectedAssets[currentPreviewIndex];

  return (
    <SafeAreaView style={styles.container} edges={['top']}>
      {/* 헤더 */}
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.headerButton}>
          <Ionicons name="close" size={28} color={theme.colors.text.primary} />
        </TouchableOpacity>

        <Text style={styles.headerTitle}>새 게시물</Text>

        <TouchableOpacity
          onPress={handleNext}
          disabled={selectedAssets.length === 0}
          style={styles.headerButton}
        >
          <Text
            style={[
              styles.nextButtonText,
              selectedAssets.length === 0 && styles.disabledText,
            ]}
          >
            다음
          </Text>
        </TouchableOpacity>
      </View>

      {/* 미디어 타입 & 다중 선택 토글 */}
      <View style={styles.controlsContainer}>
        <View style={styles.typeSelector}>
          <TouchableOpacity
            style={[styles.typeTab, contentType === 'photo' && styles.typeTabActive]}
            onPress={() => {
              setContentType('photo');
              setSelectedAssets([]);
            }}
          >
            <Text style={[styles.typeTabText, contentType === 'photo' && styles.typeTabTextActive]}>
              사진
            </Text>
          </TouchableOpacity>

          <TouchableOpacity
            style={[styles.typeTab, contentType === 'video' && styles.typeTabActive]}
            onPress={() => {
              setContentType('video');
              setSelectedAssets([]);
            }}
          >
            <Text style={[styles.typeTabText, contentType === 'video' && styles.typeTabTextActive]}>
              비디오
            </Text>
          </TouchableOpacity>
        </View>

        {contentType === 'photo' && selectedAssets.length > 0 && (
          <Text style={styles.selectionCount}>{selectedAssets.length}/10</Text>
        )}
      </View>

      {/* 선택된 미디어 큰 미리보기 - 스와이프 가능 */}
      {selectedAssets.length > 0 && (
        <View style={styles.previewContainer}>
          <FlatList
            data={selectedAssets}
            renderItem={({ item }) => (
              <Image
                source={{ uri: item.uri }}
                style={styles.previewImage}
                resizeMode="cover"
              />
            )}
            keyExtractor={(item) => item.id}
            horizontal
            pagingEnabled
            showsHorizontalScrollIndicator={false}
            onViewableItemsChanged={({ viewableItems }) => {
              if (viewableItems.length > 0 && viewableItems[0].index !== null) {
                setCurrentPreviewIndex(viewableItems[0].index);
              }
            }}
            viewabilityConfig={{
              itemVisiblePercentThreshold: 50,
            }}
          />

          {/* 여러 개 선택 시 인디케이터 */}
          {selectedAssets.length > 1 && (
            <View style={styles.previewIndicator}>
              {selectedAssets.map((_, index) => (
                <View
                  key={index}
                  style={[
                    styles.indicatorDot,
                    index === currentPreviewIndex && styles.indicatorDotActive,
                  ]}
                />
              ))}
            </View>
          )}
        </View>
      )}

      {/* 갤러리 그리드 */}
      {isLoading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary[500]} />
        </View>
      ) : (
        <FlatList
          data={mediaAssets}
          renderItem={renderGridItem}
          keyExtractor={(item) => item.id}
          numColumns={GRID_COLUMNS}
          contentContainerStyle={styles.gridContainer}
          showsVerticalScrollIndicator={false}
          refreshControl={
            <RefreshControl
              refreshing={refreshing}
              onRefresh={onRefresh}
              tintColor={theme.colors.primary[500]}
              colors={[theme.colors.primary[500]]}
            />
          }
          ListHeaderComponent={
            // 카메라 버튼을 첫 번째 아이템으로
            <TouchableOpacity style={styles.gridItem} onPress={handleCameraCapture}>
              <View style={styles.cameraButton}>
                <Ionicons name="camera" size={32} color={theme.colors.text.secondary} />
                <Text style={styles.cameraButtonText}>카메라</Text>
              </View>
            </TouchableOpacity>
          }
        />
      )}
    </SafeAreaView>
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
    padding: theme.spacing[1],
    minWidth: 60,
  },
  headerTitle: {
    fontSize: theme.typography.fontSize.lg,
    fontWeight: theme.typography.fontWeight.semibold,
    color: theme.colors.text.primary,
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
  controlsContainer: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: theme.spacing[4],
    paddingVertical: theme.spacing[2],
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border.light,
  },
  typeSelector: {
    flexDirection: 'row',
    gap: theme.spacing[2],
  },
  typeTab: {
    paddingHorizontal: theme.spacing[3],
    paddingVertical: theme.spacing[1],
  },
  typeTabActive: {
    borderBottomWidth: 2,
    borderBottomColor: theme.colors.primary[500],
  },
  typeTabText: {
    fontSize: theme.typography.fontSize.base,
    color: theme.colors.text.tertiary,
  },
  typeTabTextActive: {
    color: theme.colors.text.primary,
    fontWeight: theme.typography.fontWeight.semibold,
  },
  selectionCount: {
    fontSize: theme.typography.fontSize.sm,
    color: theme.colors.text.secondary,
    fontWeight: theme.typography.fontWeight.medium,
  },
  previewContainer: {
    width: SCREEN_WIDTH,
    height: PREVIEW_HEIGHT,
    backgroundColor: theme.colors.gray[900],
    position: 'relative',
  },
  previewImage: {
    width: SCREEN_WIDTH,
    height: PREVIEW_HEIGHT,
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
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  gridContainer: {
    paddingBottom: theme.spacing[20],
  },
  gridItem: {
    width: GRID_IMAGE_SIZE,
    height: GRID_IMAGE_SIZE,
    padding: 1,
    position: 'relative',
  },
  gridImage: {
    width: '100%',
    height: '100%',
  },
  cameraButton: {
    width: '100%',
    height: '100%',
    backgroundColor: theme.colors.gray[100],
    justifyContent: 'center',
    alignItems: 'center',
  },
  cameraButtonText: {
    marginTop: theme.spacing[1],
    fontSize: theme.typography.fontSize.xs,
    color: theme.colors.text.secondary,
  },
  durationBadge: {
    position: 'absolute',
    bottom: 4,
    right: 4,
    backgroundColor: 'rgba(0, 0, 0, 0.7)',
    paddingHorizontal: 4,
    paddingVertical: 2,
    borderRadius: theme.borderRadius.sm,
  },
  durationText: {
    color: theme.colors.text.inverse,
    fontSize: 10,
    fontWeight: theme.typography.fontWeight.medium,
  },
  selectedOverlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(255, 255, 255, 0.4)',
  },
  selectedBadge: {
    position: 'absolute',
    top: 4,
    right: 4,
    width: 24,
    height: 24,
    borderRadius: 12,
    backgroundColor: theme.colors.primary[500],
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 2,
    borderColor: '#fff',
  },
  selectedText: {
    color: theme.colors.text.inverse,
    fontSize: 12,
    fontWeight: theme.typography.fontWeight.bold,
  },
});
