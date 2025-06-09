package org.example;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Server {

    static final int PORT = 5000;
    static final int WIDTH = 80, HEIGHT = 50;

    private static final ConcurrentHashMap<Point, String> board = new ConcurrentHashMap<>();
    private static final List<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<String, CursorPosition> cursors = new ConcurrentHashMap<>();

    private static final Gson gson = new Gson();
    private static final ChangeBuffer changeBuffer = new ChangeBuffer();
    private static final AtomicInteger idSeq  = new AtomicInteger(1);

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(PORT);
        System.out.println("Serwer nasłuchuje na porcie " + PORT);

        // Wątek rozsyłający co okolo 20 ms zbuforowane paczki
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(20); }
                catch (InterruptedException ignored) {}

                if (changeBuffer.shouldFlush()) {
                    List<CanvasChangeCompressed> packets = changeBuffer.getCompressedChanges();
                    if (!packets.isEmpty()) broadcast(gson.toJson(packets));
                }
            }
        }, "flusher").start();

        while (true) new Thread(() -> handleClient(server)).start();
    }

    //Obsluga jednego klienta
    private static void handleClient(ServerSocket server) {
        try (Socket sock = server.accept();
             BufferedReader in  = new BufferedReader(new InputStreamReader(sock.getInputStream()));
             PrintWriter    out = new PrintWriter(sock.getOutputStream(), true)) {

            clients.add(out);
            String myId = String.valueOf(idSeq.getAndIncrement());
            out.println("{\"userId\":\"" + myId + "\"}"); // handshake

            //pełny stan początkowy w formie skompresowanej
            out.println(gson.toJson(CanvasChangeCompressed.compress(fullBoardAsChanges())));

            String line;
            while ((line = in.readLine()) != null) try {
                JsonElement elem = JsonParser.parseString(line);

                //pakiety kursora
                if (elem.isJsonObject() && elem.getAsJsonObject().has("cursor")) {
                    CursorPosition cp = gson.fromJson(elem.getAsJsonObject().get("cursor"), CursorPosition.class);
                    cursors.put(cp.userId, cp);
                    broadcast(gson.toJson(Collections.singletonMap("cursor", cp)));
                    continue;
                }

                //paczki zmian
                if (!elem.isJsonArray()) continue;
                JsonArray arr = elem.getAsJsonArray();
                if (arr.size() == 0) continue;

                //obecność pola mask => CanvasChangeCompressed
                if (arr.get(0).getAsJsonObject().has("mask")) {
                    CanvasChangeCompressed[] packets = gson.fromJson(line, CanvasChangeCompressed[].class);
                    for (CanvasChangeCompressed p : packets) apply(p.decompress());
                } else {
                    CanvasChange[] changes = gson.fromJson(line, CanvasChange[].class);
                    apply(Arrays.asList(changes));
                }
            } catch (Exception ex) {
                System.err.println("Błąd danych od klienta: " + ex);
            }
        } catch (IOException e) {
            System.err.println("Błąd połączenia: " + e);
        }
    }

    private static void broadcast(String msg) {
        synchronized (clients) {
            for (PrintWriter w : clients) w.println(msg);
        }
    }

    private static List<CanvasChange> fullBoardAsChanges() {
        List<CanvasChange> list = new ArrayList<>();
        for (Map.Entry<Point, String> e : board.entrySet())
            list.add(new CanvasChange("DRAW", e.getKey().x, e.getKey().y, e.getValue(), 1));
        return list;
    }

    private static void apply(Collection<CanvasChange> changes) {
        for (CanvasChange ch : changes) {
            if (!isValid(ch)) continue;
            changeBuffer.addChange(ch); // dla broadcastu
            Point p = new Point(ch.getX(), ch.getY(), ch.getColor());
            if ("DRAW".equals(ch.getType())) board.put(p, ch.getColor());
            else                              board.remove(p);
        }
    }

    private static boolean isValid(CanvasChange ch) {
        return ch != null &&
                ch.getX() >= 0 && ch.getX() < WIDTH &&
                ch.getY() >= 0 && ch.getY() < HEIGHT;
    }
}
