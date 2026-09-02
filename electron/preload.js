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
}

contextBridge.exposeInMainWorld('curlGui', api)
