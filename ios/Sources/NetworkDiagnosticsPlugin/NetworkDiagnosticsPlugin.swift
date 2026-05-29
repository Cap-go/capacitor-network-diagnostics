import Foundation
import Capacitor

@objc(NetworkDiagnosticsPlugin)
public class NetworkDiagnosticsPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "NetworkDiagnosticsPlugin"
    public let jsName = "NetworkDiagnostics"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "getNetworkStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "testUrl", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "testPort", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "testWebSocket", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "testDownloadSpeed", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "testPacketLoss", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "runDiagnostics", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise)
    ]

    private let implementation = NetworkDiagnostics()

    @objc func getNetworkStatus(_ call: CAPPluginCall) {
        Task {
            call.resolve(await implementation.getNetworkStatus())
        }
    }

    @objc func testUrl(_ call: CAPPluginCall) {
        guard let url = call.getString("url"), !url.isEmpty else {
            call.reject("URL is required")
            return
        }

        Task {
            call.resolve(await implementation.testUrl(
                url: url,
                method: call.getString("method") ?? "HEAD",
                timeoutMs: call.getInt("timeoutMs") ?? 10_000,
                followRedirects: call.getBool("followRedirects") ?? true
            ))
        }
    }

    @objc func testPort(_ call: CAPPluginCall) {
        guard let host = call.getString("host"), !host.isEmpty else {
            call.reject("Host is required")
            return
        }
        let port = call.getInt("port") ?? 0
        guard port > 0 && port <= 65_535 else {
            call.reject("Port must be between 1 and 65535")
            return
        }

        Task {
            call.resolve(await implementation.testPort(
                host: host,
                port: port,
                timeoutMs: call.getInt("timeoutMs") ?? 5_000
            ))
        }
    }

    @objc func testWebSocket(_ call: CAPPluginCall) {
        guard let url = call.getString("url"), !url.isEmpty else {
            call.reject("URL is required")
            return
        }

        Task {
            call.resolve(await implementation.testWebSocket(
                url: url,
                timeoutMs: call.getInt("timeoutMs") ?? 10_000
            ))
        }
    }

    @objc func testDownloadSpeed(_ call: CAPPluginCall) {
        guard let url = call.getString("url"), !url.isEmpty else {
            call.reject("URL is required")
            return
        }

        Task {
            call.resolve(await implementation.testDownloadSpeed(
                url: url,
                maxBytes: call.getInt("maxBytes") ?? 5 * 1024 * 1024,
                timeoutMs: call.getInt("timeoutMs") ?? 30_000
            ))
        }
    }

    @objc func testPacketLoss(_ call: CAPPluginCall) {
        let host = call.getString("host") ?? ""
        let port = call.getInt("port") ?? 0
        let url = call.getString("url") ?? ""

        guard (!host.isEmpty && port > 0) || !url.isEmpty else {
            call.reject("Either host and port, or url is required")
            return
        }

        Task {
            let options = PacketLossOptions(
                mode: call.getString("mode") ?? "",
                host: host,
                port: port,
                url: url,
                count: call.getInt("count") ?? 10,
                timeoutMs: call.getInt("timeoutMs") ?? 3_000,
                intervalMs: call.getInt("intervalMs") ?? 250
            )
            call.resolve(await implementation.testPacketLoss(options))
        }
    }

    @objc func runDiagnostics(_ call: CAPPluginCall) {
        let urls = call.getArray("urls", []) as? [[String: Any]] ?? []
        let ports = call.getArray("ports", []) as? [[String: Any]] ?? []
        let websockets = call.getArray("websockets", []) as? [[String: Any]] ?? []
        let download = call.getObject("download")
        let packetLoss = call.getObject("packetLoss")

        Task {
            call.resolve(await implementation.runDiagnostics(
                urls: urls,
                ports: ports,
                websockets: websockets,
                download: download,
                packetLoss: packetLoss
            ))
        }
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve([
            "version": implementation.getPluginVersion()
        ])
    }
}
