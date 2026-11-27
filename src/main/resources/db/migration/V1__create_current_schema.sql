CREATE TABLE tickets (
    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(255),
    owner_subject VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status ENUM('OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED') NOT NULL,
    priority ENUM('LOW', 'MEDIUM', 'HIGH', 'URGENT') NOT NULL,
    assignee VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT,
    PRIMARY KEY (id),
    INDEX idx_tickets_owner_subject (owner_subject)
);

CREATE TABLE conversations (
    id VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    owner_subject VARCHAR(255),
    started_at DATETIME(6) NOT NULL,
    last_active_at DATETIME(6) NOT NULL,
    status VARCHAR(255),
    PRIMARY KEY (id),
    INDEX idx_conversations_username (username),
    INDEX idx_conversations_owner_subject (owner_subject)
);

CREATE TABLE chat_messages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    role VARCHAR(255),
    content TEXT,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_messages_conversation_id (conversation_id),
    INDEX idx_chat_messages_username (username)
);

CREATE TABLE conversation_summaries (
    id BIGINT NOT NULL AUTO_INCREMENT,
    conversation_id VARCHAR(255) NOT NULL,
    summary TEXT NOT NULL,
    message_count INT,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_conversation_summaries_conversation_id UNIQUE (conversation_id)
);

CREATE TABLE outbox_events (
    id VARCHAR(36) NOT NULL,
    topic VARCHAR(200) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status ENUM('PENDING', 'PROCESSING', 'PUBLISHED', 'DEAD') NOT NULL,
    attempts INT NOT NULL,
    available_at DATETIME(6) NOT NULL,
    lock_until DATETIME(6),
    last_error VARCHAR(1000),
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6),
    PRIMARY KEY (id),
    INDEX idx_outbox_due (status, available_at, created_at)
);

CREATE TABLE processed_ticket_events (
    event_id VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id)
);
