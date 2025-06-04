package org.example;

import com.google.gson.Gson;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.HashSet;
import java.util.Set;

public class Client {
    static final int PORT = 5000;
    static final int WIDTH = 50, HEIGHT = 50;
    static Set<Point> drawnPoints = new HashSet<>();
    private static final Gson gson = new Gson();
    private static boolean isDrawing = false;

    public static void main(String[] args) throws IOException {
        String host = JOptionPane.showInputDialog("Adres serwera:"); //test: localhost
        Socket socket = new Socket(host, PORT);
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        JFrame frame = new JFrame("Tablica");
        JPanel panel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                for (Point p : drawnPoints) {
                    g.setColor(Color.BLACK);
                    g.fillRect(p.x * 10, p.y * 10, 10, 10);
                }
            }
        };

        panel.setPreferredSize(new Dimension(WIDTH * 10, HEIGHT * 10));

        //sledzenie myszki
        panel.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) {
                int x = e.getX() / 10;
                int y = e.getY() / 10;
                CanvasChange change = new CanvasChange("DRAW", x, y, "#000000", 1);
                out.println(gson.toJson(change));
            }
        });

        panel.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                isDrawing = true;
                int x = e.getX() / 10;
                int y = e.getY() / 10;
                CanvasChange change = new CanvasChange("DRAW", x, y, "#000000", 1);
                out.println(gson.toJson(change));
            }

            public void mouseReleased(MouseEvent e) {
                isDrawing = false;
            }
        });

        frame.add(panel);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    CanvasChange change = gson.fromJson(line, CanvasChange.class);
                    if (change.getType().equals("DRAW")) {
                        drawnPoints.add(new Point(change.getX(), change.getY()));
                        panel.repaint();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }
}