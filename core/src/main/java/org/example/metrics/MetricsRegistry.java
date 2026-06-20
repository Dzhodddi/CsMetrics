package org.example.metrics;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class MetricsRegistry {
    private static final int MAX_ELEMENTS = 100;

    private final ConcurrentMap<String, AtomicReference<MetricBucket>> metrics = new ConcurrentHashMap<>();

    public void record(String key, double durationNanos) {
        AtomicReference<MetricBucket> bucketRef = metrics.computeIfAbsent(
                key, _ -> new AtomicReference<>(new MetricBucket())
        );

        MetricBucket currentBucket = bucketRef.get();

        currentBucket.timings.add(durationNanos);
        int currentSize = currentBucket.count.incrementAndGet();

        if (currentSize >= MAX_ELEMENTS) {

            MetricBucket freshBucket = new MetricBucket();

            if (bucketRef.compareAndSet(currentBucket, freshBucket)) {
                flushBucketAsync(key, currentBucket);
            }
        }
    }

    private void flushBucketAsync(String uniqueKey, MetricBucket ignored) {
        CompletableFuture.runAsync(() -> System.out.println("Flushing bucket " + uniqueKey));
    }
}