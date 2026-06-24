package org.example.proxies;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.example.processors.TimerProcessor;
import org.example.reporters.MetricsReporter;

public class ProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(
            T target,
            Class<T> interfaceType,
            Map<Class<? extends Annotation>, MetricsReporter> reporters) {

        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{ interfaceType },
                new TimerProcessor(target, reporters)
        );
    }
}