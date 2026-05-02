# Script to create a new release tag and push to GitHub
# This will trigger the GitHub Actions workflow to build and upload APKs

$ErrorActionPreference = "Stop"

# Get current version from build.gradle.kts
$buildGradle = Get-Content "app/build.gradle.kts" -Raw
$currentVersion = [regex]::Match($buildGradle, 'versionName = "([^"]+)"').Groups[1].Value

Write-Host "Current version: $currentVersion" -ForegroundColor Green
Write-Host ""

# Ask for new version
$newVersion = Read-Host "Enter new version (e.g., 9.3.0)"

if ([string]::IsNullOrWhiteSpace($newVersion)) {
    Write-Host "Error: Version cannot be empty" -ForegroundColor Red
    exit 1
}

# Confirm
Write-Host ""
Write-Host "This will:" -ForegroundColor Yellow
Write-Host "  1. Update version in build.gradle.kts to $newVersion"
Write-Host "  2. Commit the version change"
Write-Host "  3. Create and push tag v$newVersion"
Write-Host "  4. Trigger GitHub Actions to build and release APKs"
Write-Host ""
$confirm = Read-Host "Continue? (y/n)"

if ($confirm -ne "y" -and $confirm -ne "Y") {
    Write-Host "Cancelled" -ForegroundColor Red
    exit 1
}

# Update version in build.gradle.kts
Write-Host "Updating version..." -ForegroundColor Green
$buildGradle = $buildGradle -replace 'versionName = "[^"]+"', "versionName = `"$newVersion`""

# Also update versionCode (increment by 1)
$currentCode = [regex]::Match($buildGradle, 'versionCode = (\d+)').Groups[1].Value
$newCode = [int]$currentCode + 1
$buildGradle = $buildGradle -replace "versionCode = $currentCode", "versionCode = $newCode"

Set-Content "app/build.gradle.kts" $buildGradle -NoNewline

Write-Host "Version updated to $newVersion (code: $newCode)" -ForegroundColor Green

# Commit version change
Write-Host "Committing version change..." -ForegroundColor Green
git add app/build.gradle.kts
git commit -m "Bump version to $newVersion"

# Create and push tag
Write-Host "Creating tag v$newVersion..." -ForegroundColor Green
git tag -a "v$newVersion" -m "Release version $newVersion"

Write-Host "Pushing to GitHub..." -ForegroundColor Green
git push origin main
git push origin "v$newVersion"

Write-Host ""
Write-Host "✓ Done!" -ForegroundColor Green
Write-Host ""
Write-Host "GitHub Actions will now build and create a release."
Write-Host "Check: https://github.com/Bsraccc1/daily-quran/actions"
Write-Host ""
