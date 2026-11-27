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
- [Integrations](integrations/README.md)
- [Production Operations](docs/production-runbook.md)
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
│                     React UI (Vite)                         │
│              http://localhost:5173                           │
└────────────────────────┬────────────────────────────────────┘
                         │ Bearer Token (Google JWT)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                   Spring Boot App                           │
│                   http://localhost:9191                      │
│                                                             │
│   BotController → ChatClient → Gemini 3.1 Flash-Lite       │
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
│      conversation_summaries / outbox_events)                │
│                        │                                    │
│   Transactional Outbox Publisher                            │
│                        │                                    │
└────────────────────────┼────────────────────────────────────┘
                         ▼
              ┌──────────────────┐
              │  Apache Kafka    │
              │  (KRaft mode)    │
              │ ticket topics +  │
              │      DLTs        │
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

### Backend
| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.5 |
| AI Orchestration | Spring AI 1.1.x |
| LLM | Google Gemini (`gemini-3.1-flash-lite`) |
| Database | MySQL 8 |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + Google OAuth2 |
| Messaging | Apache Kafka 4.x (KRaft — no Zookeeper) |
| Email | Spring Mail + Thymeleaf HTML templates |
| API Docs | Springdoc OpenAPI (Swagger UI) |
| Build Tool | Maven |
| Java Version | Java 21 |

### Frontend
| Layer | Technology |
|---|---|
| Framework | React + Vite |
| Routing | React Router DOM |
| Language | JavaScript |
| Styling | CSS Variables (dark theme) |

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
- **React UI** — ChatGPT-style dark interface with sidebar, typing indicator, conversation history
- **Swagger UI** — auto-generated API documentation
- **Secret management** — all credentials loaded from environment variables, never hardcoded

---

## Project Structure

### Backend
```
src/
├── main/
│   ├── java/com/akshat/ai/help_desk_bot/
│   │   ├── config/
│   │   │   ├── AiConfig.java                    # ChatClient beans (main + summarization)
│   │   │   ├── CorsConfig.java                  # CORS configuration for UI
│   │   │   ├── KafkaConfig.java                 # Kafka topics + listener factory
│   │   │   ├── OpenApiConfig.java               # Versioned API metadata + JWT scheme
│   │   │   └── SecurityConfig.java              # OAuth2 + JWT validation
│   │   ├── controller/
│   │   │   ├── BotController.java               # Main chat endpoint
│   │   │   ├── TicketController.java             # Versioned REST integration API
│   │   │   └── DevAuthController.java           # Dev-only Google login helper
│   │   ├── dto/
│   │   │   ├── ChatResponse.java                # Chat API response DTO
│   │   │   ├── TicketRequest.java               # Ticket creation DTO
│   │   │   └── TicketUpdateRequest.java         # Ticket update DTO
│   │   ├── entity/
│   │   │   ├── Ticket.java                      # Ticket JPA entity
│   │   │   ├── ChatMessage.java                 # Chat history entity
│   │   │   ├── Conversation.java                # Conversation metadata entity
│   │   │   └── ConversationSummary.java         # Summarization entity
│   │   ├── enums/
│   │   │   ├── Status.java                      # OPEN, IN_PROGRESS, RESOLVED, CLOSED
│   │   │   └── Priority.java                    # LOW, MEDIUM, HIGH, URGENT
│   │   ├── event/
│   │   │   ├── TicketEvent.java                 # Versioned Kafka event DTO
│   │   │   └── producer/OutboxPublisherJob.java  # Publishes committed outbox events
│   │   ├── event/consumer/
│   │   │   └── TicketEventConsumer.java          # Retries and deduplicates notifications
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
│               ├── ticket-created.html
│               ├── ticket-updated.html
│               └── ticket-resolved.html
├── .env                                         # Local secrets (never commit)
├── .gitignore
├── docker-compose.yml                           # Local MySQL + single-node Kafka
├── docker-compose.prod.yml                      # Hardened app + rate-limited ingress
├── integrations/                                # Reusable JS/TS client and MCP server
├── monitoring/                                  # Prometheus scrape and alert rules
└── docs/production-runbook.md                   # Migration and operations guide
```

