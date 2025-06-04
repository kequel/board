package org.example;

import java.awt.*;

public abstract class DrawingTool {
    protected String color;
    protected int size;

    public DrawingTool(String color, int size) {
        this.color = color;
        this.size = size;
    }

    public abstract void draw(Graphics g, int x, int y);
    public abstract String getType();
}