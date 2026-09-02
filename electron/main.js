'use strict'

// Electron main process for Graphical cURL.
//
// Responsibilities (see Phase 12 spec):
//   1. pick a free loopback port
//   2. start the bundled Spring Boot backend on it (packaged mode only - in
//      `npm run electron:dev` Gradle owns the backend)
//   3. wait until GET /api/health answers
//   4. create the window and load the UI
//   5. on shutdown, terminate the backend process it started
//
// Security posture: contextIsolation on, nodeIntegration off, sandbox on, a
// minimal preload, external links open in the real browser, in-app navigation
// is confined to the app origin. The backend binds to 127.0.0.1 only.

const path = require('node:path')
const fs = require('node:fs')
const { app, BrowserWindow, dialog, shell, ipcMain } = require('electron')

const { Logger } = require('./lib/logger')
const { findFreePort, waitForHttp } = require('./lib/net')
const { resolveArtifacts, startBackend, stopBackend } = require('./lib/backend')

// productName drives app.getPath('userData'|'logs') -> %APPDATA%\Graphical cURL
app.setName('Graphical cURL')

const IS_DEV = process.env.CURL_GUI_DEV === '1'
const PREFERRED_PORT = 8080

/** @type {import('node:child_process').ChildProcess|null} */
let backendProc = null
let backendExited = false
let shuttingDown = false
/** @type {Logger} */
let logger
/** @type {BrowserWindow|null} */
let mainWindow = null
/** @type {BrowserWindow|null} */
let splashWindow = null

// ---------------------------------------------------------------------------
// Single instance - a second launch just focuses the existing window (and, more
// importantly, never starts a second backend).
// ---------------------------------------------------------------------------
if (!app.requestSingleInstanceLock()) {
  app.quit()
} else {
  app.on('second-instance', () => {
    if (mainWindow) {
      if (mainWindow.isMinimized()) mainWindow.restore()
      mainWindow.focus()
    }
  })
  app.whenReady().then(main).catch((err) => {
    // Last-ditch: whenReady() itself should not throw, but if it does, surface it.
    try {
      logger && logger.error('Fatal error during startup', err)
    } catch {
      /* ignore */
    }
    fail('Graphical cURL could not start.', err)
  })
}

// ---------------------------------------------------------------------------
// Startup
// ---------------------------------------------------------------------------
async function main() {
  logger = new Logger(app.getPath('logs'))
  logger.info('--------------------------------------------------------------')
  logger.info(`Electron starting (v${app.getVersion()}, dev=${IS_DEV}, packaged=${app.isPackaged})`)

  wireLifecycle()

  let backendBaseUrl
  /** @type {{type:'url'|'file', value:string}} */
  let loadTarget

  try {
    if (IS_DEV) {
      // Gradle + Vite are already running (started by `npm run electron:dev`).
      backendBaseUrl = process.env.CURL_GUI_BACKEND_URL || `http://localhost:${PREFERRED_PORT}`
      const viteUrl = process.env.ELECTRON_START_URL || 'http://localhost:5173'
      loadTarget = { type: 'url', value: viteUrl }
      logger.info(`Dev mode: backend ${backendBaseUrl}, UI ${viteUrl}`)
      await waitForHttp(`${backendBaseUrl}/api/health`, { timeoutMs: 90000 })
      logger.info('Backend is ready')
      await waitForHttp(viteUrl, { timeoutMs: 90000 })
      logger.info('Vite dev server is ready')
    } else {
      showSplash()
      const port = await findFreePort(PREFERRED_PORT)
      backendBaseUrl = `http://127.0.0.1:${port}`

      const { jarPath, jreDir, uiDir } = resolveArtifacts({
        isPackaged: app.isPackaged,
        resourcesPath: process.resourcesPath,
        appPath: app.getAppPath(),
      })
      if (!jarPath) {
        throw new Error(
          'Could not find the packaged backend jar. Expected it under ' +
            (app.isPackaged ? process.resourcesPath : 'backend/build/libs') +
            '. Run "npm run prepackage" first.',
        )
      }
      const indexHtml = path.join(uiDir, 'index.html')
      if (!fs.existsSync(indexHtml)) {
        throw new Error(`Could not find the built frontend at ${indexHtml}. Run "npm run build:frontend".`)
      }
      loadTarget = { type: 'file', value: indexHtml }

      const dbPath = path.join(app.getPath('userData'), 'graphical-curl.db')
      logger.info(`SQLite database: ${dbPath}`)

      backendProc = startBackend({
        port,
        dbPath,
        jarPath,
        jreDir,
        workingDir: app.getPath('userData'),
        logger,
      })
      backendProc.on('exit', (code, signal) => {
        backendExited = true
        logger.info(`Backend process exited (code=${code}, signal=${signal})`)
        if (!shuttingDown) {
          fail(
            'Graphical cURL could not start its backend.\n\n' +
              'The backend process stopped unexpectedly. Please check the logs:\n' +
              app.getPath('logs'),
          )
        }
      })

      await waitForHttp(`${backendBaseUrl}/api/health`, {
        timeoutMs: 90000,
        abort: () => backendExited,
      })
      logger.info(`Backend is ready on ${backendBaseUrl}`)
    }
  } catch (err) {
    logger.error('Backend did not become ready', err)
    fail(
      'Graphical cURL could not start its backend.\n\n' +
        'It did not become ready in time. Please check the logs:\n' +
        app.getPath('logs'),
      err,
    )
    return
  }

  createWindow(loadTarget, backendBaseUrl)
}

