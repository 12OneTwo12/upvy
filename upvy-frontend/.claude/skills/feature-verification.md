# 기능 구현 후 검증 프로세스

## 개요
React Native (Expo) 기능을 구현한 후, 반드시 실제 앱을 실행하여 동작을 검증해야 합니다.
단순히 빌드 성공만으로는 불충분하며, 런타임 에러가 없는지 확인이 필수입니다.

## 필수 검증 단계

### 1. 모든 백그라운드 프로세스 종료
```bash
pkill -9 -f "expo|metro|gradle|node"
```

### 2. 캐시 완전 삭제
```bash
rm -rf .expo android/app/build node_modules/.cache
```

### 3. Clean Build 실행
```bash
cd android && ./gradlew clean && cd ..
npx expo run:android --no-build-cache 2>&1 | tee /tmp/build-verification.log
```

### 4. 빌드 완료 대기
- 빌드가 완전히 완료될 때까지 기다립니다
- 일반적으로 5-10분 소요
- "BUILD SUCCESSFUL" 메시지 확인

### 5. 앱 설치 및 실행 확인
- 에뮬레이터/디바이스에 앱이 자동으로 설치됩니다
- 앱이 실행되고 초기 화면이 표시되는지 확인

### 6. LogCat으로 런타임 에러 확인 (필수!)
```bash
# 앱의 PID 찾기
adb shell ps | grep upvy

# LogCat으로 에러 모니터링
adb logcat --pid=<PID> | grep -E "ReactNativeJS|runtime not ready|TypeError|Error"
```

**반드시 확인해야 할 에러 패턴:**
- `[runtime not ready]`
- `TypeError: Cannot read property`
- `undefined is not an object`
- JavaScript 스택 트레이스

### 7. 구현한 기능 직접 테스트
- 앱에서 구현한 화면/기능으로 이동
- 모든 UI 컴포넌트가 정상 렌더링되는지 확인
- 버튼 클릭, 입력 등 인터랙션 테스트
- LogCat에서 에러가 발생하지 않는지 실시간 모니터링

## 성공 기준

다음 조건을 **모두** 만족해야 성공으로 판단:

1. ✅ 빌드가 성공적으로 완료됨
2. ✅ 앱이 정상적으로 설치되고 실행됨
3. ✅ LogCat에 런타임 에러가 없음
4. ✅ 구현한 기능이 정상 동작함
5. ✅ UI가 깨지지 않고 정상 렌더링됨

## 주의사항

### 절대 하지 말아야 할 것
- ❌ LogCat 확인 없이 "해결됐다"고 보고
- ❌ 빌드만 성공하고 앱 실행 확인 안함
- ❌ 에러 로그를 대충 훑어보고 넘어감
- ❌ 이전 빌드 결과를 재사용

### 반드시 해야 할 것
- ✅ 매번 Clean Build 수행
- ✅ LogCat으로 실제 에러 확인
- ✅ 앱을 직접 조작하며 테스트
- ✅ 에러가 정말로 없는지 최소 1-2분간 모니터링

## New Architecture 관련 주의사항

프로젝트가 `"newArchEnabled": true`로 설정되어 있을 경우:

### StyleSheet.create() 이슈
- New Architecture에서는 native bridge 준비 전 StyleSheet.create() 호출 시 에러 발생
- `src/utils/styles.ts`의 `createStyleSheet()` 유틸리티 사용 필수

**올바른 사용법:**
```typescript
import { createStyleSheet } from '@/utils/styles';

// ✅ Lazy evaluation - native bridge 준비 후 호출됨
const useStyles = createStyleSheet({
  container: { flex: 1 },
});

function MyComponent() {
  const styles = useStyles(); // 컴포넌트 렌더링 시점에 생성
  return <View style={styles.container} />;
}
```

**잘못된 사용법:**
```typescript
// ❌ 모듈 로드 시점에 즉시 실행 - 에러 발생!
const styles = StyleSheet.create({
  container: { flex: 1 },
});
```

### Native Module 동적 Import
- expo-image-picker 같은 native module은 동적 import 사용 권장

```typescript
// ✅ 올바른 방법
const pickImage = async () => {
  const ImagePicker = await import('expo-image-picker');
  const result = await ImagePicker.launchImageLibraryAsync(...);
};

// ❌ 잘못된 방법
import * as ImagePicker from 'expo-image-picker'; // 모듈 레벨 import
```

## 검증 체크리스트

기능 구현 완료 후, 다음 체크리스트를 순서대로 수행:

