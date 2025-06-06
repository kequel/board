package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    static final int PORT = 5000;
    static final int WIDTH = 50, HEIGHT = 50;
    private static final ConcurrentHashMap<Point, String> board = new ConcurrentHashMap<>();
    private static final List<BufferedOutputStream> clients = new CopyOnWriteArrayList<>();
    private static final ChangeBuffer changeBuffer = new ChangeBuffer();

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Serwer nasłuchuje na porcie " + PORT);

        // Wątek wysyłający zmiany do klientów
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (changeBuffer.shouldFlush()) {
                        List<CanvasChange> changes = changeBuffer.getChanges();
                        if (!changes.isEmpty()) {
                            byte[] data = serializeChanges(changes);
                            synchronized (clients) {
                                for (BufferedOutputStream client : clients) {
                                    client.write(data);
                                    client.flush();
                                }
                            }
                        }
                    }
                } catch (InterruptedException | IOException e) {
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
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            synchronized (clients) {
                clients.add(out);
            }

            // Wysyłanie początkowego stanu tablicy
            synchronized (board) {
                List<CanvasChange> initialChanges = new ArrayList<>();
                for (Map.Entry<Point, String> entry : board.entrySet()) {
                    Point p = entry.getKey();
                    initialChanges.add(new CanvasChange("DRAW", p.x, p.y, entry.getValue(), 1));
                }
                if (!initialChanges.isEmpty()) {
                    out.write(serializeChanges(initialChanges));
                    out.flush();
                }
            }

            byte[] buffer = new byte[4];
            while (in.read(buffer) != -1) {
                CanvasChange change = deserializeChange(buffer);
                if (isValidChange(change)) {
                    changeBuffer.addChange(change);
                    synchronized (board) {
                        applyChangeToBoard(change);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Błąd połączenia: " + e.getMessage());
        } finally {
            synchronized (clients) {
                clients.removeIf(client -> {
                    try {
                        client.flush();
                        return false;
                    } catch (IOException e) {
                        return true;
                    }
                });
            }
        }
    }

    private static byte[] serializeChanges(List<CanvasChange> changes) {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        for (CanvasChange change : changes) {
            byteStream.write(change.getX());
            byteStream.write(change.getY());
            byteStream.write(colorToByte(change.getColor()));
            byteStream.write(change.getType().equals("DRAW") ? 0x01 : 0x00);
        }
        return byteStream.toByteArray();
    }

    private static CanvasChange deserializeChange(byte[] data) {
        int x = data[0] & 0xFF;
        int y = data[1] & 0xFF;
        String color = byteToColor(data[2]);
        String type = data[3] == 0x01 ? "DRAW" : "ERASE";
        return new CanvasChange(type, x, y, color, 1);
    }

    private static byte colorToByte(String color) {
        switch (color) {
            case "#FF0000": return 0x01;
            case "#0000FF": return 0x02;
            case "#FFFF00": return 0x03;
            case "#000000": return 0x04;
            default: return 0x00;
        }
    }

    private static String byteToColor(byte b) {
        switch (b) {
            case 0x01: return "#FF0000";
            case 0x02: return "#0000FF";
            case 0x03: return "#FFFF00";
            case 0x04: return "#000000";
            default: return "#FFFFFF";
        }
    }

    private static boolean isValidChange(CanvasChange change) {
        return change != null &&
                change.getX() >= 0 && change.getX() < WIDTH &&
                change.getY() >= 0 && change.getY() < HEIGHT;
    }

    private static void applyChangeToBoard(CanvasChange change) {
        Point p = new Point(change.getX(), change.getY(), change.getColor());
        board.compute(p, (key, oldValue) ->
                change.getType().equals("DRAW") ? change.getColor() : null
        );
    }
}