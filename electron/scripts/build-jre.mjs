// Build a minimal, self-contained Java runtime with `jlink` and drop it at
//   backend/build/jre/
// electron-builder then copies it into the installer (see electron-builder.yml),
// which is why the end user never has to install Java.
//
// Requirements to RUN this script (build machine / CI only, never the end user):
//   - a full JDK (not a JRE) that includes `jlink`, matching the app's Java
//     version. JAVA_HOME must point at it.
//
// The app targets Java 21 bytecode but is developed/run on JDK 25; this produces
// a JDK-25 runtime, which runs the 21-bytecode jar fine. If you ever need to
// pin a different JDK just for this step, set JAVA_HOME before running.

import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const outDir = path.join(repoRoot, 'backend', 'build', 'jre')
const libsDir = path.join(repoRoot, 'backend', 'build', 'libs')
const EXE = process.platform === 'win32' ? '.exe' : ''

// Modules known to be needed by Spring Boot 3.5 (web + Data JPA + Hibernate),
// the SQLite JDBC driver, TLS, and java.net.http. jdeps output (below) is merged
// on top of this; the baseline covers reflective / service-loaded deps jdeps
// cannot see.
const BASELINE_MODULES = [
  'java.base',
  'java.compiler',
  'java.desktop', // java.beans - used throughout Spring
  'java.instrument',
  'java.logging',
  'java.management',
  'java.naming',
  'java.net.http', // the app's outbound HTTP client
  'java.prefs',
  'java.rmi',
  'java.scripting',
  'java.security.jgss',
  'java.security.sasl',
  'java.sql',
  'java.sql.rowset',
  'java.transaction.xa',
  'java.xml',
  'java.xml.crypto',
  'jdk.charsets',
  'jdk.crypto.cryptoki',
  'jdk.crypto.ec', // ECC cipher suites for HTTPS
  'jdk.management',
  'jdk.naming.dns',
  'jdk.net',
  'jdk.security.auth',
  'jdk.unsupported', // sun.misc.Unsafe - Byte Buddy / Hibernate
  'jdk.unsupported.desktop',
  'jdk.zipfs', // Spring Boot loader reads nested jars via the zip filesystem
]

function jdkTool(name) {
  const home = process.env.JAVA_HOME
  if (!home) {
    throw new Error(
      'JAVA_HOME is not set. Point it at a full JDK (with jlink) to build the bundled runtime.',
    )
  }
  const p = path.join(home, 'bin', name + EXE)
  if (!fs.existsSync(p)) {
    throw new Error(`${name} not found at ${p}. JAVA_HOME must be a full JDK, not a JRE.`)
  }
  return p
}

function findAppJar() {
  if (!fs.existsSync(libsDir)) return null
  const jar = fs
    .readdirSync(libsDir)
    .filter((f) => f.endsWith('.jar') && !f.endsWith('-plain.jar'))
    .sort()
    .pop()
  return jar ? path.join(libsDir, jar) : null
}

function modulesFromJdeps(jarPath) {
  if (!jarPath) return []
  try {
    const out = execFileSync(
      jdkTool('jdeps'),
      [
        '--multi-release',
        '25',
        '--ignore-missing-deps',
        '--print-module-deps',
        jarPath,
      ],
      { encoding: 'utf8' },
    )
    return out
      .trim()
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .filter((m) => !m.startsWith('jdk.jdwp')) // no debugger agent in a shipped runtime
  } catch (err) {
    console.warn('[build-jre] jdeps could not analyse the jar; using the baseline module set only.')
    console.warn('[build-jre] ' + (err.message || err))
    return []
  }
}

function rmrf(dir) {
  fs.rmSync(dir, { recursive: true, force: true })
}

const jlink = jdkTool('jlink')
const appJar = findAppJar()
if (!appJar) {
  console.warn(
    '[build-jre] No backend jar in backend/build/libs yet - run "npm run build:backend" first ' +
      'for a jdeps-tuned module set. Proceeding with the baseline set.',
  )
}

const modules = Array.from(new Set([...BASELINE_MODULES, ...modulesFromJdeps(appJar)])).sort()
console.log(`[build-jre] modules (${modules.length}): ${modules.join(',')}`)

rmrf(outDir)
fs.mkdirSync(path.dirname(outDir), { recursive: true })

const baseArgs = [
  '--add-modules',
  modules.join(','),
  '--strip-debug',
  '--no-header-files',
  '--no-man-pages',
  '--output',
  outDir,
]

try {
  execFileSync(jlink, ['--compress=zip-6', ...baseArgs], { stdio: 'inherit' })
} catch {
  console.warn('[build-jre] "--compress=zip-6" not accepted by this jlink; retrying without compression.')
  rmrf(outDir)
  execFileSync(jlink, baseArgs, { stdio: 'inherit' })
}

// Sanity check: the runtime must actually run. `--version` (two dashes) prints
// to stdout, unlike the legacy `-version` which prints to stderr.
const javaOut = execFileSync(path.join(outDir, 'bin', 'java' + EXE), ['--version'], {
  encoding: 'utf8',
})
console.log('[build-jre] bundled runtime: ' + javaOut.trim().split('\n')[0])
console.log(`[build-jre] done -> ${outDir}`)
