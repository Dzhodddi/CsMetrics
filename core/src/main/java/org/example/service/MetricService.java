package org.example.service;

import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.example.dtos.MetricDto;
import org.example.repository.MetricRepository;

public class MetricService {

    private static final Logger log = Logger.getLogger(MetricService.class.getName());
    private final MetricRepository repository;

    public MetricService(MetricRepository repository) {
        this.repository = repository;
    }

    public void recordMetric(MetricDto metric) {
        if (metric.className() != null && metric.className().equals(MetricRepository.class.getName())) {
            return;
        }

        try {
            repository.save(metric);
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Error recording metric " + metric, e);
        }
    }

    public List<MetricDto> getMetrics() {
        try {
            return repository.findAll();
        } catch (SQLException e) {
            log.log(Level.SEVERE, "Error reading metrics", e);
            return List.of();
        }
    }
}
