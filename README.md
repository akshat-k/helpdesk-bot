# NovaDesk — AI-Powered IT Help Desk Bot

An intelligent IT support agent built with Spring Boot and Spring AI that helps internal employees report issues, manage tickets, and resolve problems through a conversational interface.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Setup & Configuration](#setup--configuration)
- [Running the Application](#running-the-application)
- [API Reference](#api-reference)
- [Authentication](#authentication)
- [Email Notifications](#email-notifications)
- [Memory & Conversation Management](#memory--conversation-management)
- [Ticket Management](#ticket-management)
- [Known Limitations](#known-limitations)

---

## Overview

NovaDesk is a conversational IT helpdesk assistant that behaves like an experienced IT analyst. Instead of filling out forms, employees describe their issue in plain language and the bot handles ticket creation, updates, and lookups — always asking for confirmation before taking action.

```
Employee: "My VPN keeps failing since this morning, I can't access internal tools."

NovaDesk: "Here's what I'll log:
  Title       : VPN Authentication Failure
  Description : VPN has been repeatedly failing since this morning, blocking access to internal tools.
  Priority    : HIGH
  Status      : OPEN
  Assignee    : Unassigned

Shall I go ahead and raise this?"

Employee: "Yes"

NovaDesk: "Ticket #1042 has been raised. Our team will pick it up shortly."
```

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Client                               │
│              (Postman / Frontend / Mobile)                  │
└────────────────────────┬────────────────────────────────────┘
                         │ Bearer Token (Google JWT)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot App                           │
│                                                             │
│   BotController → ChatClient → LLM (Groq llama-3.3-70b)    │
│        │               │                                    │
│        │         Spring AI Advisors                         │
│        │         (Memory + Logger)                          │
│        │               │                                    │
│        ▼               ▼                                    │
│   ConversationService  DatabaseChatMemory                   │
│        │               │                                    │
│        ▼               ▼                                    │
│              MySQL Database                                 │
│     (tickets / chat_messages / conversations /              │
│      conversation_summaries)                                │
│                        │                                    │
│        TicketEventProducer                                  │
│                        │                                    │
└────────────────────────┼────────────────────────────────────┘
                         ▼
              ┌──────────────────┐
              │  Apache Kafka    │
              │  (ticket-events) │
              └────────┬─────────┘
                       ▼
              ┌──────────────────┐
              │ TicketEvent      │
              │ Consumer         │
              │ + EmailService   │
              │ (Thymeleaf HTML) │
              └────────┬─────────┘
                       ▼
              ┌──────────────────┐
              │  Gmail SMTP      │
              │  Employee Inbox  │
              └──────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5 |
| AI Orchestration | Spring AI 1.1.x |
| LLM | Groq (llama-3.3-70b-versatile) |
| Database | MySQL 8 |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + Google OAuth2 |
| Messaging | Apache Kafka (KRaft mode — no Zookeeper) |
| Email | Spring Mail + Thymeleaf HTML templates |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Build Tool | Maven |
| Java Version | Java 21 |

---

## Features

- **Conversational ticket creation** — employees describe issues in plain language
- **Mandatory confirmation flow** — bot always shows ticket summary before creating
- **Analyst behavior** — probes vague descriptions, infers priority, auto-generates titles
- **Full ticket lifecycle** — create, update, fetch by ID or username
- **Persistent conversation memory** — full chat history stored in MySQL per conversation
- **Conversation summarization** — older messages compressed to keep token usage flat
- **Google SSO authentication** — only verified company email accounts can access
- **Duplicate ticket prevention** — idempotency check before every create
- **Conversation metadata** — tracks all sessions per user with timestamps
- **Async email notifications** — Kafka-driven HTML emails on ticket create, update, resolve
- **Swagger UI** — auto-generated API documentation
- **Secret management** — all credentials loaded from environment variables, never hardcoded

---

## Project Structure

```
src/
├── main/
│   ├── java/com/akshat/ai/help_desk_bot/
│   │   ├── config/
│   │   │   ├── AiConfig.java                    # ChatClient beans (main + summarization)
│   │   │   ├── KafkaConfig.java                 # Kafka topics + listener factory
│   │   │   └── SecurityConfig.java              # OAuth2 + JWT validation
│   │   ├── controller/
│   │   │   ├── BotController.java               # Main chat endpoint
│   │   │   └── DevAuthController.java           # Dev-only Google login helper
│   │   ├── entity/
│   │   │   ├── Ticket.java                      # Ticket JPA entity
│   │   │   ├── ChatMessage.java                 # Chat history entity
│   │   │   ├── Conversation.java                # Conversation metadata entity
│   │   │   └── ConversationSummary.java         # Summarization entity
│   │   ├── enums/
│   │   │   ├── Status.java                      # OPEN, IN_PROGRESS, RESOLVED, CLOSED
│   │   │   └── Priority.java                    # LOW, MEDIUM, HIGH, URGENT
│   │   ├── event/
│   │   │   └── TicketEvent.java                 # Kafka event DTO
│   │   ├── kafka/
│   │   │   ├── TicketEventProducer.java         # Publishes events to Kafka topics
│   │   │   └── TicketEventConsumer.java         # Consumes events, triggers emails
│   │   ├── memory/
│   │   │   └── DatabaseChatMemory.java          # JPA-backed ChatMemory implementation
│   │   ├── repository/
│   │   │   ├── TicketRepository.java
│   │   │   ├── ChatMessageRepository.java
│   │   │   ├── ConversationRepository.java
│   │   │   └── ConversationSummaryRepository.java
│   │   ├── security/
│   │   │   └── JwtUserContext.java              # Extracts username/email from JWT
│   │   ├── service/
│   │   │   ├── HelpDeskService.java
│   │   │   ├── HelpDeskServiceImpl.java
│   │   │   ├── ConversationService.java
│   │   │   ├── ConversationSummarizationService.java
│   │   │   └── EmailService.java                # Thymeleaf email rendering + sending
│   │   └── tools/
│   │       └── BotTools.java                    # Spring AI @Tool definitions
│   └── resources/
│       ├── application.properties               # Config with ${ENV_VAR} placeholders
│       ├── helpdesk-system.st                   # System prompt for NovaDesk
│       └── templates/
│           └── email/
│               ├── ticket-created.html          # HTML email — ticket created
│               ├── ticket-updated.html          # HTML email — ticket updated
│               └── ticket-resolved.html         # HTML email — ticket resolved
├── .env                                         # Local secrets (never commit)
├── .gitignore                                   # Must include .env
└── docker-compose.yml                           # Kafka in KRaft mode
```

---

## Prerequisites

- Java 21+
- Maven 3.8+
- MySQL 8+
- Docker (for Kafka)
- A Google Cloud project with OAuth2 credentials
- A Groq API key — free at [console.groq.com](https://console.groq.com)
- A Gmail account with an App Password generated

---

## Setup & Configuration

### 1. Database

```sql
CREATE DATABASE helpdesk_bot;
```

Tables are auto-created by Hibernate on first run (`spring.jpa.hibernate.ddl-auto=update`).

### 2. Google OAuth2

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Create a project → APIs & Services → Credentials → OAuth 2.0 Client ID
3. Add authorized redirect URI: `http://localhost:9191/v1/auth/callback`
4. Copy Client ID and Client Secret

### 3. Gmail App Password

1. Go to [myaccount.google.com](https://myaccount.google.com)
2. Search **"App Passwords"**
3. Type a name (e.g. `helpdesk-bot`) → Click **Create**
4. Copy the 16-character password — remove spaces when pasting

### 4. Kafka (Docker)

```bash
docker-compose up -d
```

`docker-compose.yml` (KRaft mode — no Zookeeper required):

```yaml
version: '3.8'
services:
  kafka:
    image: confluentinc/cp-kafka:7.9.0
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: PLAINTEXT:PLAINTEXT,CONTROLLER:PLAINTEXT
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_INTER_BROKER_LISTENER_NAME: PLAINTEXT
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```

### 5. Environment Variables

Create a `.env` file in the project root — **never commit this file**:

```bash
# Database
DB_URL=jdbc:mysql://localhost:3306/helpdesk_bot
DB_USERNAME=root
DB_PASSWORD=yourpassword

# LLM
GROQ_API_KEY=your_groq_api_key

# Google OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret

# Mail
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=yourapppasword

# App
ALLOWED_DOMAIN=gmail.com
AUTH_REDIRECT_URI=http://localhost:9191/v1/auth/callback
SUPPORT_TEAM_EMAIL=your-email@gmail.com
```

### 6. `application.properties`

No secrets — all values come from environment variables:

```properties
# Server
server.port=9191
spring.profiles.active=dev

# Database
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update

# LLM — Groq
spring.ai.openai.api-key=${GROQ_API_KEY}
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile
spring.ai.openai.chat.options.max-tokens=500

# Google OAuth2
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,email,profile
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://www.googleapis.com/oauth2/v3/certs

# Kafka
spring.kafka.bootstrap-servers=localhost:9092
app.kafka.topics.ticket-created=ticket-created
app.kafka.topics.ticket-updated=ticket-updated
app.kafka.topics.ticket-resolved=ticket-resolved

# Mail
spring.mail.host=smtp.gmail.com
spring.mail.port=587
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
spring.mail.properties.mail.smtp.starttls.required=true

# App
app.security.allowed-domain=${ALLOWED_DOMAIN}
app.auth.redirect-uri=${AUTH_REDIRECT_URI}
app.mail.from=${MAIL_USERNAME}
app.mail.support-team=${SUPPORT_TEAM_EMAIL}

# Swagger
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true
```

---

## Running the Application

```bash
# Clone the repo
git clone https://github.com/your-org/help-desk-bot.git
cd help-desk-bot

# Start Kafka
docker-compose up -d

# Build
mvn clean install

# Run with dev profile (enables /v1/auth/login endpoint)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## API Reference

Swagger UI available at: `http://localhost:9191/swagger-ui.html`

### Chat

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/v1/ai/chat?prompt={message}` | Send a message to the bot |
| `GET` | `/v1/ai/memory` | View raw chat messages for a conversation (debug) |
| `GET` | `/v1/ai/conversations` | Get all conversations for the authenticated user |
| `GET` | `/v1/ai/conversations/{id}/messages` | Get all messages in a conversation |
| `GET` | `/v1/ai/me` | Get authenticated user info from JWT |

### Auth (Dev profile only)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/v1/auth/login` | Redirects to Google login |
| `GET` | `/v1/auth/callback` | Handles OAuth2 callback, returns `id_token` |

### Headers

```
Authorization : Bearer <google_id_token>
ConversationId: <any-uuid>              # Optional — generated if not provided
```

### Example Request (Postman)

```
POST http://localhost:9191/v1/ai/chat?prompt=My laptop won't connect to wifi

Headers:
  Authorization : Bearer eyJhbGci......
  ConversationId: 550e8400-e29b-41d4-a716-446655440000
```

---

## Authentication

NovaDesk uses Google OAuth2. Your Spring Boot app acts as a **resource server** — it never handles login directly. Google issues the token; the app only validates it.

### Getting a Token (Dev)

```
1. Open browser → http://localhost:9191/v1/auth/login
2. Sign in with your Google account
3. You receive a JSON response:
   {
     "id_token": "eyJhbGci......",
     "usage": "Use this as: Authorization: Bearer <id_token>"
   }
4. Use id_token as Bearer token in Postman — set under Authorization tab as Bearer Token
```

### Domain Restriction

Only emails from `ALLOWED_DOMAIN` are accepted. All others receive `401 Unauthorized`. Set this in your `.env` file to restrict access to your company email domain.

---

## Email Notifications

NovaDesk sends HTML emails asynchronously via Kafka when:

| Event | Email sent |
|---|---|
| Ticket created | Full ticket details — ID, title, description, priority, status, assignee |
| Ticket updated | Latest ticket details after the update |
| Ticket resolved | Confirmation with ticket ID and title |

### Flow

```
Ticket action in BotTools
        ↓
TicketEventProducer → Kafka topic
        ↓ (async — user gets API response immediately)
TicketEventConsumer
        ↓
EmailService (Thymeleaf HTML rendering)
        ↓
Employee inbox (Gmail SMTP)
```

Emails are sent to the address from the employee's Google JWT (`email` claim) automatically — no manual email input needed.

---

## Memory & Conversation Management

### How memory works

Every message is persisted to `chat_messages` with `conversationId`, `role`, and `content`. On each request the bot receives:

1. A **summary** of older messages (if conversation is long)
2. The **last 6 messages** in full

This keeps token usage flat regardless of conversation length.

### Summarization

After every 10 messages, a dedicated summarization `ChatClient` (no memory advisor) compresses older history into a rolling summary stored in `conversation_summaries`. This summary is injected as a `SystemMessage` on subsequent requests.

### Cleanup

A scheduled job runs daily at midnight deleting messages older than 30 days. Enable it by adding `@EnableScheduling` to your main application class.

---

## Ticket Management

### Fields

| Field | Description | Default |
|---|---|---|
| `id` | Auto-generated | Never set manually |
| `username` | From authenticated JWT | Required |
| `title` | Short summary | Auto-generated by bot |
| `description` | Full issue details | Required |
| `status` | Lifecycle state | `OPEN` |
| `priority` | Urgency level | Inferred by bot |
| `assignee` | Assigned engineer | `Unassigned` |

### Priority Levels

| Priority | When used |
|---|---|
| `LOW` | Cosmetic issues, minor inconveniences |
| `MEDIUM` | App issues, access requests, intermittent problems |
| `HIGH` | Work blocked, repeated failures |
| `URGENT` | Production outage, security breach, data loss |

### Lifecycle

```
OPEN → IN_PROGRESS → RESOLVED → CLOSED
```

### Duplicate Prevention

Before creating a ticket, the bot checks for an existing ticket with the same `username` and `title` created in the last 30 seconds. If found, it returns the existing ticket instead of creating a duplicate.

---

## Known Limitations

- Google `id_token` expires in **1 hour** — re-login required after expiry
- Groq free tier: 1,000 requests/day, 12,000 tokens/minute
- Gmail free tier: 500 emails/day — switch to SendGrid or AWS SES for production
- Summarization adds slight latency on every 10th message in a conversation
- `ConversationId` is client-provided — use a UUID generator on the frontend to ensure uniqueness
- Kafka runs with replication factor 1 in local setup — increase to 3 for production
