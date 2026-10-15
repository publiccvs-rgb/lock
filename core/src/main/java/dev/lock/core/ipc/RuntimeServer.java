package dev.lock.core.ipc;

import dev.lock.core.json.Json;
import dev.lock.core.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Loopback-only, token-authenticated JSON-lines server run inside the game so the launcher can show
 * live status and keep module state in sync. One request per line, one response per line.
 */
public final class RuntimeServer {
    private final Function<Map<String, Object>, Map<String, Object>> handler;
    private final String token;
    private ServerSocket socket;
    private RuntimeInfo info;

    public RuntimeServer(Function<Map<String, Object>, Map<String, Object>> handler) {
        this.handler = handler;
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        this.token = HexFormat.of().formatHex(b);
    }

    public RuntimeInfo start(String mcVersion, String lockVersion) throws IOException {
        socket = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        info = new RuntimeInfo(ProcessHandle.current().pid(), socket.getLocalPort(), token, mcVersion, lockVersion, System.currentTimeMillis());
        info.write();
        Thread t = new Thread(this::acceptLoop, "Lock IPC");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Lock IPC shutdown"));
        return info;
    }

    public void stop() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        try {
            if (info != null) java.nio.file.Files.deleteIfExists(info.file());
        } catch (IOException ignored) {
        }
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try {
                Socket s = socket.accept();
                Thread t = new Thread(() -> serve(s), "Lock IPC client");
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!socket.isClosed()) Log.warn("IPC accept failed: " + e.getMessage());
            }
        }
    }

    private void serve(Socket s) {
        try (s; BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             Writer out = new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8)) {
            s.setSoTimeout(60_000);
            String line;
            while ((line = in.readLine()) != null) {
                if (line.length() > 4_000_000) break;
                Map<String, Object> resp;
                try {
                    Map<String, Object> req = Json.parseObject(line);
                    if (!token.equals(req.get("token"))) resp = error("unauthorized");
                    else resp = handler.apply(req);
                } catch (Exception e) {
                    resp = error(e.toString());
                }
                out.write(Json.write(resp));
                out.write('\n');
                out.flush();
            }
        } catch (IOException ignored) {
        }
    }

    public static Map<String, Object> error(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("error", msg);
        return m;
    }

    public static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }
}