- [ ] 모든 프로세스 종료 (`pkill -9 -f "expo|metro|gradle|node"`)
- [ ] 캐시 삭제 (`rm -rf .expo android/app/build node_modules/.cache`)
- [ ] Clean Build 실행 (`npx expo run:android --no-build-cache`)
- [ ] 빌드 완료 대기 (BUILD SUCCESSFUL 확인)
- [ ] 앱 설치 및 실행 확인
- [ ] LogCat으로 에러 모니터링 시작
- [ ] 구현한 기능 직접 테스트
- [ ] 1-2분간 에러 로그 모니터링
- [ ] 모든 UI가 정상 렌더링되는지 확인
- [ ] 인터랙션이 정상 동작하는지 확인

**모든 항목이 체크되어야만 "해결 완료"로 보고할 것!**

## 자동화된 검증 프로세스 (Claude가 직접 수행)

**사용자가 일일이 확인할 필요 없이, Claude가 스스로 모든 검증을 완료해야 합니다.**

### 1단계: 빌드 실행 및 모니터링
```bash
# 백그라운드에서 빌드 시작
pkill -9 -f "expo|metro|gradle|node"
rm -rf .expo android/app/build node_modules/.cache
cd android && ./gradlew clean && cd ..
npx expo run:android --no-build-cache 2>&1 | tee /tmp/verification.log &

# BashOutput 도구로 빌드 진행 상태 확인
# "BUILD SUCCESSFUL" 메시지가 나올 때까지 대기
```

### 2단계: APK 설치 확인 및 수동 설치
```bash
# 빌드 로그에서 설치 에러 확인
grep -E "Error: adb: failed to install|Broken pipe" /tmp/verification.log

# 설치 실패 시 수동으로 APK 설치
adb install -r /Users/jeongjeong-il/Desktop/projects/upvy/upvy-frontend/android/app/build/outputs/apk/debug/app-debug.apk
```

### 3단계: Metro Bundler 시작
```bash
# Metro bundler를 백그라운드에서 시작
npx expo start --clear &

# 30초 대기 (bundler 초기화 시간)
sleep 30
```

### 4단계: 앱 실행 (에뮬레이터에서 수동으로 앱 아이콘 클릭 or 자동 실행)
```bash
# Upvy 앱이 설치되어 있으므로 에뮬레이터에서 앱 아이콘 클릭하거나
# 또는 adb로 실행 (패키지명 확인 필요)
# 일반적으로 Expo 앱은 에뮬레이터에서 수동으로 실행해야 함
```

### 5단계: PID 자동 감지 및 LogCat 모니터링
```bash
# 앱 실행 후 PID 찾기 (여러 번 시도)
for i in {1..10}; do
  PID=$(adb shell ps | grep upvy | awk '{print $2}')
  if [ -n "$PID" ]; then
    echo "Found PID: $PID"
    break
  fi
  sleep 2
done

# LogCat으로 에러 모니터링 (최소 60초간)
adb logcat --pid=$PID | grep -E "ReactNativeJS|runtime not ready|TypeError|Error|undefined is not" > /tmp/logcat-errors.log &
LOGCAT_PID=$!

# 60초 대기
sleep 60

# LogCat 프로세스 종료
kill $LOGCAT_PID

# 에러 로그 확인
cat /tmp/logcat-errors.log
```

### 6단계: 자동 검증 결과 판정
```bash
# 에러 로그 파일에서 치명적 에러 검색
if grep -E "runtime not ready|TypeError.*Cannot read property.*create.*undefined" /tmp/logcat-errors.log; then
  echo "❌ VERIFICATION FAILED: Runtime errors detected"
  exit 1
else
  echo "✅ VERIFICATION PASSED: No critical errors found"
  exit 0
fi
```

## 완전 자동화 스크립트

아래 스크립트를 **순서대로** 실행하여 사용자 개입 없이 검증을 완료합니다:

