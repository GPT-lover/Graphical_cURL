# Graphical cURL

A lightweight, local, single-user desktop GUI for building, importing, sending, saving, and exporting HTTP/cURL requests.

Graphical cURL is designed as a simple alternative to tools such as Postman and Insomnia, with a particular focus on working with **cURL commands** and understanding what happens when an HTTP request is sent.

> **Status:** Active development — v1.0.0

## Features

* **HTTP request editor**

  * GET, POST, PUT, PATCH, DELETE, HEAD, and OPTIONS
  * URL, headers, cookies, and request body
* **cURL import**

  * Import cURL commands directly into the request editor
  * Handles quoted arguments, headers, cookies, request bodies, and multiline commands
* **cURL export**

  * Convert an edited request back into a readable cURL command
* **HTTP execution**

  * Requests are executed locally through the system's `curl` executable
  * HTTP status codes such as `404`, `429`, and `500` are displayed normally rather than treated as application errors
* **Request history**

  * Automatically records recent requests
  * Up to 100 history entries
  * Sensitive headers and cookies are not persisted
* **Saved requests**

  * Save requests for later use
  * Organise requests into collections
* **Environment variables**

  * Use variables such as `{{API_URL}}` or `{{AUTH_TOKEN}}`
  * Variables can be used in URLs, headers, cookies, and request bodies
  * Sensitive variables can be masked
* **Response viewer**

  * Response body, headers, and raw response views
  * JSON formatting
  * Response metadata including status and duration
* **Run Multiple**

  * Execute a request repeatedly
  * Sequential or bounded-parallel execution
  * Progress and success/failure statistics
* **Desktop application**

  * Windows installer
  * macOS DMG builds
  * Electron desktop shell
  * Bundled Java runtime
  * No Java, Node.js, or Gradle required by end users

---

## Architecture

Graphical cURL consists of a React frontend, a Spring Boot backend, SQLite storage, and the system cURL executable.

```text
┌──────────────────────────────────────────────┐
│                 Electron                     │
│          Desktop shell / process manager     │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │          React + Vite frontend         │  │
│  │                                        │  │
│  │ Request editor                         │  │
│  │ History / Saved Requests / Environments│  │
│  │ Response viewer                        │  │
│  └───────────────────┬────────────────────┘  │
│                      │ REST / JSON           │
│                      ▼                       │
│  ┌────────────────────────────────────────┐  │
│  │          Spring Boot backend            │  │
│  │                                        │  │
│  │ Controllers                            │  │
│  │ Request services                       │  │
│  │ cURL parser / generator                │  │
│  │ Environment resolution                 │  │
│  │ History / Saved Request persistence    │  │
│  └───────────────┬───────────────┬────────┘  │
│                  │               │            │
│                  ▼               ▼            │
│              SQLite         curl / curl.exe  │
│                              │                │
└──────────────────────────────┼────────────────┘
                               ▼
                         Target HTTP server
```

### Request flow

For an ordinary request:

```text
Request Editor
     │
     ▼
POST /api/requests/send
     │
     ▼
Spring Boot
     │
     ├── Resolve environment variables
     ├── Validate request
     ├── Record history
     │
     ▼
Build curl arguments
     │
     ▼
curl / curl.exe
     │
     ▼
Target server
     │
     ▼
Response
     │
     ├── Status
     ├── Headers
     ├── Body
     └── Duration
     │
     ▼
Spring Boot
     │
     ▼
React Response Viewer
```

The frontend **does not send requests directly to the target server**. The Spring Boot backend performs the outbound request.

---

## Technology Stack

| Component         | Technology                  |
| ----------------- | --------------------------- |
| Frontend          | React 19 + Vite             |
| Backend           | Java + Spring Boot 3.5      |
| Build system      | Gradle 9.7.1                |
| Database          | SQLite                      |
| Persistence       | Spring Data JPA / Hibernate |
| HTTP execution    | `curl` / `curl.exe`         |
| Desktop shell     | Electron                    |
| Desktop packaging | electron-builder            |
| Windows package   | NSIS `.exe`                 |
| macOS package     | `.dmg`                      |
| Bundled runtime   | Custom `jlink` Java runtime |

The application currently targets **Java 21 bytecode**. Development and packaging use JDK 25.

---

# Running the project

## Prerequisites

For development and building, install:

* Node.js 22 LTS
* JDK 25
* Git

You do **not** need Gradle installed because the project includes the Gradle wrapper.

Check your versions:

