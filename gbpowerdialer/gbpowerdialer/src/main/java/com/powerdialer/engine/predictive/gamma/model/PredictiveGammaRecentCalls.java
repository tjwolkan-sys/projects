// com.powerdialer.engine.predictive.gamma.model.PredictiveGammaCalledQueue
package com.powerdialer.engine.predictive.gamma.model;

import com.powerdialer.model.Campaign;

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * RING BUFFER
 * Keeps the LAST N calls in a single structure (Last-N).
 */
public final class PredictiveGammaRecentCalls {

    private String snapshotId;
    private final int capacity;
    private final PredictiveGammaCallModel[] buf;
    private int head = 0;
    private int size = 0;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private int totalCallCount = 0;
    private int totalAbandonCallCount = 0;
    private int totalAbandonedByCustomer = 0;
    private int totalAbandonedBySystem = 0;

    public PredictiveGammaRecentCalls(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be > 0, got: " + capacity);
        this.capacity = capacity;
        this.buf = new PredictiveGammaCallModel[capacity];
    }

    /** Adds a completed call (includes it in the last N). */
    public void add(PredictiveGammaCallModel m) {
        lock.writeLock().lock();
        try {
            buf[head] = m;
            head = (head + 1) % capacity;
            if (size < capacity) size++;
            totalCallCount++;

            if (m.isAbandoned()) {
                totalAbandonCallCount++;
                if(m.getResultCode() == 78) totalAbandonedBySystem++;
                else totalAbandonedByCustomer++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * MADDE 1 — cevap anında pencereye alınmış bir kaydı sonradan abandon olarak damgalar.
     *
     * <p>Neden gerekli: {@link #add} abandon sayaçlarını EKLEME anında artırır. Cevaplanan çağrı
     * artık answer anında ({@code abandoned=false}) giriyor; hangup'ta abandon olduğu anlaşılırsa
     * sayaç bir daha artmazdı. Damga, kayıt pencereden düşmeden gelmelidir.
     *
     * <p>Kimlik taraması yalnız pencere boyu kadardır (O(capacity)) ve yalnız gerçekten abandon
     * olmuş, cevap anında örneklenmiş çağrılar için çalışır — abandon oranı düşük olduğu için
     * hangup hot path'ine anlamlı yük binmez. Kayıt pencereden düşmüşse sessizce false döner.
     *
     * <p>{@code resultCode} çağıran tarafından damga ÖNCESİNDE set edilmelidir; abandon'un
     * sistem/müşteri kırılımı ona bakar.
     *
     * @return sayaçlar güncellendiyse true
     */
    public boolean markAbandoned(PredictiveGammaCallModel target) {
        if (target == null) return false;
        lock.writeLock().lock();
        try {
            if (target.isAbandoned()) return false; // zaten sayıldı
            if (!containsIdentity(target)) return false; // pencereden düştü
            target.setAbandoned(true);
            totalAbandonCallCount++;
            if (target.getResultCode() == 78) totalAbandonedBySystem++;
            else totalAbandonedByCustomer++;
            return true;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Write lock altında çağrılmalıdır. */
    private boolean containsIdentity(PredictiveGammaCallModel target) {
        for (int i = 0; i < size; i++) {
            int idx = (head - size + i + capacity) % capacity;
            if (buf[idx] == target) return true;
        }
        return false;
    }

    public void addForPrePopulation(PredictiveGammaCallModel m) {
        lock.writeLock().lock();
        try {
            buf[head] = m;
            head = (head + 1) % capacity;
            if (size < capacity) size++;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /** Returns the last N calls. */
    public PredictiveGammaSnapshot snapshot(Campaign campaign) {
        lock.readLock().lock();
        try {
            if (snapshotId == null) {
                snapshotId = PredictiveGammaSnapshot.generateSnapshotId(campaign);
            }
            return new PredictiveGammaSnapshot(snapshotId, buf, head, size, capacity, totalCallCount, totalAbandonCallCount, totalAbandonedByCustomer, totalAbandonedBySystem);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try { return size; }
        finally { lock.readLock().unlock(); }
    }

    public int getCapacity() {
        return capacity;
    }

    public int getTotalCallCount() {
        lock.readLock().lock();
        try { return totalCallCount; }
        finally { lock.readLock().unlock(); }
    }
}
