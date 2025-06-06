package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class CanvasBuffer {
    private final ConcurrentHashMap<Point, String> canvasState = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CanvasChange> pendingChanges = new ConcurrentLinkedQueue<>(); //szybsza od CopyOnWriteArrayList

    public void applyChange(CanvasChange change) {
        Point p = new Point(change.getX(), change.getY(), change.getColor());
        if (change.getType().equals("DRAW")) {
            canvasState.put(p, change.getColor());
        } else if (change.getType().equals("ERASE")) {
            canvasState.remove(p);
        }
        pendingChanges.add(change);
    }

    public ConcurrentHashMap<Point, String> getCanvasState() {
        return canvasState;
    }

    public List<CanvasChange> getPendingChanges() {
        List<CanvasChange> changes = new ArrayList<>(pendingChanges);
        pendingChanges.clear();
        return changes;
    }
}