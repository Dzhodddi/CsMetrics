package org.example;


import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;
import org.example.metrics.MetricsRegistry;
import org.example.proxies.ProxyFactory;
import org.example.reporters.DbMetricsReporter;
import org.example.reporters.HttpMetricsReporter;
import org.example.reporters.MetricsReporter;

import java.lang.annotation.Annotation;
import java.util.Map;

public class Main {
    public static void main(String[] ignored) {
        MetricsRegistry registry = new MetricsRegistry();
        Map<Class<? extends Annotation>, MetricsReporter> reporters = Map.of(
                DbQueryTimer.class, new DbMetricsReporter(registry),
                HttpRequestTimer.class, new HttpMetricsReporter(registry)
        );

        DataService realService = new DefaultDataService();

        DataService proxyService = ProxyFactory.createProxy(
                realService,
                DataService.class,
                reporters
        );

        System.out.println("--- Calling loadData ---");
        proxyService.loadData();

        System.out.println("\n--- Calling fastValidation ---");
        proxyService.fastValidation();
    }
}