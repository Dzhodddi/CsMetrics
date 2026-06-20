package org.example.reporters;

import org.example.annotations.HttpRequestTimer;
import org.example.metrics.MetricsRegistry;

public class HttpMetricsReporter implements MetricsReporter<HttpRequestTimer> {

    private final MetricsRegistry registry;

    public HttpMetricsReporter(MetricsRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void report(HttpRequestTimer annotation, String methodName, double durationMs) {
        String key = String.format("\"%s\"", annotation.path());

        registry.record(key, durationMs);
    }
}