### Frontend
```
novadesk-ui/
├── app/
│   ├── routes/
│   │   ├── _index.jsx          # Landing page with Google SSO button
│   │   └── chat.jsx            # Main chat interface
│   ├── components/
│   │   ├── ChatMessage.jsx     # Message bubble component
│   │   ├── ChatInput.jsx       # Input box + send button
│   │   └── Sidebar.jsx         # Conversation history sidebar
│   ├── utils/
│   │   └── api.js              # All backend API calls
│   ├── styles/
│   │   └── global.css          # Dark theme CSS variables
│   └── main.jsx                # React app entry point
├── index.html
├── vite.config.ts
└── package.json
```

---

## Prerequisites

### Backend
- Java 21+
- Maven 3.8+
- Docker Engine with Docker Compose
- A Google Cloud project with OAuth2 credentials
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/app/apikey)
- A Gmail account with an App Password generated

### Frontend
- Node.js 20+
- npm

---

## Setup & Configuration

### 1. Start local infrastructure (MySQL + Kafka)

Docker Compose runs both services with persistent named volumes. The included
credentials are for local development only; override them with a local `.env`
file before starting the services if needed.

```bash
docker compose up -d
docker compose ps
```

The backend connects to MySQL at `localhost:3307` using the `helpdesk` application
user and to Kafka at `localhost:9093`. To stop the services without deleting
their data, run `docker compose down`. To intentionally remove local database
and Kafka data as well, run `docker compose down -v`.

The database and Kafka addresses and credentials can be overridden with
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and `KAFKA_BOOTSTRAP_SERVERS` when
running the backend. Compose also accepts `MYSQL_PORT`, `MYSQL_DATABASE`,
`MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, and `KAFKA_PORT`; these
must match the backend settings when overridden.

### 2. Google OAuth2

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Create a project → APIs & Services → Credentials → OAuth 2.0 Client ID
3. Add authorized redirect URIs:
    - `http://localhost:9191/v1/auth/callback`
4. Copy Client ID and Client Secret

### 3. Gemini API key

