// Dev helper for `npm run electron:dev`.
//
// `concurrently` starts three things at once: Gradle bootRun, the Vite dev
// server, and this script. This script waits until BOTH the backend health
// endpoint and the Vite server respond, then launches Electron pointed at them.
// (Electron's main process also waits, but launching it only once the servers
// are up keeps the console output tidy and DevTools from erroring on first paint.)

import { spawn } from 'node:child_process'
import http from 'node:http'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')

const BACKEND = process.env.CURL_GUI_BACKEND_URL || 'http://localhost:8080'
const VITE = process.env.ELECTRON_START_URL || 'http://localhost:5173'

function ping(url) {
  return new Promise((resolve) => {
    const req = http.get(url, { timeout: 2000 }, (res) => {
      res.resume()
      resolve(true)
    })
    req.on('error', () => resolve(false))
    req.on('timeout', () => {
      req.destroy()
      resolve(false)
    })
  })
}

async function waitFor(label, url, timeoutMs = 120000) {
  const deadline = Date.now() + timeoutMs
  process.stdout.write(`[shell] waiting for ${label} at ${url} ...\n`)
  while (Date.now() < deadline) {
    if (await ping(url)) {
      process.stdout.write(`[shell] ${label} is up\n`)
      return
    }
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error(`Timed out waiting for ${label} at ${url}`)
}

try {
  await waitFor('backend', `${BACKEND}/api/health`)
  await waitFor('vite', VITE)
} catch (err) {
  console.error(`[shell] ${err.message}`)
  process.exit(1)
}

// `electron` resolves to the local binary from node_modules/.bin.
const electronBin = process.platform === 'win32' ? 'electron.cmd' : 'electron'
const child = spawn(path.join(repoRoot, 'node_modules', '.bin', electronBin), ['.'], {
  cwd: repoRoot,
  stdio: 'inherit',
  env: { ...process.env, CURL_GUI_DEV: '1' },
})

// When `concurrently -k` (or a Ctrl+C) tears this script down, take Electron with
// it so it does not linger - and Electron in turn stops the Gradle backend it
// might have been waiting on.
for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
  process.on(sig, () => {
    try {
      child.kill()
    } catch {
      /* ignore */
    }
    process.exit(0)
  })
}
child.on('exit', (code) => process.exit(code ?? 0))
