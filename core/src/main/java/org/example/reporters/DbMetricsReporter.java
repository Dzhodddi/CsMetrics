package org.example.reporters;

import org.example.annotations.DbQueryTimer;
import org.example.metrics.MetricsRegistry;

public class DbMetricsReporter implements MetricsReporter<DbQueryTimer> {

    private final MetricsRegistry registry;

    public DbMetricsReporter(MetricsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void report(DbQueryTimer annotation, String javaMethodName, long durationNs) {
        String category = "DB: " + annotation.dbName();
        String action = annotation.queryAction();
        registry.record(category, action, durationNs, annotation.secured());
    }
}