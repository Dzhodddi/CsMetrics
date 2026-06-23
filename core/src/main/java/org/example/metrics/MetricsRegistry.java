package org.example.metrics;

import org.example.dtos.MetricDto;
import org.example.tcp.TcpMetricClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class MetricsRegistry {
    private static final int MAX_ELEMENTS = 5;

    private final ConcurrentMap<MetricKey, AtomicReference<MetricBucket>> metrics = new ConcurrentHashMap<>();
    private final TcpMetricClient tcpClient;

    public MetricsRegistry(TcpMetricClient tcpClient) {
        this.tcpClient = tcpClient;
        Runtime.getRuntime().addShutdownHook(new Thread(this::flushAll));
    }

    public void record(String className, String methodName, long durationNs, boolean secured) {
        MetricKey key = new MetricKey(className, methodName, secured);

        AtomicReference<MetricBucket> bucketRef = metrics.computeIfAbsent(
                key, _ -> new AtomicReference<>(new MetricBucket())
        );

        MetricBucket currentBucket = bucketRef.get();
        currentBucket.timings.add(durationNs);
        int currentSize = currentBucket.count.incrementAndGet();

        if (currentSize >= MAX_ELEMENTS) {
            MetricBucket freshBucket = new MetricBucket();
            if (bucketRef.compareAndSet(currentBucket, freshBucket)) {
                flushBucketAsync(key, currentBucket);
            }
        }
    }

    private CompletableFuture<Void> flushBucketAsync(MetricKey key, MetricBucket bucket) {
        return CompletableFuture.runAsync(() -> {
            String env = System.getenv().getOrDefault("APP_ENV", "local");
            String host = resolveHostName();

            List<MetricDto> dtos = new ArrayList<>(bucket.count.get());
            for (Long durationNs : bucket.timings) {
                var dto = new MetricDto(
                        UUID.randomUUID(),
                        OffsetDateTime.now(),
                        env,
                        host,
                        key.className(),
                        key.methodName(),
                        durationNs,
                        null,
                        key.secured()
                );
                dtos.add(dto);
            }
            tcpClient.send(dtos);

            System.out.printf("[MetricsRegistry] Flushed %d metrics for %s#%s%n",
                    bucket.timings.size(), key.className(), key.methodName());
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

    private static String resolveHostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}