package org.example.service;

import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;

public class DefaultDataService implements DataService {

    @DbQueryTimer(dbName = "ALTGATE", queryAction = "get_all_users")
    @Override
    public void loadData() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException ignored) {}
        System.out.println("Data loaded from DB.");
    }

    @HttpRequestTimer(path = "/api/v1/validate")
    @Override
    public void fastValidation() {
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}
        System.out.println("Validation complete.");
    }
}
