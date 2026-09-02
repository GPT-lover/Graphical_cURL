'use strict'

// A tiny append-only file logger for the desktop shell.
//
// It records the application lifecycle ONLY (startup, backend spawn, readiness,
// shutdown, failures). It deliberately does not inspect or record HTTP traffic,
// request bodies, headers, cookies or tokens - that data never passes through
// the Electron main process. Backend stdout/stderr is tee'd verbatim into a
// separate file; the "desktop" Spring profile keeps SQL parameter echoing off so
// bound values do not land there.

const fs = require('node:fs')
const path = require('node:path')

function stamp() {
  return new Date().toISOString()
}

class Logger {
  /** @param {string} dir  a writable directory (app.getPath('logs')) */
  constructor(dir) {
    this.dir = dir
    this.mainFile = path.join(dir, 'main.log')
    this.backendFile = path.join(dir, 'backend.log')
    try {
      fs.mkdirSync(dir, { recursive: true })
    } catch {
      /* logging must never crash the app */
    }
    this._main = this._open(this.mainFile)
    this._backend = this._open(this.backendFile)
  }

  _open(file) {
    try {
      const s = fs.createWriteStream(file, { flags: 'a' })
      s.on('error', () => {})
      return s
    } catch {
      return null
    }
  }

  info(msg) {
    this._writeMain('INFO ', msg)
  }

  warn(msg) {
    this._writeMain('WARN ', msg)
  }

  error(msg, err) {
    const detail = err ? ` :: ${err && err.stack ? err.stack : err}` : ''
    this._writeMain('ERROR', `${msg}${detail}`)
  }

  _writeMain(level, msg) {
    const line = `${stamp()} ${level} ${msg}\n`
    // Mirror to the real console too, so `npm run electron:dev` shows it.
    if (level.trim() === 'ERROR') process.stderr.write(line)
    else process.stdout.write(line)
    try {
      this._main && this._main.write(line)
    } catch {
      /* ignore */
    }
  }

  /** Raw backend output - already line-buffered by the caller. */
  backend(chunk) {
    try {
      this._backend && this._backend.write(chunk)
    } catch {
      /* ignore */
    }
  }

  /** Best-effort flush before the process exits. */
  close() {
    try {
      this._main && this._main.end()
      this._backend && this._backend.end()
    } catch {
      /* ignore */
    }
  }
}

module.exports = { Logger }