Create a Gemini API key in [Google AI Studio](https://aistudio.google.com/app/apikey).
This key is separate from the Google OAuth client ID and secret used for
sign-in.

### 4. Gmail App Password

1. Go to [myaccount.google.com](https://myaccount.google.com)
2. Search **"App Passwords"**
3. Type a name (e.g. `helpdesk-bot`) → Click **Create**
4. Copy the 16-character password — remove spaces when pasting

### 5. Backend Environment Variables

Set these variables in the backend process or IDE run configuration. Docker
Compose's `.env` file is used for Compose interpolation; Spring Boot does not
automatically load it.

```bash
# Database
DB_URL=jdbc:mysql://localhost:3307/helpdesk_bot
DB_USERNAME=helpdesk
DB_PASSWORD=helpdesk_local
KAFKA_BOOTSTRAP_SERVERS=localhost:9093

# Gemini API (separate from Google OAuth credentials)
GEMINI_API_KEY=your_gemini_api_key

# Google OAuth2
GOOGLE_CLIENT_ID=your_google_client_id
GOOGLE_CLIENT_SECRET=your_google_client_secret
ALLOWED_DOMAIN=your-company.com

# Mail
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=yourapppasword

# App
AUTH_REDIRECT_URI=http://localhost:9191/v1/auth/callback
SUPPORT_TEAM_EMAIL=your-email@gmail.com
```

### 6. `application.properties`

Database and Kafka settings support environment overrides. Local defaults match
the Docker Compose services; credentials for external services should be
provided through the deployment platform's secret manager:

```properties
# Server
server.port=9191
spring.profiles.active=dev

# Database
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update

# LLM — Google Gemini
spring.ai.google.genai.api-key=${GEMINI_API_KEY}
spring.ai.google.genai.chat.options.model=gemini-3.1-flash-lite
spring.ai.google.genai.chat.options.max-output-tokens=2048
spring.ai.google.genai.chat.options.temperature=0.3

# Google OAuth2 — GOOGLE_CLIENT_ID is also the required JWT audience
spring.security.oauth2.client.registration.google.client-id=${GOOGLE_CLIENT_ID}
spring.security.oauth2.client.registration.google.client-secret=${GOOGLE_CLIENT_SECRET}
spring.security.oauth2.client.registration.google.scope=openid,email,profile
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://www.googleapis.com/oauth2/v3/certs
app.security.allowed-domain=${ALLOWED_DOMAIN}

# Kafka
spring.kafka.bootstrap-servers=localhost:9093
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.properties.spring.json.add.type.headers=true
spring.kafka.consumer.group-id=helpdesk-email-group
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.properties.spring.json.trusted.packages=com.akshat.ai.help_desk_bot.event
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
app.auth.redirect-uri=${AUTH_REDIRECT_URI}
app.mail.from=${MAIL_USERNAME}
app.mail.support-team=${SUPPORT_TEAM_EMAIL}

# Swagger
springdoc.api-docs.path=/v3/api-docs
springdoc.swagger-ui.path=/swagger-ui.html
springdoc.swagger-ui.enabled=true

# Logging
logging.level.org.springframework.ai.chat.client.advisor=DEBUG
logging.level.com.akshat.ai.help_desk_bot=DEBUG
```

### 7. Frontend Environment Variables

Create `.env` in `novadesk-ui/`:

```bash
VITE_API_URL=http://localhost:9191
```

---

## Running the Application

### Backend

```bash
# Start Kafka
docker compose up -d

# Build and run
mvn clean install
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Frontend

```bash
cd novadesk-ui
npm install
npm run dev
```

Open `http://localhost:5173` — click **Continue with Google** to log in.

---

## API Reference

Swagger UI: `http://localhost:9191/swagger-ui.html`

### Chat

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/v1/ai/chat?prompt={message}` | Send a message to the bot |
| `GET` | `/v1/ai/memory` | View raw chat messages for a conversation (debug) |
| `GET` | `/v1/ai/conversations` | Get all conversations for the authenticated user |
| `GET` | `/v1/ai/conversations/{id}/messages` | Get all messages in a conversation |
| `GET` | `/v1/ai/me` | Get authenticated user info from JWT |

### Versioned ticket API

The authenticated REST contract is versioned under `/v1/tickets` and documented
in OpenAPI at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. All ticket
operations are scoped to the Google identity in the bearer token; clients cannot
submit an owner identity.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/v1/tickets?page=0&size=20` | List the caller's tickets (paged, newest first) |
| `POST` | `/v1/tickets` | Create a ticket for the caller |
| `GET` | `/v1/tickets/{id}` | Get one of the caller's tickets |
| `PATCH` | `/v1/tickets/{id}` | Update fields on one of the caller's tickets |

Create requests use `title`, `description`, and optional `priority`; update
requests accept `title`, `description`, `priority`, `status`, and `assignee`.
Responses omit internal owner identifiers. Requests must include a Google ID
token in the `Authorization: Bearer <token>` header.

Operator routes are separately allowlisted by the Google `sub` values in
`OPS_ADMIN_SUBJECTS`: `GET /v1/admin/outbox/dead` lists dead events and
`POST /v1/admin/outbox/dead/{eventId}/replay` requeues one after investigation.
The response omits serialized payloads.

## Integrations

Other projects can use the JavaScript/TypeScript Fetch client, connect through
the MCP stdio server, or generate a client from the deployed OpenAPI contract.
See [integration install and configuration](integrations/README.md).

## Production operations

Flyway owns schema migrations, Actuator/Micrometer exposes health and Prometheus
metrics, and Micrometer Tracing exports OTLP traces. The production Compose file
expects external managed MySQL and Kafka services; the local Compose services
are development-only single-node infrastructure. Deployment, baseline,
backups, alerts, rate limiting, and event replay are covered in the
[production runbook](docs/production-runbook.md).

### Reliable ticket notifications

Ticket changes and notification events are written to the same database
transaction. The outbox publisher retries failed Kafka sends with bounded
exponential backoff and moves exhausted events to `DEAD` for operator review.
Kafka listener failures are retried and then routed to the corresponding
`<topic>.DLT` topic. Successful notifications are deduplicated by event ID.

Delivery is at-least-once: a process crash after an email provider accepts a
message but before the processed-event record commits can result in a duplicate
email. SMTP delivery and a database transaction cannot provide end-to-end
exactly-once behavior.

### Auth (Dev profile only)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/v1/auth/login` | Redirects to Google login |
| `GET` | `/v1/auth/callback` | Handles OAuth2 callback, redirects to UI with token |

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

NovaDesk uses Google-issued ID tokens. The Spring Boot app acts as a **resource server** and validates the token on each protected API request.

All `/v1/ai/**` endpoints require `Authorization: Bearer <Google ID token>`.
The backend verifies the token signature against Google's JWKS, issuer, expiration,
audience (`GOOGLE_CLIENT_ID`), verified email, and configured `ALLOWED_DOMAIN`.
The local `/v1/auth/login` and callback helper is available only with the `dev`
Spring profile; production deployments must use their own trusted Google sign-in
flow and send the resulting Google ID token as a bearer token.

Ticket and conversation ownership is keyed by Google's stable `sub` claim, not
the email or username. Existing rows have no verified subject mapping and are
intentionally inaccessible until an operator backfills `owner_subject` using a
trusted identity mapping; do not infer ownership from user-provided values.

### Flow

```
1. User clicks "Continue with Google" on the UI landing page
2. Redirects to GET /v1/auth/login → Google login page
3. Google redirects to GET /v1/auth/callback with auth code
4. Backend exchanges code for id_token
5. Backend redirects to http://localhost:5173/?token=<id_token>
6. UI stores token in localStorage
7. All subsequent API calls use Authorization: Bearer <id_token>
```

### Getting a Token (Postman/Dev)

```
1. Open browser → http://localhost:9191/v1/auth/login
2. Sign in with your Google account
3. Token appears in URL: http://localhost:5173/?token=eyJhbGci...
4. Copy token → use as Bearer token in Postman
```

### Domain Restriction

Only emails from `ALLOWED_DOMAIN` are accepted. All others receive `401 Unauthorized`.

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
Ticket + outbox event commit together
        ↓
OutboxPublisherJob → Kafka topic
        ↓
TicketEventConsumer (record ack, bounded retries)
        ↓
EmailService (Thymeleaf HTML rendering)
        ↓
Gmail SMTP → Employee inbox
```

Email address is taken from the employee's Google JWT `email` claim automatically — no manual input needed.
Events exceeding the retry budget remain in the `DEAD` outbox state for operator
review; consumer failures are routed to each source topic's `.DLT` topic.

---

## Memory & Conversation Management

Every message is persisted to `chat_messages` with `conversationId`, `role`, and `content`. On each request the bot receives:

1. A **summary** of older messages (if conversation is long)
2. The **last 6 messages** in full

This keeps token usage flat regardless of conversation length.

### Summarization

After every 10 messages, a dedicated summarization `ChatClient` (no memory advisor) compresses older history into a rolling summary stored in `conversation_summaries`. Injected as a `SystemMessage` on subsequent requests.

### Cleanup

A scheduled job runs daily at midnight deleting messages older than 30 days.

---

## Ticket Management

### Fields

| Field | Description | Default |
|---|---|---|
| `id` | Auto-generated | Never set manually |
| `username` | From authenticated JWT — never from user input | Required |
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

Before creating a ticket, the bot checks for an existing ticket with the same `username` and `title` created in the last 30 seconds. If found, returns the existing ticket instead of creating a duplicate.

### DTOs

All tool interactions use clean DTOs — never raw entity objects:

- `TicketRequest` — for creation (username, title, description, status, priority, assignee)
- `TicketUpdateRequest` — for updates (title, description, status, priority, assignee)

Date fields (`createdAt`, `updatedAt`) are always set by `@PrePersist`/`@PreUpdate` hooks — never by the model.

---

## Known Limitations

- Google `id_token` expires in **1 hour** — re-login required after expiry
- Gemini API quotas and pricing depend on your Google AI plan and region
- Gmail free tier: 500 emails/day — switch to SendGrid or AWS SES for production
- Summarization adds slight latency on every 10th message in a conversation
- `ConversationId` is client-provided — the React UI generates a UUID automatically per session
- Local Kafka uses one broker and replication factor 1; production requires a multi-broker cluster and replication factor 3
- Nginx request limits are local to one proxy process; use an API gateway with shared/distributed quotas when horizontally scaling ingress
- Email notifications are at-least-once; SMTP acceptance followed by a process crash can cause a duplicate