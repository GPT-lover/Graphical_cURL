# Packaging resources

`electron-builder` reads build-time assets from this directory
(`directories.buildResources` in `electron-builder.yml`).

## Application icon (optional)

Drop a **256×256** (or multi-size) `icon.ico` here, then uncomment the
`win.icon` line in `electron-builder.yml`:

```yaml
win:
  icon: electron/resources/icon.ico
```

Without it, electron-builder uses the default Electron icon. The build does not
fail if the icon is missing.

## Custom NSIS script (optional)

An `installer.nsh` here is picked up automatically by the `nsis` target if you
need to customise the installer later.
