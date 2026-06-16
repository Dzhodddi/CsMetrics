package org.example;

import org.example.annotations.DbQueryTimer;
import org.example.annotations.HttpRequestTimer;

class DefaultDataService implements DataService {

    @DbQueryTimer(dbName = "test", queryAction = "get_all_users")
    @Override
    public void loadData() {
        try { Thread.sleep(250); } catch (InterruptedException ignored) {}
        System.out.println("Data loaded from DB.");
    }

    @HttpRequestTimer(path = "/api/v1/validate")
    @Override
    public void fastValidation() {
        try { Thread.sleep(15); } catch (InterruptedException ignored) {}
        System.out.println("Validation complete.");
    }
}