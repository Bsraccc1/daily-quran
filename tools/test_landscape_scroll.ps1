# =============================================================
# test_landscape_scroll.ps1
# Multi-device landscape scroll regression test
# Usage:
#   .\tools\test_landscape_scroll.ps1                    # all connected devices
#   .\tools\test_landscape_scroll.ps1 -Devices RR8Y...   # specific device(s)
# =============================================================
param(
    [string[]]$Devices = @(),
    [int]$Page = 6,                       # page to land on for scroll test
    [string]$Pkg = "com.quranreader.custom",
    [string]$Activity = ".ui.MainActivity",
    [string]$OutDir = "screenshots/multi"
)

function Get-Devices {
    if ($Devices.Count -gt 0) { return $Devices }
    return (adb devices) `
        | Select-String -Pattern '^\S+\s+device$' `
        | ForEach-Object { ($_ -split '\s+')[0] }
}

function Invoke-Adb { param([string]$Serial, [string]$Cmd)
    $args = @("-s", $Serial) + ($Cmd -split ' ')
    & adb @args
}

function Get-DeviceModel { param([string]$Serial)
    (adb -s $Serial shell getprop ro.product.model).Trim()
}

function Force-Landscape { param([string]$Serial)
    adb -s $Serial shell settings put system accelerometer_rotation 0 | Out-Null
    adb -s $Serial shell settings put system user_rotation 1 | Out-Null
}

function Force-Portrait { param([string]$Serial)
    adb -s $Serial shell settings put system user_rotation 0 | Out-Null
    adb -s $Serial shell settings put system accelerometer_rotation 1 | Out-Null
}

# In-app orientation toggle: the reader's bottom action panel has a
# rotate-icon that cycles AUTO → PORTRAIT → LANDSCAPE → AUTO. The panel
# only appears AFTER the user taps an ayah glyph, so we tap the page
# centre first, then look up the toggle button by its content-desc and
# tap it as many times as needed to reach LANDSCAPE.
function Set-InAppLandscape { param([string]$Serial)
    $s = Get-ScreenSize $Serial
    # Tap page centre to summon the bottom action panel
    $cx = [int]($s.W / 2)
    $cy = [int]($s.H / 2)
    adb -s $Serial shell input tap $cx $cy | Out-Null
    Start-Sleep -Milliseconds 800
    for ($i = 0; $i -lt 3; $i++) {
        $xml = Get-UiXml $Serial
        if ($xml -match 'content-desc="Orientation: ([^"]+)"[^/]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
            $current = $matches[1]
            $bx = [int](([int]$matches[2] + [int]$matches[4]) / 2)
            $by = [int](([int]$matches[3] + [int]$matches[5]) / 2)
            Write-Host "    orientation = $current at ($bx,$by)" -ForegroundColor DarkGray
            if ($current -match 'Landscape') { return $true }
            adb -s $Serial shell input tap $bx $by | Out-Null
            Start-Sleep -Milliseconds 700
        } else {
            Write-Host "    (orientation toggle not found yet, retrying tap on page)" -ForegroundColor DarkGray
            adb -s $Serial shell input tap $cx $cy | Out-Null
            Start-Sleep -Milliseconds 800
        }
    }
    return $false
}

function Reset-InAppOrientation { param([string]$Serial)
    # Cycle once more from LANDSCAPE → AUTO
    $xml = Get-UiXml $Serial
    if ($xml -match 'content-desc="Orientation: ([^"]+)"[^/]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"') {
        $bx = [int](([int]$matches[2] + [int]$matches[4]) / 2)
        $by = [int](([int]$matches[3] + [int]$matches[5]) / 2)
        adb -s $Serial shell input tap $bx $by | Out-Null
    }
}

function Launch-App { param([string]$Serial)
    adb -s $Serial shell am force-stop $Pkg | Out-Null
    Start-Sleep -Milliseconds 300
    adb -s $Serial shell am start -n "$Pkg/$Activity" | Out-Null
}

# Try multiple dump strategies (Samsung compresses, some devices need /data/local/tmp).
function Get-UiXml { param([string]$Serial)
    $candidates = @(
        @{ dump = "uiautomator dump --compressed /sdcard/__ui.xml"; path = "/sdcard/__ui.xml" },
        @{ dump = "uiautomator dump /sdcard/__ui.xml";              path = "/sdcard/__ui.xml" },
        @{ dump = "uiautomator dump /data/local/tmp/__ui.xml";      path = "/data/local/tmp/__ui.xml" }
    )
    foreach ($c in $candidates) {
        $out = adb -s $Serial shell $c.dump 2>&1
        if ($out -match 'dumped to') {
            return (adb -s $Serial shell cat $c.path) -join "`n"
        }
    }
    return ""
}

