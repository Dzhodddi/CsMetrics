CREATE TABLE IF NOT EXISTS metrics (
                                       id UUID PRIMARY KEY,
                                       recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                       environment VARCHAR(50),
                                       host_name VARCHAR(100),
                                       class_name VARCHAR(255),
                                       method_name VARCHAR(255),
                                       duration_ns BIGINT,
                                       metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_metrics_recorded_at ON metrics (recorded_at DESC);
CREATE INDEX IF NOT EXISTS idx_metrics_method_time ON metrics (class_name, method_name, recorded_at DESC);