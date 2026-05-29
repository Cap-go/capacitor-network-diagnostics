import XCTest
@testable import NetworkDiagnosticsPlugin

class NetworkDiagnosticsTests: XCTestCase {
    func testGetPluginVersion() {
        let implementation = NetworkDiagnostics()
        let result = implementation.getPluginVersion()

        XCTAssertEqual("native", result)
    }
}
