package org.example;

import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanvasChange that = (CanvasChange) o;
        return x == that.x && y == that.y &&
                Objects.equals(type, that.type) &&
                Objects.equals(color, that.color);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, x, y, color);
    }
}