# EAS 로컬 빌드 & 업로드 가이드

EAS Build 크레딧을 소모하지 않고 로컬에서 빌드하여 스토어에 업로드하는 방법입니다.

## 목차

- [iOS 로컬 빌드](#ios-로컬-빌드)
- [Android 로컬 빌드](#android-로컬-빌드)
- [문제 해결](#문제-해결)

---

## iOS 로컬 빌드

### 요구사항

- **macOS** (Windows/Linux 불가)
- **Xcode 15.0+** 설치
- **Apple Developer 계정** (유료, $99/년)
- **Command Line Tools** 설치
  ```bash
  xcode-select --install
  ```

### 1. 로컬 빌드 실행

```bash
# Production 빌드 (App Store 제출용)
eas build --platform ios --profile production --local

# Preview 빌드 (테스트용)
eas build --platform ios --profile preview --local
```

### 2. 빌드 프로세스

빌드 중 다음 단계가 진행됩니다:

1. **Apple 계정 로그인**
   - Apple ID와 비밀번호 입력
   - 2FA 코드 입력 (필요 시)

2. **인증서 및 프로비저닝 프로필 확인**
   - EAS가 자동으로 관리
   - 필요 시 새로 생성

3. **네이티브 프로젝트 생성**
   - `npx expo prebuild` 실행
   - iOS 네이티브 코드 생성

4. **Xcode 빌드**
   - `xcodebuild archive` 실행
   - `.ipa` 파일 생성

### 3. 빌드 결과 확인

빌드 완료 후 `.ipa` 파일 위치:

```bash
# 프로젝트 루트의 dist 폴더
ls -lh dist/

# 출력 예시:
# Upvy-1.4.1-22.ipa  (약 100-200MB)
```

### 4. App Store Connect 업로드

#### 방법 1: EAS Submit (권장)

```bash
# 가장 최근 로컬 빌드 자동 선택
eas submit --platform ios --latest

# 특정 .ipa 파일 지정
eas submit --platform ios --path dist/Upvy-1.4.1-22.ipa
```

**필요한 정보:**
- Apple ID
- App Store Connect API Key (EAS가 자동으로 관리)
- ASC App ID (자동 감지 또는 `eas.json`에 설정)

#### 방법 2: Xcode Transporter (수동)

1. **App Store Connect 준비**
   - https://appstoreconnect.apple.com 접속
   - 앱 선택 → "TestFlight" 탭

2. **Transporter 앱 사용**
   - Mac App Store에서 "Transporter" 다운로드
   - Transporter 실행
   - `.ipa` 파일을 드래그 앤 드롭
   - "전송" 버튼 클릭

3. **업로드 확인**
   - App Store Connect → TestFlight
   - 5-10분 후 빌드가 "처리 중" 상태로 표시됨
   - 처리 완료 후 테스터에게 배포 가능

#### 방법 3: Xcode (고급)

```bash
# Xcode로 .ipa 업로드
xcrun altool --upload-app \
  --type ios \
  --file dist/Upvy-1.4.1-22.ipa \
  --apiKey <API_KEY_ID> \
  --apiIssuer <ISSUER_ID>
```

### 5. 심사 제출

1. **TestFlight 테스트** (선택 사항)
   - 내부 테스터에게 배포
   - 버그 확인

2. **App Store 심사 제출**
   - App Store Connect → "App Store" 탭
   - 새 버전 추가
   - 빌드 선택
   - 스크린샷, 설명 등 메타데이터 입력
   - "심사 제출" 버튼 클릭

---

## Android 로컬 빌드

### 요구사항

- **모든 OS 가능** (macOS, Windows, Linux)
- **Android SDK** 설치
- **JDK 17** 설치
- **Android Studio** 설치 (권장)

### 1. 환경 변수 설정

#### macOS/Linux

```bash
# ~/.zshrc 또는 ~/.bashrc에 추가
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/emulator
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin

# Java 설정
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

#### Windows

```powershell
# 환경 변수 설정 (시스템 속성 → 환경 변수)
ANDROID_HOME=C:\Users\YourName\AppData\Local\Android\Sdk
JAVA_HOME=C:\Program Files\Java\jdk-17

# PATH에 추가
%ANDROID_HOME%\platform-tools
%ANDROID_HOME%\tools
%JAVA_HOME%\bin
```

### 2. 로컬 빌드 실행

```bash
# Production 빌드 (Play Store 제출용 - AAB)
eas build --platform android --profile production --local

# Preview 빌드 (테스트용 - APK)
eas build --platform android --profile preview --local
```

### 3. 빌드 프로세스

1. **Android Keystore 확인**
   - EAS가 자동으로 생성 및 관리
   - 또는 기존 keystore 사용 가능

2. **네이티브 프로젝트 생성**
   - `npx expo prebuild` 실행
   - Android 네이티브 코드 생성

3. **Gradle 빌드**
   - `./gradlew assembleRelease` (APK)
   - `./gradlew bundleRelease` (AAB)

### 4. 빌드 결과 확인

빌드 완료 후 파일 위치:

```bash
# 프로젝트 루트의 dist 폴더
ls -lh dist/

# 출력 예시 (production):
# Upvy-1.4.1-22.aab  (약 50-100MB)

# 출력 예시 (preview):
# Upvy-1.4.1-22.apk  (약 80-150MB)
```

**파일 형식 차이:**
- **AAB (Android App Bundle)**: Play Store 제출용, 최적화된 APK 자동 생성
- **APK**: 직접 설치 가능, 테스트용

### 5. Play Console 업로드

#### 방법 1: EAS Submit (권장)

```bash
# 가장 최근 로컬 빌드 자동 선택
eas submit --platform android --latest

# 특정 .aab 파일 지정
eas submit --platform android --path dist/Upvy-1.4.1-22.aab
```

**필요한 정보:**
- Google Play Console Service Account JSON (EAS가 자동으로 관리)
- Package Name: `com.upvy.app`

#### 방법 2: Play Console 웹 인터페이스 (수동)

1. **Play Console 접속**
   - https://play.google.com/console 접속
   - 앱 선택

2. **내부 테스트 또는 프로덕션**
   - 왼쪽 메뉴 → "릴리스" → "프로덕션" 또는 "내부 테스트"
   - "새 릴리스 만들기" 버튼 클릭

3. **AAB 업로드**
   - "업로드" 버튼 클릭
   - `dist/Upvy-1.4.1-22.aab` 파일 선택
   - 업로드 완료 대기

4. **릴리스 노트 작성**
   - 변경 사항 설명
   - "검토" 버튼 클릭

5. **검토 시작**
   - 모든 정보 확인
   - "프로덕션으로 출시 시작" 버튼 클릭

#### 방법 3: Google Play CLI (고급)

```bash
# fastlane 사용 예시
fastlane supply \
  --aab dist/Upvy-1.4.1-22.aab \
  --track production \
  --json_key service-account.json
```

### 6. APK 직접 설치 (테스트)

Preview 빌드로 생성한 APK는 직접 설치 가능:

```bash
# USB 연결된 Android 기기에 설치
adb install dist/Upvy-1.4.1-22.apk

# 또는 APK 파일을 기기로 전송 후 설치
```

---

## 빌드 시간 예상

| 플랫폼 | 빌드 시간 | 파일 크기 |
|--------|-----------|-----------|
| iOS    | 10-20분   | 100-200MB |
| Android | 5-15분   | 50-150MB  |

**참고:** 첫 빌드는 의존성 다운로드로 인해 더 오래 걸릴 수 있습니다.

---

## 주의사항

### iOS

1. **인증서 만료 확인**
   - Distribution Certificate: 1년 유효
   - Provisioning Profile: 1년 유효
   - EAS가 자동으로 갱신하지만 수동 확인 권장

2. **Xcode 버전 호환성**
   - Expo SDK 54 → Xcode 15.0+ 필요
   - `xcodebuild -version` 으로 확인

3. **디스크 공간**
   - 빌드 중 최소 10GB 여유 공간 필요
   - iOS Simulator 이미지 삭제로 공간 확보 가능

### Android

1. **JDK 버전**
   - JDK 17 필수 (Expo SDK 54 기준)
   - `java -version` 으로 확인

2. **Keystore 백업**
   - EAS가 자동 생성한 keystore는 안전하게 백업
   - 분실 시 기존 앱 업데이트 불가능

3. **Gradle 캐시**
   - 첫 빌드 느리면 `~/.gradle/caches` 정리
   - `./gradlew clean` 실행

---

## 비용 비교

| 방법 | iOS 빌드 비용 | Android 빌드 비용 |
|------|---------------|-------------------|
| EAS 클라우드 빌드 | $2/빌드 | $1/빌드 |
| EAS 로컬 빌드 | **무료** | **무료** |

**크레딧 절약:**
- 월 15회 빌드 시: $30 절약
- 연 180회 빌드 시: $360 절약

---

## 문제 해결

### iOS 빌드 실패

**문제: "xcodebuild: command not found"**
```bash
# Command Line Tools 설치
xcode-select --install

# Xcode 경로 설정
sudo xcode-select --switch /Applications/Xcode.app
```

**문제: "Provisioning profile doesn't include signing certificate"**
```bash
# EAS가 관리하는 인증서 재생성
eas credentials --platform ios
# "Remove all credentials" 선택 후 재빌드
```

**문제: "Module 'expo' not found"**
```bash
# 의존성 재설치
rm -rf node_modules
npm install
npx expo prebuild --clean
```

### Android 빌드 실패

**문제: "ANDROID_HOME is not set"**
```bash
# Android SDK 경로 확인
ls ~/Library/Android/sdk  # macOS
ls %LOCALAPPDATA%\Android\Sdk  # Windows

# 환경 변수 설정 (위 "환경 변수 설정" 참고)
```

**문제: "Gradle build failed"**
```bash
# Gradle 캐시 정리
cd android
./gradlew clean

# 또는 프로젝트 완전 정리
npx expo prebuild --clean
```

**문제: "Java version mismatch"**
```bash
# JDK 17 설치 확인
java -version

# macOS: Java 17로 전환
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
```

---

## 추가 팁

### 빌드 속도 향상

1. **Gradle Daemon 활성화** (Android)
   ```bash
   # ~/.gradle/gradle.properties
   org.gradle.daemon=true
   org.gradle.parallel=true
   org.gradle.caching=true
   ```

2. **npm ci 대신 npm install** (의존성 변경 없을 때)
   ```bash
   # package-lock.json 그대로 유지
   npm ci  # 느림, 정확함
   npm install  # 빠름, 캐시 활용
   ```

3. **Xcode Derived Data 정리** (iOS, 용량 절약)
   ```bash
   # 10-50GB 절약 가능
   rm -rf ~/Library/Developer/Xcode/DerivedData
   ```

### 빌드 자동화

GitHub Actions에서 로컬 빌드를 실행할 수 있습니다:

```yaml
# .github/workflows/build.yml
name: EAS Build (Local)
on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: macos-latest  # iOS용, Android는 ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: 18
      - run: npm ci
      - run: npx eas-cli@latest build --platform ios --profile production --local --non-interactive
```

**장점:**
- 무료 빌드 (GitHub Actions 무료 한도 내)
- 자동화된 워크플로우
- 팀원 모두 동일한 환경에서 빌드

---

## 참고 문서

- [EAS Build 로컬 빌드 공식 문서](https://docs.expo.dev/build-reference/local-builds/)
- [EAS Submit 공식 문서](https://docs.expo.dev/submit/introduction/)
- [App Store Connect 가이드](https://developer.apple.com/app-store-connect/)
- [Google Play Console 가이드](https://support.google.com/googleplay/android-developer/)

---

**작성일:** 2025-12-19
**Expo SDK 버전:** 54
**문서 버전:** 1.0
