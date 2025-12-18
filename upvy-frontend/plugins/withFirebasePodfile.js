/**
 * Expo Config Plugin to add use_modular_headers! to Podfile
 *
 * Fixes Firebase Swift pods integration issue:
 * "The Swift pod `FirebaseCoreInternal` depends upon `GoogleUtilities`,
 * which does not define modules"
 */

const { withDangerousMod } = require('@expo/config-plugins');
const fs = require('fs');
const path = require('path');

module.exports = function withFirebasePodfile(config) {
  return withDangerousMod(config, [
    'ios',
    async (config) => {
      const podfilePath = path.join(config.modRequest.platformProjectRoot, 'Podfile');

      let podfileContent = fs.readFileSync(podfilePath, 'utf-8');

      // Check if use_modular_headers! already exists
      if (!podfileContent.includes('use_modular_headers!')) {
        // Add use_modular_headers! after target declaration
        podfileContent = podfileContent.replace(
          /target\s+'Upvy'\s+do/,
          `target 'Upvy' do\n  use_modular_headers!`
        );

        fs.writeFileSync(podfilePath, podfileContent);
        console.log('✅ Added use_modular_headers! to Podfile');
      }

      return config;
    },
  ]);
};
