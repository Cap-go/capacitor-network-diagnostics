import Foundation

extension NetworkDiagnostics {
    public func runDiagnostics(
        urls: [[String: Any]],
        ports: [[String: Any]],
        websockets: [[String: Any]],
        download: [String: Any]?,
        packetLoss: [String: Any]?
    ) async -> [String: Any] {
        let status = await getNetworkStatus()
        var issues = statusIssues(status)
        let urlDiagnostics = await runUrlDiagnostics(urls)
        let portDiagnostics = await runPortDiagnostics(ports)
        let websocketDiagnostics = await runWebSocketDiagnostics(websockets)
        var output: [String: Any] = ["status": status]

        output["urls"] = urlDiagnostics.results
        output["ports"] = portDiagnostics.results
        output["websockets"] = websocketDiagnostics.results
        issues += urlDiagnostics.issues + portDiagnostics.issues + websocketDiagnostics.issues

        if let downloadResult = await downloadDiagnostic(download) {
            output["download"] = downloadResult.result
            issues += downloadResult.issues
        }

        if let packetLossResult = await packetLossDiagnostic(packetLoss) {
            output["packetLoss"] = packetLossResult.result
            issues += packetLossResult.issues
        }

        output["issues"] = issues
        return output
    }

    func runUrlDiagnostics(_ urls: [[String: Any]]) async -> DiagnosticGroupResult {
        var results: [[String: Any]] = []
        var issues: [String] = []
        for options in urls {
            let result = await testUrl(
                url: options["url"] as? String ?? "",
                method: options["method"] as? String ?? "HEAD",
                timeoutMs: options["timeoutMs"] as? Int ?? 10_000,
                followRedirects: options["followRedirects"] as? Bool ?? true
            )
            results.append(result)
            appendUrlIssue(result, to: &issues)
        }
        return DiagnosticGroupResult(results: results, issues: issues)
    }

    func runPortDiagnostics(_ ports: [[String: Any]]) async -> DiagnosticGroupResult {
        var results: [[String: Any]] = []
        var issues: [String] = []
        for options in ports {
            let result = await testPort(
                host: options["host"] as? String ?? "",
                port: options["port"] as? Int ?? 0,
                timeoutMs: options["timeoutMs"] as? Int ?? 5_000
            )
            results.append(result)
            appendPortIssue(result, to: &issues)
        }
        return DiagnosticGroupResult(results: results, issues: issues)
    }

    func runWebSocketDiagnostics(_ websockets: [[String: Any]]) async -> DiagnosticGroupResult {
        var results: [[String: Any]] = []
        var issues: [String] = []
        for options in websockets {
            let result = await testWebSocket(
                url: options["url"] as? String ?? "",
                timeoutMs: options["timeoutMs"] as? Int ?? 10_000
            )
            results.append(result)
            appendWebSocketIssue(result, to: &issues)
        }
        return DiagnosticGroupResult(results: results, issues: issues)
    }

    func downloadDiagnostic(_ download: [String: Any]?) async -> OptionalDiagnosticResult? {
        guard let download = download else {
            return nil
        }

        let result = await testDownloadSpeed(
            url: download["url"] as? String ?? "",
            maxBytes: download["maxBytes"] as? Int ?? 5 * 1024 * 1024,
            timeoutMs: download["timeoutMs"] as? Int ?? 30_000
        )
        var issues: [String] = []
        if !(result["ok"] as? Bool ?? false) {
            issues.append("Download speed test failed: \(result["url"] as? String ?? "")")
        }
        return OptionalDiagnosticResult(result: result, issues: issues)
    }

    func packetLossDiagnostic(_ packetLoss: [String: Any]?) async -> OptionalDiagnosticResult? {
        guard let packetLoss = packetLoss else {
            return nil
        }

        let result = await testPacketLoss(packetLossOptions(from: packetLoss))
        var issues: [String] = []
        if let lossPercent = result["lossPercent"] as? Double, lossPercent > 0 {
            issues.append("Packet loss detected: \(lossPercent)% to \(result["target"] as? String ?? "")")
        }
        return OptionalDiagnosticResult(result: result, issues: issues)
    }

    func packetLossOptions(from options: [String: Any]) -> PacketLossOptions {
        PacketLossOptions(
            mode: options["mode"] as? String ?? "",
            host: options["host"] as? String ?? "",
            port: options["port"] as? Int ?? 0,
            url: options["url"] as? String ?? "",
            count: options["count"] as? Int ?? 10,
            timeoutMs: options["timeoutMs"] as? Int ?? 3_000,
            intervalMs: options["intervalMs"] as? Int ?? 250
        )
    }

    func statusIssues(_ status: [String: Any]) -> [String] {
        if !(status["connected"] as? Bool ?? false) {
            return ["No active network connection"]
        }
        if !(status["internetReachable"] as? Bool ?? false) {
            return ["Network is connected but internet reachability is not confirmed"]
        }
        return []
    }

    func appendUrlIssue(_ result: [String: Any], to issues: inout [String]) {
        if !(result["reachable"] as? Bool ?? false) {
            issues.append("URL unreachable: \(result["url"] as? String ?? "")")
        } else if !(result["ok"] as? Bool ?? false) {
            issues.append("URL returned non-success status: \(result["url"] as? String ?? "")")
        }
    }

    func appendPortIssue(_ result: [String: Any], to issues: inout [String]) {
        if !(result["open"] as? Bool ?? false) {
            issues.append("TCP port blocked or unreachable: \(result["host"] as? String ?? ""):\(result["port"] as? Int ?? 0)")
        }
    }

    func appendWebSocketIssue(_ result: [String: Any], to issues: inout [String]) {
        if !(result["open"] as? Bool ?? false) {
            issues.append("WebSocket failed: \(result["url"] as? String ?? "")")
        }
    }
}

struct DiagnosticGroupResult {
    let results: [[String: Any]]
    let issues: [String]
}

struct OptionalDiagnosticResult {
    let result: [String: Any]
    let issues: [String]
}
