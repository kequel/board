package org.example;

import com.google.gson.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Klient : wysyła/odbiera skompresowane paczki CanvasChangeCompressed. */

public class Client {

    static final int PORT = 5000, WIDTH = 80, HEIGHT = 50, CELL = 10;

    private static final Gson gson = new Gson();

    private static String currentColor = "#000000";
    private static int    currentSize  = 1;

    private static final CanvasBuffer canvas = new CanvasBuffer();
    private static       String       myId;

    private static volatile CursorPosition myCursor;
    private static final ConcurrentHashMap<String, CursorPosition> remote = new ConcurrentHashMap<>();

    /* ----------------  MAIN  ---------------- */
    public static void main(String[] args) throws IOException {

        Socket s = new Socket(JOptionPane.showInputDialog("Adres serwera:"), PORT);
        PrintWriter out = new PrintWriter(s.getOutputStream(), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));

        //handshake
        JsonObject hello = JsonParser.parseString(in.readLine()).getAsJsonObject();
        myId = hello.get("userId").getAsString();

        //UI
        JFrame f = new JFrame("Tablica");
        JPanel board = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // piksele
                canvas.getCanvasState().forEach((p, col) -> {
                    g.setColor(Color.decode(col));
                    g.fillRect(p.x * CELL, p.y * CELL, CELL, CELL);
                });
                // mój kursor
                if (myCursor != null) drawCursor(g, myCursor, Color.GREEN, "Ja");
                // cudze kursory
                remote.values().forEach(cp -> drawCursor(g, cp, Color.RED, cp.userId));
            }
            private void drawCursor(Graphics g, CursorPosition cp, Color c, String label) {
                int px = cp.x * CELL, py = cp.y * CELL;
                g.setColor(c);
                g.drawOval(px, py, CELL, CELL);
                g.drawString(label, px + 2, py + 12);
            }
        };
        board.setPreferredSize(new Dimension(WIDTH * CELL, HEIGHT * CELL));

        //myszka
        board.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseDragged(MouseEvent e) { draw(e); }
            public void mouseMoved  (MouseEvent e) { updateCursor(e); }
        });
        board.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { draw(e); } });

        //toolbar
        JPanel tools = new JPanel();
        String[] colors = {"#000000","#FF0000","#0000FF","#FFFF00","#FFFFFF"};
        String[] names  = {"Czarny","Czerwony","Niebieski","Żółty","Gumka"};
        for (int i=0;i<colors.length;i++) {
            String c = colors[i];
            JButton b = new JButton(names[i]);
            b.addActionListener(e -> currentColor = c);
            tools.add(b);
        }
        JSlider size = new JSlider(1,5,1);
        size.addChangeListener(e -> currentSize = size.getValue());
        tools.add(new JLabel("Grubość:")); tools.add(size);
        JButton save = new JButton("JPG");
        save.addActionListener(e -> saveImage());
        tools.add(save);

        f.add(tools, BorderLayout.NORTH);
        f.add(board, BorderLayout.CENTER);
        f.pack(); f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); f.setVisible(true);

        //sender
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(40);

                    /* pakiet kursora */
                    if (myCursor != null)
                        out.println(gson.toJson(Collections.singletonMap("cursor", myCursor)));

                    /* paczka zmian */
                    List<CanvasChange> pend = canvas.getPendingChanges();
                    if (!pend.isEmpty())
                        out.println(gson.toJson(CanvasChangeCompressed.compress(pend)));
                }
            } catch (InterruptedException ignored) {}
        }, "sender").start();

        //receiver
        new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    JsonElement el = JsonParser.parseString(line);

                    //cursor
                    if (el.isJsonObject() && el.getAsJsonObject().has("cursor")) {
                        CursorPosition cp = gson.fromJson(el.getAsJsonObject().get("cursor"), CursorPosition.class);
                        if (!cp.userId.equals(myId)) remote.put(cp.userId, cp);
                        board.repaint();
                        continue;
                    }
                    if (!el.isJsonArray()) continue;
                    JsonArray arr = el.getAsJsonArray();
                    if (arr.size()==0) continue;

                    if (arr.get(0).getAsJsonObject().has("mask")) {
                        //CanvasChangeCompressed[]
                        for (CanvasChangeCompressed p :
                                gson.fromJson(line, CanvasChangeCompressed[].class))
                            p.decompress().forEach(canvas::applyChange);
                    } else {
                        //CanvasChange[]
                        for (CanvasChange ch : gson.fromJson(line, CanvasChange[].class))
                            canvas.applyChange(ch);
                    }
                    board.repaint();
                }
            } catch (IOException ex) { ex.printStackTrace(); }
        }, "receiver").start();
    }

    private static void draw(MouseEvent e) {
        int cx = e.getX() / CELL, cy = e.getY() / CELL;
        myCursor = new CursorPosition(myId, cx, cy);

        String type = currentColor.equals("#FFFFFF") ? "ERASE" : "DRAW";
        int half = currentSize / 2;

        for (int dx=-half; dx<=half; dx++)
            for (int dy=-half; dy<=half; dy++) {
                int x = cx + dx, y = cy + dy;
                if (x>=0 && x<WIDTH && y>=0 && y<HEIGHT)
                    canvas.applyChange(new CanvasChange(type, x, y, currentColor, currentSize));
            }
        ((JComponent) e.getSource()).repaint();
    }
    private static void updateCursor(MouseEvent e) {
        int gx = e.getX()/CELL, gy = e.getY()/CELL;
        if (gx>=0 && gx<WIDTH && gy>=0 && gy<HEIGHT) myCursor = new CursorPosition(myId, gx, gy);
    }
    private static void saveImage() {
        try {
            int scale=10;
            BufferedImage img = new BufferedImage(WIDTH*scale, HEIGHT*scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.WHITE); g.fillRect(0,0,img.getWidth(), img.getHeight());
            canvas.getCanvasState().forEach((p,col)->{
                g.setColor(Color.decode(col));
                g.fillRect(p.x*scale, p.y*scale, scale, scale);
            });
            g.dispose(); ImageIO.write(img,"jpg", new File("tablica.jpg"));
            JOptionPane.showMessageDialog(null,"Zapisano tablica.jpg");
        } catch (IOException ex) { ex.printStackTrace(); }
    }
}
