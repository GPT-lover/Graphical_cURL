# cURL GUI

A local, single-user desktop-style GUI for building, sending, saving, and
converting HTTP / cURL requests. Learning project.

- **Frontend:** React 19 + Vite
- **Backend:** Java + Spring Boot 3.5 (Gradle)
- **Database:** SQLite (embedded file, no server) via Spring Data JPA
- **Outbound HTTP:** Java's built-in `java.net.http.HttpClient` (added in Phase 3)

The frontend never calls the user's target URL directly. It only calls this
project's Spring Boot backend, which performs the outbound HTTP request.

```
React (Vite dev server :5173)
   |  REST / JSON   (CORS allows localhost:5173)
   v
Spring Boot (:8080)
   |-- controller/  REST endpoints
   |-- service/     business logic        (Phase 3+)
   |-- repository/  Spring Data JPA        (Phase 4+)
   |-- model/       @Entity classes        (Phase 4+)
   |-- dto/         API request/response shapes
   |-- config/      CORS (+ HttpClient later)
   |
   |-- java.net.http.HttpClient --> target HTTP server   (Phase 3+)
   |
   v
SQLite  ->  backend/data/app.db
```

## Build status of this phase

**Phase 1 — project skeleton.** One backend endpoint (`GET /api/health`) and a
frontend that calls it on load and shows a connected / not-connected banner.
No request-sending yet.

## Prerequisites

| Tool | Version | Check |
|------|---------|-------|
| JDK | 21+ (tested on 25) | `java -version` |
| Node.js | 20.19+ or 22.12+ (22 LTS recommended) | `node -v` |

You do **not** need Gradle installed — the project ships a Gradle wrapper
(`./gradlew`) pinned to 9.7.1.

> This machine currently has **no Node.js**. Install Node 22 LTS from
> <https://nodejs.org/> (or `winget install OpenJS.NodeJS.LTS`) before running
> the frontend. Open a new terminal afterwards so `PATH` updates.

## Project layout

```
curl-gui/
├── backend/                  Spring Boot app
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew / gradlew.bat  Gradle wrapper (no local Gradle needed)
│   ├── data/                  SQLite file app.db is created here at runtime
│   └── src/main/java/com/example/curlgui/
│       ├── CurlGuiApplication.java   entry point
│       ├── config/     WebCorsConfig.java
│       ├── controller/ HealthController.java   ->  GET /api/health
│       ├── dto/        HealthResponse.java
│       ├── service/    (empty — Phase 3)
│       ├── repository/ (empty — Phase 4)
│       └── model/      (empty — Phase 4)
└── frontend/                 React + Vite app
    ├── package.json
    ├── vite.config.js
    ├── .env.development       VITE_API_BASE_URL=http://localhost:8080
    └── src/
        ├── main.jsx
        ├── App.jsx
        ├── api/client.js      fetch wrapper
        └── components/BackendStatus.jsx
```

## Run the backend

From `backend/`:

```bash
./gradlew bootRun
```

On Windows `cmd` / PowerShell use `gradlew.bat bootRun`.

- First run downloads Gradle 9.7.1 and the dependencies (a few minutes).
- Started successfully when you see a line like
  `Tomcat started on port 8080` / `Started CurlGuiApplication in X seconds`.
- Leave it running. Stop with `Ctrl+C`.
- A SQLite file appears at `backend/data/app.db`.

Quick check (new terminal):

```bash
curl http://localhost:8080/api/health
```

Expected:

```json
{"status":"UP","service":"curl-gui-backend","timestamp":"2026-09-02T12:34:56.789Z"}
```

## Run the frontend

From `frontend/` (first time only):

```bash
npm install
```

Then:

```bash
npm run dev
```

Open <http://localhost:5173>.

## Verify the two are communicating

1. Backend running (`./gradlew bootRun`) — `curl http://localhost:8080/api/health`
   returns the JSON above.
2. Frontend running (`npm run dev`) — open <http://localhost:5173>.
3. The page shows a **green** banner:
   `● Backend connected — curl-gui-backend reported UP at <timestamp>`.
   That value came from the backend over REST, so the round trip works
   (including CORS).
4. Stop the backend, refresh the page → banner turns **red**:
   `● Backend not reachable — Could not reach the backend...`. Restart the
   backend and refresh → green again.

## Notes / engineering choices

- **Port 8080 (backend) / 5173 (frontend).** `WebCorsConfig` allows exactly the
  Vite origin; `vite.config.js` sets `strictPort` so Vite won't silently move to
  another (blocked) port.
- **`spring.jpa.hibernate.ddl-auto=update`** — Hibernate builds the schema from
  `@Entity` classes automatically. Fine for a local learning app; a production
  app would use versioned migrations. No entities exist yet in Phase 1.
- **`-Dnet.bytebuddy.experimental=true`** is passed to `bootRun`/`test` in
  `build.gradle`. Byte Buddy (used by Hibernate) can lag a brand-new JDK's
  class-file version; this flag lets it proceed. Harmless on older JDKs.
- **Java 21 bytecode** (`sourceCompatibility = '21'`) even though the machine
  runs JDK 25 — broadest library compatibility.
```
