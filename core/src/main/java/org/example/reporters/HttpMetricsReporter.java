package org.example.reporters;

import org.example.annotations.HttpRequestTimer;

public class HttpMetricsReporter implements MetricsReporter<HttpRequestTimer> {
    @Override
    public void report(HttpRequestTimer annotation, String methodName, long durationMs) {
        System.out.printf("Sending HTTP Metric: Path=%s, Time=%dms%n", annotation.path(), durationMs);
    }
}
