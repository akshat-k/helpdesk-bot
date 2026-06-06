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
│   BotController → ChatClient → LLM (Groq / Gemini)         │
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
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
              ┌──────────────────┐
              │  Google OAuth2   │
              │  (JWT Validation)│
              └──────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 3.x |
| AI Orchestration | Spring AI 1.1.x |
| LLM | Groq (llama-3.3-70b-versatile) or Google Gemini |
| Database | MySQL |
| ORM | Spring Data JPA / Hibernate |
| Security | Spring Security + Google OAuth2 |
| Build Tool | Maven |
| Java Version | Java 21 |

---

## Features

- **Conversational ticket creation** — employees describe issues in plain language
- **Mandatory confirmation flow** — bot always shows ticket summary before creating
- **Analyst behavior** — probes vague descriptions, infers priority, auto-generates titles
- **Full ticket lifecycle** — create, update, fetch by ID or username
- **Persistent conversation memory** — full chat history stored in MySQL per conversation
- **Conversation summarization** — older messages are summarized to keep token usage flat
- **Google SSO authentication** — only verified company email accounts can access
- **Duplicate ticket prevention** — idempotency check before every create
- **Conversation metadata** — tracks all sessions per user with timestamps

---

## Project Structure

```
src/
├── main/
│   ├── java/com/akshat/ai/help_desk_bot/
│   │   ├── config/
│   │   │   ├── AiConfig.java              # ChatClient beans (main + summarization)
│   │   │   └── SecurityConfig.java        # OAuth2 + JWT validation
│   │   ├── controller/
│   │   │   ├── BotController.java         # Main chat endpoint
│   │   │   └── DevAuthController.java     # Dev-only Google login helper
│   │   ├── entity/
│   │   │   ├── Ticket.java                # Ticket JPA entity
│   │   │   ├── ChatMessage.java           # Chat history entity
│   │   │   └── Conversation.java          # Conversation metadata entity
│   │   ├── enums/
│   │   │   ├── Status.java                # OPEN, IN_PROGRESS, RESOLVED, CLOSED
│   │   │   └── Priority.java              # LOW, MEDIUM, HIGH, URGENT
│   │   ├── memory/
│   │   │   └── DatabaseChatMemory.java    # JPA-backed ChatMemory implementation
│   │   ├── repository/
│   │   │   ├── TicketRepository.java
│   │   │   ├── ChatMessageRepository.java
│   │   │   ├── ConversationRepository.java
│   │   │   └── ConversationSummaryRepository.java
│   │   ├── security/
│   │   │   └── JwtUserContext.java        # Extracts username/email from JWT
│   │   ├── service/
│   │   │   ├── HelpDeskService.java
│   │   │   ├── HelpDeskServiceImpl.java
│   │   │   ├── ConversationService.java
│   │   │   └── ConversationSummarizationService.java
│   │   └── tools/
│   │       └── BotTools.java              # Spring AI @Tool definitions
│   └── resources/
│       ├── application.properties
│       └── helpdesk-system.st             # System prompt for NovaDesk
```

---

## Prerequisites

- Java 21+
- Maven 3.8+
- MySQL 8+
- A Google Cloud project with OAuth2 credentials
- A Groq API key (free at [console.groq.com](https://console.groq.com))

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

### 3. `application.properties`

```properties
# Server
server.port=9191

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/helpdesk_bot
spring.datasource.username=your_db_user
spring.datasource.password=your_db_password
spring.jpa.hibernate.ddl-auto=update

# LLM — Groq
spring.ai.openai.api-key=your_groq_api_key
spring.ai.openai.base-url=https://api.groq.com/openai
spring.ai.openai.chat.options.model=llama-3.3-70b-versatile
spring.ai.openai.chat.options.max-tokens=500

# Google OAuth2
spring.security.oauth2.client.registration.google.client-id=your_google_client_id
spring.security.oauth2.client.registration.google.client-secret=your_google_client_secret
spring.security.oauth2.client.registration.google.scope=openid,email,profile
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://www.googleapis.com/oauth2/v3/certs

# Security
app.security.allowed-domain=yourcompany.com
app.auth.redirect-uri=http://localhost:9191/v1/auth/callback

# Active profile
spring.profiles.active=dev
```

---

## Running the Application

```bash
# Clone the repo
git clone https://github.com/your-org/help-desk-bot.git
cd help-desk-bot

# Build
mvn clean install

# Run with dev profile (enables /v1/auth/login endpoint)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## API Reference

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
4. Use id_token as Bearer token in all API requests
```

### Domain Restriction

Only emails from `app.security.allowed-domain` are accepted. All others receive `401 Unauthorized`. Change this in `application.properties` to restrict access to your company email domain.

---

## Memory & Conversation Management

### How memory works

Every message is persisted to `chat_messages` table with `conversationId`, `role`, and `content`.

On each request, the bot receives:
1. A **summary** of older messages (if conversation is long)
2. The **last 6 messages** in full

This keeps token usage flat regardless of conversation length.

### Summarization

After every 10 messages, a background summarization runs using a dedicated `ChatClient` (no memory advisor) that compresses older conversation history into a rolling summary stored in `conversation_summaries`.

### Cleanup

A scheduled job runs daily at midnight to delete messages older than 30 days:

```java
@Scheduled(cron = "0 0 0 * * *")
public void deleteOldMessages() { ... }
```

Enable scheduling by adding `@EnableScheduling` to your main application class.

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
- Summarization runs synchronously — on very long conversations the first response after the 10th message may be slightly slower
- `ConversationId` is client-provided — use a UUID generator on the frontend to ensure uniqueness