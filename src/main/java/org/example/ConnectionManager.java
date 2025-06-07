package org.example;

import com.google.gson.Gson;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

public class ConnectionManager {
    private final String host;
    private final int port;
    private final Gson gson = new Gson();

    private volatile Socket socket;
    private volatile PrintWriter out;
    private volatile BufferedReader in;
    private volatile boolean running = true;
    private String userId;          // otrzymane przy 1. połączeniu

    public ConnectionManager(String host, int port) {
        this.host = host;
        this.port = port;
        connectWithRetry();         // start od razu
        startHeartbeatThread();
    }

    /* -------------------------------- public API -------------------------------- */

    public PrintWriter getWriter() { return out; }
    public BufferedReader getReader() { return in; }
    public String getUserId()        { return userId; }

    /** Wywołaj, gdy pętla readera złapie IOException/EOF - spróbuje samo się odświeżyć. */
    public void reconnectAsync() { new Thread(this::connectWithRetry).start(); }

    /** Zatrzymaj całość (na wyjściu z aplikacji) */
    public void shutdown() {
        running = false;
        closeSilently();
    }

    /* ---------------------------------- internal -------------------------------- */

    private void connectWithRetry() {
        int attempt = 0;
        while (running) {
            try {
                socket = new Socket(host, port);
                out    = new PrintWriter(socket.getOutputStream(), true);
                in     = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // ---- handshake ----
                if (userId != null) {                       // reconnect
                    out.println("{\"reconnect\":\"" + userId + "\"}");
                }
                else {
                    out.println("{}");                      // ← DODANE: pusty JSON + \n
                }
                String hello = in.readLine();               // {"userId":"..."}
                if (hello != null && hello.contains("userId")) {
                    userId = gson.fromJson(hello, Hello.class).userId;
                }

                System.out.println("✓ Połączono jako userId=" + userId);
                return;                                     // sukces
            } catch (IOException e) {
                attempt++;
                int backoff = Math.min(30, 2 << attempt);   // exp back-off max 30 s
                System.err.println("Nie mogę się połączyć – próba " + attempt +
                        ". Następna za " + backoff + " s");
                sleepSeconds(backoff);
            }
        }
    }

    private void startHeartbeatThread() {
        new Thread(() -> {
            while (running) {
                try {
                    sleepSeconds(5);
                    if (out != null) out.println("{\"ping\":1}");
                } catch (Exception ignored) {}
            }
        }, "heartbeat").start();
    }

    private static void sleepSeconds(int sec) {
        try { TimeUnit.SECONDS.sleep(sec); } catch (InterruptedException ignored) {}
    }

    private void closeSilently() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
    }

    private static class Hello { String userId; }
}