# Find a clickable element by content-desc or text and tap its center.
function Tap-ByText { param([string]$Serial, [string[]]$Patterns)
    $xml = Get-UiXml $Serial
    if (-not $xml) { return $false }
    foreach ($p in $Patterns) {
        $rx = "(?:text|content-desc)=`"[^`"]*$([Regex]::Escape($p))[^`"]*`"[^/]*?bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`""
        if ($xml -match $rx) {
            $cx = [int](([int]$matches[1] + [int]$matches[3]) / 2)
            $cy = [int](([int]$matches[2] + [int]$matches[4]) / 2)
            adb -s $Serial shell input tap $cx $cy | Out-Null
            return $true
        }
    }
    return $false
}

# Fallback: tap at a percentage of screen size (Continue Session sits ~52% vertical)
function Tap-ByRatio { param([string]$Serial, [double]$Rx, [double]$Ry)
    $s = Get-ScreenSize $Serial
    $cx = [int]($s.W * $Rx)
    $cy = [int]($s.H * $Ry)
    adb -s $Serial shell input tap $cx $cy | Out-Null
}

function Enter-Reader { param([string]$Serial)
    # 1) Try text-based tap (works when uiautomator dump succeeds)
    if (Tap-ByText $Serial @("Continue Session", "Lanjutkan Sesi")) { return }
    Start-Sleep -Milliseconds 600
    if (Tap-ByText $Serial @("Start New Session", "Mulai Sesi Baru")) { return }
    # 2) Fallback: percentage tap on the Continue Session CTA position
    Write-Host "  (UI dump failed; using percentage-tap fallback)" -ForegroundColor DarkYellow
    Tap-ByRatio $Serial 0.5 0.52
}

function Get-ScreenSize { param([string]$Serial)
    $line = adb -s $Serial shell wm size
    if ($line -match 'Physical size:\s*(\d+)x(\d+)') {
        return @{ W = [int]$matches[1]; H = [int]$matches[2] }
    }
    return @{ W = 1080; H = 1920 }
}

# Slow swipe = scroll (NOT a fling, NOT a tap). Duration > touch slop time.
function Swipe-Up { param([string]$Serial, [int]$Duration = 500)
    $s = Get-ScreenSize $Serial
    # In landscape, width and height are swapped. Use shorter dimension as Y.
    $cx = [int]($s.H / 2)   # landscape width = original height
    $y1 = [int]($s.W * 0.75)
    $y2 = [int]($s.W * 0.25)
    adb -s $Serial shell input swipe $cx $y1 $cx $y2 $Duration | Out-Null
}

function Swipe-Down { param([string]$Serial, [int]$Duration = 500)
    $s = Get-ScreenSize $Serial
    $cx = [int]($s.H / 2)
    $y1 = [int]($s.W * 0.25)
    $y2 = [int]($s.W * 0.75)
    adb -s $Serial shell input swipe $cx $y1 $cx $y2 $Duration | Out-Null
}

function Capture { param([string]$Serial, [string]$Tag)
    $model = Get-DeviceModel $Serial
    $safe  = ($model -replace '[^a-zA-Z0-9]', '_')
    $file  = Join-Path $OutDir "${safe}_${Tag}.png"
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
    adb -s $Serial shell screencap -p /sdcard/__sc.png | Out-Null
    adb -s $Serial pull /sdcard/__sc.png $file 2>$null | Out-Null
    Write-Host "  saved: $file"
}

function Test-Device { param([string]$Serial)
    $model = Get-DeviceModel $Serial
    Write-Host ""
    Write-Host "=== $Serial ($model) ===" -ForegroundColor Cyan
    Launch-App $Serial
    Start-Sleep -Seconds 4
    Write-Host "Entering reader..." -ForegroundColor Yellow
    Enter-Reader $Serial
    Start-Sleep -Seconds 3
    Write-Host "Toggling orientation to LANDSCAPE via in-app slider..." -ForegroundColor Yellow
    if (-not (Set-InAppLandscape $Serial)) {
        Write-Host "  WARNING: orientation toggle did not reach LANDSCAPE" -ForegroundColor Red
    }
    Start-Sleep -Seconds 2
    # Tap page centre to dismiss any selection highlight + panel,
    # so the swipe gestures hit the scrollable page area cleanly.
    $s = Get-ScreenSize $Serial
    adb -s $Serial shell input tap ([int]($s.H / 2)) ([int]($s.W / 2)) | Out-Null
    Start-Sleep -Milliseconds 600
    Capture $Serial "01_initial_landscape"
    Write-Host "Swipe up (scroll down)..." -ForegroundColor Yellow
    Swipe-Up $Serial 600
    Start-Sleep -Seconds 1
    Capture $Serial "02_after_scroll_down"
    Write-Host "Swipe down (scroll up)..." -ForegroundColor Yellow
    Swipe-Down $Serial 600
    Start-Sleep -Seconds 1
    Capture $Serial "03_after_scroll_up"
    # Reset orientation back to AUTO (one more cycle from LANDSCAPE)
    Reset-InAppOrientation $Serial
    Write-Host "  done." -ForegroundColor Green
}

# === Main ===
$serials = Get-Devices
Write-Host "Targets: $($serials -join ', ')" -ForegroundColor White
foreach ($s in $serials) { Test-Device $s }

Write-Host ""
Write-Host "All devices done. Screenshots in: $OutDir" -ForegroundColor Green
