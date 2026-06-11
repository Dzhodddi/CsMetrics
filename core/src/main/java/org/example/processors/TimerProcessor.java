package org.example.processors;

import org.example.annotations.Timer;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

public class TimerProcessor implements InvocationHandler {

    private final Object target;

    public TimerProcessor(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Method targetMethod = target.getClass().getMethod(method.getName(), method.getParameterTypes());

        if (targetMethod.isAnnotationPresent(Timer.class)) {
            long start = System.nanoTime();
            Object result = method.invoke(target, args);
            long end = System.nanoTime();
            System.out.println(String.format("%s took %d ms", method.getName(), (end - start) / 1_000_000));
            return result;
        }
        return method.invoke(target, args);
    }
}
