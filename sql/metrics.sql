CREATE TABLE IF NOT EXISTS metrics (
     id UUID PRIMARY KEY,
     recorded_at TIMESTAMP WITH TIME ZONE,
     environment VARCHAR(50),
     host_name VARCHAR(100),
     class_name VARCHAR(255),
     method_name VARCHAR(255),
     duration_ms BIGINT,
     metadata JSONB
);

CREATE INDEX IF NOT EXISTS idx_metrics_method ON metrics (class_name, method_name);

CREATE INDEX IF NOT EXISTS idx_metrics_time ON metrics (recorded_at);