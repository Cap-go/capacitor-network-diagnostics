# @capgo/capacitor-network-diagnostics

<a href="https://capgo.app/"><img src="https://capgo.app/readme-banner.svg?repo=Cap-go/capacitor-network-diagnostics" alt="Capgo - Instant updates for Capacitor" /></a>

<div align="center">
  <h2><a href="https://capgo.app/?ref=plugin_network_diagnostics"> ➡️ Get Instant updates for your App with Capgo</a></h2>
  <h2><a href="https://capgo.app/consulting/?ref=plugin_network_diagnostics"> Missing a feature? We’ll build the plugin for you 💪</a></h2>
</div>

Capacitor plugin for native network diagnostics. It checks connection type, native HTTP reachability, TCP ports, WebSocket handshakes, download speed, and application-level packet loss from iOS and Android.

## Install

You can use our AI-Assisted Setup to install the plugin. Add the Capgo skills to your AI tool using the following command:

```bash
npx skills add https://github.com/cap-go/capacitor-skills --skill capacitor-plugins
```

Then use the following prompt:

```text
Use the `capacitor-plugins` skill from `cap-go/capacitor-skills` to install the `@capgo/capacitor-network-diagnostics` plugin in my project.
```

If you prefer Manual Setup, install the plugin by running the following commands and follow the platform-specific instructions below:

```bash
npm install @capgo/capacitor-network-diagnostics
npx cap sync
```

## What It Tests

- Current native connection type: WiFi, cellular, ethernet, VPN, none, or unknown.
- OS network flags: validated internet on Android, captive portal on Android, expensive or constrained path where available.
- Native HTTP/HTTPS URL reachability with status code and latency.
- Native TCP host:port connectivity.
- Native WebSocket handshake for `ws://` and `wss://`.
- Download throughput against your own test file endpoint.
- Packet loss using repeated TCP connects or HTTP requests.

Raw ICMP ping is not consistently available to App Store and Play Store apps. `testPacketLoss` therefore measures application-level loss with TCP or HTTP probes.

## Usage

```typescript
import { NetworkDiagnostics } from '@capgo/capacitor-network-diagnostics';

const status = await NetworkDiagnostics.getNetworkStatus();

const api = await NetworkDiagnostics.testUrl({
  url: 'https://api.example.com/health',
  method: 'HEAD',
  timeoutMs: 5000,
});

const port = await NetworkDiagnostics.testPort({
  host: 'api.example.com',
  port: 443,
  timeoutMs: 3000,
});

const ws = await NetworkDiagnostics.testWebSocket({
  url: 'wss://ws.example.com/socket',
  timeoutMs: 5000,
});

const packetLoss = await NetworkDiagnostics.testPacketLoss({
  mode: 'tcp',
  host: 'api.example.com',
  port: 443,
  count: 10,
});

console.log({ status, api, port, ws, packetLoss });
```

## Combined Diagnostic Run

```typescript
const report = await NetworkDiagnostics.runDiagnostics({
  urls: [{ url: 'https://api.example.com/health' }],
  ports: [{ host: 'api.example.com', port: 443 }],
  websockets: [{ url: 'wss://ws.example.com/socket' }],
  download: {
    url: 'https://speed.example.com/5mb.bin',
    maxBytes: 5 * 1024 * 1024,
  },
  packetLoss: {
    mode: 'tcp',
    host: 'api.example.com',
    port: 443,
    count: 10,
  },
});

console.log(report.issues);
```

## Platform Notes

- iOS: no extra permissions are required. Connection type comes from `Network.framework`.
- Android: the plugin declares `android.permission.INTERNET` and `android.permission.ACCESS_NETWORK_STATE`.
- Web: provided as a development fallback. Browsers cannot open raw TCP sockets, and URL checks are limited by CORS.

## API

<docgen-index>

