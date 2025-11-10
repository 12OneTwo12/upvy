const { withXcodeProject } = require('@expo/config-plugins');
const path = require('path');

/**
 * Expo Config Plugin for VideoAssetExporter
 *
 * Adds an Xcode build phase that patches ExpoModulesProvider.swift
 * after Expo autolinking generates it, adding our custom module.
 */

function withVideoAssetExporter(config) {
  return withXcodeProject(config, async (config) => {
    const xcodeProject = config.modResults;
    const projectRoot = config.modRequest.projectRoot;

    // Add a build phase script that runs after autolinking
    const scriptPhase = {
      isa: 'PBXShellScriptBuildPhase',
      buildActionMask: 2147483647,
      files: [],
      inputPaths: [],
      name: '"[Video Module] Register Custom Module"',
      outputPaths: [],
      runOnlyForDeploymentPostprocessing: 0,
      shellPath: '/bin/sh',
      shellScript: `"cd \\"$PROJECT_DIR/..\\" && node scripts/register-video-module.js || true"`,
      showEnvVarsInLog: 0
    };

    // Find the first native target (usually the main app target)
    const nativeTargets = xcodeProject.pbxNativeTargetSection();
    const firstTargetKey = Object.keys(nativeTargets).find(key => key.indexOf('_comment') === -1);

    if (firstTargetKey) {
      const target = nativeTargets[firstTargetKey];

      // Generate a unique ID for our script phase
      const scriptPhaseId = xcodeProject.generateUuid();

      // Add the script phase to the project
      xcodeProject.hash.project.objects['PBXShellScriptBuildPhase'] =
        xcodeProject.hash.project.objects['PBXShellScriptBuildPhase'] || {};
      xcodeProject.hash.project.objects['PBXShellScriptBuildPhase'][scriptPhaseId] = scriptPhase;
      xcodeProject.hash.project.objects['PBXShellScriptBuildPhase'][`${scriptPhaseId}_comment`] = '[Video Module] Register Custom Module';

      // Add the script phase to the target's build phases (at the end)
      target.buildPhases = target.buildPhases || [];
      if (!target.buildPhases.find(phase => phase.value === scriptPhaseId)) {
        target.buildPhases.push({
          value: scriptPhaseId,
          comment: '[Video Module] Register Custom Module'
        });
      }

      console.log('[VideoAssetExporter Plugin] Added build phase to register custom module');
    }

    return config;
  });
}

module.exports = withVideoAssetExporter;