```bash
node -v
java -version
git --version
```

### cURL

The application uses the system `curl` executable.

Windows 10/11 normally includes `curl.exe`.

Check it with:

```powershell
curl.exe --version
```

On macOS/Linux:

```bash
curl --version
```

The executable can optionally be overridden using:

```properties
app.curl.path=/path/to/curl
```

---

# Development

There are two ways to run Graphical cURL during development.

## Option 1 — Run frontend and backend separately

### Start the backend

Windows:

```powershell
cd backend
.\gradlew.bat bootRun
```

macOS/Linux:

```bash
cd backend
./gradlew bootRun
```

The backend normally runs on:

```text
http://localhost:8080
```

### Start the frontend

```bash
cd frontend
npm install
npm run dev
```

The Vite development server normally runs on:

```text
http://localhost:5173
```

Then open the frontend in your browser.

---

## Option 2 — Run the desktop application

The Electron development command starts the backend, Vite, and Electron together:

```bash
npm install
npm run electron:dev
```

The development flow is:

```text
npm run electron:dev
       │
       ├── Spring Boot
       ├── Vite
       └── Electron
```

---

# Building the application

## Windows

Build the Windows installer:

```bash
npm run electron:build:win
```

The process performs approximately:

```text
React build
     ↓
Spring Boot bootJar
     ↓
Create bundled Java runtime with jlink
     ↓
Electron packaging
     ↓
NSIS installer
```

The installer is produced in:

```text
release/
```

The resulting installer has a name similar to:

```text
Graphical-cURL-Setup-1.0.0.exe
```

End users do **not** need to install Java, Node.js, Gradle, or the source code.

---

## macOS

Build a macOS package on a Mac:

```bash
npm run electron:build:mac
```

The bundled Java runtime is platform-specific, so macOS builds should be performed on macOS.

For official releases, GitHub Actions builds the macOS packages on native macOS runners.

The release artifacts are:

```text
Graphical-cURL-1.0.0-x64.dmg
Graphical-cURL-1.0.0-arm64.dmg
```

---

# GitHub Releases

Releases are automatically built using GitHub Actions.

Pushing a version tag matching:

```text
v*
```

starts the release workflow.

For example:

```bash
git tag v1.0.1
git push origin v1.0.1
```

The workflow builds:

```text
Windows x64
    ↓
Graphical-cURL-Setup-1.0.1.exe

macOS x64
    ↓
Graphical-cURL-1.0.1-x64.dmg

macOS arm64
    ↓
Graphical-cURL-1.0.1-arm64.dmg
```

The workflow then creates a GitHub Release and attaches the installers.

## Versioning

The Git tag must match the version in the root `package.json`.

For example:

```json
{
  "version": "1.0.1"
}
```

must be released with:

```text
v1.0.1
```

A typical release is:

```bash
# 1. Change package.json version

git add .
git commit -m "Release v1.0.1"
git push origin main

# 2. Create and push the release tag

git tag v1.0.1
git push origin v1.0.1
```

GitHub Actions then handles the platform-specific builds.

---

# macOS security

The current macOS packages are **unsigned and not notarized**.

As a result, macOS Gatekeeper may display a warning when opening the application downloaded from GitHub.

This is expected for the current development/release setup.

Proper Apple code signing and notarization can be added later using an Apple Developer account and GitHub Actions secrets.

---

# Data storage

Graphical cURL uses SQLite for local persistence.

The database contains application data such as:

* Request history
* Saved requests
* Collections
* Environments
* Environment variables

The desktop application stores writable data outside the installation directory.

### Windows

```text
%APPDATA%\Graphical cURL\
```

### macOS

```text
~/Library/Application Support/Graphical cURL/
```

Logs are also stored in the operating system's appropriate application-data/log directory.

---

# Security considerations

Graphical cURL is designed to execute arbitrary HTTP requests on behalf of the local user.

Some important implementation decisions are intentional:

### No shell execution

Requests are passed to cURL using Java's `ProcessBuilder` argument-list API.

The application does **not** construct a command such as:

```text
cmd.exe /c "curl ..."
```

or:

```text
powershell -Command "curl ..."
```

This prevents request values from being interpreted as shell commands.

### Sensitive information

The application avoids persisting or logging sensitive request values such as:

* `Authorization`
* Cookies
* Authentication credentials
* Sensitive environment variables
* Request bodies

Request logs contain metadata rather than complete request contents.

### TLS

TLS certificate verification is enabled by default.

