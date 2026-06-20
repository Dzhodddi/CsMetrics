package org.example.reporters;

import org.example.annotations.DbQueryTimer;
import org.example.metrics.MetricsRegistry;

public class DbMetricsReporter implements MetricsReporter<DbQueryTimer> {

    private final MetricsRegistry registry;

    public DbMetricsReporter(MetricsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void report(DbQueryTimer annotation, String methodName, double durationMs) {
        String key = String.format("Db query for %s DB and \"%s\" queryAction",
                annotation.dbName(), annotation.queryAction());
        registry.record(key, durationMs);
    }
}