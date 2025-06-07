package org.example;

import java.util.*;

/**
 * Kompaktowa reprezentacja paczki zmian.
 *  ─ type   – "DRAW" / "ERASE"
 *  ─ (x,y)  – lewy-górny róg obszaru
 *  ─ width  / height
 *  ─ color  – hex koloru
 *  ─ mask   – ciąg bitów w porządku wierszowym (1 ⇒ zmieniony piksel)
 *
 *  Dzięki masce wysyłamy 1 bit zamiast 3 × byte RGB + metadata dla każdego piksela.
 */
public class CanvasChangeCompressed {

    private String type;
    private int    x, y, width, height;
    private String color;
    private String mask;          // row-major, length == width*height

    public CanvasChangeCompressed() {}                        // dla GSON-a

    public CanvasChangeCompressed(String type, int x, int y,
                                  int width, int height,
                                  String color, String mask) {
        this.type   = type;
        this.x      = x;
        this.y      = y;
        this.width  = width;
        this.height = height;
        this.color  = color;
        this.mask   = mask;
    }

    /* ----------------  API  ---------------- */

    /** Rozbija skompresowaną paczkę na listę pojedynczych CanvasChange. */
    public List<CanvasChange> decompress() {
        List<CanvasChange> out = new ArrayList<>();
        char[] bits = mask.toCharArray();
        for (int i = 0; i < bits.length; i++) {
            if (bits[i] == '1') {
                int dx =  i % width;
                int dy = (i / width);
                out.add(new CanvasChange(type, x + dx, y + dy, color, 1));
            }
        }
        return out;
    }

    /** Z listy pojedynczych zmian buduje zoptymalizowane paczki. */
    public static List<CanvasChangeCompressed> compress(List<CanvasChange> changes) {
        List<CanvasChangeCompressed> result = new ArrayList<>();
        if (changes == null || changes.isEmpty()) return result;

        // 1️⃣ Grupujemy według (type+color) – użytkownik rysuje jednym kolorem naraz.
        Map<String, List<CanvasChange>> groups = new HashMap<>();
        for (CanvasChange ch : changes) {
            String key = ch.getType() + '|' + ch.getColor();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(ch);
        }

        // 2️⃣ Dla każdej grupy tworzymy paczkę.
        for (List<CanvasChange> group : groups.values()) {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;

            for (CanvasChange ch : group) {
                minX = Math.min(minX, ch.getX());
                minY = Math.min(minY, ch.getY());
                maxX = Math.max(maxX, ch.getX());
                maxY = Math.max(maxY, ch.getY());
            }

            int w = maxX - minX + 1;
            int h = maxY - minY + 1;
            char[] bits = new char[w * h];
            Arrays.fill(bits, '0');

            for (CanvasChange ch : group) {
                int idx = (ch.getY() - minY) * w + (ch.getX() - minX);
                bits[idx] = '1';
            }

            CanvasChange first = group.get(0);
            result.add(new CanvasChangeCompressed(
                    first.getType(), minX, minY, w, h,
                    first.getColor(), new String(bits)
            ));
        }
        return result;
    }

    /* -------- gettery potrzebne w kliencie/serwerze -------- */
    public String getType()   { return type; }
    public int    getX()      { return x; }
    public int    getY()      { return y; }
    public int    getWidth()  { return width; }
    public int    getHeight() { return height; }
    public String getColor()  { return color; }
    public String getMask()   { return mask; }
}
