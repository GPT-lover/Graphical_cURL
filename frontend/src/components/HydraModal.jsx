import { useEffect, useRef, useState } from 'react'

const PROTOCOLS = ['http', 'https']

function canPickFile() {
  return typeof window !== 'undefined' && window.curlGui && typeof window.curlGui.pickFile === 'function'
}

function isWindows() {
  return typeof window !== 'undefined' && window.curlGui && window.curlGui.platform === 'win32'
}

/**
 * "Hydra" tool dialog: configure and launch an HTTP-form brute-force attack
 * with THC Hydra - an external tool the user installs separately. Graphical
 * cURL never bundles or reimplements Hydra; this only builds the argument
 * list and hands it to the backend, which runs it via ProcessBuilder.
 *
 * Hydra can run two ways (see useHydra's `settings.executionMode`):
 *   LOCAL - a native Hydra executable, run directly.
 *   WSL   - Hydra installed inside a WSL distribution (e.g. Kali Linux); the
 *           backend invokes it via `wsl.exe -d <distro> --exec <path> ...` and
 *           converts a Windows wordlist path to its WSL equivalent.
 *
 * Props:
 *   open, onClose
 *   hydra   - the object from useHydra()
 *   prefill - { host, port, protocol, path, formParams } guessed from the
 *             current request editor when the dialog was opened (best-effort;
 *             every field stays freely editable)
 */