* [`getNetworkStatus()`](#getnetworkstatus)
* [`testUrl(...)`](#testurl)
* [`testPort(...)`](#testport)
* [`testWebSocket(...)`](#testwebsocket)
* [`testDownloadSpeed(...)`](#testdownloadspeed)
* [`testPacketLoss(...)`](#testpacketloss)
* [`runDiagnostics(...)`](#rundiagnostics)
* [`getPluginVersion()`](#getpluginversion)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

Native network diagnostics API.

### getNetworkStatus()

```typescript
getNetworkStatus() => Promise<NetworkStatusResult>
```

Read the current native connection type and platform network flags.

**Returns:** <code>Promise&lt;<a href="#networkstatusresult">NetworkStatusResult</a>&gt;</code>

--------------------


### testUrl(...)

```typescript
testUrl(options: UrlTestOptions) => Promise<UrlTestResult>
```

Test whether an HTTP or HTTPS URL can be reached from native networking.

| Param         | Type                                                      |
| ------------- | --------------------------------------------------------- |
| **`options`** | <code><a href="#urltestoptions">UrlTestOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#urltestresult">UrlTestResult</a>&gt;</code>

--------------------


### testPort(...)

```typescript
testPort(options: PortTestOptions) => Promise<PortTestResult>
```

Test whether a TCP host:port can be opened from native networking.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#porttestoptions">PortTestOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#porttestresult">PortTestResult</a>&gt;</code>

--------------------


### testWebSocket(...)

```typescript
testWebSocket(options: WebSocketTestOptions) => Promise<WebSocketTestResult>
```

Test whether a WebSocket URL can complete its native handshake.

| Param         | Type                                                                  |
| ------------- | --------------------------------------------------------------------- |
| **`options`** | <code><a href="#websockettestoptions">WebSocketTestOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#websockettestresult">WebSocketTestResult</a>&gt;</code>

--------------------


### testDownloadSpeed(...)

```typescript
testDownloadSpeed(options: DownloadSpeedTestOptions) => Promise<DownloadSpeedTestResult>
```

Measure download throughput from a native HTTP request.

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code><a href="#downloadspeedtestoptions">DownloadSpeedTestOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#downloadspeedtestresult">DownloadSpeedTestResult</a>&gt;</code>

--------------------


### testPacketLoss(...)

```typescript
testPacketLoss(options: PacketLossTestOptions) => Promise<PacketLossTestResult>
```

Estimate application-level packet loss with repeated TCP or HTTP probes.

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code><a href="#packetlosstestoptions">PacketLossTestOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#packetlosstestresult">PacketLossTestResult</a>&gt;</code>

--------------------


### runDiagnostics(...)

```typescript
runDiagnostics(options?: RunDiagnosticsOptions | undefined) => Promise<RunDiagnosticsResult>
```

Run several diagnostics and return a compact issue list.

| Param         | Type                                                                    |
| ------------- | ----------------------------------------------------------------------- |
| **`options`** | <code><a href="#rundiagnosticsoptions">RunDiagnosticsOptions</a></code> |

**Returns:** <code>Promise&lt;<a href="#rundiagnosticsresult">RunDiagnosticsResult</a>&gt;</code>

--------------------


### getPluginVersion()

```typescript
getPluginVersion() => Promise<PluginVersionResult>
```

Returns the platform implementation version marker.

**Returns:** <code>Promise&lt;<a href="#pluginversionresult">PluginVersionResult</a>&gt;</code>

--------------------


### Interfaces


#### NetworkStatusResult

Current native network state.

| Prop                    | Type                                                                                 | Description                                                          |
| ----------------------- | ------------------------------------------------------------------------------------ | -------------------------------------------------------------------- |
| **`connected`**         | <code>boolean</code>                                                                 | True when the platform reports an active network path.               |
| **`connectionType`**    | <code><a href="#connectiontype">ConnectionType</a></code>                            | Best-effort active transport type.                                   |
| **`internetReachable`** | <code>boolean</code>                                                                 | True when the OS marks the network as internet-capable or validated. |
| **`expensive`**         | <code>boolean</code>                                                                 | True for metered or expensive network paths.                         |
| **`constrained`**       | <code>boolean</code>                                                                 | True when the OS reports a low-data or constrained network path.     |
| **`captivePortal`**     | <code>boolean</code>                                                                 | True when Android reports captive portal capability.                 |
| **`details`**           | <code><a href="#record">Record</a>&lt;string, string \| number \| boolean&gt;</code> | Native platform details useful for debugging.                        |


#### UrlTestResult

Native HTTP URL reachability result.

| Prop               | Type                                                    |
| ------------------ | ------------------------------------------------------- |
| **`url`**          | <code>string</code>                                     |
| **`method`**       | <code><a href="#urltestmethod">UrlTestMethod</a></code> |
| **`ok`**           | <code>boolean</code>                                    |
| **`reachable`**    | <code>boolean</code>                                    |
| **`durationMs`**   | <code>number</code>                                     |
| **`statusCode`**   | <code>number</code>                                     |
| **`finalUrl`**     | <code>string</code>                                     |
| **`errorCode`**    | <code>string</code>                                     |
| **`errorMessage`** | <code>string</code>                                     |


#### UrlTestOptions

Options for native HTTP URL reachability checks.

| Prop                  | Type                                                    | Description                                           |
| --------------------- | ------------------------------------------------------- | ----------------------------------------------------- |
| **`url`**             | <code>string</code>                                     | HTTP or HTTPS URL to test.                            |
| **`method`**          | <code><a href="#urltestmethod">UrlTestMethod</a></code> | HTTP method. Defaults to `HEAD`.                      |
| **`timeoutMs`**       | <code>number</code>                                     | Request timeout in milliseconds. Defaults to `10000`. |
| **`followRedirects`** | <code>boolean</code>                                    | Follow redirects. Defaults to `true`.                 |


#### PortTestResult

Native TCP port check result.

| Prop               | Type                 |
| ------------------ | -------------------- |
| **`host`**         | <code>string</code>  |
| **`port`**         | <code>number</code>  |
| **`open`**         | <code>boolean</code> |
| **`durationMs`**   | <code>number</code>  |
| **`errorCode`**    | <code>string</code>  |
| **`errorMessage`** | <code>string</code>  |


#### PortTestOptions

Options for native TCP port checks.

| Prop            | Type                | Description                                         |
| --------------- | ------------------- | --------------------------------------------------- |
| **`host`**      | <code>string</code> | Hostname or IP address.                             |
| **`port`**      | <code>number</code> | TCP port to open.                                   |
| **`timeoutMs`** | <code>number</code> | Socket timeout in milliseconds. Defaults to `5000`. |


#### WebSocketTestResult

Native WebSocket handshake result.

| Prop               | Type                 |
| ------------------ | -------------------- |
| **`url`**          | <code>string</code>  |
| **`open`**         | <code>boolean</code> |
| **`durationMs`**   | <code>number</code>  |
| **`protocol`**     | <code>string</code>  |
| **`statusCode`**   | <code>number</code>  |
| **`errorCode`**    | <code>string</code>  |
| **`errorMessage`** | <code>string</code>  |


#### WebSocketTestOptions

Options for native WebSocket handshake checks.

| Prop            | Type                | Description                                             |
| --------------- | ------------------- | ------------------------------------------------------- |
| **`url`**       | <code>string</code> | `ws://` or `wss://` URL to test.                        |
| **`timeoutMs`** | <code>number</code> | Handshake timeout in milliseconds. Defaults to `10000`. |


#### DownloadSpeedTestResult

Native download speed measurement result.

| Prop                  | Type                 |
| --------------------- | -------------------- |
| **`url`**             | <code>string</code>  |
| **`ok`**              | <code>boolean</code> |
| **`durationMs`**      | <code>number</code>  |
| **`bytesDownloaded`** | <code>number</code>  |
| **`bytesPerSecond`**  | <code>number</code>  |
| **`mbps`**            | <code>number</code>  |
| **`statusCode`**      | <code>number</code>  |
| **`errorCode`**       | <code>string</code>  |
| **`errorMessage`**    | <code>string</code>  |


#### DownloadSpeedTestOptions

Options for native download speed measurement.

| Prop            | Type                | Description                                                           |
| --------------- | ------------------- | --------------------------------------------------------------------- |
| **`url`**       | <code>string</code> | HTTP or HTTPS URL returning a downloadable body.                      |
| **`maxBytes`**  | <code>number</code> | Maximum bytes to read before stopping. Defaults to `5242880` (5 MiB). |
| **`timeoutMs`** | <code>number</code> | Request timeout in milliseconds. Defaults to `30000`.                 |


#### PacketLossTestResult

Application-level packet loss result.

| Prop                   | Type                                                      |
| ---------------------- | --------------------------------------------------------- |
| **`mode`**             | <code><a href="#packetlossmode">PacketLossMode</a></code> |
| **`target`**           | <code>string</code>                                       |
| **`sent`**             | <code>number</code>                                       |
| **`received`**         | <code>number</code>                                       |
| **`lost`**             | <code>number</code>                                       |
| **`lossPercent`**      | <code>number</code>                                       |
| **`averageLatencyMs`** | <code>number</code>                                       |
| **`minLatencyMs`**     | <code>number</code>                                       |
| **`maxLatencyMs`**     | <code>number</code>                                       |
| **`errorCode`**        | <code>string</code>                                       |
| **`errorMessage`**     | <code>string</code>                                       |


#### PacketLossTestOptions

Options for packet loss measurement.

Native apps cannot rely on raw ICMP ping on both iOS and Android, so this
method measures application-level loss with repeated TCP connects or HTTP
requests.

| Prop             | Type                                                      | Description                                                                 |
| ---------------- | --------------------------------------------------------- | --------------------------------------------------------------------------- |
| **`mode`**       | <code><a href="#packetlossmode">PacketLossMode</a></code> | Probe mode. Defaults to `tcp` when host/port is provided, otherwise `http`. |
| **`host`**       | <code>string</code>                                       | Hostname or IP address for TCP probes.                                      |
| **`port`**       | <code>number</code>                                       | TCP port for TCP probes.                                                    |
| **`url`**        | <code>string</code>                                       | HTTP or HTTPS URL for HTTP probes.                                          |
| **`count`**      | <code>number</code>                                       | Number of probes to send. Defaults to `10`.                                 |
| **`timeoutMs`**  | <code>number</code>                                       | Per-probe timeout in milliseconds. Defaults to `3000`.                      |
| **`intervalMs`** | <code>number</code>                                       | Delay between probes in milliseconds. Defaults to `250`.                    |


#### RunDiagnosticsResult

Combined native network diagnostic result.

| Prop             | Type                                                                        |
| ---------------- | --------------------------------------------------------------------------- |
| **`status`**     | <code><a href="#networkstatusresult">NetworkStatusResult</a></code>         |
| **`urls`**       | <code>UrlTestResult[]</code>                                                |
| **`ports`**      | <code>PortTestResult[]</code>                                               |
| **`websockets`** | <code>WebSocketTestResult[]</code>                                          |
| **`issues`**     | <code>string[]</code>                                                       |
| **`download`**   | <code><a href="#downloadspeedtestresult">DownloadSpeedTestResult</a></code> |
| **`packetLoss`** | <code><a href="#packetlosstestresult">PacketLossTestResult</a></code>       |


#### RunDiagnosticsOptions

Options for a combined native network diagnostic run.

| Prop             | Type                                                                          |
| ---------------- | ----------------------------------------------------------------------------- |
| **`urls`**       | <code>UrlTestOptions[]</code>                                                 |
| **`ports`**      | <code>PortTestOptions[]</code>                                                |
| **`websockets`** | <code>WebSocketTestOptions[]</code>                                           |
| **`download`**   | <code><a href="#downloadspeedtestoptions">DownloadSpeedTestOptions</a></code> |
| **`packetLoss`** | <code><a href="#packetlosstestoptions">PacketLossTestOptions</a></code>       |


#### PluginVersionResult

Plugin version payload.

| Prop          | Type                | Description                                                 |
| ------------- | ------------------- | ----------------------------------------------------------- |
| **`version`** | <code>string</code> | Version identifier returned by the platform implementation. |


### Type Aliases


#### ConnectionType

<code>'none' | 'wifi' | 'cellular' | 'ethernet' | 'vpn' | 'other' | 'unknown'</code>


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


#### UrlTestMethod

<code>'HEAD' | 'GET'</code>


#### PacketLossMode

<code>'tcp' | 'http'</code>

</docgen-api>
