# Android 비디오 트리밍 문제 해결 가이드

## 🔴 문제 상황

안드로이드에서 `react-native-video-trim` 라이브러리를 사용한 비디오 트리밍이 실패합니다.

```
ERROR ❌ Video trim failed: [java.lang.Exception: Command failed with state COMPLETED and rc 1.null]
```

### 증상
- ✅ iOS에서는 정상 작동
- ❌ Android에서만 FFmpeg 명령어가 rc 1 에러 코드로 실패
- 📱 테스트 환경: Android (Hermes 엔진)

---

## 🔍 원인 분석

### 1. **FFmpeg 빌드 문제**
`react-native-video-trim` 6.x 버전의 Android FFmpeg 빌드가 일부 비디오 코덱/컨테이너를 지원하지 않을 수 있습니다.

### 2. **비디오 포맷/코덱 호환성**
- H.264가 아닌 다른 비디오 코덱
- AAC가 아닌 다른 오디오 코덱
- MP4가 아닌 다른 컨테이너 포맷

### 3. **Android Scoped Storage 권한**
Android API 29+ (Android 10+)에서는 Scoped Storage로 인해 파일 접근이 제한됩니다.

---

## ✅ 적용된 해결책

### 1. **다중 Quality 옵션 시도**
```typescript
const trimAttempts = [
  { name: 'HIGH quality', options: { quality: 'high' } },
  { name: 'MEDIUM quality', options: { quality: 'medium' } },
  { name: 'LOW quality', options: { quality: 'low' } },
];
```

### 2. **상세 로깅 추가**
```typescript
// 파일 메타데이터 로깅
console.log('📊 [Trim] Source file info:');
console.log('   Size:', fileInfo.size, 'bytes');

// FFmpeg 옵션 로깅
console.log('   Trim options:', JSON.stringify(trimOptions, null, 2));
```

### 3. **Graceful Fallback 구현**
트리밍 실패 시 사용자에게 선택권 제공:
- **취소**: 업로드 중단
- **원본 업로드**: 트리밍 없이 원본 비디오 업로드

```typescript
const userChoice = await new Promise<'cancel' | 'original'>((resolve) => {
  Alert.alert(
    '비디오 트리밍 실패',
    '원본 비디오를 업로드하거나 취소할 수 있습니다.',
    [
      { text: '취소', onPress: () => resolve('cancel') },
      { text: '원본 업로드', onPress: () => resolve('original') },
    ]
  );
});
```

### 4. **파일 캐시 복사 보장**
Android Scoped Storage 문제를 우회하기 위해 항상 앱 캐시로 복사:

```typescript
const cacheVideoPath = `${FileSystem.cacheDirectory}trim_source_${Date.now()}.mp4`;
await FileSystem.copyAsync({
  from: videoUri,
  to: cacheVideoPath,
});
```

---

## 🛠️ 추가 해결 방법 (시도 가능)

### 1. **react-native-video-trim 최신 버전 확인**
```bash
npm install react-native-video-trim@latest
```

현재 버전: `6.0.11`
최신 버전 확인: https://www.npmjs.com/package/react-native-video-trim

### 2. **대체 라이브러리 고려**
- **react-native-ffmpeg**: 더 많은 제어 옵션 제공
- **expo-video-thumbnails + 커스텀 구현**: 프레임 추출 후 재조합

### 3. **네이티브 코드 수정 (고급)**
`android/app/src/main/java/.../VideoTrimModule.java` 에서 FFmpeg 명령어 커스터마이징

---

## 📊 디버깅 체크리스트

테스트 시 다음 로그를 확인하세요:

```
✅ 확인 항목:
- [ ] 파일 존재 여부 (File exists: true)
- [ ] 파일 크기 (File size: XXX bytes)
- [ ] isValidFile 결과 (isValid: true, duration: XXX ms)
- [ ] Trim source path (경로에 한글/특수문자 없는지)
- [ ] Output file path (쓰기 권한 있는 디렉토리인지)
- [ ] Start/End time (ms 단위가 정수인지)
```

---

## 🎯 권장 사항

### 단기 해결책
현재 구현된 **fallback 메커니즘**을 사용:
- 트리밍 실패 시 원본 비디오 업로드
- 사용자에게 명확한 안내 메시지 제공

### 장기 해결책
1. **서버 사이드 트리밍 구현**
   - 클라이언트에서 원본 업로드
   - 백엔드에서 FFmpeg로 트리밍 처리
   - 더 안정적이고 일관된 결과

2. **비디오 포맷 제한**
   - 업로드 시 H.264 + AAC + MP4 강제
   - expo-av 또는 expo-video로 재인코딩 후 업로드

---

## 📱 테스트 방법

### 1. 상세 로그 확인
```bash
npx react-native log-android
```

### 2. 여러 비디오 포맷 테스트
- 카메라로 촬영한 비디오 (H.264)
- 갤러리에서 선택한 비디오 (다양한 코덱)
- 다운로드한 비디오 (외부 소스)

### 3. 다양한 Android 버전 테스트
- Android 10 (API 29)
- Android 11 (API 30)
- Android 12+ (API 31+)

---

## 📚 참고 자료

- [react-native-video-trim GitHub Issues](https://github.com/iamjasurbekbarboyev/react-native-video-trim/issues)
- [FFmpeg Command Return Codes](https://ffmpeg.org/ffmpeg.html#toc-Main-options)
- [Android Scoped Storage 가이드](https://developer.android.com/training/data-storage/shared/documents-files)

---

## ⚠️ 알려진 제한사항

1. `react-native-video-trim` 6.x는 일부 Android 기기에서 불안정
2. FFmpeg 빌드가 모든 비디오 코덱을 지원하지 않음
3. 고해상도 비디오 (4K+)는 메모리 부족으로 실패할 수 있음

---

**최종 업데이트**: 2025-12-11
**작성자**: Claude Code
