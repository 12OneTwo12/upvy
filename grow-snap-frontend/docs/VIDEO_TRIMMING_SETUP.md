# 비디오 트리밍 기능 설정 가이드

## 개요

이 프로젝트는 네이티브 iOS AVFoundation을 사용하여 비디오 트리밍 기능을 제공합니다.
Instagram/TikTok과 같은 고성능 비디오 편집 경험을 제공합니다.

## 기술 스택

- **네이티브 모듈**: Swift + AVFoundation (iOS)
- **프론트엔드**: React Native + Expo SDK 54
- **자동 등록**: Post-install 스크립트

## 아키텍처

```
modules/video-asset-exporter/           # 로컬 Expo 모듈
├── ios/
│   └── ExpoVideoAssetExporter.swift   # Swift 네이티브 코드 (AVFoundation)
├── index.ts                            # JavaScript export
├── expo-module.config.json             # Expo 모듈 설정
├── package.json                        # 모듈 메타데이터
└── video-asset-exporter.podspec        # CocoaPods 설정

scripts/
└── register-video-module.js            # 자동 등록 스크립트

ios/
└── Podfile                             # post_install hook 포함
```

## 주요 기능

### 1. 비디오 트리밍
- **네이티브 성능**: AVFoundation의 `AVAssetExportSession` 사용
- **고품질**: `AVAssetExportPresetHighestQuality` 프리셋
- **정확한 시간**: 600 timescale로 프레임 단위 정확도
- **비동기 처리**: UI 블로킹 없이 백그라운드에서 트리밍

### 2. 사용 예시

```typescript
import { VideoAssetExporter } from 'video-asset-exporter';

// 비디오 트리밍
const result = await VideoAssetExporter.trimVideo(
  '/path/to/input.mp4',    // 입력 파일 절대 경로
  '/path/to/output.mp4',   // 출력 파일 절대 경로
  5.0,                     // 시작 시간 (초)
  15.0                     // 종료 시간 (초)
);

console.log('Trimmed video:', result); // 출력 파일 경로 반환
```

## 설치 및 설정

### 초기 설치 (한 번만)

```bash
# 프로젝트 클론 후
cd grow-snap-frontend

# 의존성 설치
npm install --legacy-peer-deps

# iOS 의존성 설치 (자동으로 모듈 등록됨)
cd ios && pod install && cd ..

# iOS 앱 빌드 및 실행
npx expo run:ios
```

### 새로운 개발자 온보딩

1. **저장소 클론**
   ```bash
   git clone <repository-url>
   cd grow-snap-frontend
   ```

2. **의존성 설치**
   ```bash
   npm install --legacy-peer-deps
   ```

3. **iOS 빌드**
   ```bash
   npx expo run:ios
   ```

**그게 전부입니다!** 추가 설정이 필요 없습니다. ✅

## 자동화 동작 원리

### 1. npm postinstall Hook

`package.json`:
```json
{
  "scripts": {
    "postinstall": "node scripts/register-video-module.js || true"
  }
}
```

- `npm install` 실행 시 자동으로 모듈 등록
- 실패해도 install은 계속 진행 (`|| true`)

### 2. CocoaPods post_install Hook

`ios/Podfile`:
```ruby
post_install do |installer|
  react_native_post_install(...)

  # VideoAssetExporter 모듈 자동 등록
  Dir.chdir(File.join(__dir__, '..')) do
    system('node', 'scripts/register-video-module.js')
  end
end
```

- `pod install` 실행 시 자동으로 모듈 등록
- ExpoModulesProvider.swift에 모듈 추가

### 3. 등록 스크립트

`scripts/register-video-module.js`:
- ExpoModulesProvider.swift 파일을 읽음
- `import ExpoVideoAssetExporter` 추가
- DEBUG/RELEASE 섹션에 `ExpoVideoAssetExporter.self` 추가
- 이미 등록되어 있으면 스킵

## 트러블슈팅

### ❌ "Cannot find native module 'VideoAssetExporter'"

**원인**: 모듈이 ExpoModulesProvider에 등록되지 않음

**해결**:
```bash
# 1. 수동으로 등록 스크립트 실행
node scripts/register-video-module.js

# 2. iOS 클린 빌드
cd ios
rm -rf Pods Podfile.lock
pod install
cd ..

# 3. 앱 재빌드
npx expo run:ios --no-build-cache
```

