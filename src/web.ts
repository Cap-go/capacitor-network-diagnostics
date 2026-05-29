import { WebPlugin } from '@capacitor/core';

import type {
  ConnectionType,
  DownloadSpeedTestOptions,
  DownloadSpeedTestResult,
  NetworkDiagnosticsPlugin,
  NetworkStatusResult,
  PacketLossMode,
  PacketLossTestOptions,
  PacketLossTestResult,
  PluginVersionResult,
  PortTestOptions,
  PortTestResult,
  RunDiagnosticsOptions,
  RunDiagnosticsResult,
  UrlTestMethod,
  UrlTestOptions,
  UrlTestResult,
  WebSocketTestOptions,
  WebSocketTestResult,
} from './definitions';

interface NavigatorConnection {
  effectiveType?: string;
  type?: string;
  downlink?: number;
  rtt?: number;
  saveData?: boolean;
}

interface NavigatorWithConnection extends Navigator {
  connection?: NavigatorConnection;
  mozConnection?: NavigatorConnection;
  webkitConnection?: NavigatorConnection;
}

export class NetworkDiagnosticsWeb extends WebPlugin implements NetworkDiagnosticsPlugin {
  async getNetworkStatus(): Promise<NetworkStatusResult> {
    const nav = navigator as NavigatorWithConnection;
    const connection = nav.connection ?? nav.mozConnection ?? nav.webkitConnection;
    const connectionType = this.mapConnectionType(connection?.type ?? connection?.effectiveType);
    const connected = navigator.onLine;
    const details: Record<string, string | number | boolean> = {};

    if (connection?.effectiveType) {
      details.effectiveType = connection.effectiveType;
    }
    if (typeof connection?.downlink === 'number') {
      details.downlinkMbps = connection.downlink;
    }
    if (typeof connection?.rtt === 'number') {
      details.rttMs = connection.rtt;
    }

    return {
      connected,
      connectionType,
      constrained: connection?.saveData,
      details,
      expensive: false,
      internetReachable: connected,
    };
  }

