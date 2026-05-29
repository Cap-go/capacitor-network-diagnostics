package app.capgo.networkdiagnostics;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "NetworkDiagnostics")
public class NetworkDiagnosticsPlugin extends Plugin {

    private final NetworkDiagnostics implementation = new NetworkDiagnostics();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PluginMethod
    public void getNetworkStatus(PluginCall call) {
        executor.execute(() -> call.resolve(implementation.getNetworkStatus(getContext())));
    }

    @PluginMethod
    public void testUrl(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("URL is required");
            return;
        }

        String method = call.getString("method", "HEAD");
        Integer timeoutMs = call.getInt("timeoutMs", 10000);
        Boolean followRedirects = call.getBoolean("followRedirects", true);

        executor.execute(() ->
            call.resolve(
                implementation.testUrl(url, method, timeoutMs == null ? 10000 : timeoutMs, followRedirects == null || followRedirects)
            )
        );
    }

    @PluginMethod
    public void testPort(PluginCall call) {
        String host = call.getString("host");
        Integer port = call.getInt("port");
        if (host == null || host.isEmpty()) {
            call.reject("Host is required");
            return;
        }
        if (port == null || port <= 0 || port > 65535) {
            call.reject("Port must be between 1 and 65535");
            return;
        }

        Integer timeoutMs = call.getInt("timeoutMs", 5000);
        executor.execute(() -> call.resolve(implementation.testPort(host, port, timeoutMs == null ? 5000 : timeoutMs)));
    }

    @PluginMethod
    public void testWebSocket(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("URL is required");
            return;
        }

        Integer timeoutMs = call.getInt("timeoutMs", 10000);
        executor.execute(() -> call.resolve(implementation.testWebSocket(url, timeoutMs == null ? 10000 : timeoutMs)));
    }

    @PluginMethod
    public void testDownloadSpeed(PluginCall call) {
        String url = call.getString("url");
        if (url == null || url.isEmpty()) {
            call.reject("URL is required");
            return;
        }

        Integer maxBytes = call.getInt("maxBytes", 5 * 1024 * 1024);
        Integer timeoutMs = call.getInt("timeoutMs", 30000);
        executor.execute(() ->
            call.resolve(
                implementation.testDownloadSpeed(url, maxBytes == null ? 5 * 1024 * 1024 : maxBytes, timeoutMs == null ? 30000 : timeoutMs)
            )
        );
    }

    @PluginMethod
    public void testPacketLoss(PluginCall call) {
        String mode = call.getString("mode", "");
        String host = call.getString("host", "");
        String url = call.getString("url", "");
        Integer port = call.getInt("port", 0);
        Integer count = call.getInt("count", 10);
        Integer timeoutMs = call.getInt("timeoutMs", 3000);
        Integer intervalMs = call.getInt("intervalMs", 250);

        if ((host == null || host.isEmpty() || port == null || port <= 0) && (url == null || url.isEmpty())) {
            call.reject("Either host and port, or url is required");
            return;
        }

        executor.execute(() ->
            call.resolve(
                implementation.testPacketLoss(
                    mode == null ? "" : mode,
                    host == null ? "" : host,
                    port == null ? 0 : port,
                    url == null ? "" : url,
                    count == null ? 10 : count,
                    timeoutMs == null ? 3000 : timeoutMs,
                    intervalMs == null ? 250 : intervalMs
                )
            )
        );
    }

    @PluginMethod
    public void runDiagnostics(PluginCall call) {
        JSArray urls = call.getArray("urls", new JSArray());
        JSArray ports = call.getArray("ports", new JSArray());
        JSArray websockets = call.getArray("websockets", new JSArray());
        JSObject download = call.getObject("download");
        JSObject packetLoss = call.getObject("packetLoss");

        executor.execute(() -> call.resolve(implementation.runDiagnostics(getContext(), urls, ports, websockets, download, packetLoss)));
    }

    @PluginMethod
    public void getPluginVersion(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("version", implementation.getPluginVersion());
        call.resolve(ret);
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        executor.shutdownNow();
    }
}