```bash
#!/bin/bash
# complete-verification.sh

set -e

echo "🔄 Step 1: 프로세스 종료 및 캐시 삭제"
pkill -9 -f "expo|metro|gradle|node" || true
rm -rf .expo android/app/build node_modules/.cache

echo "🔄 Step 2: Gradle Clean"
cd android && ./gradlew clean && cd ..

echo "🔄 Step 3: 빌드 시작"
npx expo run:android --no-build-cache 2>&1 | tee /tmp/verification.log &
BUILD_PID=$!

echo "⏳ 빌드 완료 대기 (최대 10분)..."
for i in {1..60}; do
  if grep -q "BUILD SUCCESSFUL" /tmp/verification.log 2>/dev/null; then
    echo "✅ 빌드 성공"
    break
  fi
  sleep 10
done

echo "🔄 Step 4: APK 설치 확인"
if grep -q "Error: adb: failed to install" /tmp/verification.log; then
  echo "⚠️  자동 설치 실패, 수동 설치 시도..."
  adb install -r android/app/build/outputs/apk/debug/app-debug.apk
fi

echo "🔄 Step 5: Metro Bundler 시작"
npx expo start --clear > /tmp/metro.log 2>&1 &
METRO_PID=$!
sleep 30

echo "📱 Step 6: 앱 실행 대기"
echo "   에뮬레이터에서 Upvy 앱을 클릭하거나 자동으로 실행될 때까지 대기..."
for i in {1..20}; do
  PID=$(adb shell ps | grep upvy | awk '{print $2}')
  if [ -n "$PID" ]; then
    echo "✅ 앱 실행됨 (PID: $PID)"
    break
  fi
  sleep 3
done

if [ -z "$PID" ]; then
  echo "❌ 앱이 실행되지 않음"
  exit 1
fi

echo "🔍 Step 7: LogCat 모니터링 (60초)"
adb logcat --pid=$PID 2>&1 | grep -E "ReactNativeJS|runtime|TypeError|Error" > /tmp/logcat-errors.log &
LOGCAT_PID=$!
sleep 60
kill $LOGCAT_PID 2>/dev/null || true

echo "📊 Step 8: 검증 결과 확인"
if grep -E "runtime not ready|TypeError.*Cannot read property.*create" /tmp/logcat-errors.log 2>/dev/null; then
  echo "❌ 검증 실패: 런타임 에러 발견"
  echo "에러 로그:"
  cat /tmp/logcat-errors.log
  exit 1
else
  echo "✅ 검증 성공: 런타임 에러 없음"
  exit 0
fi
```

## Claude 실행 지침

**기능 구현 후 Claude는 다음 단계를 자동으로 수행해야 합니다:**

1. 위의 "완전 자동화 스크립트"의 각 단계를 순서대로 Bash 도구로 실행
2. 각 단계의 출력을 BashOutput으로 확인
3. 에러가 발생하면 즉시 사용자에게 보고
4. 모든 단계가 성공하면 /tmp/logcat-errors.log의 내용을 확인
5. 에러가 없으면 "검증 완료"로 판정
6. **사용자가 직접 확인하지 않아도 되도록 모든 단계를 자동 수행**

## 백엔드 API 연동 테스트

로컬 백엔드와 에뮬레이터 앱을 연동하여 API 테스트를 진행할 때는 **포트 포워딩**이 필수입니다.

### Android 에뮬레이터 포트 포워딩
```bash
# 백엔드가 localhost:8080에서 실행 중일 때
adb reverse tcp:8080 tcp:8080

# 확인: 앱에서 http://localhost:8080 또는 http://10.0.2.2:8080으로 접근 가능
```

**중요:**
- Android 에뮬레이터는 자체 네트워크를 사용하므로 호스트 머신의 `localhost`에 직접 접근 불가
- `adb reverse`로 에뮬레이터의 포트를 호스트 머신의 포트로 포워딩해야 함
- 백엔드 포트를 변경하면 해당 포트도 포워딩 필요 (예: `adb reverse tcp:3000 tcp:3000`)

### API 연동 테스트 체크리스트
- [ ] 백엔드 서버가 실행 중인지 확인 (예: `localhost:8080`)
- [ ] `adb reverse tcp:8080 tcp:8080` 실행
- [ ] 앱에서 API 호출 시 LogCat에서 네트워크 로그 확인
- [ ] 응답이 정상적으로 수신되는지 확인
- [ ] API 에러 발생 시 에러 메시지가 제대로 표시되는지 확인

## 빠른 참조 명령어

```bash
# 전체 검증 플로우 (한 번에 실행)
pkill -9 -f "expo|metro|gradle|node" && \
rm -rf .expo android/app/build node_modules/.cache && \
cd android && ./gradlew clean && cd .. && \
npx expo run:android --no-build-cache 2>&1 | tee /tmp/verification.log

# 별도 터미널에서 LogCat 모니터링
adb logcat --pid=$(adb shell ps | grep upvy | awk '{print $2}') | grep -E "ReactNativeJS|runtime|Error|TypeError"

# 백엔드 API 연동을 위한 포트 포워딩
adb reverse tcp:8080 tcp:8080
```
