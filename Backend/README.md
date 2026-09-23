# Cadence API (Java backend)

Spring Boot 3.2 backend for the Cadence productivity tracker. Serves both the
REST API under `/api/**` **and** the frontend app itself at `http://localhost:8080/`.

## Requirements

- Java 17+ (a JDK is required; the Android Studio bundled JBR works too)
- Maven 3.9+ (`mvn` on PATH)

## Run

```powershell
cd Backend
mvn spring-boot:run
```

or build once and run the jar:

```powershell
mvn clean package
java -jar target/api-1.0.0.jar
```

Then open **http://localhost:8080/** — the app auto-detects the API and shows
"API connected". The standalone `index.html` at the repo root also works; it
probes `http://localhost:8080/api` and falls back to browser storage if the
backend is offline.

## Default admin

| Username | Password         |
|----------|------------------|
| admin    | admin-change-me  |

Created automatically on first boot. Change via `app.security.admin-*`
properties in `src/main/resources/application.properties`.

## Configuration highlights (`application.properties`)

| Property | Default | Meaning |
|---|---|---|
| `app.security.enabled` | `false` | `false`: API open, `/api/admin/**` needs an ADMIN JWT. `true`: every `/api/**` call needs a JWT. |
| `app.security.jwt-secret` | dev value | HS256 secret, min 32 chars. **Override in production.** |
| `app.security.jwt-ttl-hours` | `12` | Token lifetime |
| `app.audit.max-entries` | `500` | Audit log cap |

Data is stored in a file-based H2 database at `./data/cadence`
(delete that folder to reset everything). H2 web console:
http://localhost:8080/h2-console (JDBC URL `jdbc:h2:file:./data/cadence`, user `sa`, empty password).

## API overview

- `POST /api/auth/login` → `{token, username, role}` · `POST /api/auth/password`
- `GET/POST /api/projects`, `PUT/DELETE /api/projects/{id}`
- `GET/POST /api/tasks`, `PUT/DELETE /api/tasks/{id}`
- `GET /api/stats` · `GET /api/focus?since=yyyy-MM-dd` · `POST /api/focus`
- `GET/POST /api/audiologs` · `GET /api/audiologs/{id}/data` · `DELETE /api/audiologs/{id}`
- `GET/PUT /api/prefs` · `GET /api/report/weekly` · `GET /api/export`
- Admin (ADMIN JWT required): `overview`, `system`, `users` (+ create/update/reset-password),
  `projects`, `audit`, `clear-focus`, `clear-audio`, `reset-all`

Errors always return `{ "error": "message" }`.

## Smoke test

With the server running:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File smoke-test.ps1
```

Exercises every endpoint above end-to-end.