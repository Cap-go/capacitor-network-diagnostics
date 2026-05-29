package app.capgo.networkdiagnostics;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.util.Base64;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Locale;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.json.JSONArray;
import org.json.JSONObject;

public class NetworkDiagnostics {

    private static final int DEFAULT_URL_TIMEOUT_MS = 10000;
    private static final int DEFAULT_PORT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_DOWNLOAD_TIMEOUT_MS = 30000;
    private static final int DEFAULT_DOWNLOAD_MAX_BYTES = 5 * 1024 * 1024;
    private static final int DEFAULT_PACKET_COUNT = 10;
    private static final int DEFAULT_PACKET_TIMEOUT_MS = 3000;
    private static final int DEFAULT_PACKET_INTERVAL_MS = 250;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String getPluginVersion() {
        return "native";
    }

    public JSObject getNetworkStatus(Context context) {
        JSObject ret = new JSObject();
        JSObject details = new JSObject();
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        if (connectivityManager == null) {
            ret.put("connected", false);
            ret.put("connectionType", "unknown");
            ret.put("internetReachable", false);
            ret.put("details", details);
            return ret;
        }

        Network network = connectivityManager.getActiveNetwork();
        NetworkCapabilities capabilities = network == null ? null : connectivityManager.getNetworkCapabilities(network);
        LinkProperties linkProperties = network == null ? null : connectivityManager.getLinkProperties(network);
        boolean connected = capabilities != null;
        boolean validated = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        boolean captivePortal = capabilities != null && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL);

        ret.put("connected", connected);
        ret.put("connectionType", capabilities == null ? fallbackConnectionType(connectivityManager) : connectionType(capabilities));
        ret.put("internetReachable", validated);
        ret.put("expensive", connectivityManager.isActiveNetworkMetered());
        ret.put("constrained", false);
        ret.put("captivePortal", captivePortal);

        if (capabilities != null) {
            details.put("hasInternetCapability", capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            details.put("validated", validated);
            details.put("captivePortal", captivePortal);
            details.put("transports", transports(capabilities));
        }
        if (linkProperties != null) {
            if (linkProperties.getInterfaceName() != null) {
                details.put("interfaceName", linkProperties.getInterfaceName());
            }
            details.put("dnsServers", linkProperties.getDnsServers().toString());
        }

        ret.put("details", details);
        return ret;
    }

    public JSObject testUrl(String url, String method, int timeoutMs, boolean followRedirects) {
        long started = System.nanoTime();
        String normalizedMethod = normalizeMethod(method);
        HttpURLConnection connection = null;
        JSObject ret = new JSObject();
        ret.put("url", url);
        ret.put("method", normalizedMethod);

        try {
            URL parsedUrl = new URL(url);
            connection = (HttpURLConnection) parsedUrl.openConnection();
            connection.setConnectTimeout(positive(timeoutMs, DEFAULT_URL_TIMEOUT_MS));
            connection.setReadTimeout(positive(timeoutMs, DEFAULT_URL_TIMEOUT_MS));
            connection.setInstanceFollowRedirects(followRedirects);
            connection.setRequestMethod(normalizedMethod);
            connection.setUseCaches(false);

            int statusCode = connection.getResponseCode();
            ret.put("ok", statusCode >= 200 && statusCode < 400);
            ret.put("reachable", true);
            ret.put("durationMs", elapsedMs(started));
            ret.put("statusCode", statusCode);
            ret.put("finalUrl", connection.getURL().toString());
        } catch (Exception exception) {
            ret.put("ok", false);
            ret.put("reachable", false);
            ret.put("durationMs", elapsedMs(started));
            ret.put("errorCode", errorCode(exception));
            ret.put("errorMessage", exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return ret;
    }

    public JSObject testPort(String host, int port, int timeoutMs) {
        ProbeResult probe = probeTcp(host, port, positive(timeoutMs, DEFAULT_PORT_TIMEOUT_MS));
        JSObject ret = new JSObject();
        ret.put("host", host);
        ret.put("port", port);
        ret.put("open", probe.success);
        ret.put("durationMs", probe.durationMs);
        if (!probe.success) {
            ret.put("errorCode", probe.errorCode);
            ret.put("errorMessage", probe.errorMessage);
        }
        return ret;
    }

    public JSObject testWebSocket(String url, int timeoutMs) {
        long started = System.nanoTime();
        JSObject ret = new JSObject();
        ret.put("url", url);

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.US);
            if (!scheme.equals("ws") && !scheme.equals("wss")) {
                throw new IllegalArgumentException("WebSocket URL must use ws:// or wss://");
            }

            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("WebSocket host is required");
            }

            int port = uri.getPort() == -1 ? defaultWebSocketPort(scheme) : uri.getPort();
            int timeout = positive(timeoutMs, DEFAULT_URL_TIMEOUT_MS);
            Socket rawSocket = new Socket();
            rawSocket.connect(new InetSocketAddress(host, port), timeout);
            rawSocket.setSoTimeout(timeout);

            Socket socket = rawSocket;
            if (scheme.equals("wss")) {
                SSLSocketFactory socketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) socketFactory.createSocket(rawSocket, host, port, true);
                sslSocket.setSoTimeout(timeout);
                sslSocket.startHandshake();
                socket = sslSocket;
            }

