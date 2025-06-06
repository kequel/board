package org.example;

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
    private static String currentColor = "#000000";
    private static final CanvasBuffer canvasBuffer = new CanvasBuffer();

    public static void main(String[] args) throws IOException {
        String host = JOptionPane.showInputDialog("Adres serwera:");
        Socket socket = new Socket(host, PORT);
        BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
        BufferedInputStream in = new BufferedInputStream(socket.getInputStream());

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
                        byte[] data = serializeChanges(pending);
                        out.write(data);
                        out.flush();
                    }
                } catch (InterruptedException | IOException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();

        // Wątek odbierający zmiany
        new Thread(() -> {
            try {
                byte[] buffer = new byte[4];
                while (in.read(buffer) != -1) {
                    CanvasChange change = deserializeChange(buffer);
                    canvasBuffer.applyChange(change);
                    panel.repaint();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
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

    private static void handleDrawing(MouseEvent e) {
        int x = e.getX() / 10;
        int y = e.getY() / 10;
        String type = currentColor.equals("#FFFFFF") ? "ERASE" : "DRAW";
        CanvasChange change = new CanvasChange(type, x, y, currentColor, 1);
        canvasBuffer.applyChange(change);
    }
}