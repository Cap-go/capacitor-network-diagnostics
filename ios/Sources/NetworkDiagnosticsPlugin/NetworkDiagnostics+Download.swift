import Foundation

extension NetworkDiagnostics {
    public func testDownloadSpeed(url: String, maxBytes: Int, timeoutMs: Int) async -> [String: Any] {
        let byteLimit = positive(maxBytes, fallback: 5 * 1024 * 1024)
        var context = DownloadContext(url: url, started: Date())

        guard let parsedUrl = URL(string: url) else {
            return downloadResult(context, errorCode: "INVALID_URL", errorMessage: "Invalid URL")
        }

        let session = URLSession(configuration: downloadConfiguration(timeoutMs))
        defer { session.finishTasksAndInvalidate() }

        do {
            let (data, response) = try await session.data(for: downloadRequest(parsedUrl, byteLimit: byteLimit, timeoutMs: timeoutMs))
            context.bytesDownloaded = min(data.count, byteLimit)
            context.statusCode = (response as? HTTPURLResponse)?.statusCode
            return downloadResult(context)
        } catch {
            return downloadResult(context, errorCode: errorCode(error), errorMessage: error.localizedDescription)
        }
    }

    func downloadConfiguration(_ timeoutMs: Int) -> URLSessionConfiguration {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = timeout(timeoutMs, fallback: 30)
        configuration.timeoutIntervalForResource = timeout(timeoutMs, fallback: 30)
        return configuration
    }

    func downloadRequest(_ url: URL, byteLimit: Int, timeoutMs: Int) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = timeout(timeoutMs, fallback: 30)
        request.setValue("bytes=0-\(byteLimit - 1)", forHTTPHeaderField: "Range")
        return request
    }

    func downloadResult(_ context: DownloadContext, errorCode: String? = nil, errorMessage: String? = nil) -> [String: Any] {
        let durationMs = max(elapsedMs(context.started), 1)
        let bytesPerSecond = Double(context.bytesDownloaded) / (Double(durationMs) / 1000.0)
        var result: [String: Any] = [
            "url": context.url,
            "ok": errorCode == nil && (context.statusCode ?? 0) >= 200 && (context.statusCode ?? 0) < 400,
            "durationMs": durationMs,
            "bytesDownloaded": context.bytesDownloaded,
            "bytesPerSecond": bytesPerSecond,
            "mbps": (bytesPerSecond * 8) / 1_000_000
        ]

        if let statusCode = context.statusCode {
            result["statusCode"] = statusCode
        }
        if let errorCode = errorCode {
            result["errorCode"] = errorCode
        }
        if let errorMessage = errorMessage {
            result["errorMessage"] = errorMessage
        }

        return result
    }
}

struct DownloadContext {
    let url: String
    let started: Date
    var bytesDownloaded = 0
    var statusCode: Int?
}
