package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class ChangeBuffer {
    private final List<CanvasChange> changes = new ArrayList<>();
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
//                (System.currentTimeMillis() - lastFlushTime > 100 || changes.size() > 50); //przyszlosciowo - gdy wieksza macierz
                (System.currentTimeMillis() - lastFlushTime > 1);
    }
}