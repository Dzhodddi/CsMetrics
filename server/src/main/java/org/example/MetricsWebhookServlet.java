package org.example;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import javax.sql.DataSource;

public class MetricsWebhookServlet extends HttpServlet {
    private final DataSource dataSource;

    public MetricsWebhookServlet(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        try (InputStream inputStream = req.getInputStream()) {
            byte[] binaryData = inputStream.readAllBytes();

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.getOutputStream().write("OK".getBytes());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
