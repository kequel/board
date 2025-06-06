package org.example;

import java.util.Objects;

class Point {
    int x, y;
    String color;

    public Point(int x, int y, String color) {
        this.x = x;
        this.y = y;
        this.color = color;
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Point point = (Point) o;
        return x == point.x && y == point.y;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }
}
