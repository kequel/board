package org.example;

import com.google.gson.Gson;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List;

public class Client {
    static final int PORT = 5000;
    static final int WIDTH = 50, HEIGHT = 50;
    private static final Gson gson = new Gson();
    private static String currentColor = "#000000";
    private static final CanvasBuffer canvasBuffer = new CanvasBuffer();

    public static void main(String[] args) throws IOException {
        String host = JOptionPane.showInputDialog("Adres serwera:");
        Socket socket = new Socket(host, PORT);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        JFrame frame = new JFrame("Tablica");
        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                for (Map.Entry<Point, String> entry : canvasBuffer.getCanvasState().entrySet()) {
                    Point p = entry.getKey();
                    try {
                        g.setColor(Color.decode(entry.getValue()));
                        g.fillRect(p.x * 10, p.y * 10, 10, 10);
                    } catch (NumberFormatException e) {
                        g.setColor(Color.WHITE);
                        g.fillRect(p.x * 10, p.y * 10, 10, 10);
                    }
                }
            }
        };

        // Panel z przyciskami kolorów
        JPanel colorPanel = new JPanel();
        String[] colors = {"#000000", "#FF0000", "#0000FF", "#FFFF00", "#FFFFFF"};
        String[] colorNames = {"Czarny", "Czerwony", "Niebieski", "Żółty", "Gumka"};

        for (int i = 0; i < colors.length; i++) {
            JButton colorBtn = new JButton(colorNames[i]);
            final String color = colors[i];
            colorBtn.addActionListener(e -> currentColor = color);
            colorPanel.add(colorBtn);
        }

        panel.setPreferredSize(new Dimension(WIDTH * 10, HEIGHT * 10));

        panel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                handleDrawing(e);
            }
        });

        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                handleDrawing(e);
            }
        });

        frame.add(colorPanel, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        // Wątek wysyłający zmiany
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    List<CanvasChange> pending = canvasBuffer.getPendingChanges();
                    if (!pending.isEmpty()) {
                        out.println(gson.toJson(pending));
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Wątek odbierający zmiany
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    CanvasChange[] changes = gson.fromJson(line, CanvasChange[].class);
                    for (CanvasChange change : changes) {
                        canvasBuffer.applyChange(change);
                    }
                    panel.repaint();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void handleDrawing(MouseEvent e) {
        int x = e.getX() / 10;
        int y = e.getY() / 10;
        String type = currentColor.equals("#FFFFFF") ? "ERASE" : "DRAW";
        CanvasChange change = new CanvasChange(type, x, y, currentColor, 1);
        canvasBuffer.applyChange(change);
    }
}