export default function HydraModal({ open, onClose, hydra, prefill }) {
  const [executionMode, setExecutionMode] = useState('LOCAL')
  const [executablePath, setExecutablePath] = useState('')
  const [wslDistro, setWslDistro] = useState('kali-linux')
  const [wslHydraPath, setWslHydraPath] = useState('/usr/bin/hydra')
  const [savingSettings, setSavingSettings] = useState(false)
  const [settingsError, setSettingsError] = useState(null)

  const [host, setHost] = useState('')
  const [port, setPort] = useState('80')
  const [protocol, setProtocol] = useState('http')
  const [username, setUsername] = useState('')
  const [wordlistPath, setWordlistPath] = useState('')
  const [path, setPath] = useState('/')
  const [formParams, setFormParams] = useState('username=^USER^&password=^PASS^')
  const [cookies, setCookies] = useState('')
  const [failureCondition, setFailureCondition] = useState('')
  const [formError, setFormError] = useState(null)

  const outputRef = useRef(null)

  // Sync the settings fields from the backend whenever the dialog (re)opens.
  useEffect(() => {
    if (open) {
      const s = hydra.settings
      setExecutionMode(s.executionMode || 'LOCAL')
      setExecutablePath(s.executablePath || '')
      setWslDistro(s.wslDistro || 'kali-linux')
      setWslHydraPath(s.wslHydraPath || '/usr/bin/hydra')
      setSettingsError(null)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, hydra.settings])

  // Prefill the target/path/form fields from the current request, once, when opened.
  useEffect(() => {
    if (open && hydra.phase === 'idle' && prefill) {
      if (prefill.host) setHost(prefill.host)
      if (prefill.port) setPort(String(prefill.port))
      if (prefill.protocol) setProtocol(prefill.protocol)
      if (prefill.path) setPath(prefill.path)
      if (prefill.formParams) setFormParams(prefill.formParams)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open])

  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight
    }
  }, [hydra.output])

  useEffect(() => {
    if (!open) return undefined
    function onKey(e) {
      if (e.key === 'Escape' && hydra.phase !== 'running') onClose()
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose, hydra.phase])

  if (!open) return null

  function currentSettings() {
    return {
      executionMode,
      executablePath: executablePath.trim(),
      wslDistro: wslDistro.trim(),
      wslHydraPath: wslHydraPath.trim(),
    }
  }

  async function handleBrowseExecutable() {
    const picked = await window.curlGui.pickFile({ title: 'Select the Hydra executable' })
    if (picked) setExecutablePath(picked)
  }

  async function handleBrowseWordlist() {
    const picked = await window.curlGui.pickFile({ title: 'Select a password wordlist' })
    if (picked) setWordlistPath(picked)
  }

  async function handleSaveSettings() {
    setSavingSettings(true)
    setSettingsError(null)
    try {
      await hydra.saveSettings(currentSettings())
    } catch (err) {
      setSettingsError(err.message)
    } finally {
      setSavingSettings(false)
    }
  }

  async function handleStart() {
    const p = Number(port)
    if (executionMode === 'LOCAL' && !executablePath.trim()) {
      return setFormError('Hydra executable is required.')
    }
    if (executionMode === 'WSL' && !wslDistro.trim()) {
      return setFormError('WSL distribution is required.')
    }
    if (executionMode === 'WSL' && !wslHydraPath.trim()) {
      return setFormError('Hydra path inside WSL is required.')
    }
    if (!host.trim()) return setFormError('Target host is required.')
    if (!Number.isInteger(p) || p < 1 || p > 65535) {
      return setFormError('Port must be between 1 and 65535.')
    }
    if (!username.trim()) return setFormError('Username is required.')
    if (!wordlistPath.trim()) return setFormError('A password wordlist is required.')
    if (!path.trim().startsWith('/')) return setFormError('Path must start with "/".')
    if (!formParams.includes('^USER^') || !formParams.includes('^PASS^')) {
      return setFormError('Form parameters must include the ^USER^ and ^PASS^ placeholders.')
    }
    if (!failureCondition.trim()) return setFormError('Failure condition is required.')
    setFormError(null)

    try {
      await hydra.saveSettings(currentSettings())
    } catch (err) {
      setFormError(`Could not save the Hydra settings: ${err.message}`)
      return
    }

    hydra.start({
      host: host.trim(),
      port: p,
      protocol,
      username: username.trim(),
      wordlistPath: wordlistPath.trim(),
      path: path.trim(),
      formParams: formParams.trim(),
      cookies: cookies.trim(),
      failureCondition: failureCondition.trim(),
    })
  }

  const running = hydra.phase === 'running'
  const finished = hydra.phase === 'done' || hydra.phase === 'stopped'
  const dismissable = !running

  return (
    <div className="modal-overlay" onMouseDown={dismissable ? onClose : undefined}>
      <div
        className="modal modal--hydra"
        role="dialog"
        aria-modal="true"
        aria-labelledby="hydra-title"
        onMouseDown={(e) => e.stopPropagation()}
      >
        <div className="modal__header">
          <h2 id="hydra-title">Hydra</h2>
          {dismissable && (
            <button type="button" className="btn btn--icon" onClick={onClose} aria-label="Close">
              &times;
            </button>
          )}
        </div>

        <p className="hydra-info">
          Requires Hydra to be installed separately. Graphical cURL does not include or
          bundle Hydra.{' '}
          {executionMode === 'WSL' ? (
            <>
              It will be run inside WSL - install THC Hydra <strong>inside the WSL distribution</strong>{' '}
              below (e.g. <code>sudo apt install hydra</code> in Kali Linux), not on Windows itself.
            </>
          ) : (
            <>Install THC Hydra yourself and point this at its executable.</>
          )}
        </p>

        <div className="hydra-settings">
          <fieldset className="field pacing-mode">
            <span className="field__label">Run Hydra</span>
            <div className="pacing-mode__options">
              <label>
                <input
                  type="radio"
                  name="hydra-exec-mode"
                  checked={executionMode === 'LOCAL'}
                  onChange={() => setExecutionMode('LOCAL')}
                  disabled={running}
                />{' '}
                Local executable
              </label>
              <label>
                <input
                  type="radio"
                  name="hydra-exec-mode"
                  checked={executionMode === 'WSL'}
                  onChange={() => setExecutionMode('WSL')}
                  disabled={running}
                />{' '}
                Inside WSL (e.g. Kali Linux)
              </label>
            </div>
          </fieldset>

          {executionMode === 'LOCAL' ? (
            <label className="field">
              <span className="field__label">Hydra executable</span>
              <div className="hydra-path-row">
                <input
                  className="input"
                  type="text"
                  value={executablePath}
                  onChange={(e) => setExecutablePath(e.target.value)}
                  placeholder={isWindows() ? 'C:\\...\\hydra.exe' : '/usr/bin/hydra'}
                  disabled={running}
                />
                {canPickFile() && (
                  <button type="button" className="btn" onClick={handleBrowseExecutable} disabled={running}>
                    Browse
                  </button>
                )}
                <button
                  type="button"
                  className="btn"
                  onClick={handleSaveSettings}
                  disabled={running || savingSettings}
                >
                  {savingSettings ? 'Saving…' : 'Save'}
                </button>
                <button
                  type="button"
                  className="btn"
                  onClick={() => hydra.detect(currentSettings())}
                  disabled={running || hydra.detecting}
                >
                  {hydra.detecting ? 'Testing…' : 'Test'}
                </button>
              </div>
            </label>
          ) : (
            <>
              <label className="field">
                <span className="field__label">WSL distribution</span>
                <input
                  className="input"
                  type="text"
                  value={wslDistro}
                  onChange={(e) => setWslDistro(e.target.value)}
                  placeholder="kali-linux"
                  disabled={running}
                />
                <span className="field__hint">
                  The distro name as shown by <code>wsl -l</code> on Windows.
                </span>
              </label>
              <label className="field">
                <span className="field__label">Hydra path inside WSL</span>
                <div className="hydra-path-row">
                  <input
                    className="input"
                    type="text"
                    value={wslHydraPath}
                    onChange={(e) => setWslHydraPath(e.target.value)}
                    placeholder="/usr/bin/hydra"
                    disabled={running}
                  />
                  <button
                    type="button"
                    className="btn"
                    onClick={handleSaveSettings}
                    disabled={running || savingSettings}
                  >
                    {savingSettings ? 'Saving…' : 'Save'}
                  </button>
                  <button
                    type="button"
                    className="btn"
                    onClick={() => hydra.detect(currentSettings())}
                    disabled={running || hydra.detecting}
                  >
                    {hydra.detecting ? 'Testing…' : 'Test'}
                  </button>
                </div>
                <span className="field__hint">
                  Run as{' '}
                  <code>
                    wsl.exe -d {wslDistro || '<distro>'} --exec {wslHydraPath || '/usr/bin/hydra'} -h
                  </code>{' '}
                  to test.
                </span>
              </label>
            </>
          )}
          {settingsError && <div className="modal__error" role="alert">{settingsError}</div>}
          {hydra.detectResult && (
            <p
              className={`hydra-detect ${hydra.detectResult.available ? 'hydra-detect--ok' : 'hydra-detect--fail'}`}
            >
              {hydra.detectResult.available ? '\u2713 ' : '\u2717 '}
              {hydra.detectResult.message}
            </p>
          )}
        </div>

        {hydra.phase === 'idle' && (
          <>
            <div className="hydra-form-grid">
              <label className="field">
                <span className="field__label">Host / IP</span>
                <input
                  className="input"
                  type="text"
                  value={host}
                  onChange={(e) => setHost(e.target.value)}
                  placeholder="10.0.0.5"
                />
              </label>
              <label className="field">
                <span className="field__label">Port</span>
                <input
                  className="input"
                  type="number"
                  min="1"
                  max="65535"
                  value={port}
                  onChange={(e) => setPort(e.target.value)}
                />
              </label>
              <label className="field">
                <span className="field__label">Protocol</span>
                <select className="input" value={protocol} onChange={(e) => setProtocol(e.target.value)}>
                  {PROTOCOLS.map((p) => (
                    <option key={p} value={p}>
                      {p}
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                <span className="field__label">Username</span>
                <input
                  className="input"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="admin"
                />
              </label>
              <label className="field hydra-form-grid__wide">
                <span className="field__label">Password wordlist</span>
                <div className="hydra-path-row">
                  <input
                    className="input"
                    type="text"
                    value={wordlistPath}
                    onChange={(e) => setWordlistPath(e.target.value)}
                    placeholder={
                      executionMode === 'WSL' ? 'C:\\...\\passwords.txt or /usr/share/wordlists/rockyou.txt' : '/path/to/wordlist.txt'
                    }
                  />
                  {canPickFile() && (
                    <button type="button" className="btn" onClick={handleBrowseWordlist}>
                      Browse
                    </button>
                  )}
                </div>
                {executionMode === 'WSL' && (
                  <span className="field__hint">
                    A Windows path (e.g. <code>C:\Users\...\passwords.txt</code>) is converted to its WSL
                    path automatically; a path that&apos;s already inside WSL/Linux (e.g.{' '}
                    <code>/usr/share/wordlists/rockyou.txt</code>) is used as-is.
                  </span>
                )}
              </label>
              <label className="field hydra-form-grid__wide">
                <span className="field__label">Path</span>
                <input
                  className="input"
                  type="text"
                  value={path}
                  onChange={(e) => setPath(e.target.value)}
                  placeholder="/login"
                />
              </label>
              <label className="field hydra-form-grid__wide">
                <span className="field__label">Form parameters</span>
                <input
                  className="input"
                  type="text"
                  value={formParams}
                  onChange={(e) => setFormParams(e.target.value)}
                  placeholder="username=^USER^&password=^PASS^"
                />
                <span className="field__hint">
                  Use <code>^USER^</code> and <code>^PASS^</code> where the username and password
                  should be substituted.
                </span>
              </label>
              <label className="field hydra-form-grid__wide">
                <span className="field__label">Cookies</span>
                <input
                  className="input"
                  type="text"
                  value={cookies}
                  onChange={(e) => setCookies(e.target.value)}
                  placeholder="session=abc123; csrftoken=xyz789"
                />
                <span className="field__hint">
                  Optional. Cookie header value to include with HTTP authentication attempts.
                </span>
              </label>
              <label className="field hydra-form-grid__wide">
                <span className="field__label">Failure condition</span>
                <input
                  className="input"
                  type="text"
                  value={failureCondition}
                  onChange={(e) => setFailureCondition(e.target.value)}
                  placeholder="incorrect"
                />
                <span className="field__hint">
                  Text that appears in the response when a login attempt fails (Hydra&apos;s{' '}
                  <code>F=</code> condition).
                </span>
              </label>
            </div>

            {formError && <div className="modal__error" role="alert">{formError}</div>}

            <div className="modal__actions">
              <button type="button" className="btn" onClick={onClose}>
                Cancel
              </button>
              <button type="button" className="btn btn--primary" onClick={handleStart}>
                Start Hydra
              </button>
            </div>
          </>
        )}

        {hydra.phase === 'error' && (
          <>
            <div className="modal__error" role="alert">
              {hydra.error}
            </div>
            <div className="modal__actions">
              <button type="button" className="btn btn--primary" onClick={onClose}>
                Close
              </button>
            </div>
          </>
        )}

        {(running || finished) && (
          <>
            <div className="run-progress">
              <span className="run-progress__mode">
                {running ? 'Running…' : hydra.phase === 'stopped' ? 'Stopped' : 'Finished'}
              </span>
              {hydra.exitCode != null && (
                <span className="run-progress__stat">Exit code: {hydra.exitCode}</span>
              )}
            </div>
            <pre ref={outputRef} className="hydra-output">
              {hydra.output.length > 0 ? hydra.output.join('\n') : 'Waiting for output…'}
            </pre>
            <div className="modal__actions">
              {running ? (
                <button type="button" className="btn" onClick={hydra.stop}>
                  Stop
                </button>
              ) : (
                <button type="button" className="btn btn--primary" onClick={onClose}>
                  Close
                </button>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
