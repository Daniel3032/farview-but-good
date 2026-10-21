package net.gensokyoreimagined.farview;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-wide byte budget shared by every session, on top of each player's own budget. It is debited by the same
 * wire meter as the per-player budget, so it counts all outbound bytes of farview players, and farview stops
 * dispatching fake chunks while that total runs above the cap. Real game traffic is never held back.
 */
final class GlobalRateLimiter {
    private static final long KB = 1024L;
    private static final double BURST_SECONDS = 0.25;
    private static final double MAX_DEBT_SECONDS = 2.0;

    private final AtomicLong tokenBytes = new AtomicLong();
    private final AtomicLong lastRefillNanos = new AtomicLong(System.nanoTime());
    private volatile long bytesPerSec;

    /** {@code kbps <= 0} disables the cap. */
    void configure(int kbps) {
        this.bytesPerSec = Math.max(0, kbps) * KB;
        this.tokenBytes.set(0);
        this.lastRefillNanos.set(System.nanoTime());
    }

    boolean enabled() {
        return bytesPerSec > 0;
    }

    boolean hasTokens() {
        if (!enabled()) return true;
        refill();
        return tokenBytes.get() > 0;
    }

    void onWireBytes(int bytes) {
        long rate = bytesPerSec;
        if (rate <= 0) return;
        long floor = -(long) (rate * MAX_DEBT_SECONDS);
        long after = tokenBytes.addAndGet(-bytes);
        if (after < floor) tokenBytes.compareAndSet(after, floor);
    }

    private void refill() {
        long rate = bytesPerSec;
        long now = System.nanoTime();
        long last = lastRefillNanos.get();
        long elapsed = now - last;
        if (elapsed <= 0 || !lastRefillNanos.compareAndSet(last, now)) return;
        long refill = (long) (rate * (elapsed / 1_000_000_000.0));
        long burst = Math.max(1L, (long) (rate * BURST_SECONDS));
        tokenBytes.updateAndGet(current -> Math.min(current + refill, burst));
    }
}
