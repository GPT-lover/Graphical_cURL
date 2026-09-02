// Build the executable Spring Boot jar, cross-platform.
//
// `npm run build:backend` used to hard-code `cd backend && gradlew.bat ...`,
// which only works on Windows. This runs the matching Gradle wrapper for the
// current OS so the same npm script works on Windows (local dev / CI) and macOS
// (CI / local dev on a Mac). Same task, same output: backend/build/libs/*.jar
//
// Task is `clean bootJar` - identical to what the Windows script ran before.

import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const backendDir = path.join(repoRoot, 'backend')
const isWin = process.platform === 'win32'
const wrapper = path.join(backendDir, isWin ? 'gradlew.bat' : 'gradlew')
const gradleArgs = ['clean', 'bootJar']

if (!fs.existsSync(wrapper)) {
  console.error(`[build-backend] Gradle wrapper not found: ${wrapper}`)
  process.exit(1)
}

// The unix wrapper can lose its executable bit on checkout (stored 100644 in
// this repo's git index); restore it before running.
if (!isWin) {
  try {
    fs.chmodSync(wrapper, 0o755)
  } catch {
    /* best effort */
  }
}

console.log(`[build-backend] (cwd: backend/) ${isWin ? 'gradlew.bat' : './gradlew'} ${gradleArgs.join(' ')}`)

// Windows: invoke cmd.exe with `.\gradlew.bat` (leading `.\` = "current dir",
//   immune to spaces in the repo path and to NoDefaultCurrentDirectoryInExePath).
// macOS/Linux: exec the wrapper directly by absolute path (spaces fine as argv[0]).
const [command, args] = isWin
  ? [process.env.comspec || 'cmd.exe', ['/d', '/s', '/c', '.\\gradlew.bat', ...gradleArgs]]
  : [wrapper, gradleArgs]

const res = spawnSync(command, args, { cwd: backendDir, stdio: 'inherit', windowsHide: true })

if (res.error) {
  console.error(`[build-backend] failed to start Gradle: ${res.error.message}`)
  process.exit(1)
}
process.exit(res.status ?? 1)