### ❌ Swift 빌드 에러

**원인**: Swift 파일이 Xcode 프로젝트에 없음

**해결**:
```bash
# Ruby 스크립트로 Xcode 프로젝트에 추가
ruby scripts/add_swift_module.rb

# 또는 Xcode에서 수동 추가
# 1. Xcode에서 GrowSnap.xcworkspace 열기
# 2. GrowSnap/Modules 폴더에 ExpoVideoAssetExporter.swift 파일 확인
# 3. 없으면 "Add Files to GrowSnap..." 클릭
# 4. modules/video-asset-exporter/ios/ExpoVideoAssetExporter.swift 추가
# 5. "Add to targets: GrowSnap" 체크
```

### ❌ pod install 실패

**원인**: 의존성 충돌

**해결**:
```bash
cd ios
rm -rf Pods Podfile.lock ~/Library/Caches/CocoaPods
pod install --repo-update
```

## 개발 워크플로우

### 일상적인 개발

```bash
# 앱 시작 (Metro bundler)
npm start

# iOS 시뮬레이터에서 실행
npm run ios
```

### 네이티브 코드 변경 시

```bash
# Swift 파일 수정 후 재빌드
npx expo run:ios
```

### 의존성 추가 시

```bash
# 새로운 npm 패키지 추가
npm install <package> --legacy-peer-deps

# iOS 네이티브 의존성 추가
cd ios && pod install && cd ..
```

## 파일 구조

### 핵심 파일

| 파일 | 역할 |
|------|------|
| `modules/video-asset-exporter/ios/ExpoVideoAssetExporter.swift` | 네이티브 트리밍 로직 |
| `modules/video-asset-exporter/index.ts` | JavaScript export |
| `scripts/register-video-module.js` | 자동 등록 스크립트 |
| `ios/Podfile` | CocoaPods 설정 + post_install hook |
| `src/screens/upload/VideoEditScreen.tsx` | 비디오 편집 UI |

### 자동 생성 파일 (Git에서 제외)

- `ios/Pods/` - CocoaPods 의존성
- `ios/Podfile.lock` - 의존성 잠금 파일
- `ios/Pods/Target Support Files/Pods-GrowSnap/ExpoModulesProvider.swift` - 자동 생성, 수동 편집 금지

## 성능 최적화

### 비디오 트리밍 성능

- **품질**: `AVAssetExportPresetHighestQuality` 사용
- **시간 정확도**: 600 timescale (프레임 단위)
- **백그라운드 처리**: `DispatchQueue.global(qos: .userInitiated)`
- **메모리 효율**: Semaphore로 동기화, 메모리 누수 방지

### 예상 처리 시간

- 10초 비디오: ~2-3초
- 30초 비디오: ~5-8초
- 60초 비디오: ~10-15초

*실제 시간은 기기 성능과 비디오 해상도에 따라 다릅니다*

## Git Workflow

### 커밋해야 할 파일

```bash
# 네이티브 모듈
modules/video-asset-exporter/

# 자동화 스크립트
scripts/register-video-module.js
scripts/add_swift_module.rb

# 설정 파일
ios/Podfile
ios/GrowSnap.xcodeproj/
ios/GrowSnap/Modules/

# 문서
docs/VIDEO_TRIMMING_SETUP.md
```

### 커밋하지 말아야 할 파일 (.gitignore에 이미 추가됨)

```bash
# CocoaPods
ios/Pods/
ios/Podfile.lock

# Xcode
ios/build/
*.xcworkspace/xcuserdata/
*.xcodeproj/xcuserdata/
```

## 추가 리소스

- [AVFoundation 공식 문서](https://developer.apple.com/documentation/avfoundation)
- [Expo Modules API](https://docs.expo.dev/modules/overview/)
- [React Native 비디오 처리](https://reactnative.dev/docs/native-modules-ios)

## 지원

문제가 발생하면:
1. 이 문서의 트러블슈팅 섹션 확인
2. 팀 슬랙 채널에 질문
3. GitHub Issues에 버그 리포트

---

**마지막 업데이트**: 2025-11-06
**작성자**: Claude Code Assistant
**버전**: 1.0.0
