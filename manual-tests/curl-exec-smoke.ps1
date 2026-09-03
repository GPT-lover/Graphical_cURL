# curl-exec-smoke.ps1  —  manual smoke test for the new curl.exe execution path.
#
# It runs curl.exe with the SAME flags CurlProcessExecutor uses
#   --disable --silent --show-error --output <body> --dump-header <hdr>
#   --write-out "%{http_code} %{http_version} %{size_download} %{time_total} %{num_redirects}"
#   --connect-timeout 10 --max-time 30 --request <M> (-H ...)* [--data-binary @<file>] --url <url>
# so what you see here is what the backend will produce for the same request.
#
# It never prints your auth cookie. Test F reads it from $env:GROOVES_COOKIE
# (full "name=value" string). Nothing sensitive is written to disk except the
# temp request-body file, which is deleted.
#
#   Test A  simple GET
#   Test B  POST JSON (body round-trips)
#   Test C  custom headers survive
#   Test D  cookies survive
#   Test E  binary response not corrupted (PNG magic bytes + byte count)
#   Test F  the previously-failing Vercel request (needs $env:GROOVES_COOKIE)
#
# Usage:
#   pwsh manual-tests/curl-exec-smoke.ps1
#   $env:GROOVES_COOKIE = 'sb-...-auth-token=base64-...'; pwsh manual-tests/curl-exec-smoke.ps1

$ErrorActionPreference = 'Stop'
$curl = (Get-Command curl.exe -ErrorAction SilentlyContinue)?.Source
if (-not $curl) { Write-Error 'curl.exe not found on PATH'; exit 1 }
Write-Host "curl.exe: $curl"
& $curl --version | Select-Object -First 1
Write-Host ('-' * 70)

function Invoke-CurlLikeApp {
    param(
        [string]$Method, [string]$Url,
        [string[]]$Headers = @(),
        [string]$Body = $null,
        [string]$CookieHeader = $null
    )
    $dir  = New-Item -ItemType Directory -Path (Join-Path $env:TEMP ("curlsmoke-" + [guid]::NewGuid()))
    $bodyF = Join-Path $dir 'body'; $hdrF = Join-Path $dir 'headers'; $dataF = $null
    $args = @('--disable','--silent','--show-error',
              '--output', $bodyF, '--dump-header', $hdrF,
              '--write-out', "`n%{http_code} %{http_version} %{size_download} %{time_total} %{num_redirects}`n",
              '--connect-timeout','10','--max-time','30')
    if ($Method -eq 'HEAD') { $args += '--head' } else { $args += @('--request', $Method) }
    foreach ($h in $Headers) { $args += @('--header', $h) }
    if ($CookieHeader) { $args += @('--header', "Cookie: $CookieHeader") }
    if ($Body -ne $null -and $Method -ne 'HEAD') {
        $dataF = Join-Path $dir 'data'
        [System.IO.File]::WriteAllText($dataF, $Body, (New-Object System.Text.UTF8Encoding($false)))
        $args += @('--data-binary', "@$dataF")
    }
    $args += @('--url', $Url)

    $stdout = & $curl @args
    $exit = $LASTEXITCODE
    $meta = ($stdout -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 1).Trim() -split '\s+'
    $hdrText = if (Test-Path $hdrF) { Get-Content $hdrF -Raw } else { '' }
    $bodyBytes = if (Test-Path $bodyF) { [System.IO.File]::ReadAllBytes($bodyF) } else { @() }
    Remove-Item $dir -Recurse -Force -ErrorAction SilentlyContinue

    [pscustomobject]@{
        ExitCode   = $exit
        Status     = if ($meta.Count -ge 1) { [int]$meta[0] } else { $null }
        HttpVer    = if ($meta.Count -ge 2) { $meta[1] } else { $null }
        SizeDown   = if ($meta.Count -ge 3) { [long]$meta[2] } else { $null }
        Headers    = $hdrText
        BodyBytes  = $bodyBytes
        BodyText   = [System.Text.Encoding]::UTF8.GetString($bodyBytes)
    }
}

function Show($name, $r, [int]$preview = 300) {
    Write-Host "### $name"
    Write-Host "  exit=$($r.ExitCode) status=$($r.Status) http=$($r.HttpVer) bytes=$($r.SizeDown)"
    $firstHdrs = ($r.Headers -split "`r?`n" | Select-Object -First 6) -join "`n    "
    Write-Host "  headers:`n    $firstHdrs"
    if ($r.BodyText) { Write-Host "  body: $($r.BodyText.Substring(0, [Math]::Min($preview, $r.BodyText.Length)))" }
    Write-Host ('-' * 70)
}

