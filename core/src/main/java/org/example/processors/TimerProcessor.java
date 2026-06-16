package org.example.processors;

import org.example.reporters.MetricsReporter;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Map;

public class TimerProcessor implements InvocationHandler {

    private final Object target;

    private final Map<Class<? extends Annotation>, MetricsReporter> reporters;

    public TimerProcessor(Object target, Map<Class<? extends Annotation>, MetricsReporter> reporters) {
        this.target = target;
        this.reporters = reporters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());

        for (Annotation annotation : targetMethod.getAnnotations()) {
            var reporter = reporters.get(annotation.annotationType());

            if (reporter != null) {
                long start = System.nanoTime();
                Object result = method.invoke(target, args);
                long durationMs = (System.nanoTime() - start) / 1_000_000;

                reporter.report(annotation, method.getName(), durationMs);
                return result;
            }
        }

        return method.invoke(target, args);
    }
}