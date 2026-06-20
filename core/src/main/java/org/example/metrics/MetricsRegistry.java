package org.example.metrics;

import org.example.dtos.MetricDto;
import org.example.db.QueryExecutor;

import java.util.UUID;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class MetricsRegistry {
    private static final int MAX_ELEMENTS = 100;

    private final ConcurrentMap<MetricKey, AtomicReference<MetricBucket>> metrics = new ConcurrentHashMap<>();
    private final QueryExecutor<MetricDto> executor;

    public MetricsRegistry(QueryExecutor<MetricDto> executor) {
        this.executor = executor;

        Runtime.getRuntime().addShutdownHook(new Thread(this::flushAll));
    }

    public void record(String className, String methodName, double durationNanos) {
        MetricKey key = new MetricKey(className, methodName);

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

    private CompletableFuture<Void> flushBucketAsync(MetricKey key, MetricBucket bucketToFlush) {
        return CompletableFuture.runAsync(() -> {
            try {
                var size = bucketToFlush.timings.size();
                var ids = new UUID[size];
                var durationsNs = new Long[size];

                int index = 0;
                for (var durationNanos : bucketToFlush.timings) {
                    ids[index] = UUID.randomUUID();
                    durationsNs[index] = durationNanos.longValue();
                    index++;
                }

                var sql = """
                    INSERT INTO metrics (id, recorded_at, class_name, method_name, duration_ns) 
                    SELECT id_element, CURRENT_TIMESTAMP, :className, :methodName, duration_element 
                    FROM unnest(:ids, :durationsNs) AS t(id_element, duration_element)
                """;

                executor.execQuery(sql, Map.of(
                        "className", key.className(),
                        "methodName", key.methodName(),
                        "ids", ids,
                        "durationsNs", durationsNs
                ));

                System.out.println("Flushed " + size + " metrics for " + key.methodName());

            } catch (Exception e) {
                System.err.println("Failed to flush metrics to database for " + key);
                e.printStackTrace();
            }
        });
    }

    public void flushAll() {
        for (Map.Entry<MetricKey, AtomicReference<MetricBucket>> entry : metrics.entrySet()) {
            MetricBucket finalBucket = entry.getValue().get();
            if (!finalBucket.timings.isEmpty()) {
                flushBucketAsync(entry.getKey(), finalBucket).join();
            }
        }
    }
}