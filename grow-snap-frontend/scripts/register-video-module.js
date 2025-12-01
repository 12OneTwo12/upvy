#!/usr/bin/env node

/**
 * ExpoVideoAssetExporter 모듈 자동 등록 스크립트
 *
 * Expo의 autolinking이 로컬 Swift 모듈을 인식하지 못하는 문제를 해결합니다.
 * pod install 후 ExpoModulesProvider.swift에 우리 모듈을 자동으로 추가합니다.
 */

const fs = require('fs');
const path = require('path');

const PROVIDER_PATH = path.join(
  __dirname,
  '../ios/Pods/Target Support Files/Pods-GrowSnap/ExpoModulesProvider.swift'
);

const MODULE_NAME = 'ExpoVideoAssetExporter';

function registerModule() {
  console.log('🔧 Registering VideoAssetExporter module...');

  if (!fs.existsSync(PROVIDER_PATH)) {
    console.error('❌ ExpoModulesProvider.swift not found. Run pod install first.');
    process.exit(1);
  }

  let content = fs.readFileSync(PROVIDER_PATH, 'utf8');

  // 이미 등록되어 있는지 확인
  if (content.includes(`${MODULE_NAME}.self`)) {
    console.log('✅ Module already registered');
    return;
  }

  // import 추가
  const importSection = content.match(/(import ExpoModulesCore[\s\S]*?)(#if EXPO_CONFIGURATION_DEBUG)/);
  if (!importSection) {
    console.error('❌ Could not find import section');
    process.exit(1);
  }

  const newImport = `import ExpoWebBrowser\nimport ${MODULE_NAME}\n`;
  content = content.replace(
    'import ExpoWebBrowser',
    newImport
  );

  // DEBUG 섹션에 모듈 추가
  const debugModules = content.match(/(return \[[\s\S]*?DevMenuPreferences\.self)/);
  if (debugModules) {
    content = content.replace(
      'DevMenuPreferences.self',
      `DevMenuPreferences.self,\n      ${MODULE_NAME}.self`
    );
  }

  // RELEASE 섹션에 모듈 추가
  const releasePattern = /#else\s*return \[([\s\S]*?)WebBrowserModule\.self/;
  const releaseMatch = content.match(releasePattern);
  if (releaseMatch) {
    content = content.replace(
      releasePattern,
      `#else\n    return [$1WebBrowserModule.self,\n      ${MODULE_NAME}.self`
    );
  }

  // 파일 저장
  fs.writeFileSync(PROVIDER_PATH, content, 'utf8');

  // 파일의 타임스탬프를 업데이트해서 Xcode가 재컴파일하도록 강제
  const now = new Date();
  fs.utimesSync(PROVIDER_PATH, now, now);

  console.log('✅ Module registered successfully!');
  console.log(`   Added ${MODULE_NAME} to ExpoModulesProvider`);
  console.log(`   Updated file timestamp to force recompilation`);
}

try {
  registerModule();
} catch (error) {
  console.error('❌ Failed to register module:', error.message);
  process.exit(1);
}