  async testUrl(options: UrlTestOptions): Promise<UrlTestResult> {
    const started = performance.now();
    const method = this.normalizeMethod(options.method);
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), this.timeout(options.timeoutMs, 10000));

    try {
      const response = await fetch(options.url, {
        method,
        redirect: options.followRedirects === false ? 'manual' : 'follow',
        signal: controller.signal,
      });

      return {
        durationMs: this.elapsed(started),
        finalUrl: response.url || options.url,
        method,
        ok: response.status >= 200 && response.status < 400,
        reachable: true,
        statusCode: response.status,
        url: options.url,
      };
    } catch (error) {
      return {
        durationMs: this.elapsed(started),
        errorCode: this.errorCode(error),
        errorMessage: this.errorMessage(error),
        method,
        ok: false,
        reachable: false,
        url: options.url,
      };
    } finally {
      window.clearTimeout(timeout);
    }
  }

  async testPort(options: PortTestOptions): Promise<PortTestResult> {
    return {
      durationMs: 0,
      errorCode: 'UNSUPPORTED_WEB',
      errorMessage: 'Browsers cannot open raw TCP sockets. Use iOS or Android for native port diagnostics.',
      host: options.host,
      open: false,
      port: options.port,
    };
  }

  async testWebSocket(options: WebSocketTestOptions): Promise<WebSocketTestResult> {
    const started = performance.now();

    return new Promise<WebSocketTestResult>((resolve) => {
      let settled = false;
      let socket: WebSocket | undefined;
      const timeout = window.setTimeout(
        () => {
          if (settled) {
            return;
          }
          settled = true;
          socket?.close();
          resolve({
            durationMs: this.elapsed(started),
            errorCode: 'TIMEOUT',
            errorMessage: 'WebSocket handshake timed out',
            open: false,
            url: options.url,
          });
        },
        this.timeout(options.timeoutMs, 10000),
      );

      const finish = (result: WebSocketTestResult): void => {
        if (settled) {
          return;
        }
        settled = true;
        window.clearTimeout(timeout);
        socket?.close();
        resolve(result);
      };

      try {
        socket = new WebSocket(options.url);
        socket.onopen = () => {
          finish({
            durationMs: this.elapsed(started),
            open: true,
            protocol: socket?.protocol,
            url: options.url,
          });
        };
        socket.onerror = () => {
          finish({
            durationMs: this.elapsed(started),
            errorCode: 'WEBSOCKET_ERROR',
            errorMessage: 'WebSocket handshake failed',
            open: false,
            url: options.url,
          });
        };
      } catch (error) {
        finish({
          durationMs: this.elapsed(started),
          errorCode: this.errorCode(error),
          errorMessage: this.errorMessage(error),
          open: false,
          url: options.url,
        });
      }
    });
  }

  async testDownloadSpeed(options: DownloadSpeedTestOptions): Promise<DownloadSpeedTestResult> {
    const started = performance.now();
    const maxBytes = this.positiveNumber(options.maxBytes, 5 * 1024 * 1024);
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), this.timeout(options.timeoutMs, 30000));
    let bytesDownloaded = 0;
    let statusCode: number | undefined;

    try {
      const response = await fetch(options.url, {
        method: 'GET',
        signal: controller.signal,
      });
      statusCode = response.status;

      if (response.body) {
        const reader = response.body.getReader();
        while (bytesDownloaded < maxBytes) {
          const read = await reader.read();
          if (read.done) {
            break;
          }
          bytesDownloaded += read.value.byteLength;
        }
        await reader.cancel().catch(() => undefined);
      } else {
        const buffer = await response.arrayBuffer();
        bytesDownloaded = Math.min(buffer.byteLength, maxBytes);
      }

      const durationMs = Math.max(this.elapsed(started), 1);
      const bytesPerSecond = bytesDownloaded / (durationMs / 1000);

      return {
        bytesDownloaded,
        bytesPerSecond,
        durationMs,
        mbps: (bytesPerSecond * 8) / 1_000_000,
        ok: response.ok,
        statusCode,
        url: options.url,
      };
    } catch (error) {
      const durationMs = Math.max(this.elapsed(started), 1);
      const bytesPerSecond = bytesDownloaded / (durationMs / 1000);

      return {
        bytesDownloaded,
        bytesPerSecond,
        durationMs,
        errorCode: this.errorCode(error),
        errorMessage: this.errorMessage(error),
        mbps: (bytesPerSecond * 8) / 1_000_000,
        ok: false,
        statusCode,
        url: options.url,
      };
    } finally {
      window.clearTimeout(timeout);
    }
  }

  async testPacketLoss(options: PacketLossTestOptions): Promise<PacketLossTestResult> {
    const mode = this.packetLossMode(options);
    const count = Math.max(1, Math.floor(this.positiveNumber(options.count, 10)));
    const intervalMs = this.positiveNumber(options.intervalMs, 250);
    const latencies: number[] = [];
    let received = 0;
    let lastErrorCode: string | undefined;
    let lastErrorMessage: string | undefined;

    if (mode === 'tcp') {
      return {
        errorCode: 'UNSUPPORTED_WEB',
        errorMessage: 'Browsers cannot open raw TCP sockets. Use iOS or Android for native packet loss diagnostics.',
        lost: count,
        lossPercent: 100,
        mode,
        received: 0,
        sent: count,
        target: this.packetLossTarget(options, mode),
      };
    }

    for (let index = 0; index < count; index++) {
      const result = await this.testUrl({
        method: 'HEAD',
        timeoutMs: options.timeoutMs,
        url: options.url ?? '',
      });
      if (result.reachable) {
        received += 1;
        latencies.push(result.durationMs);
      } else {
        lastErrorCode = result.errorCode;
        lastErrorMessage = result.errorMessage;
      }
      if (index < count - 1) {
        await this.sleep(intervalMs);
      }
    }

    return this.packetLossResult(
      mode,
      this.packetLossTarget(options, mode),
      count,
      received,
      latencies,
      lastErrorCode,
      lastErrorMessage,
    );
  }

  async runDiagnostics(options: RunDiagnosticsOptions = {}): Promise<RunDiagnosticsResult> {
    const status = await this.getNetworkStatus();
    const urls: UrlTestResult[] = [];
    const ports: PortTestResult[] = [];
    const websockets: WebSocketTestResult[] = [];

    for (const url of options.urls ?? []) {
      urls.push(await this.testUrl(url));
    }
    for (const port of options.ports ?? []) {
      ports.push(await this.testPort(port));
    }
    for (const websocket of options.websockets ?? []) {
      websockets.push(await this.testWebSocket(websocket));
    }

    const download = options.download ? await this.testDownloadSpeed(options.download) : undefined;
    const packetLoss = options.packetLoss ? await this.testPacketLoss(options.packetLoss) : undefined;

    return {
      download,
      issues: this.buildIssues(status, urls, ports, websockets, download, packetLoss),
      packetLoss,
      ports,
      status,
      urls,
      websockets,
    };
  }

  async getPluginVersion(): Promise<PluginVersionResult> {
    return {
      version: 'web',
    };
  }

  private buildIssues(
    status: NetworkStatusResult,
    urls: UrlTestResult[],
    ports: PortTestResult[],
    websockets: WebSocketTestResult[],
    download?: DownloadSpeedTestResult,
    packetLoss?: PacketLossTestResult,
  ): string[] {
    const issues: string[] = [];

    if (!status.connected) {
      issues.push('No active network connection');
    } else if (!status.internetReachable) {
      issues.push('Network is connected but internet reachability is not confirmed');
    }

    for (const result of urls) {
      if (!result.reachable) {
        issues.push(`URL unreachable: ${result.url}`);
      } else if (!result.ok) {
        issues.push(`URL returned non-success status: ${result.url}`);
      }
    }

    for (const result of ports) {
      if (!result.open) {
        issues.push(`TCP port blocked or unreachable: ${result.host}:${result.port}`);
      }
    }

    for (const result of websockets) {
      if (!result.open) {
        issues.push(`WebSocket failed: ${result.url}`);
      }
    }

    if (download && !download.ok) {
      issues.push(`Download speed test failed: ${download.url}`);
    }

    if (packetLoss && packetLoss.lossPercent > 0) {
      issues.push(`Packet loss detected: ${packetLoss.lossPercent}% to ${packetLoss.target}`);
    }

    return issues;
  }

  private elapsed(started: number): number {
    return Math.round(performance.now() - started);
  }

  private errorCode(error: unknown): string {
    if (error instanceof DOMException && error.name === 'AbortError') {
      return 'TIMEOUT';
    }
    if (error instanceof Error && error.name) {
      return error.name;
    }
    return 'ERROR';
  }

  private errorMessage(error: unknown): string {
    if (error instanceof Error && error.message) {
      return error.message;
    }
    return String(error);
  }

  private mapConnectionType(type?: string): ConnectionType {
    if (!type) {
      return navigator.onLine ? 'unknown' : 'none';
    }

    if (type === 'wifi') {
      return 'wifi';
    }
    if (
      type === 'cellular' ||
      type.includes('2g') ||
      type.includes('3g') ||
      type.includes('4g') ||
      type.includes('5g')
    ) {
      return 'cellular';
    }
    if (type === 'ethernet') {
      return 'ethernet';
    }
    if (type === 'none') {
      return 'none';
    }

    return 'unknown';
  }

  private normalizeMethod(method?: UrlTestMethod): UrlTestMethod {
    return method === 'GET' ? 'GET' : 'HEAD';
  }

  private packetLossMode(options: PacketLossTestOptions): PacketLossMode {
    if (options.mode) {
      return options.mode;
    }
    return options.host && options.port ? 'tcp' : 'http';
  }

  private packetLossResult(
    mode: PacketLossMode,
    target: string,
    sent: number,
    received: number,
    latencies: number[],
    errorCode?: string,
    errorMessage?: string,
  ): PacketLossTestResult {
    const lost = sent - received;
    const result: PacketLossTestResult = {
      lost,
      lossPercent: (lost / sent) * 100,
      mode,
      received,
      sent,
      target,
    };

    if (latencies.length > 0) {
      result.averageLatencyMs = latencies.reduce((sum, latency) => sum + latency, 0) / latencies.length;
      result.minLatencyMs = Math.min(...latencies);
      result.maxLatencyMs = Math.max(...latencies);
    }
    if (errorCode) {
      result.errorCode = errorCode;
    }
    if (errorMessage) {
      result.errorMessage = errorMessage;
    }

    return result;
  }

  private packetLossTarget(options: PacketLossTestOptions, mode: PacketLossMode): string {
    if (mode === 'http') {
      return options.url ?? '';
    }
    return `${options.host ?? ''}:${options.port ?? 0}`;
  }

  private positiveNumber(value: number | undefined, fallback: number): number {
    return typeof value === 'number' && Number.isFinite(value) && value > 0 ? value : fallback;
  }

  private sleep(ms: number): Promise<void> {
    return new Promise((resolve) => window.setTimeout(resolve, ms));
  }

  private timeout(value: number | undefined, fallback: number): number {
    return Math.floor(this.positiveNumber(value, fallback));
  }
}
