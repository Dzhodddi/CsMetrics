package org.example;

import org.example.annotations.Timer;

public class DefaultDataService implements DataService {
    @Timer()
    @Override
    public void loadData() {
        System.out.println("Loading massive data...");
        try { Thread.sleep(2000); } catch (InterruptedException e) {}
    }

    @Override
    public void fastValidation() {
        System.out.println("Validating data...");
    }
}
