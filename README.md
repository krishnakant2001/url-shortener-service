# URL Shortener Service

A RESTful URL shortener built with **Spring Boot 4.1** and **Java 21**. It converts long URLs into short, shareable codes and permanently redirects users when they visit those codes.

---

## Table of Contents

- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Running the Application](#running-the-application)
- [API Reference](#api-reference)
- [API Examples](#api-examples-curl)
- [Running Tests](#running-tests)
- [Configuration Reference](#configuration-reference)
- [Design Decisions](#design-decisions)
- [Design Write-Up](#design-write-up)

---

## Features

- **Shorten any URL** — auto-generates a Base62 short code from the database ID
- **Custom alias** — optionally supply your own memorable alias
- **Idempotent** — shortening the same URL twice returns the existing mapping
- **Permanent redirect** — `301 Moved Permanently` for browser-cache efficiency
- **Input validation** — rejects blank URLs, invalid schemes, malformed aliases
- **Global error handling** — structured JSON error responses

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 |
| Web | Spring MVC |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL |
| Validation | Jakarta Bean Validation |
| Boilerplate | Lombok |
| Testing | JUnit 5 · Mockito · MockMvc · H2 (in-memory) |
| Build | Maven (wrapper included) |

---

## Project Structure

```
src/
├── main/
│   ├── java/com/krishnakant/url_shortener/
│   │   ├── UrlShortenerApplication.java       # Entry point
│   │   ├── controller/
│   │   │   └── UrlShortenerController.java    # REST endpoints
│   │   ├── dto/
│   │   │   ├── UrlShortenRequest.java         # POST request body
│   │   │   └── UrlShortenResponse.java        # Response body
│   │   ├── entity/
│   │   │   └── UrlMapping.java                # JPA entity
│   │   ├── exception/
│   │   │   ├── AliasAlreadyExistsException.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── UrlNotFoundException.java
│   │   ├── repository/
│   │   │   └── UrlMappingRepository.java      # JPA repository
│   │   └── service/
│   │       └── UrlShortenerService.java       # Business logic
│   └── resources/
│       └── application.properties             # Main config (PostgreSQL)
└── test/
    ├── java/com/krishnakant/url_shortener/
    │   ├── UrlShortenerApplicationTests.java  # Context load test
    │   ├── controller/
    │   │   └── UrlShortenerControllerTest.java # Web layer tests
    │   └── service/
    │       └── UrlShortenerServiceTest.java   # Unit tests
    └── resources/
        └── application.properties             # Test config (H2 in-memory)
```

---

## Prerequisites

| Tool | Version |
|---|---|
| Java JDK | 21 or higher |
| PostgreSQL | 13 or higher |
| Maven | Not required — Maven wrapper (`mvnw`) is included |

> **Check your Java version:**
> ```bash
> java -version
> ```

---

## Installation & Setup

### 1. Clone the repository

```bash
git clone <your-repo-url>
cd url-shortener-service
```

### 2. Create the PostgreSQL database

```sql
CREATE DATABASE "urlshortener-db";
```

Or using the CLI:

```bash
psql -U postgres -c 'CREATE DATABASE "urlshortener-db";'
```

### 3. Set environment variables

The application reads database credentials from environment variables — **never hardcode credentials**.

**macOS / Linux:**
```bash
export DB_USERNAME=your_postgres_username
export DB_PASSWORD=your_postgres_password
```

To persist across terminal sessions, add those lines to your `~/.zshrc` or `~/.bashrc`.

**Windows (PowerShell):**
```powershell
$env:DB_USERNAME="your_postgres_username"
$env:DB_PASSWORD="your_postgres_password"
```

### 4. Install dependencies

```bash
./mvnw dependency:resolve
```

---

## Running the Application

```bash
./mvnw spring-boot:run
```

The server starts on **`http://localhost:8080`** by default.

> **To run on a different port**, add to `application.properties`:
> ```properties
> server.port=9090
> ```

### Build and run as a JAR

```bash
./mvnw clean package -DskipTests
java -jar target/url-shortener-0.0.1-SNAPSHOT.jar
```

---

## API Reference

### POST `/shorten` — Shorten a URL

**Request Body:**

| Field | Type | Required | Constraints |
|---|---|---|---|
| `url` | String | ✅ Yes | Must be a valid `http` or `https` URL |
| `alias` | String | ❌ No | 3–20 characters, only `[a-zA-Z0-9_-]` |

**Response — `201 Created`:**

```json
{
  "id": 1,
  "originalUrl": "https://www.example.com/some/very/long/path",
  "shortCode": "1",
  "shortUrl": "http://localhost:8080/1",
  "message": "Created successfully with base62 encoding",
  "createdAt": "2026-07-15T10:30:00Z"
}
```

**`message` values:**

| Scenario | Message |
|---|---|
| New URL, auto code | `Created successfully with base62 encoding` |
| New URL, custom alias | `Created successfully with alias` |
| URL already exists | `Already original url exists` |

---

### GET `/{code}` — Redirect

Performs a **`301 Moved Permanently`** redirect to the original URL.

```
GET /1  →  301 Location: https://www.example.com/some/very/long/path
```

---

### Error Responses

| Status | Scenario | Response Body |
|---|---|---|
| `400 Bad Request` | Blank URL, invalid URL, bad alias format | `"Invalid URL: <url>"` or `{ "field": "message" }` |
| `404 Not Found` | Short code does not exist | `"No URL found for short code: xyz"` |
| `409 Conflict` | Alias is already taken | `"Alias already taken: my-alias"` |
| `500 Internal Server Error` | Unexpected server error | `"Internal Server Error"` |

---

## API Examples (curl)

### Shorten a URL (auto-generated code)

```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.google.com/search?q=url+shortener"}'
```

### Shorten a URL with a custom alias

```bash
curl -X POST http://localhost:8080/shorten \
  -H "Content-Type: application/json" \
  -d '{"url": "https://www.github.com", "alias": "github"}'
```

### Use the short URL (follow redirect)

```bash
curl -L http://localhost:8080/github
```

### Use the short URL (see redirect without following)

```bash
curl -v http://localhost:8080/github
```

---

## Running Tests

Tests use an **H2 in-memory database** — no PostgreSQL instance or environment variables are needed.

### Run all tests

```bash
./mvnw test
```

### Run a specific test class

```bash
# Service unit tests only
./mvnw test -Dtest=UrlShortenerServiceTest

# Controller (web layer) tests only
./mvnw test -Dtest=UrlShortenerControllerTest

# Application context test only
./mvnw test -Dtest=UrlShortenerApplicationTests
```

### Test coverage overview

| Test Class | Type | Tests |
|---|---|---|
| `UrlShortenerServiceTest` | Unit (Mockito) | 11 tests — idempotency, alias, base62 encoding, URL validation, redirect |
| `UrlShortenerControllerTest` | Web layer (MockMvc) | 12 tests — request validation, happy paths, error responses |
| `UrlShortenerApplicationTests` | Integration | 1 test — Spring context loads correctly |

---

## Configuration Reference

### `src/main/resources/application.properties` (production)

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/urlshortener-db
spring.datasource.username=${DB_USERNAME}
spring.datasource.password=${DB_PASSWORD}
spring.jpa.hibernate.ddl-auto=update
```

### `src/test/resources/application.properties` (tests)

```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop
```

---

## Design Decisions

### Base62 Encoding
Short codes are generated by Base62-encoding the auto-incremented database ID. This guarantees uniqueness without extra DB lookups and keeps codes short (ID=1 → `"1"`, ID=62 → `"10"`, ID=3844 → `"100"`).

### Idempotency
Submitting the same URL twice returns the existing mapping unchanged. This prevents duplicate rows and makes the API safe to call multiple times.

### 301 vs 302 Redirect
The service uses **301 Moved Permanently** because URLs and their short codes are immutable — once created, the mapping never changes (`short_code` is `updatable = false` at the JPA level). Browsers cache 301s, which reduces server load on repeated visits.

### Credentials via Environment Variables
Database credentials are never hardcoded. They are injected at runtime via `${DB_USERNAME}` and `${DB_PASSWORD}`, keeping secrets out of version control.

---

## Design Write-Up


### Q1 — What did you ask the AI to do, and what did you write or decide yourself?

I used Claude throughout this build, but mostly for boilerplate and bouncing ideas. I asked it to generate the initial entity class, repository, exception handler structure, and test cases. For the Base62 encoder I asked it to write the utility method since it's purely mechanical. For tests, I gave Claude the scenarios I wanted to cover — happy path, duplicate URL, unknown code, invalid URL, custom alias conflict — and it scaffolded the test methods. I reviewed each one, fixed the assertions that didn't match my actual response structure, and added edge cases it missed.

The core design decisions were mine. I chose to use the database-generated `BIGSERIAL` id with Base62 encoding for short code generation rather than random generation with a retry loop, because I wanted something mathematically guaranteed, not probabilistically safe.

I defined how duplicate URLs behave (idempotent: the same URL always returns the same code), how custom aliases interact with that idempotency check (alias requests skip dedup), and how the service flow is structured so that all business logic stays in the service layer. At one point the AI was leaking entity objects into the controller — I caught that and corrected it.

---

### Q2 — Where did you override, correct, or throw away the AI's output — and why?

Three times I disagreed and pushed back.

**1. DB trigger for short code generation** — The AI suggested `insertable = false, updatable = false` on `shortCode` with a DB trigger handling generation. I removed the trigger entirely because I didn't want DB logic scattered across two places. The service layer owns the logic, full stop.

**2. 301 vs 302 redirect** — The AI initially suggested `302`, arguing it preserves analytics since browsers don't cache it. I overrode it and went with `301`. I'm aware of the trade-off: `301` is cached permanently by browsers, meaning future hits won't reach my server, which would break click tracking. I accepted that cost because the mappings are immutable.

**3. Alias and duplicate URL handling** — The AI's responses tended to mix both code paths together. I explicitly separated the logic to avoid ambiguity and ensure predictable, independently testable behavior.

---

### Q3 — The two or three biggest trade-offs you made, and the alternatives you considered

**Short code generation strategy** — I went with `BIGSERIAL id → Base62 encode`, which requires two DB calls per shorten (INSERT to get the id, then UPDATE to write the code back). The cleaner alternative is a Redis counter — an atomic `INCR` gives you the next value before the INSERT, so you only need one DB call. I chose the DB approach to avoid adding Redis as a dependency for this exercise.

**Idempotent deduplication** — The same URL always returns the same short code. The alternative is generating a new code every time, which is simpler but means one URL can have ten different short codes floating around. I chose idempotent because it keeps analytics clean and storage honest. The cost is a `findByOriginalUrl` lookup on every POST — that's an indexed query, so it's fast.

---

### Q4 — What's missing, or what you'd do with another day?

- Click analytics (tracking number of redirects per short URL)
- Rate limiting on the shorten API
- Redis caching for faster redirect lookups (hot-path optimization)
- Better URL validation and normalization