'use strict'

// Preload script. Runs in an isolated context with Node integration OFF and
// contextIsolation ON. It exposes a tiny, explicit API on `window.curlGui` and
// nothing else - the React renderer never gets direct access to Node or Electron.

const { contextBridge, ipcRenderer } = require('electron')

// The backend base URL is handed to us by the main process as a command-line
// switch, because the port is only known at runtime (it is picked dynamically
// to avoid clashing with whatever else is using 8080).
function readSwitch(name) {
  const prefix = `--${name}=`
  const hit = process.argv.find((a) => a.startsWith(prefix))
  return hit ? hit.slice(prefix.length) : null
}

const api = {
  // Consumed by frontend/src/api/client.js (falls back to the .env value when
  // this is absent, i.e. in a plain browser).
  apiBaseUrl: readSwitch('curl-gui-api-base') || '',
  appVersion: readSwitch('curl-gui-version') || '',
  platform: process.platform,
  isDesktop: true,
  // Open a URL in the user's real browser instead of navigating the app window.
  openExternal: (url) => ipcRenderer.invoke('curl-gui:open-external', String(url)),
  // Native "Browse" file picker (used by the Hydra tool for the executable and
  // wordlist paths, and by the request editor for multipart/binary file
  // fields). Resolves to the chosen absolute path, or null if cancelled.
  pickFile: (options) => ipcRenderer.invoke('curl-gui:pick-file', options || {}),
  // Best-effort local existence check for a multipart/binary file field, so a
  // moved/deleted file can be flagged in the editor before Send is even
  // pressed. Resolves to false in a plain browser (no window.curlGui) or on
  // any error - callers must not treat that as proof the file is missing,
  // only as "can't tell locally"; the backend always re-validates on Send.
  pathExists: (path) => ipcRenderer.invoke('curl-gui:path-exists', String(path || '')),
}

contextBridge.exposeInMainWorld('curlGui', api)
