# ToLink Service

> The Java admin & backend service for ToLink · current version `v0.1.0`

[简体中文](./README.md) · **English**

ToLink Service is the Java admin/backend service for ToLink. It handles users, LLM configuration, conversations, datasets, document files, OSS upload, parse-task dispatch, and parse-result queries. The actual document parsing, RAG execution, and LLM calls are handled by the Python RAG engine.

## System Boundary

ToLink uses a "Java admin + Python RAG engine" split:

```text
              ┌─────────────────────────┐       MySQL / Redis        ┌───────────────────────┐
  Frontend ─▶ │  Java Admin (this svc)  │ ◀─ OSS / MinIO ──────────▶ │  Python RAG Engine    │
              │  entry · auth · config  │ ── MQ (parse_task) ──────▶ │  parsing · RAG · LLM  │
              │  status · result query  │ ◀─ Shared DB ───────────── │  artifacts · state    │
              └─────────────────────────┘  internal HTTP (file/recall)└───────────────────────┘
```

- **Java side**: admin entry, user-scoped resources, configuration, file upload, object-storage location, parse-task dispatch, result queries (frontend polling, no more SSE push), and lightweight chat-title generation after the first turn.
- **Python side**: document parsing, RAG execution, LLM calls, parse-artifact generation, and part of the state progression.
- The two sides cooperate through MySQL, MQ, OSS/MinIO, and the necessary internal HTTP interfaces.

## Tech Stack

| Category | Technology |
| --- | --- |
| Language | Java 17 |
| Framework | Spring Boot 2.5.3 |
| Build | Maven multi-module |
| ORM | MyBatis-Plus |
| Auth | sa-token |
| Database | MySQL 8 |
| Cache | Redis / Lettuce |
| MQ | Kafka / RabbitMQ component abstraction |
| Files | Local storage / MinIO OSS component |
| Docs | Spec-as-Test: brief + acceptance.feature + technical_design |

## Module Structure

```text
link-model       # Entities, Request/Response DTOs, Enums, Result/PageResult
link-core        # Exception system, global exception handling, auth context, crypto utils, base config
link-components  # Redis, MQ, OSS components
link-mapper      # MyBatis-Plus Mappers
link-service     # Core business services
link-api         # Controllers and the Spring Boot entry point
```

Main entry class:

```text
link-api/src/main/java/com/qingluo/link/api/LinkApplication.java
```

## Core Capabilities

- **Users & permissions**: register, login, logout, user profile, admin user management — based on sa-token and `ADMIN/USER` roles.
- **LLM configuration**: system providers, user API-key configuration, default config, model-capability display; API keys encrypted with AES-256-GCM.
- **Conversations & usage**: sessions, messages, first-turn temporary title plus async model-generated title, usage aggregation, daily statistics, detail queries.
- **Datasets & document files**: dataset management, raw file upload, parse submission, parse-status queries (frontend polls `parse-results`, no more SSE push).
- **OSS**: local storage and MinIO file service, distinguishing public/private objects.
- **MQ**: parse-task `tolink.rag.parse_task` dispatch, delete notification `tolink.rag.document_delete` dispatch, cache compensation `tolink.cache.evict`; Java no longer consumes `tolink.rag.parse_result` — the parse terminal state is sourced from the shared database written by Python and queried via frontend polling of `parse-results`.
- **Redis**: caching for users, LLM config, and document-file runtime config, plus synchronous and compensating delete.
- **Recall session issuance**: chat recall uses "frontend connects directly to Python" — after Java verifies login state, user status, and dataset permission, it issues a short-lived HS256 session token (carrying `streamUrl`); the frontend pulls the recall/generation SSE directly from Python with that token. Java is not on the recall/generation request path.
- **Blog**: article draft/publish management, Markdown body with cover/inline image object storage, public-side queries.
- **Feedback**: anonymous user feedback submission (optional attachment) and admin-side feedback handling (status, priority, reply).
- **CDC cache compensation**: consumes Canal binlog, translated via a CDC bridge into `tolink.cache.evict`, performing eventually-consistent compensating invalidation on cache targets such as users/config/providers.

## Quick Start

### 1. Initialize the database

```bash
mysql -h <DB_HOST> -u root -p < scripts/db/schema.sql
mysql -h <DB_HOST> -u root -p tolink_rag_db < scripts/db/init.sql
```

### 2. Configure environment variables

Common config lives in `link-api/src/main/resources/application.yml`:

| Variable | Description |
| --- | --- |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL connection |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | Redis connection |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka address |
| `TOLINK_MQ_VENDOR` | MQ implementation, defaults to `kafka` |
| `OSS_SERVICE_TYPE` | OSS implementation, defaults to `local` |
| `OSS_FILE_ROOT_PATH` | Local OSS root directory |
| `OSS_MINIO_*` | MinIO config |
| `DOCUMENT_FILE_*` | Document-file upload and internal-access config |
| `LLM_SECRET` | API-key encryption key, 64-char hex string |
| `tolink.chat.title-generation.*` | Chat-title generation switch, length, timeout, and model output params |

### 3. Start the service

```bash
mvn spring-boot:run -pl link-api
```

Default port: `8080`.

### 4. Run tests

```bash
mvn clean test
mvn -pl link-api test
mvn -pl link-service test
```

## Containerized Deployment

The repo provides `deploy/docker-compose.yml` for deploying the packaged service (database, Redis, MQ, OSS and other middleware to be self-hosted or externally connected as needed):

```bash
cd deploy
docker compose up -d
```

The service connects to external MySQL / Redis / Kafka / OSS via environment variables; variable meanings are listed under "Configure environment variables" above.

## API Overview

| Module | Entry |
| --- | --- |
| Auth | `/api/v1/auth/login`, `/register`, `/logout` |
| User | `/api/v1/user/profile` |
| Admin | `/api/v1/admin/users`, `/providers`, `/document-file-config` |
| Provider | `/api/v1/llm/providers` |
| LLM Config | `/api/v1/llm/configs` |
| Chat | `/api/v1/chat/conversations` |
| Usage | `/api/v1/llm/usage/*` |
| Dataset | `/api/v1/datasets` |
| Document File | `/api/v1/datasets/{datasetId}/files`, `/api/v1/files/{fileId}` |
| Recall | `/api/v1/recall/sessions` (issues the session token for frontend-to-Python recall) |
| Blog | `/api/v1/blog` (public), `/api/v1/admin/blog` (admin) |
| Feedback | `/api/v1/feedback` (submit), `/api/v1/admin/feedback` (admin handling) |
| OSS File | `/api/v1/oss-files/{bizType}` |
| Internal | `/api/v1/internal/files/{fileId}/content`, `/api/v1/internal/parse-tasks/{taskId}/events` |

See `docs/api/api_contracts.md` for the full contract.

## AI Collaboration Workflow

This repo uses Spec-as-Test:

```text
brief.md -> acceptance.feature -> technical_design.md -> Code + Tests
```

The entry docs are `AGENTS.md` / `CLAUDE.md`, both pointing to `.ai/prompts/project.md`. The old seven-stage doc directory has been removed; new requirements use `.specs/<feature>/brief.md`, `acceptance.feature`, and `technical_design.md`.

Common checks:

```bash
python3 scripts/setup_ai_links.py
python3 scripts/check_ai_links.py
python3 scripts/check_docs_sync.py --working
```

## License

Private Project
