<#
.SYNOPSIS
    Fetches the complete Tanzil Uthmani Quran text and writes it as a
    seeder-compatible JSON file at app/src/main/assets/quran_data/quran_uthmani.json.

.DESCRIPTION
    The translation reader's ArabicVerseSeeder consumes a flat array of
    `{ s: <surahNumber>, a: <ayahNumber>, t: <text> }` entries. The
    bundled fixture only covered Al-Fatihah 1..3, which made every juz
    past Al-Fatihah render the "Arabic text not yet bundled" empty
    state. This script populates the full 6,236-ayah set in one shot so
    the translation reader works for every juz offline.

    Source: api.alquran.cloud (mirrors the Tanzil project's
    `quran-uthmani` edition, already credited under
    assets/quran_data/LICENSE-tanzil.txt).

    The script is idempotent — re-running overwrites the JSON.

.PARAMETER OutFile
    Override the output path (defaults to the seeder's expected location).

.PARAMETER Source
    Either `alquran-cloud` (default, network) or a path to a Tanzil
    plain-text file already on disk (`s|a|t` per line).
#>
[CmdletBinding()]
param(
    [string]$OutFile,
    [ValidateSet('alquran-cloud', 'tanzil-file')]
    [string]$Source = 'alquran-cloud',
    [string]$TanzilFile
)

$ErrorActionPreference = 'Stop'

# ── Resolve & validate output ──────────────────────────────────────────
# Default to the seeder's expected asset path. We resolve $PSScriptRoot
# inside the script body (not the param default) because some shells
# evaluate param defaults before $PSScriptRoot is populated, which
# silently turned ".../app/..." into "C:\app\..." in earlier runs.
if (-not $OutFile) {
    if (-not $PSScriptRoot) {
        $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
    } else {
        $scriptDir = $PSScriptRoot
    }
    $OutFile = Join-Path $scriptDir '..\app\src\main\assets\quran_data\quran_uthmani.json'
}
$OutFile = [System.IO.Path]::GetFullPath($OutFile)
$outDir = Split-Path -Parent $OutFile
if (-not (Test-Path $outDir)) {
    Write-Host "Creating output directory: $outDir"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
}

# ── Fetch ──────────────────────────────────────────────────────────────
$entries = New-Object System.Collections.Generic.List[object]

switch ($Source) {
    'alquran-cloud' {
        $apiUrl = 'https://api.alquran.cloud/v1/quran/quran-uthmani'
        Write-Host "Fetching $apiUrl ..."

        # Force TLS 1.2+ (older PowerShell sessions default to TLS 1.0).
        [Net.ServicePointManager]::SecurityProtocol =
            [Net.SecurityProtocolType]::Tls12 -bor [Net.SecurityProtocolType]::Tls11

        $response = Invoke-RestMethod -Uri $apiUrl -Method Get -TimeoutSec 60
        if ($response.code -ne 200) {
            throw "API responded with code=$($response.code) status=$($response.status)"
        }

        foreach ($surah in $response.data.surahs) {
            foreach ($ayah in $surah.ayahs) {
                $entries.Add([pscustomobject]@{
                    s = [int]$surah.number
                    a = [int]$ayah.numberInSurah
                    t = [string]$ayah.text
                })
            }
        }
    }

    'tanzil-file' {
        if (-not $TanzilFile -or -not (Test-Path $TanzilFile)) {
            throw "Pass -TanzilFile <path-to-quran-uthmani.txt> when using tanzil-file source."
        }
        Write-Host "Reading $TanzilFile ..."
        Get-Content -Path $TanzilFile -Encoding UTF8 | ForEach-Object {
            $line = $_.Trim()
            if ([string]::IsNullOrWhiteSpace($line)) { return }
            if ($line.StartsWith('#')) { return }
            $parts = $line.Split('|', 3)
            if ($parts.Length -lt 3) { return }
            $entries.Add([pscustomobject]@{
                s = [int]$parts[0]
                a = [int]$parts[1]
                t = $parts[2]
            })
        }
    }
}

# ── Sanity check ───────────────────────────────────────────────────────
$expected = 6236
if ($entries.Count -ne $expected) {
    Write-Warning "Expected $expected ayahs, got $($entries.Count). Continuing anyway."
} else {
    Write-Host "Got $expected ayahs (matches Tanzil reference)."
}

$bySurah = $entries | Group-Object -Property s | Sort-Object -Property { [int]$_.Name }
Write-Host "Surahs covered: $($bySurah.Count) (expected 114)"

# ── Write JSON ─────────────────────────────────────────────────────────
Write-Host "Writing $OutFile ..."

# Build the JSON manually so we get one ayah per line (better diffability
# in git than a one-line ConvertTo-Json -Compress) and stable property
# order matching the existing fixture: s, a, t.
$sb = [System.Text.StringBuilder]::new()
[void]$sb.Append('[')
$first = $true
foreach ($e in $entries) {
    if (-not $first) { [void]$sb.Append(',') }
    $first = $false
    [void]$sb.AppendLine()
    [void]$sb.Append('  {"s":')
    [void]$sb.Append($e.s)
    [void]$sb.Append(',"a":')
    [void]$sb.Append($e.a)
    [void]$sb.Append(',"t":')
    # JSON-escape the Arabic text — Newtonsoft.Json is bundled with .NET on Windows.
    $escaped = [System.Web.HttpUtility]::JavaScriptStringEncode($e.t, $true)
    [void]$sb.Append($escaped)
    [void]$sb.Append('}')
}
[void]$sb.AppendLine()
[void]$sb.Append(']')
[void]$sb.AppendLine()

# UTF-8 *without* BOM — Android's AssetManager reads bytes, the BOM would
# leak into the first ayah's text and corrupt rendering of "بِسْمِ".
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($OutFile, $sb.ToString(), $utf8NoBom)

$kb = [math]::Round((Get-Item $OutFile).Length / 1024, 1)
Write-Host "Wrote $($entries.Count) ayahs ($kb KB) to $OutFile"
