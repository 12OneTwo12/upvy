import ExpoModulesCore
import AVFoundation

public class ExpoVideoAssetExporter: Module {
  public func definition() -> ModuleDefinition {
    Name("VideoAssetExporter")

    AsyncFunction("trimVideo") { (inputPath: String, outputPath: String, startTime: Double, endTime: Double) -> String in
      return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
        DispatchQueue.global(qos: .userInitiated).async {
          do {
            let result = try self.performTrim(
              inputPath: inputPath,
              outputPath: outputPath,
              startTime: startTime,
              endTime: endTime
            )
            continuation.resume(returning: result)
          } catch {
            continuation.resume(throwing: error)
          }
        }
      }
    }
  }

  private func performTrim(inputPath: String, outputPath: String, startTime: Double, endTime: Double) throws -> String {
    let inputURL = URL(fileURLWithPath: inputPath)
    let outputURL = URL(fileURLWithPath: outputPath)

    // Remove existing file
    if FileManager.default.fileExists(atPath: outputURL.path) {
      try FileManager.default.removeItem(at: outputURL)
    }

    // Create AVAsset
    let asset = AVAsset(url: inputURL)

    // Create export session
    guard let exportSession = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetHighestQuality) else {
      throw NSError(domain: "VideoAssetExporter", code: -1, userInfo: [NSLocalizedDescriptionKey: "Failed to create export session"])
    }

    // Set time range
    let startCMTime = CMTime(seconds: startTime, preferredTimescale: 600)
    let endCMTime = CMTime(seconds: endTime, preferredTimescale: 600)
    let timeRange = CMTimeRange(start: startCMTime, end: endCMTime)

    exportSession.outputURL = outputURL
    exportSession.outputFileType = .mp4
    exportSession.timeRange = timeRange

    // Synchronous export using semaphore
    let semaphore = DispatchSemaphore(value: 0)
    var exportError: Error?

    exportSession.exportAsynchronously {
      if exportSession.status == .failed {
        exportError = exportSession.error
      }
      semaphore.signal()
    }

    semaphore.wait()

    if let error = exportError {
      throw error
    }

    if exportSession.status != .completed {
      throw NSError(domain: "VideoAssetExporter", code: -2, userInfo: [NSLocalizedDescriptionKey: "Export failed with status: \(exportSession.status.rawValue)"])
    }

    return outputURL.path
  }
}