            final Socket activeSocket = socket;
            try (
                activeSocket;
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(activeSocket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(activeSocket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                writer.write(webSocketHandshake(uri, host, port, scheme));
                writer.flush();

                String statusLine = reader.readLine();
                int statusCode = parseStatusCode(statusLine);
                ret.put("open", statusCode == 101);
                ret.put("durationMs", elapsedMs(started));
                ret.put("statusCode", statusCode);
                if (statusCode != 101) {
                    ret.put("errorCode", "WEBSOCKET_HANDSHAKE_FAILED");
                    ret.put("errorMessage", statusLine == null ? "No handshake response" : statusLine);
                }
            }
        } catch (Exception exception) {
            ret.put("open", false);
            ret.put("durationMs", elapsedMs(started));
            ret.put("errorCode", errorCode(exception));
            ret.put("errorMessage", exception.getMessage());
        }

        return ret;
    }

    public JSObject testDownloadSpeed(String url, int maxBytes, int timeoutMs) {
        long started = System.nanoTime();
        int byteLimit = positive(maxBytes, DEFAULT_DOWNLOAD_MAX_BYTES);
        int bytesDownloaded = 0;
        int statusCode = 0;
        HttpURLConnection connection = null;
        JSObject ret = new JSObject();
        ret.put("url", url);

        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(positive(timeoutMs, DEFAULT_DOWNLOAD_TIMEOUT_MS));
            connection.setReadTimeout(positive(timeoutMs, DEFAULT_DOWNLOAD_TIMEOUT_MS));
            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            statusCode = connection.getResponseCode();

            InputStream stream = statusCode >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream != null) {
                try (InputStream inputStream = stream) {
                    byte[] buffer = new byte[16 * 1024];
                    while (bytesDownloaded < byteLimit) {
                        int maxRead = Math.min(buffer.length, byteLimit - bytesDownloaded);
                        int read = inputStream.read(buffer, 0, maxRead);
                        if (read == -1) {
                            break;
                        }
                        bytesDownloaded += read;
                    }
                }
            }

