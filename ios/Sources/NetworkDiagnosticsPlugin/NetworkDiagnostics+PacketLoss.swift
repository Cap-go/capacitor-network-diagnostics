import Foundation

extension NetworkDiagnostics {
    func testPacketLoss(_ options: PacketLossOptions) async -> [String: Any] {
        let mode = normalizePacketLossMode(mode: options.mode, host: options.host, port: options.port, url: options.url)
        let probeCount = positive(options.count, fallback: 10)
        let delayMs = positive(options.intervalMs, fallback: 250)
        var report = PacketLossReport(mode: mode, target: packetLossTarget(mode: mode, options: options), sent: probeCount)

        for index in 0..<probeCount {
            let probe = mode == "http"
                ? await probeHttp(url: options.url, timeoutMs: options.timeoutMs)
                : await probeTcp(host: options.host, port: options.port, timeoutMs: options.timeoutMs)
            report.record(probe)

            if index < probeCount - 1 {
                try? await Task.sleep(nanoseconds: UInt64(delayMs) * 1_000_000)
            }
        }

        return packetLossResult(report)
    }

    func packetLossTarget(mode: String, options: PacketLossOptions) -> String {
        mode == "http" ? options.url : "\(options.host):\(options.port)"
    }

    func packetLossResult(_ report: PacketLossReport) -> [String: Any] {
        let lost = report.sent - report.received
        var result: [String: Any] = [
            "mode": report.mode,
            "target": report.target,
            "sent": report.sent,
            "received": report.received,
            "lost": lost,
            "lossPercent": report.sent == 0 ? 0 : (Double(lost) * 100.0) / Double(report.sent)
        ]

        if !report.latencies.isEmpty {
            result["averageLatencyMs"] = Double(report.latencies.reduce(0, +)) / Double(report.latencies.count)
            result["minLatencyMs"] = report.latencies.min()
            result["maxLatencyMs"] = report.latencies.max()
        }
        if let errorCode = report.errorCode {
            result["errorCode"] = errorCode
        }
        if let errorMessage = report.errorMessage {
            result["errorMessage"] = errorMessage
        }

        return result
    }
}

struct PacketLossOptions {
    let mode: String
    let host: String
    let port: Int
    let url: String
    let count: Int
    let timeoutMs: Int
    let intervalMs: Int
}

struct PacketLossReport {
    let mode: String
    let target: String
    let sent: Int
    var received = 0
    var latencies: [Int] = []
    var errorCode: String?
    var errorMessage: String?

    mutating func record(_ probe: ProbeResult) {
        if probe.success {
            received += 1
            latencies.append(probe.durationMs)
            return
        }

        errorCode = probe.errorCode
        errorMessage = probe.errorMessage
    }
}

struct ProbeResult {
    let success: Bool
    let durationMs: Int
    let errorCode: String?
    let errorMessage: String?
}
