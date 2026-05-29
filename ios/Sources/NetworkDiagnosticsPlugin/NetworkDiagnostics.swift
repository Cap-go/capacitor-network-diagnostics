import Foundation
import Network

@objc public class NetworkDiagnostics: NSObject {
    @objc public func getPluginVersion() -> String {
        return "native"
    }

    public func getNetworkStatus() async -> [String: Any] {
        let path = await currentPath()
        let connected = path.status == .satisfied
        var details: [String: Any] = [
            "status": statusLabel(path.status),
            "interfaces": path.availableInterfaces.map { $0.name }.joined(separator: ",")
        ]

        if let gateway = path.gateways.first {
            details["gateway"] = "\(gateway)"
        }

        return [
            "connected": connected,
            "connectionType": connected ? connectionType(path) : "none",
            "internetReachable": connected,
            "expensive": path.isExpensive,
            "constrained": path.isConstrained,
            "details": details
        ]
    }

    public func testUrl(url: String, method: String, timeoutMs: Int, followRedirects: Bool) async -> [String: Any] {
        let started = Date()
        let normalizedMethod = normalizeMethod(method)
        var result: [String: Any] = [
            "url": url,
            "method": normalizedMethod
        ]

        guard let parsedUrl = URL(string: url) else {
            result["ok"] = false
            result["reachable"] = false
            result["durationMs"] = elapsedMs(started)
            result["errorCode"] = "INVALID_URL"
            result["errorMessage"] = "Invalid URL"
            return result
        }

        var request = URLRequest(url: parsedUrl)
        request.httpMethod = normalizedMethod
        request.timeoutInterval = timeout(timeoutMs, fallback: 10)

        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = request.timeoutInterval
        configuration.timeoutIntervalForResource = request.timeoutInterval
        let delegate = followRedirects ? nil : NoRedirectDelegate()
        let session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }

        do {
            let (_, response) = try await session.data(for: request)
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            result["ok"] = statusCode >= 200 && statusCode < 400
            result["reachable"] = true
            result["durationMs"] = elapsedMs(started)
            result["statusCode"] = statusCode
            result["finalUrl"] = response.url?.absoluteString ?? url
        } catch {
            result["ok"] = false
            result["reachable"] = false
            result["durationMs"] = elapsedMs(started)
            result["errorCode"] = errorCode(error)
            result["errorMessage"] = error.localizedDescription
        }

        return result
    }

    public func testPort(host: String, port: Int, timeoutMs: Int) async -> [String: Any] {
        let probe = await probeTcp(host: host, port: port, timeoutMs: timeoutMs)
        var result: [String: Any] = [
            "host": host,
            "port": port,
            "open": probe.success,
            "durationMs": probe.durationMs
        ]

        if !probe.success {
            result["errorCode"] = probe.errorCode
            result["errorMessage"] = probe.errorMessage
        }

        return result
    }

    public func testWebSocket(url: String, timeoutMs: Int) async -> [String: Any] {
        let started = Date()
        var result: [String: Any] = ["url": url]

        guard let parsedUrl = URL(string: url), let scheme = parsedUrl.scheme?.lowercased(), scheme == "ws" || scheme == "wss" else {
            result["open"] = false
            result["durationMs"] = elapsedMs(started)
            result["errorCode"] = "INVALID_URL"
            result["errorMessage"] = "WebSocket URL must use ws:// or wss://"
            return result
        }

        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = timeout(timeoutMs, fallback: 10)
        configuration.timeoutIntervalForResource = timeout(timeoutMs, fallback: 10)
        let session = URLSession(configuration: configuration)
        let task = session.webSocketTask(with: parsedUrl)
        task.resume()

        return await withCheckedContinuation { continuation in
            let queue = DispatchQueue(label: "app.capgo.networkdiagnostics.websocket")
            var finished = false

            func finish(open: Bool, error: Error?) {
                if finished {
                    return
                }
                finished = true
                task.cancel(with: .goingAway, reason: nil)
                session.invalidateAndCancel()
                continuation.resume(returning: websocketResult(result, started: started, open: open, error: error))
            }

            queue.asyncAfter(deadline: .now() + timeout(timeoutMs, fallback: 10)) {
                finish(open: false, error: NetworkDiagnosticsError.timeout)
            }

            task.sendPing { error in
                queue.async {
                    finish(open: error == nil, error: error)
                }
            }
        }
    }
}

private final class NoRedirectDelegate: NSObject, URLSessionTaskDelegate {
    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        willPerformHTTPRedirection response: HTTPURLResponse,
        newRequest request: URLRequest,
        completionHandler: @escaping (URLRequest?) -> Void
    ) {
        completionHandler(nil)
    }
}
