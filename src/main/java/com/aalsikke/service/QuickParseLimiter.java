package com.aalsikke.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KAN-372: at most MAX_PER_HOUR quick-adds per user in any rolling hour, so nobody burns the AI quota.
 * In memory: resets on restart, which is fine for a single Railway instance.
 */
@Component
public class QuickParseLimiter {

    static final int MAX_PER_HOUR = 30;
    private static final Duration WINDOW = Duration.ofHours(1);

    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    public boolean tryAcquire(String user) {
        return tryAcquire(user, Instant.now());
    }

    boolean tryAcquire(String user, Instant now) {
        Deque<Instant> times = calls.computeIfAbsent(user, key -> new ArrayDeque<>());
        synchronized (times) {
            Instant cutoff = now.minus(WINDOW);
            while (!times.isEmpty() && !times.peekFirst().isAfter(cutoff)) {
                times.pollFirst();
            }
            if (times.size() >= MAX_PER_HOUR) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}