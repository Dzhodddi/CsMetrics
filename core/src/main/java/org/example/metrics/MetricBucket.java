package org.example.metrics;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MetricBucket {
    public final ConcurrentLinkedQueue<Long> timings = new ConcurrentLinkedQueue<>();
    public final AtomicInteger count = new AtomicInteger(0);
}