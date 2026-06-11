package org.example;


import org.example.proxies.ProxyFactory;

public class Main {
    public static void main(String[] ignored) {
        DataService realService = new DefaultDataService();

        DataService proxyService = ProxyFactory.createProxy(realService, DataService.class);

        System.out.println("--- Calling loadData ---");
        proxyService.loadData();

        System.out.println("\n--- Calling fastValidation ---");
        proxyService.fastValidation();
    }
}
