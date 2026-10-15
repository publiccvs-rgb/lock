package dev.lock.core.ipc;

import dev.lock.core.json.Json;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Launcher-side connection to a running Lock runtime. */
public final class RuntimeClient implements Closeable {
    private final RuntimeInfo info;
    private Socket socket;
    private BufferedReader in;
    private Writer out;

    public RuntimeClient(RuntimeInfo info) { this.info = info; }

    public RuntimeInfo info() { return info; }

    private void connect() throws IOException {
        if (socket != null && !socket.isClosed()) return;
        socket = new Socket();
        socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), info.port()), 1500);
        socket.setSoTimeout(5000);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
    }

    public synchronized Map<String, Object> send(String cmd, Map<String, Object> args) throws IOException {
        Map<String, Object> req = new LinkedHashMap<>();
        if (args != null) req.putAll(args);
        req.put("cmd", cmd);
        req.put("token", info.token());
        try {
            connect();
            out.write(Json.write(req));
            out.write('\n');
            out.flush();
            String line = in.readLine();
            if (line == null) throw new IOException("runtime closed the connection");
            return Json.parseObject(line);
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    @Override
    public synchronized void close() {
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        socket = null;
    }
}
