package org.example.reporters;

import org.example.annotations.DbQueryTimer;
import org.example.metrics.MetricsRegistry;

public class DbMetricsReporter implements MetricsReporter<DbQueryTimer> {

    private final MetricsRegistry registry;

    public DbMetricsReporter(MetricsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void report(DbQueryTimer annotation, String javaMethodName, long durationMs) {
        String category = "DB: " + annotation.dbName();
        String action = annotation.queryAction();
        long durationNanos = durationMs * 1_000_000L;
        registry.record(category, action, durationNanos, annotation.secured());
    }
}