package org.example.reporters;

import org.example.annotations.DbQueryTimer;

public class DbMetricsReporter implements MetricsReporter<DbQueryTimer> {
    @Override
    public void report(DbQueryTimer annotation, String methodName, long durationMs) {
        System.out.printf("Sending DB Metric: DB=%s, Query=%s, Time=%dms%n",
                annotation.dbName(), annotation.queryAction(), durationMs);
    }
}