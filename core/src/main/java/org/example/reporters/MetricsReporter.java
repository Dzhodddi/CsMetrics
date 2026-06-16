package org.example.reporters;

import java.lang.annotation.Annotation;

public interface MetricsReporter<T extends Annotation> {
    void report(T annotation, String methodName, long durationMs);
}