# ---- Test A ---------------------------------------------------------------
Show 'A  simple GET  (httpbin /get)' (Invoke-CurlLikeApp -Method GET -Url 'https://httpbin.org/get')

# ---- Test B ---------------------------------------------------------------
$b = Invoke-CurlLikeApp -Method POST -Url 'https://httpbin.org/post' `
        -Headers @('content-type: application/json') -Body '{"hello":"world","n":42}'
Show 'B  POST JSON  (echo shows "json": {...})' $b
if ($b.BodyText -notmatch '"hello"\s*:\s*"world"') { Write-Warning 'B: JSON body did not round-trip' }

# ---- Test C ---------------------------------------------------------------
$c = Invoke-CurlLikeApp -Method GET -Url 'https://httpbin.org/headers' `
        -Headers @('x-smoke-a: one', 'x-smoke-b: two words', 'accept: application/json')
Show 'C  custom headers' $c
if ($c.BodyText -notmatch 'two words') { Write-Warning 'C: custom header not seen by server' }

# ---- Test D ---------------------------------------------------------------
$d = Invoke-CurlLikeApp -Method GET -Url 'https://httpbin.org/cookies' -CookieHeader 'sb-smoke=abc123; theme=dark'
Show 'D  cookies' $d
if ($d.BodyText -notmatch 'abc123') { Write-Warning 'D: cookie not seen by server' }

# ---- Test E ---------------------------------------------------------------
$e = Invoke-CurlLikeApp -Method GET -Url 'https://httpbin.org/image/png'
$png = ($e.BodyBytes.Length -gt 8 -and $e.BodyBytes[0] -eq 0x89 -and $e.BodyBytes[1] -eq 0x50 `
        -and $e.BodyBytes[2] -eq 0x4E -and $e.BodyBytes[3] -eq 0x47)
Write-Host "### E  binary response (PNG)"
Write-Host "  exit=$($e.ExitCode) status=$($e.Status) bytes=$($e.BodyBytes.Length) size_download=$($e.SizeDown)"
Write-Host "  PNG magic bytes intact: $png    (byte count matches size_download: $($e.BodyBytes.Length -eq $e.SizeDown))"
Write-Host ('-' * 70)

# ---- Test F ------------------------------------------------------------------
if ($env:GROOVES_COOKIE) {
    $headers = @(
        'accept: text/x-component',
        'accept-language: en-US,en;q=0.9',
        'cache-control: no-cache',
        'content-type: text/plain;charset=UTF-8',
        'origin: https://grooves-web.vercel.app',
        'pragma: no-cache',
        'priority: u=1, i',
        'referer: https://grooves-web.vercel.app/track/1442954500',
        'sec-ch-ua: "Not=A?Brand";v="99", "Google Chrome";v="151", "Chromium";v="151"',
        'sec-ch-ua-mobile: ?0',
        'sec-ch-ua-platform: "Windows"',
        'sec-fetch-dest: empty',
        'sec-fetch-mode: cors',
        'sec-fetch-site: same-origin',
        'user-agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36'
    )
    $f = Invoke-CurlLikeApp -Method POST -Url 'https://grooves-web.vercel.app/track/1442954500' `
            -Headers $headers -Body '["1442954500",false]' -CookieHeader $env:GROOVES_COOKIE
    Write-Host "### F  grooves-web.vercel.app  (via real curl.exe, same flags as the app)"
    Write-Host "  exit=$($f.ExitCode) status=$($f.Status) http=$($f.HttpVer) bytes=$($f.SizeDown)"
    $mit = ($f.Headers -split "`r?`n" | Where-Object { $_ -match '^(?i)x-vercel-mitigated:' })
    Write-Host "  x-vercel-mitigated: $(if ($mit) { ($mit -replace '(?i)^x-vercel-mitigated:\s*','') } else { '(absent)' })"
    Write-Host "  --> expected: same status your CLI curl gets (NOT the Java-HttpClient 429/challenge)"
} else {
    Write-Host "### F  skipped — set `$env:GROOVES_COOKIE to the full 'name=value' cookie string to run it"
}
Write-Host ('-' * 70)
Write-Host "Done. To test the real app end to end: start the backend + frontend, Import this cURL,"
Write-Host "press Send once, and confirm the Response panel shows the same status as your CLI curl."
