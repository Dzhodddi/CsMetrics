package org.example.service;

import org.example.annotations.DbQueryTimer;

public class DefaultDataService implements DataService {

    @DbQueryTimer(dbName = "load some data", queryAction = "load some data")
    @Override
    public void loadData() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {}
        System.out.println("Data loaded from DB");
    }

    @Override
    public void fastValidation() {
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}
        System.out.println("Validation complete");
    }
}
