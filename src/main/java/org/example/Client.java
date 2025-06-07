package org.example;

import com.google.gson.Gson;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.Map;

public class Client {
    static final int PORT = 5000;
    static final int WIDTH = 80, HEIGHT = 50;
    private static final Gson gson = new Gson();
    private static String currentColor = "#000000";
    private static int currentSize = 1;
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

        // Suwak do grubości
        JSlider sizeSlider = new JSlider(1, 5, 1);
        sizeSlider.setMajorTickSpacing(1);
        sizeSlider.setPaintTicks(true);
        sizeSlider.setPaintLabels(true);
        sizeSlider.addChangeListener(e -> currentSize = sizeSlider.getValue());
        colorPanel.add(new JLabel("Grubość:"));
        colorPanel.add(sizeSlider);

        // Przycisk do zapisu do JPG
        JButton saveButton = new JButton("Zapisz do JPG");
        saveButton.addActionListener(e -> {
            try {
                exportCanvasToImage("tablica.jpg");
                JOptionPane.showMessageDialog(frame, "Zapisano jako tablica.jpg");
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Błąd podczas zapisu", "Błąd", JOptionPane.ERROR_MESSAGE);
            }
        });
        colorPanel.add(saveButton);

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
        frame.setSize(
                Math.max(WIDTH * 10 + 16, colorPanel.getPreferredSize().width),
                HEIGHT * 10 + colorPanel.getPreferredSize().height + 60
        );

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
        int baseX = e.getX() / 10;
        int baseY = e.getY() / 10;
        String type = currentColor.equals("#FFFFFF") ? "ERASE" : "DRAW";
        int half = currentSize / 2;

        for (int dx = -half; dx <= half; dx++) {
            for (int dy = -half; dy <= half; dy++) {
                int x = baseX + dx;
                int y = baseY + dy;
                if (x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT) {
                    CanvasChange change = new CanvasChange(type, x, y, currentColor, currentSize);
                    canvasBuffer.applyChange(change);
                }
            }
        }

        ((JComponent) e.getSource()).repaint();
    }

    public static void exportCanvasToImage(String filename) throws IOException {
        int scale = 10;
        BufferedImage image = new BufferedImage(WIDTH * scale, HEIGHT * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, image.getWidth(), image.getHeight());

        for (Map.Entry<Point, String> entry : canvasBuffer.getCanvasState().entrySet()) {
            Point p = entry.getKey();
            g2d.setColor(Color.decode(entry.getValue()));
            g2d.fillRect(p.x * scale, p.y * scale, scale, scale);
        }

        g2d.dispose();
        ImageIO.write(image, "jpg", new File(filename));
    }
}
