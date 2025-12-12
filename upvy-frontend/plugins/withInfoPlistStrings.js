/**
 * Expo Config Plugin for Multi-language InfoPlist.strings
 *
 * iOS 권한 설명을 다국어로 지원하기 위한 Config Plugin
 * 한국어, 영어, 일본어 지원
 */

const {
  withDangerousMod,
  IOSConfig,
} = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

/**
 * InfoPlist.strings 파일 내용 생성
 * @param {Object} translations - 권한 설명 번역 객체
 * @returns {string} InfoPlist.strings 파일 내용
 */
function generateInfoPlistStrings(translations) {
  const lines = [];

  // NSPhotoLibraryUsageDescription
  if (translations.NSPhotoLibraryUsageDescription) {
    lines.push(`"NSPhotoLibraryUsageDescription" = "${translations.NSPhotoLibraryUsageDescription}";`);
  }

  return lines.join('\n') + '\n';
}

/**
 * 언어별 InfoPlist.strings 파일 생성
 * @param {Object} config - Expo config
 * @param {string} projectRoot - 프로젝트 루트 경로
 * @param {Object} localizations - 언어별 번역 객체
 */
function createInfoPlistStrings(config, projectRoot, localizations) {
  const iosPath = path.join(projectRoot, 'ios');
  const projectName = config.name || 'Upvy';
  const projectPath = path.join(iosPath, projectName);

  // 각 언어별로 .lproj 폴더 생성 및 InfoPlist.strings 작성
  Object.keys(localizations).forEach((languageCode) => {
    const lprojPath = path.join(projectPath, `${languageCode}.lproj`);
    const infoPlistStringsPath = path.join(lprojPath, 'InfoPlist.strings');

    // .lproj 폴더 생성
    if (!fs.existsSync(lprojPath)) {
      fs.mkdirSync(lprojPath, { recursive: true });
    }

    // InfoPlist.strings 파일 작성
    const content = generateInfoPlistStrings(localizations[languageCode]);
    fs.writeFileSync(infoPlistStringsPath, content, 'utf8');

    console.log(`✓ Created ${languageCode}.lproj/InfoPlist.strings`);
  });
}

/**
 * Expo Config Plugin
 */
const withInfoPlistStrings = (config, localizations = {}) => {
  return withDangerousMod(config, [
    'ios',
    async (config) => {
      const projectRoot = config.modRequest.projectRoot;

      // InfoPlist.strings 파일 생성
      createInfoPlistStrings(config, projectRoot, localizations);

      return config;
    },
  ]);
};

module.exports = withInfoPlistStrings;
