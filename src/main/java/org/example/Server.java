package org.example;

import com.google.gson.Gson;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    static final int PORT = 5000;
    static final int WIDTH = 50, HEIGHT = 50;
    private static final ConcurrentHashMap<Point, String> board = new ConcurrentHashMap<>();
    private static final List<PrintWriter> clients = new CopyOnWriteArrayList<>();
    private static final Gson gson = new Gson();
    private static final ChangeBuffer changeBuffer = new ChangeBuffer();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Serwer nasłuchuje na porcie " + PORT);

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (changeBuffer.shouldFlush()) {
                        List<CanvasChange> changes = changeBuffer.getChanges();
                        String json = gson.toJson(changes);
                        synchronized (clients) {
                            for (PrintWriter client : clients) {
                                client.println(json);
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("Nowe połączenie: " + socket);
            new Thread(() -> handleClient(socket)).start();
        }
    }

    private static void handleClient(Socket socket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            clients.add(out);

            // Wysyłanie początkowego stanu tablicy
            synchronized (board) {
                List<CanvasChange> initialChanges = new ArrayList<>();
                for (Map.Entry<Point, String> entry : board.entrySet()) {
                    Point p = entry.getKey();
                    initialChanges.add(new CanvasChange("DRAW", p.x, p.y, entry.getValue(), 1));
                }
                if (!initialChanges.isEmpty()) {
                    out.println(gson.toJson(initialChanges));
                }
            }

            String line;
            while ((line = in.readLine()) != null) {
                try {
                    CanvasChange[] changes = gson.fromJson(line, CanvasChange[].class);
                    for (CanvasChange change : changes) {
                        if (isValidChange(change)) {
                            changeBuffer.addChange(change);
                            applyChangeToBoard(change);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Błąd przetwarzania zmiany: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Błąd połączenia: " + e.getMessage());
        } finally {
            clients.removeIf(writer -> writer.checkError());
        }
    }

    private static boolean isValidChange(CanvasChange change) {
        return change != null &&
                change.getX() >= 0 && change.getX() < WIDTH &&
                change.getY() >= 0 && change.getY() < HEIGHT &&
                (change.getType().equals("DRAW") || change.getType().equals("ERASE"));
    }

    private static void applyChangeToBoard(CanvasChange change) {
        Point p = new Point(change.getX(), change.getY(), change.getColor());
        if (change.getType().equals("DRAW")) {
            board.put(p, change.getColor());
        } else {
            board.remove(p);
        }
    }
}