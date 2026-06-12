package org.example;

import org.example.utility.DatabaseConfig;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import javax.sql.DataSource;

public class Main {
    public static void main(String[] args) throws Exception {
        System.out.println("Connecting to database...");
        DataSource dataSource = DatabaseConfig.getDataSource();
        int port = 8080;
        Server server = new Server(port);

        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        MetricsWebhookServlet metricsServlet = new MetricsWebhookServlet(dataSource);
        context.addServlet(new ServletHolder(metricsServlet), "/api/v1/metrics");

        System.out.println("Starting Metrics Server on port " + port + "...");
        server.start();
        server.join();
    }
}
