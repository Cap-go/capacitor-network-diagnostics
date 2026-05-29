export type ConnectionType = 'none' | 'wifi' | 'cellular' | 'ethernet' | 'vpn' | 'other' | 'unknown';

export type UrlTestMethod = 'HEAD' | 'GET';

export type PacketLossMode = 'tcp' | 'http';

/**
 * Current native network state.
 */
export interface NetworkStatusResult {
  /**
   * True when the platform reports an active network path.
   */
  connected: boolean;

  /**
   * Best-effort active transport type.
   */
  connectionType: ConnectionType;

  /**
   * True when the OS marks the network as internet-capable or validated.
   */
  internetReachable: boolean;

  /**
   * True for metered or expensive network paths.
   */
  expensive?: boolean;

  /**
   * True when the OS reports a low-data or constrained network path.
   */
  constrained?: boolean;

  /**
   * True when Android reports captive portal capability.
   */
  captivePortal?: boolean;

  /**
   * Native platform details useful for debugging.
   */
  details?: Record<string, string | number | boolean>;
}

/**
 * Options for native HTTP URL reachability checks.
 */
export interface UrlTestOptions {
  /**
   * HTTP or HTTPS URL to test.
   */
  url: string;

  /**
   * HTTP method. Defaults to `HEAD`.
   */
  method?: UrlTestMethod;

  /**
   * Request timeout in milliseconds. Defaults to `10000`.
   */
  timeoutMs?: number;

  /**
   * Follow redirects. Defaults to `true`.
   */
  followRedirects?: boolean;
}

/**
 * Native HTTP URL reachability result.
 */
export interface UrlTestResult {
  url: string;
  method: UrlTestMethod;
  ok: boolean;
  reachable: boolean;
  durationMs: number;
  statusCode?: number;
  finalUrl?: string;
  errorCode?: string;
  errorMessage?: string;
}

/**
 * Options for native TCP port checks.
 */
export interface PortTestOptions {
  /**
   * Hostname or IP address.
   */
  host: string;

  /**
   * TCP port to open.
   */
  port: number;

  /**
   * Socket timeout in milliseconds. Defaults to `5000`.
   */
  timeoutMs?: number;
}

/**
 * Native TCP port check result.
 */
export interface PortTestResult {
  host: string;
  port: number;
  open: boolean;
  durationMs: number;
  errorCode?: string;
  errorMessage?: string;
}

/**
 * Options for native WebSocket handshake checks.
 */
export interface WebSocketTestOptions {
  /**
   * `ws://` or `wss://` URL to test.
   */
  url: string;

  /**
   * Handshake timeout in milliseconds. Defaults to `10000`.
   */
  timeoutMs?: number;
}

/**
 * Native WebSocket handshake result.
 */
export interface WebSocketTestResult {
  url: string;
  open: boolean;
  durationMs: number;
  protocol?: string;
  statusCode?: number;
  errorCode?: string;
  errorMessage?: string;
}

/**
 * Options for native download speed measurement.
 */
export interface DownloadSpeedTestOptions {
  /**
   * HTTP or HTTPS URL returning a downloadable body.
   */
  url: string;

  /**
   * Maximum bytes to read before stopping. Defaults to `5242880` (5 MiB).
   */
  maxBytes?: number;

  /**
   * Request timeout in milliseconds. Defaults to `30000`.
   */
  timeoutMs?: number;
}

/**
 * Native download speed measurement result.
 */
export interface DownloadSpeedTestResult {
  url: string;
  ok: boolean;
  durationMs: number;
  bytesDownloaded: number;
  bytesPerSecond: number;
  mbps: number;
  statusCode?: number;
  errorCode?: string;
  errorMessage?: string;
}

/**
 * Options for packet loss measurement.
 *
 * Native apps cannot rely on raw ICMP ping on both iOS and Android, so this
 * method measures application-level loss with repeated TCP connects or HTTP
 * requests.
 */
export interface PacketLossTestOptions {
  /**
   * Probe mode. Defaults to `tcp` when host/port is provided, otherwise `http`.
   */
  mode?: PacketLossMode;

  /**
   * Hostname or IP address for TCP probes.
   */
  host?: string;

  /**
   * TCP port for TCP probes.
   */
  port?: number;

  /**
   * HTTP or HTTPS URL for HTTP probes.
   */
  url?: string;

  /**
   * Number of probes to send. Defaults to `10`.
   */
  count?: number;

  /**
   * Per-probe timeout in milliseconds. Defaults to `3000`.
   */
  timeoutMs?: number;

  /**
   * Delay between probes in milliseconds. Defaults to `250`.
   */
  intervalMs?: number;
}

/**
 * Application-level packet loss result.
 */
export interface PacketLossTestResult {
  mode: PacketLossMode;
  target: string;
  sent: number;
  received: number;
  lost: number;
  lossPercent: number;
  averageLatencyMs?: number;
  minLatencyMs?: number;
  maxLatencyMs?: number;
  errorCode?: string;
  errorMessage?: string;
}

/**
 * Options for a combined native network diagnostic run.
 */
export interface RunDiagnosticsOptions {
  urls?: UrlTestOptions[];
  ports?: PortTestOptions[];
  websockets?: WebSocketTestOptions[];
  download?: DownloadSpeedTestOptions;
  packetLoss?: PacketLossTestOptions;
}

/**
 * Combined native network diagnostic result.
 */
export interface RunDiagnosticsResult {
  status: NetworkStatusResult;
  urls: UrlTestResult[];
  ports: PortTestResult[];
  websockets: WebSocketTestResult[];
  issues: string[];
  download?: DownloadSpeedTestResult;
  packetLoss?: PacketLossTestResult;
}

/**
 * Plugin version payload.
 */
export interface PluginVersionResult {
  /**
   * Version identifier returned by the platform implementation.
   */
  version: string;
}

/**
 * Native network diagnostics API.
 */
export interface NetworkDiagnosticsPlugin {
  /**
   * Read the current native connection type and platform network flags.
   */
  getNetworkStatus(): Promise<NetworkStatusResult>;

  /**
   * Test whether an HTTP or HTTPS URL can be reached from native networking.
   */
  testUrl(options: UrlTestOptions): Promise<UrlTestResult>;

  /**
   * Test whether a TCP host:port can be opened from native networking.
   */
  testPort(options: PortTestOptions): Promise<PortTestResult>;

  /**
   * Test whether a WebSocket URL can complete its native handshake.
   */
  testWebSocket(options: WebSocketTestOptions): Promise<WebSocketTestResult>;

  /**
   * Measure download throughput from a native HTTP request.
   */
  testDownloadSpeed(options: DownloadSpeedTestOptions): Promise<DownloadSpeedTestResult>;

  /**
   * Estimate application-level packet loss with repeated TCP or HTTP probes.
   */
  testPacketLoss(options: PacketLossTestOptions): Promise<PacketLossTestResult>;

  /**
   * Run several diagnostics and return a compact issue list.
   */
  runDiagnostics(options?: RunDiagnosticsOptions): Promise<RunDiagnosticsResult>;

  /**
   * Returns the platform implementation version marker.
   */
  getPluginVersion(): Promise<PluginVersionResult>;
}
