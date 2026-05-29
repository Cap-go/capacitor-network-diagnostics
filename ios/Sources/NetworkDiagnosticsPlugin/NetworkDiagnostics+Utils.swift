import Foundation
import Network

extension NetworkDiagnostics {
    func currentPath() async -> NWPath {
        await withCheckedContinuation { continuation in
            let monitor = NWPathMonitor()
            let queue = DispatchQueue(label: "app.capgo.networkdiagnostics.path")
            var resumed = false

            func resume(with path: NWPath) {
                if resumed {
                    return
                }
                resumed = true
                monitor.cancel()
                continuation.resume(returning: path)
            }

            monitor.pathUpdateHandler = { path in
                resume(with: path)
            }
            monitor.start(queue: queue)
            queue.asyncAfter(deadline: .now() + 1) {
                resume(with: monitor.currentPath)
            }
        }
    }

    func probeTcp(host: String, port: Int, timeoutMs: Int) async -> ProbeResult {
        let started = Date()
        guard let nwPort = NWEndpoint.Port(rawValue: UInt16(port)) else {
            return ProbeResult(success: false, durationMs: elapsedMs(started), errorCode: "INVALID_PORT", errorMessage: "Invalid port")
        }

        let connection = NWConnection(host: NWEndpoint.Host(host), port: nwPort, using: .tcp)

        return await withCheckedContinuation { continuation in
            let queue = DispatchQueue(label: "app.capgo.networkdiagnostics.tcp")
            var finished = false

            func finish(success: Bool, error: Error?) {
                if finished {
                    return
                }
                finished = true
                connection.cancel()
                continuation.resume(
                    returning: ProbeResult(
                        success: success,
                        durationMs: elapsedMs(started),
                        errorCode: error.map { errorCode($0) },
                        errorMessage: error?.localizedDescription
                    )
                )
            }

            connection.stateUpdateHandler = { state in
                switch state {
                case .ready:
                    finish(success: true, error: nil)
                case .failed(let error):
                    finish(success: false, error: error)
                case .cancelled:
                    if !finished {
                        finish(success: false, error: NetworkDiagnosticsError.cancelled)
                    }
                default:
                    break
                }
            }

            queue.asyncAfter(deadline: .now() + timeout(timeoutMs, fallback: 5)) {
                finish(success: false, error: NetworkDiagnosticsError.timeout)
            }
            connection.start(queue: queue)
        }
    }

    func probeHttp(url: String, timeoutMs: Int) async -> ProbeResult {
        let result = await testUrl(url: url, method: "HEAD", timeoutMs: timeoutMs, followRedirects: true)
        let reachable = result["reachable"] as? Bool ?? false
        return ProbeResult(
            success: reachable,
            durationMs: result["durationMs"] as? Int ?? 0,
            errorCode: result["errorCode"] as? String,
            errorMessage: result["errorMessage"] as? String
        )
    }

    func websocketResult(_ base: [String: Any], started: Date, open: Bool, error: Error?) -> [String: Any] {
        var output = base
        output["open"] = open
        output["durationMs"] = elapsedMs(started)
        if let error = error {
            output["errorCode"] = errorCode(error)
            output["errorMessage"] = error.localizedDescription
        }
        return output
    }

    func connectionType(_ path: NWPath) -> String {
        if path.usesInterfaceType(.wifi) {
            return "wifi"
        }
        if path.usesInterfaceType(.cellular) {
            return "cellular"
        }
        if path.usesInterfaceType(.wiredEthernet) {
            return "ethernet"
        }
        if path.usesInterfaceType(.loopback) || path.usesInterfaceType(.other) {
            return "other"
        }
        return "unknown"
    }

    func statusLabel(_ status: NWPath.Status) -> String {
        switch status {
        case .satisfied:
            return "satisfied"
        case .unsatisfied:
            return "unsatisfied"
        case .requiresConnection:
            return "requiresConnection"
        @unknown default:
            return "unknown"
        }
    }

    func normalizeMethod(_ method: String) -> String {
        method.uppercased() == "GET" ? "GET" : "HEAD"
    }

    func normalizePacketLossMode(mode: String, host: String, port: Int, url: String) -> String {
        if mode.lowercased() == "http" {
            return "http"
        }
        if mode.lowercased() == "tcp" {
            return "tcp"
        }
        if !host.isEmpty && port > 0 {
            return "tcp"
        }
        if !url.isEmpty {
            return "http"
        }
        return "tcp"
    }

    func positive(_ value: Int, fallback: Int) -> Int {
        value > 0 ? value : fallback
    }

    func timeout(_ value: Int, fallback: TimeInterval) -> TimeInterval {
        TimeInterval(positive(value, fallback: Int(fallback * 1000))) / 1000.0
    }

    func elapsedMs(_ started: Date) -> Int {
        max(Int(Date().timeIntervalSince(started) * 1000), 0)
    }

    func errorCode(_ error: Error) -> String {
        let nsError = error as NSError
        if nsError.domain == NSURLErrorDomain {
            return urlErrorCode(nsError.code)
        }
        if error is NetworkDiagnosticsError {
            return "\(error)".uppercased()
        }
        return String(describing: type(of: error))
    }

    func urlErrorCode(_ code: Int) -> String {
        switch code {
        case NSURLErrorTimedOut:
            return "TIMEOUT"
        case NSURLErrorCannotFindHost, NSURLErrorDNSLookupFailed:
            return "DNS_ERROR"
        case NSURLErrorCannotConnectToHost, NSURLErrorNetworkConnectionLost:
            return "CONNECTION_FAILED"
        case NSURLErrorSecureConnectionFailed, NSURLErrorServerCertificateUntrusted:
            return "TLS_ERROR"
        default:
            return "URL_ERROR_\(code)"
        }
    }
}

enum NetworkDiagnosticsError: Error, LocalizedError {
    case cancelled
    case timeout

    var errorDescription: String? {
        switch self {
        case .cancelled:
            return "Connection was cancelled"
        case .timeout:
            return "Connection timed out"
        }
    }
}
