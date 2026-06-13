package com.ksefhelper.validation;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ValidatorCapacityLimiter {
    private final Semaphore permits;
    private final Duration acquireTimeout;
    private final int maximum;
    private final AtomicInteger active = new AtomicInteger();

    public ValidatorCapacityLimiter(int maximum, Duration acquireTimeout) {
        if (maximum <= 0) {
            throw new IllegalArgumentException("Validator concurrency must be positive.");
        }
        if (acquireTimeout.isNegative() || acquireTimeout.isZero()) {
            throw new IllegalArgumentException("Validator capacity timeout must be positive.");
        }
        this.maximum = maximum;
        this.permits = new Semaphore(maximum, true);
        this.acquireTimeout = acquireTimeout;
    }

    public Lease acquire() throws IOException, InterruptedException {
        if (!permits.tryAcquire(acquireTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IOException("FA(3) validator capacity is temporarily exhausted.");
        }
        active.incrementAndGet();
        return new Lease();
    }

    public int active() {
        return active.get();
    }

    public int maximum() {
        return maximum;
    }

    public final class Lease implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                active.decrementAndGet();
                permits.release();
            }
        }
    }
}
