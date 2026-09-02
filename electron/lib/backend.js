'use strict'

// Locating, starting and stopping the bundled Spring Boot backend.
//
// The backend is a normal executable Spring Boot jar. In the packaged app it is
// launched with a Java runtime that ships inside the installer (jlink output),
// so the end user never needs Java on their machine or PATH.

const fs = require('node:fs')
const path = require('node:path')
const { spawn, spawnSync } = require('node:child_process')

const IS_WIN = process.platform === 'win32'
const JAVA_BIN = IS_WIN ? 'java.exe' : 'java'

/**
 * Work out where the jar and the bundled Java runtime are, handling both:
 *   - the packaged app  (files under  <install>/resources/ )
 *   - running `electron .` from a source checkout after `npm run prepackage`
 *     (files under  backend/build/ )
 *
 * @param {{ isPackaged: boolean, resourcesPath: string, appPath: string }} ctx
 */
function resolveArtifacts(ctx) {
  const repoRoot = path.resolve(__dirname, '..', '..')

  let libDir
  let jreDir
  let uiDir
  if (ctx.isPackaged) {
    libDir = path.join(ctx.resourcesPath, 'backend')
    jreDir = path.join(ctx.resourcesPath, 'jre')
    uiDir = path.join(ctx.resourcesPath, 'ui')
  } else {
    // Running `electron .` from a source checkout after `npm run prepackage`.
    libDir = path.join(repoRoot, 'backend', 'build', 'libs')
    jreDir = path.join(repoRoot, 'backend', 'build', 'jre')
    uiDir = path.join(repoRoot, 'frontend', 'dist')
  }

  let jarPath = null
  if (fs.existsSync(libDir)) {
    const jar = fs
      .readdirSync(libDir)
      .filter((f) => f.endsWith('.jar') && !f.endsWith('-plain.jar'))
      .sort()
      .pop()
    if (jar) jarPath = path.join(libDir, jar)
  }
  if (!fs.existsSync(path.join(jreDir, 'bin', JAVA_BIN))) jreDir = null

  return { jarPath, jreDir, uiDir }
}

/**
 * Choose the Java executable. Order:
 *   1. the runtime bundled with the app (jlink output) - the normal case
 *   2. $JAVA_HOME/bin/java            - dev convenience only
 *   3. "java" on PATH                 - last resort, dev only
 */
function resolveJava(jreDir, logger) {
  if (jreDir) {
    const p = path.join(jreDir, 'bin', JAVA_BIN)
    if (fs.existsSync(p)) return p
  }
  if (process.env.JAVA_HOME) {
    const p = path.join(process.env.JAVA_HOME, 'bin', JAVA_BIN)
    if (fs.existsSync(p)) {
      logger.warn(`Bundled JRE not found - falling back to JAVA_HOME (${p})`)
      return p
    }
  }
  logger.warn('No bundled JRE and no JAVA_HOME - falling back to "java" on PATH')
  return JAVA_BIN
}

/**
 * Start the backend.
 *
 * @param {object} args
 * @param {number} args.port           HTTP port to bind (already confirmed free)
 * @param {string} args.dbPath         absolute path to the SQLite file to use
 * @param {string} args.jarPath        absolute path to the executable jar
 * @param {string|null} args.jreDir    bundled runtime dir, or null
 * @param {string} args.workingDir     a writable cwd for the child
 * @param {import('./logger').Logger} args.logger
 * @returns {import('node:child_process').ChildProcess}
 */
function startBackend(args) {
  const { port, dbPath, jarPath, jreDir, workingDir, logger } = args
  if (!jarPath || !fs.existsSync(jarPath)) {
    throw new Error(`Backend jar not found${jarPath ? ` at ${jarPath}` : ''}`)
  }
  try {
    fs.mkdirSync(path.dirname(dbPath), { recursive: true })
  } catch {
    /* the userData dir already exists; ignore */
  }

  const java = resolveJava(jreDir, logger)
  const jdbcUrl = `jdbc:sqlite:${dbPath.replace(/\\/g, '/')}`
  const jvmArgs = [
    // Byte Buddy (via Hibernate) can lag a brand-new JDK class-file version;
    // this mirrors what build.gradle passes to `bootRun`.
    '-Dnet.bytebuddy.experimental=true',
    '-Dfile.encoding=UTF-8',
    '-jar',
    jarPath,
  ]
  const appArgs = [
    '--spring.profiles.active=desktop',
    `--server.port=${port}`,
    '--server.address=127.0.0.1',
    `--spring.datasource.url=${jdbcUrl}`,
  ]

  logger.info(`Starting backend: ${path.basename(java)} -jar ${path.basename(jarPath)}`)
  logger.info(`  port      = ${port} (127.0.0.1 only)`)
  logger.info(`  database  = ${dbPath}`)
  logger.info(`  java      = ${java}`)

  const child = spawn(java, [...jvmArgs, ...appArgs], {
    cwd: workingDir,
    stdio: ['ignore', 'pipe', 'pipe'],
    windowsHide: true,
    env: { ...process.env },
  })

  const tee = (stream) => {
    stream.setEncoding('utf8')
    stream.on('data', (chunk) => logger.backend(chunk))
  }
  tee(child.stdout)
  tee(child.stderr)

  return child
}

/**
 * Stop the backend process this shell started - and only that one. On Windows
 * `taskkill /t` also cleans up any child processes it spawned. Never enumerates
 * or signals other Java processes.
 *
 * @param {import('node:child_process').ChildProcess|null} child
 * @param {import('./logger').Logger} logger
 */
function stopBackend(child, logger) {
  if (!child || child.exitCode !== null || child.signalCode !== null) return
  const pid = child.pid
  if (!pid) return
  logger.info(`Stopping backend (pid ${pid})`)
  try {
    if (IS_WIN) {
      spawnSync('taskkill', ['/pid', String(pid), '/t', '/f'], { windowsHide: true })
    } else {
      child.kill('SIGTERM')
      setTimeout(() => {
        if (child.exitCode === null) child.kill('SIGKILL')
      }, 3000).unref()
    }
  } catch (err) {
    logger.error('Failed to stop backend cleanly', err)
  }
}

module.exports = { resolveArtifacts, startBackend, stopBackend }
