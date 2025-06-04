package org.example;

import com.google.gson.Gson;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class Client {
    static final int PORT = 5000;
    static final int WIDTH = 50, HEIGHT = 50;
    static final ConcurrentHashMap<Point, String> drawnPoints = new ConcurrentHashMap<>();
    private static final Gson gson = new Gson();
    private static String currentColor = "#000000";

    public static void main(String[] args) throws IOException {
        String host = JOptionPane.showInputDialog("Adres serwera:");
        Socket socket = new Socket(host, PORT);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        JFrame frame = new JFrame("Tablica");
        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                for (Map.Entry<Point, String> entry : drawnPoints.entrySet()) {
                    Point p = entry.getKey();
                    g.setColor(Color.decode(entry.getValue()));
                    g.fillRect(p.x * 10, p.y * 10, 10, 10);
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
                handleDrawing(e, out);
            }
        });

        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                handleDrawing(e, out);
            }
        });

        frame.add(colorPanel, BorderLayout.NORTH);
        frame.add(panel, BorderLayout.CENTER);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    CanvasChange change = gson.fromJson(line, CanvasChange.class);
                    Point p = new Point(change.getX(), change.getY(), change.getColor());
                    if (change.getType().equals("DRAW")) {
                        drawnPoints.put(p, change.getColor());
                    }
                    panel.repaint();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void handleDrawing(MouseEvent e, PrintWriter out) {
        int x = e.getX() / 10;
        int y = e.getY() / 10;
        CanvasChange change = new CanvasChange("DRAW", x, y, currentColor, 1);
        out.println(gson.toJson(change));
    }
}