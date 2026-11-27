CREATE TABLE dlt_replay_audit (
    id VARCHAR(255) NOT NULL,
    source_topic VARCHAR(200) NOT NULL,
    partition_number INT NOT NULL,
    record_offset BIGINT NOT NULL,
    event_id VARCHAR(36) NOT NULL,
    status ENUM('REPLAYING', 'REPLAYED') NOT NULL,
    replayed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_dlt_replay_event_id (event_id)
);
