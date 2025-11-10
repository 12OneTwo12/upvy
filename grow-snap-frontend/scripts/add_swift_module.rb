#!/usr/bin/env ruby
require 'xcodeproj'

# Xcode 프로젝트 경로
project_path = File.expand_path('../ios/GrowSnap.xcodeproj', __dir__)
project = Xcodeproj::Project.open(project_path)

# 메인 타겟 찾기
target = project.targets.find { |t| t.name == 'GrowSnap' }

# Swift 파일 경로
swift_file_path = 'GrowSnap/Modules/ExpoVideoAssetExporter.swift'

# 이미 추가되어 있는지 확인
existing_file = project.files.find { |f| f.path == swift_file_path }

if existing_file
  puts "✅ Swift file already added to project"
else
  # Modules 그룹 찾거나 생성
  group = project.main_group.find_subpath('GrowSnap', true)
  modules_group = group.find_subpath('Modules', true)

  # Swift 파일 추가
  file_ref = modules_group.new_reference(swift_file_path)

  # 타겟의 소스 빌드 페이즈에 추가
  target.source_build_phase.add_file_reference(file_ref)

  puts "✅ Swift file added to Xcode project"
end

# 프로젝트 저장
project.save

puts "🎉 Xcode project updated successfully!"
puts "Now run: npx expo run:ios"
