package org.example;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Server {
    static final int PORT = 5000;
    static final int WIDTH = 50, HEIGHT = 50;

    private static final int[][] board = new int[WIDTH][HEIGHT];
    private static final List<PrintWriter> clients = new CopyOnWriteArrayList<>();

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

            synchronized (board) {
                for (int x = 0; x < WIDTH; x++) {
                    for (int y = 0; y < HEIGHT; y++) {
                        if (board[x][y] != 0) {
                            out.println("DRAW " + x + " " + y);
                        }
                    }
                }
            }

            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("DRAW")) {
                    String[] parts = line.split(" ");
                    int x = Integer.parseInt(parts[1]);
                    int y = Integer.parseInt(parts[2]);

                    synchronized (board) {
                        board[x][y] = 1;
                    }

                    for (PrintWriter client : clients) {
                        client.println(line);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Błąd połączenia: " + e.getMessage());
        }
    }
}
