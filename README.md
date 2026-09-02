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

## Desktop app (Electron — Phase 12)

The React frontend + Spring Boot backend can be run and shipped as a self-contained
Windows desktop app. Electron is only a **wrapper / orchestrator** — the backend is
still Spring Boot, HTTP is still Java's `HttpClient`, storage is still SQLite.

```
Electron (main process)
   ├── picks a free loopback port (prefers 8080)
   ├── starts the bundled Spring Boot jar on it, with a bundled Java runtime
   ├── waits for  GET /api/health
   ├── opens the window and loads the built React app
   └── on exit, terminates the backend process it started

SQLite / logs live in a per-user, writable app-data directory (never inside the
installed app):
   Windows  %APPDATA%\Graphical cURL\graphical-curl.db   +  \logs\
   macOS    ~/Library/Application Support/Graphical cURL/graphical-curl.db
            ~/Library/Logs/Graphical cURL/
```

### Commands

| Task | Command | Notes |
|------|---------|-------|
| Browser dev — frontend | `cd frontend && npm run dev` (or `npm run dev` at repo root) | unchanged; http://localhost:5173 |
| Browser dev — backend | `cd backend && gradlew.bat bootRun` | unchanged; http://localhost:8080 |
| Desktop dev | `npm run electron:dev` | starts backend + Vite + Electron together |
| Build the Windows installer | `npm run electron:build:win` | `vite build` → `bootJar` → `jlink` → electron-builder (NSIS) |
| Build the macOS DMGs | `npm run electron:build:mac` | run on a Mac; see note below |
| Backend tests | `npm run test:backend` | = `cd backend && gradlew.bat test` |

Prerequisites for *building*: Node 22 and a full **JDK 25** (with `jlink`, on
`JAVA_HOME`). The project still compiles to **Java 21 bytecode**
(`backend/build.gradle`) — only the build/runtime JDK is 25. The *end user* needs
neither Java nor Node — both are bundled.

The bundled Java runtime (`backend/build/jre`, produced by `jlink`) is
**platform-specific**. `npm run electron:build:mac` on your Mac produces a
correct DMG only for *that* Mac's architecture; the other arch's DMG would carry
the wrong `java`. Use CI (below) for a proper dual-arch release, or pass a single
matching arch locally (`npx electron-builder --mac --arm64`).

### Releases (GitHub Actions)

Pushing a `v*` tag runs `.github/workflows/release.yml`, which builds the
Windows installer (`windows-latest`) and the macOS x64 + arm64 DMGs
(`macos-13` + `macos-14`, each with a natively-built `jlink` runtime), then
creates/updates the GitHub Release for that tag and attaches:

```
Graphical-cURL-Setup-<version>.exe
Graphical-cURL-<version>-x64.dmg
Graphical-cURL-<version>-arm64.dmg
```

The tag must match `version` in the root `package.json` (the workflow fails fast
if not). To cut `1.0.1`: bump `package.json` to `1.0.1`, commit, then
`git tag v1.0.1 && git push origin v1.0.1`.

`.github/workflows/build-desktop.yml` still runs a Windows-only installer build
on pushes to `main` (uploaded as a workflow artifact, no Release).

**macOS builds are unsigned / not notarized.** A downloaded DMG triggers
Gatekeeper; the user right-clicks the app → **Open** once, or runs
`xattr -dr com.apple.quarantine "/Applications/Graphical cURL.app"`. Adding an
Apple Developer ID later removes this (see the release notes).

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
