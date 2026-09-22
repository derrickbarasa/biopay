package com.biopay.utilities;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window rate limiter, process-local -- fine for biopay's single-instance
 * deployment; a multi-instance deployment behind a load balancer would need a shared store
 * (e.g. the database or Redis) instead. Keyed by a caller-supplied string so different call
 * sites can scope by IP, user id, or any other identity without knowing about each other.
 *
 * <p>Keys are never evicted, only the hit history within each key trims itself -- acceptable at
 * biopay's scale (bounded set of IPs/users hitting a handful of rate-limited actions), but would
 * need a cleanup pass if this were reused somewhere with a very high-cardinality key space.
 */
public final class RateLimiter {

    private static final Map<String, Deque<Long>> HITS = new ConcurrentHashMap<>();

    private RateLimiter() {
    }

    /** Returns true and records a hit if {@code key} is under {@code maxAttempts} within the
     *  trailing {@code windowMs}; returns false (without recording) once the limit is hit. */
    public static boolean allow(String key, int maxAttempts, long windowMs) {
        long now = System.currentTimeMillis();
        Deque<Long> hits = HITS.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (hits) {
            while (!hits.isEmpty() && now - hits.peekFirst() > windowMs) {
                hits.pollFirst();
            }
            if (hits.size() >= maxAttempts) {
                return false;
            }
            hits.addLast(now);
            return true;
        }
    }
}
