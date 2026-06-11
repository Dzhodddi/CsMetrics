package org.example.proxies;

import org.example.processors.TimerProcessor;

import java.lang.reflect.Proxy;

public class ProxyFactory {

    @SuppressWarnings("unchecked")
    public static <T> T createProxy(T target, Class<T> interfaceType) {
        return (T) Proxy.newProxyInstance(
                interfaceType.getClassLoader(),
                new Class<?>[]{ interfaceType },
                new TimerProcessor(target)
        );
    }
}