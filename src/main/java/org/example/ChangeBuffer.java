package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeBuffer {
    private final Queue<CanvasChange> changes = new ConcurrentLinkedQueue<>();
    private final ReentrantLock lock = new ReentrantLock();
    private long lastFlushTime = System.currentTimeMillis();

    public void addChange(CanvasChange change) {
        lock.lock();
        try {
            changes.add(change);
        } finally {
            lock.unlock();
        }
    }

    public List<CanvasChange> getChanges() {
        lock.lock();
        try {
            List<CanvasChange> copy = new ArrayList<>(changes);
            changes.clear();
            lastFlushTime = System.currentTimeMillis();
            return copy;
        } finally {
            lock.unlock();
        }
    }

    public boolean shouldFlush() {
        return !changes.isEmpty() &&
                (System.currentTimeMillis() - lastFlushTime > 200 || changes.size() > 100);
    }
}