            long durationMs = Math.max(elapsedMs(started), 1);
            double bytesPerSecond = bytesDownloaded / (durationMs / 1000.0);
            ret.put("ok", statusCode >= 200 && statusCode < 400);
            ret.put("durationMs", durationMs);
            ret.put("bytesDownloaded", bytesDownloaded);
            ret.put("bytesPerSecond", bytesPerSecond);
            ret.put("mbps", (bytesPerSecond * 8) / 1_000_000);
            ret.put("statusCode", statusCode);
        } catch (Exception exception) {
            long durationMs = Math.max(elapsedMs(started), 1);
            double bytesPerSecond = bytesDownloaded / (durationMs / 1000.0);
            ret.put("ok", false);
            ret.put("durationMs", durationMs);
            ret.put("bytesDownloaded", bytesDownloaded);
            ret.put("bytesPerSecond", bytesPerSecond);
            ret.put("mbps", (bytesPerSecond * 8) / 1_000_000);
            if (statusCode > 0) {
                ret.put("statusCode", statusCode);
            }
            ret.put("errorCode", errorCode(exception));
            ret.put("errorMessage", exception.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        return ret;
    }

    public JSObject testPacketLoss(String mode, String host, int port, String url, int count, int timeoutMs, int intervalMs) {
        String normalizedMode = normalizePacketLossMode(mode, host, port, url);
        int probeCount = positive(count, DEFAULT_PACKET_COUNT);
        int probeTimeout = positive(timeoutMs, DEFAULT_PACKET_TIMEOUT_MS);
        int delay = positive(intervalMs, DEFAULT_PACKET_INTERVAL_MS);
        JSArray latencies = new JSArray();
        int received = 0;
        String lastErrorCode = null;
        String lastErrorMessage = null;

        for (int index = 0; index < probeCount; index++) {
            ProbeResult probe = normalizedMode.equals("http") ? probeHttp(url, probeTimeout) : probeTcp(host, port, probeTimeout);

            if (probe.success) {
                received++;
                latencies.put(probe.durationMs);
            } else {
                lastErrorCode = probe.errorCode;
                lastErrorMessage = probe.errorMessage;
            }

            if (index < probeCount - 1) {
                sleep(delay);
            }
        }

        return packetLossResult(
            normalizedMode,
            normalizedMode.equals("http") ? url : host + ":" + port,
            probeCount,
            received,
            latencies,
            lastErrorCode,
            lastErrorMessage
        );
    }

    public JSObject runDiagnostics(
        Context context,
        JSONArray urls,
        JSONArray ports,
        JSONArray websockets,
        JSONObject download,
        JSONObject packetLoss
    ) {
        JSObject ret = new JSObject();
        JSArray urlResults = new JSArray();
        JSArray portResults = new JSArray();
        JSArray websocketResults = new JSArray();
        JSArray issues = new JSArray();
        JSObject status = getNetworkStatus(context);

        ret.put("status", status);
        addStatusIssues(status, issues);

        for (int index = 0; index < urls.length(); index++) {
            JSONObject options = urls.optJSONObject(index);
            if (options == null) {
                continue;
            }
            JSObject result = testUrl(
                options.optString("url", ""),
                options.optString("method", "HEAD"),
                options.optInt("timeoutMs", DEFAULT_URL_TIMEOUT_MS),
                options.optBoolean("followRedirects", true)
            );
            urlResults.put(result);
            addUrlIssue(result, issues);
        }

        for (int index = 0; index < ports.length(); index++) {
            JSONObject options = ports.optJSONObject(index);
            if (options == null) {
                continue;
            }
            JSObject result = testPort(
                options.optString("host", ""),
                options.optInt("port", 0),
                options.optInt("timeoutMs", DEFAULT_PORT_TIMEOUT_MS)
            );
            portResults.put(result);
            addPortIssue(result, issues);
        }

        for (int index = 0; index < websockets.length(); index++) {
            JSONObject options = websockets.optJSONObject(index);
            if (options == null) {
                continue;
            }
            JSObject result = testWebSocket(options.optString("url", ""), options.optInt("timeoutMs", DEFAULT_URL_TIMEOUT_MS));
            websocketResults.put(result);
            addWebSocketIssue(result, issues);
        }

        ret.put("urls", urlResults);
        ret.put("ports", portResults);
        ret.put("websockets", websocketResults);

        if (download != null) {
            JSObject result = testDownloadSpeed(
                download.optString("url", ""),
                download.optInt("maxBytes", DEFAULT_DOWNLOAD_MAX_BYTES),
                download.optInt("timeoutMs", DEFAULT_DOWNLOAD_TIMEOUT_MS)
            );
            ret.put("download", result);
            if (!result.optBoolean("ok", false)) {
                issues.put("Download speed test failed: " + result.optString("url", ""));
            }
        }

        if (packetLoss != null) {
            JSObject result = testPacketLoss(
                packetLoss.optString("mode", ""),
                packetLoss.optString("host", ""),
                packetLoss.optInt("port", 0),
                packetLoss.optString("url", ""),
                packetLoss.optInt("count", DEFAULT_PACKET_COUNT),
                packetLoss.optInt("timeoutMs", DEFAULT_PACKET_TIMEOUT_MS),
                packetLoss.optInt("intervalMs", DEFAULT_PACKET_INTERVAL_MS)
            );
            ret.put("packetLoss", result);
            if (result.optDouble("lossPercent", 0) > 0) {
                issues.put("Packet loss detected: " + result.optDouble("lossPercent", 0) + "% to " + result.optString("target", ""));
            }
        }

        ret.put("issues", issues);
        return ret;
    }

    private void addStatusIssues(JSObject status, JSArray issues) {
        if (!status.optBoolean("connected", false)) {
            issues.put("No active network connection");
        } else if (!status.optBoolean("internetReachable", false)) {
            issues.put("Network is connected but internet reachability is not confirmed");
        }
        if (status.optBoolean("captivePortal", false)) {
            issues.put("Captive portal detected");
        }
    }

    private void addUrlIssue(JSObject result, JSArray issues) {
        if (!result.optBoolean("reachable", false)) {
            issues.put("URL unreachable: " + result.optString("url", ""));
        } else if (!result.optBoolean("ok", false)) {
            issues.put("URL returned non-success status: " + result.optString("url", ""));
        }
    }

    private void addPortIssue(JSObject result, JSArray issues) {
        if (!result.optBoolean("open", false)) {
            issues.put("TCP port blocked or unreachable: " + result.optString("host", "") + ":" + result.optInt("port", 0));
        }
    }

    private void addWebSocketIssue(JSObject result, JSArray issues) {
        if (!result.optBoolean("open", false)) {
            issues.put("WebSocket failed: " + result.optString("url", ""));
        }
    }

    @SuppressWarnings("deprecation")
    private String fallbackConnectionType(ConnectivityManager connectivityManager) {
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            return "none";
        }
        switch (networkInfo.getType()) {
            case ConnectivityManager.TYPE_WIFI:
                return "wifi";
            case ConnectivityManager.TYPE_MOBILE:
                return "cellular";
            case ConnectivityManager.TYPE_ETHERNET:
                return "ethernet";
            case ConnectivityManager.TYPE_VPN:
                return "vpn";
            default:
                return "other";
        }
    }

    private String connectionType(NetworkCapabilities capabilities) {
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return "vpn";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "wifi";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "cellular";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ethernet";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return "other";
        }
        return "unknown";
    }

    private String transports(NetworkCapabilities capabilities) {
        StringBuilder builder = new StringBuilder();
        appendTransport(builder, capabilities, NetworkCapabilities.TRANSPORT_WIFI, "wifi");
        appendTransport(builder, capabilities, NetworkCapabilities.TRANSPORT_CELLULAR, "cellular");
        appendTransport(builder, capabilities, NetworkCapabilities.TRANSPORT_ETHERNET, "ethernet");
        appendTransport(builder, capabilities, NetworkCapabilities.TRANSPORT_VPN, "vpn");
        appendTransport(builder, capabilities, NetworkCapabilities.TRANSPORT_BLUETOOTH, "bluetooth");
        return builder.toString();
    }

    private void appendTransport(StringBuilder builder, NetworkCapabilities capabilities, int transport, String label) {
        if (!capabilities.hasTransport(transport)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(",");
        }
        builder.append(label);
    }

    private ProbeResult probeTcp(String host, int port, int timeoutMs) {
        long started = System.nanoTime();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return ProbeResult.success(elapsedMs(started));
        } catch (Exception exception) {
            return ProbeResult.failure(elapsedMs(started), errorCode(exception), exception.getMessage());
        }
    }

    private ProbeResult probeHttp(String url, int timeoutMs) {
        JSObject result = testUrl(url, "HEAD", timeoutMs, true);
        boolean reachable = result.optBoolean("reachable", false);
        return reachable
            ? ProbeResult.success(result.optLong("durationMs", 0))
            : ProbeResult.failure(
                  result.optLong("durationMs", 0),
                  result.optString("errorCode", "ERROR"),
                  result.optString("errorMessage", "")
              );
    }

    private JSObject packetLossResult(
        String mode,
        String target,
        int sent,
        int received,
        JSArray latencies,
        String errorCode,
        String errorMessage
    ) {
        int lost = sent - received;
        JSObject ret = new JSObject();
        ret.put("mode", mode);
        ret.put("target", target);
        ret.put("sent", sent);
        ret.put("received", received);
        ret.put("lost", lost);
        ret.put("lossPercent", sent == 0 ? 0 : (lost * 100.0) / sent);

        if (latencies.length() > 0) {
            long total = 0;
            long min = Long.MAX_VALUE;
            long max = 0;
            for (int index = 0; index < latencies.length(); index++) {
                long latency = latencies.optLong(index, 0);
                total += latency;
                min = Math.min(min, latency);
                max = Math.max(max, latency);
            }
            ret.put("averageLatencyMs", total / (double) latencies.length());
            ret.put("minLatencyMs", min);
            ret.put("maxLatencyMs", max);
        }

        if (errorCode != null) {
            ret.put("errorCode", errorCode);
        }
        if (errorMessage != null) {
            ret.put("errorMessage", errorMessage);
        }

        return ret;
    }

    private String normalizeMethod(String method) {
        return "GET".equalsIgnoreCase(method) ? "GET" : "HEAD";
    }

    private String normalizePacketLossMode(String mode, String host, int port, String url) {
        if ("http".equalsIgnoreCase(mode)) {
            return "http";
        }
        if ("tcp".equalsIgnoreCase(mode)) {
            return "tcp";
        }
        if (host != null && !host.isEmpty() && port > 0) {
            return "tcp";
        }
        if (url != null && !url.isEmpty()) {
            return "http";
        }
        return "tcp";
    }

    private String webSocketHandshake(URI uri, String host, int port, String scheme) {
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            path += "?" + uri.getRawQuery();
        }

        byte[] nonce = new byte[16];
        RANDOM.nextBytes(nonce);
        String key = Base64.encodeToString(nonce, Base64.NO_WRAP);
        String hostHeader = isDefaultWebSocketPort(scheme, port) ? host : host + ":" + port;

        return (
            "GET " +
            path +
            " HTTP/1.1\r\n" +
            "Host: " +
            hostHeader +
            "\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: " +
            key +
            "\r\n" +
            "Sec-WebSocket-Version: 13\r\n\r\n"
        );
    }

    private int parseStatusCode(String statusLine) {
        if (statusLine == null) {
            return 0;
        }
        String[] parts = statusLine.split(" ");
        if (parts.length < 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private int defaultWebSocketPort(String scheme) {
        return scheme.equals("wss") ? 443 : 80;
    }

    private boolean isDefaultWebSocketPort(String scheme, int port) {
        return port == defaultWebSocketPort(scheme);
    }

    private long elapsedMs(long started) {
        return Math.round((System.nanoTime() - started) / 1_000_000.0);
    }

    private String errorCode(Exception exception) {
        if (exception instanceof SocketTimeoutException) {
            return "TIMEOUT";
        }
        if (exception instanceof UnknownHostException) {
            return "DNS_ERROR";
        }
        if (exception instanceof ConnectException) {
            return "CONNECTION_FAILED";
        }
        if (exception instanceof SSLException) {
            return "TLS_ERROR";
        }
        if (exception instanceof IllegalArgumentException) {
            return "INVALID_ARGUMENT";
        }
        return exception.getClass().getSimpleName();
    }

    private int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }

    private void sleep(int intervalMs) {
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static class ProbeResult {

        final boolean success;
        final long durationMs;
        final String errorCode;
        final String errorMessage;

        private ProbeResult(boolean success, long durationMs, String errorCode, String errorMessage) {
            this.success = success;
            this.durationMs = durationMs;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        static ProbeResult success(long durationMs) {
            return new ProbeResult(true, durationMs, null, null);
        }

        static ProbeResult failure(long durationMs, String errorCode, String errorMessage) {
            return new ProbeResult(false, durationMs, errorCode, errorMessage);
        }
    }
}
