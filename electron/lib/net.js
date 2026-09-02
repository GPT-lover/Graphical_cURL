'use strict'

// Networking helpers for the desktop shell: pick a free loopback port, and wait
// for an HTTP endpoint to come alive. No third-party dependencies.

const net = require('node:net')
const http = require('node:http')

/**
 * Ask the OS for a free TCP port on 127.0.0.1 by binding to port 0, reading the
 * assigned port, then releasing it. There is a tiny race between releasing and
 * the backend binding it, but on a single-user desktop machine that is
 * negligible; `startBackend` additionally fails loudly if the port is taken.
 *
 * @param {number} [preferred] try this port first (e.g. 8080); fall back to a
 *                             random free one if it is busy.
 * @returns {Promise<number>}
 */
function findFreePort(preferred) {
  const tryPort = (port) =>
    new Promise((resolve, reject) => {
      const srv = net.createServer()
      srv.unref()
      srv.on('error', reject)
      srv.listen({ port, host: '127.0.0.1' }, () => {
        const { port: chosen } = srv.address()
        srv.close(() => resolve(chosen))
      })
    })

  if (preferred) {
    return tryPort(preferred).catch(() => tryPort(0))
  }
  return tryPort(0)
}

/**
 * Poll `url` with GET until it answers with any HTTP status (connection
 * succeeded) or until the timeout elapses.
 *
 * @param {string} url
 * @param {object} [opts]
 * @param {number} [opts.timeoutMs=60000]
 * @param {number} [opts.intervalMs=400]
 * @param {() => boolean} [opts.abort] return true to stop early (e.g. the
 *        backend process died)
 * @returns {Promise<void>} resolves on success, rejects on timeout / abort
 */
function waitForHttp(url, opts = {}) {
  const timeoutMs = opts.timeoutMs ?? 60000
  const intervalMs = opts.intervalMs ?? 400
  const abort = opts.abort ?? (() => false)
  const deadline = Date.now() + timeoutMs

  return new Promise((resolve, reject) => {
    const attempt = () => {
      if (abort()) {
        reject(new Error(`Aborted while waiting for ${url}`))
        return
      }
      const req = http.get(url, { timeout: 2000 }, (res) => {
        res.resume() // drain
        resolve()
      })
      req.on('error', retry)
      req.on('timeout', () => {
        req.destroy()
        retry()
      })
    }
    const retry = () => {
      if (Date.now() >= deadline) {
        reject(new Error(`Timed out after ${timeoutMs}ms waiting for ${url}`))
        return
      }
      setTimeout(attempt, intervalMs)
    }
    attempt()
  })
}

module.exports = { findFreePort, waitForHttp }
