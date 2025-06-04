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

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("Serwer nasłuchuje na porcie " + PORT);

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
                for (Map.Entry<Point, String> entry : board.entrySet()) {
                    Point p = entry.getKey();
                    CanvasChange change = new CanvasChange("DRAW", p.x, p.y, entry.getValue(), 1);
                    out.println(gson.toJson(change));
                }
            }

            String line;
            while ((line = in.readLine()) != null) {
                CanvasChange change = gson.fromJson(line, CanvasChange.class);
                Point p = new Point(change.getX(), change.getY(), change.getColor());

                synchronized (board) {
                    board.put(p, change.getColor());
                }

                for (PrintWriter client : clients) {
                    client.println(line);
                }
            }
        } catch (IOException e) {
            System.err.println("Błąd połączenia: " + e.getMessage());
        } finally {
            clients.removeIf(writer -> writer.checkError());
        }
    }
}