require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'video-asset-exporter'
  s.version        = package['version']
  s.summary        = 'Video trimming module using AVFoundation'
  s.description    = 'Native video trimming module for Expo using iOS AVFoundation'
  s.license        = package['license'] || 'MIT'
  s.author         = package['author'] || { 'author' => 'author@domain.cn' }
  s.homepage       = package['homepage'] || 'https://github.com'
  s.platforms      = { :ios => '15.1', :tvos => '15.1' }
  s.swift_version  = '5.4'
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'

  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_COMPILATION_MODE' => 'wholemodule'
  }

  s.source_files = "ios/**/*.{h,m,mm,swift,hpp,cpp}"
end
