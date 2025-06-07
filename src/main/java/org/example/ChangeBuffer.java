package org.example;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bufor po stronie serwera – gromadzi pojedyncze CanvasChange,
 * a na żądanie zwraca paczki CanvasChangeCompressed.
 */
public class ChangeBuffer {

    private static final int  MAX_BATCH_SIZE   = 200;   // ile pikseli maks. w paczu
    private static final long MAX_BATCH_AGE_MS = 50;    // lub co najwyżej co 50 ms

    private final List<CanvasChange> changes = new ArrayList<>();
    private final ReentrantLock      lock    = new ReentrantLock();
    private long lastFlushTime = System.currentTimeMillis();

    public void addChange(CanvasChange change) {
        lock.lock();
        try { changes.add(change); }
        finally { lock.unlock(); }
    }

    /** Zwraca (i czyści) spakowane zmiany. */
    public List<CanvasChangeCompressed> getCompressedChanges() {
        lock.lock();
        try {
            List<CanvasChange> copy = new ArrayList<>(changes);
            changes.clear();
            lastFlushTime = System.currentTimeMillis();
            return CanvasChangeCompressed.compress(copy);
        } finally {
            lock.unlock();
        }
    }

    /** Czy już czas wysłać kolejną paczkę? */
    public boolean shouldFlush() {
        lock.lock();
        try {
            return !changes.isEmpty() &&
                    (System.currentTimeMillis() - lastFlushTime > MAX_BATCH_AGE_MS ||
                            changes.size() >= MAX_BATCH_SIZE);
        } finally {
            lock.unlock();
        }
    }
}