Imported cURL commands containing:

```text
-k
```

or:

```text
--insecure
```

are preserved and generate a warning.

### Redirects

Redirects are not followed by default.

Imported commands containing:

```text
-L
```

or:

```text
--location
```

preserve that behavior.

---

# cURL compatibility

Graphical cURL supports the cURL features needed by its request editor and common browser-generated commands.

Currently supported during import include:

* URL
* HTTP method
* Headers
* Cookies
* Request bodies
* User-Agent
* Basic authentication
* `--compressed`
* HTTP version flags
* Redirects
* `--insecure`
* Connection timeout
* Maximum request time
* HTTP proxy
* Proxy authentication

Some advanced cURL features are **not yet supported by the GUI parser**, including:

* `-F` / `--form`
* `--form-string`
* `-T` / `--upload-file`
* `--data-urlencode`
* `-G` / `--get`
* Client certificates
* Custom CA certificates
* Pinned public keys

These commands currently produce a clear unsupported-feature error rather than silently executing an incomplete request.

---

# Run Multiple

The **Run Multiple** feature allows a request to be executed repeatedly.

Configuration includes:

* Number of runs
* Delay between runs
* Sequential execution
* Parallel execution

There is a hard limit of **5,000 runs** per operation.

Parallel execution is bounded rather than creating thousands of simultaneous operating-system processes.

Run Multiple also avoids creating a separate full history entry for every iteration.

---

# Project structure

```text
curl-gui/
│
├── backend/
│   ├── build.gradle
│   ├── settings.gradle
│   ├── gradlew
│   ├── gradlew.bat
│   │
│   └── src/
│       ├── main/
│       │   ├── java/com/example/curlgui/
│       │   │   ├── CurlGuiApplication.java
│       │   │   ├── config/
│       │   │   ├── controller/
│       │   │   ├── dto/
│       │   │   ├── model/
│       │   │   ├── repository/
│       │   │   └── service/
│       │   │
│       │   └── resources/
│       │
│       └── test/
│
├── frontend/
│   ├── package.json
│   ├── vite.config.js
│   └── src/
│       ├── api/
│       ├── components/
│       ├── hooks/
│       ├── lib/
│       └── ...
│
├── electron/
│   ├── main.js
│   ├── preload.js
│   ├── lib/
│   └── scripts/
│
├── manual-tests/
│
├── .github/
│   └── workflows/
│       └── release.yml
│
├── electron-builder.yml
├── package.json
└── README.md
```

---

# Backend API

The frontend communicates with the Spring Boot backend through REST endpoints.

Some of the main endpoints include:

```text
GET    /api/health

POST   /api/requests/send
POST   /api/requests/export-curl

GET    /api/history
DELETE /api/history/{id}
DELETE /api/history

GET    /api/collections
POST   /api/collections
...

GET    /api/environments
POST   /api/environments
...
```

The exact API is primarily intended for the application's frontend rather than as a public web API.

---

# Testing

Run the backend test suite:

Windows:

```powershell
cd backend
.\gradlew.bat test
```

macOS/Linux:

```bash
cd backend
./gradlew test
```

Or from the project root:

```bash
npm run test:backend
```

The project includes unit tests for areas including:

* cURL tokenisation
* cURL parsing
* cURL generation
* cURL command construction
* Cookies
* Environment variables
* Request execution
* Run Multiple validation
* Request loop behavior

---

# Design principles

Graphical cURL is intentionally a local application rather than a cloud service.

The project prioritises:

1. **Simple architecture**
2. **Local execution**
3. **No account required**
4. **No external request relay**
5. **Clear separation between frontend and backend**
6. **Safe process execution**
7. **Readable cURL import/export**
8. **Small, focused feature set**

The application is also a learning project for exploring:

* React
* Spring Boot
* REST APIs
* HTTP
* cURL
* SQLite
* JPA
* Electron
* Desktop application packaging
* GitHub Actions
* Cross-platform builds

---

# Known limitations

Graphical cURL is still under active development.

Current limitations include:

* Advanced multipart/file-upload cURL commands are not yet supported
* Client certificate options are not yet supported
* Saved Requests currently do not persist all imported transport-level cURL options
* Response bodies are represented as strings by the current API, so true binary-response handling is limited
* macOS releases are currently unsigned and unnotarized
* Run Multiple starts a separate cURL process for each iteration, so it has more overhead than an in-process HTTP client

---

# License

This project is released under the MIT License.
