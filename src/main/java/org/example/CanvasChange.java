package org.example;

public class CanvasChange {
    private String type; // DRAW/ERASE
    private int x;
    private int y;
    private String color; // hex color
    private int size;

    public CanvasChange(String type, int x, int y, String color, int size) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.color = color;
        this.size = size;
    }

    // Gettery
    public String getType() { return type; }
    public int getX() { return x; }
    public int getY() { return y; }
    public String getColor() { return color; }
    public int getSize() { return size; }
}