// ---------------------------------------------------------------------------
// Windows
// ---------------------------------------------------------------------------
function showSplash() {
  splashWindow = new BrowserWindow({
    width: 380,
    height: 220,
    frame: false,
    resizable: false,
    show: true,
    backgroundColor: '#1e1e2e',
    webPreferences: { contextIsolation: true, nodeIntegration: false, sandbox: true },
  })
  splashWindow.loadFile(path.join(__dirname, 'splash.html'))
  splashWindow.on('closed', () => {
    splashWindow = null
  })
}

function createWindow(loadTarget, backendBaseUrl) {
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    show: false,
    backgroundColor: '#1e1e2e',
    title: 'Graphical cURL',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      // Hand the backend URL + app version to the preload as command-line
      // switches (read there from process.argv).
      additionalArguments: [
        `--curl-gui-api-base=${backendBaseUrl}`,
        `--curl-gui-version=${app.getVersion()}`,
      ],
    },
  })

  // Keep navigation inside the app; send real links to the OS browser. The app
  // is a single-page app and never legitimately navigates the top frame, so any
  // http(s) navigation attempt is treated as an external link.
  const appOrigin = loadTarget.type === 'url' ? safeOrigin(loadTarget.value) : null
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })
  mainWindow.webContents.on('will-navigate', (event, url) => {
    const sameOrigin = appOrigin && safeOrigin(url) === appOrigin
    if (!sameOrigin) {
      event.preventDefault()
      if (/^https?:\/\//i.test(url)) shell.openExternal(url)
    }
  })

  mainWindow.once('ready-to-show', () => {
    if (splashWindow) splashWindow.close()
    mainWindow.show()
    if (IS_DEV) mainWindow.webContents.openDevTools({ mode: 'detach' })
  })

  mainWindow.on('closed', () => {
    mainWindow = null
  })

  logger.info(`Loading UI: ${loadTarget.type} ${loadTarget.value}`)
  if (loadTarget.type === 'file') mainWindow.loadFile(loadTarget.value)
  else mainWindow.loadURL(loadTarget.value)
}

// ---------------------------------------------------------------------------
// Lifecycle wiring
// ---------------------------------------------------------------------------
function wireLifecycle() {
  ipcMain.handle('curl-gui:open-external', (_evt, url) => {
    if (typeof url === 'string' && /^https?:\/\//i.test(url)) {
      return shell.openExternal(url)
    }
    return false
  })

  app.on('window-all-closed', () => {
    // Windows target: quitting when the window closes is the expected behaviour.
    app.quit()
  })

  app.on('before-quit', () => {
    shuttingDown = true
    stopBackend(backendProc, logger)
  })

  app.on('will-quit', () => {
    shuttingDown = true
    stopBackend(backendProc, logger)
    logger.info('Electron exiting')
    logger.close()
  })

  // If Electron is killed abruptly (dev Ctrl+C, OS signal) still try to take the
  // backend down with us so no orphaned Java process is left behind.
  const hardStop = () => {
    shuttingDown = true
    try {
      stopBackend(backendProc, logger)
    } catch {
      /* ignore */
    }
    process.exit(0)
  }
  process.on('SIGINT', hardStop)
  process.on('SIGTERM', hardStop)
  process.on('SIGHUP', hardStop)
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function safeOrigin(url) {
  try {
    return new URL(url).origin
  } catch {
    return null
  }
}

/** Show a blocking error, then quit. Never leaves a blank window on screen. */
function fail(message, err) {
  if (shuttingDown) return
  shuttingDown = true
  const log = logger || { info() {}, warn() {}, error() {}, close() {} }
  try {
    stopBackend(backendProc, log)
  } catch {
    /* ignore */
  }
  if (splashWindow) {
    splashWindow.close()
    splashWindow = null
  }
  const detail = err && err.message ? `\n\n${err.message}` : ''
  try {
    dialog.showErrorBox('Graphical cURL', `${message}${detail}`)
  } catch {
    /* headless / no display */
  }
  try {
    log.close()
  } catch {
    /* ignore */
  }
  app.exit(1)
}
