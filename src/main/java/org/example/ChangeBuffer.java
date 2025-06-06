package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeBuffer {
    private final Queue<CanvasChange> changes = new ConcurrentLinkedQueue<>();
    private long lastFlushTime = System.currentTimeMillis();

    public void addChange(CanvasChange change) {
        changes.add(change); // ConcurrentLinkedQueue jest thread-safe
    }

    public List<CanvasChange> getChanges() {
        List<CanvasChange> copy = new ArrayList<>();
        CanvasChange change;
        while ((change = changes.poll()) != null) {
            copy.add(change);
        }
        lastFlushTime = System.currentTimeMillis();
        return copy;
    }

    public boolean shouldFlush() {
        return !changes.isEmpty() &&
                (System.currentTimeMillis() - lastFlushTime > 500 || changes.size() > 200);
    }
}