# Release Scripts

Scripts untuk memudahkan pembuatan release dan deployment.

## 📦 GitHub Actions Workflows

### 1. Build APK on Push (`build.yml`)
- **Trigger**: Setiap push ke branch `main` atau `develop`
- **Fungsi**: 
  - Run lint checks
  - Run unit tests
  - Build debug APK
  - Upload APK sebagai artifact (tersimpan 7 hari)

### 2. Build and Release APK (`release.yml`)
- **Trigger**: 
  - Push tag dengan format `v*` (contoh: `v9.3.0`)
  - Manual trigger via GitHub Actions UI
- **Fungsi**:
  - Build debug dan release APK
  - Rename APK dengan version name
  - Upload ke GitHub Releases
  - Generate release notes otomatis

## 🚀 Cara Membuat Release

### Opsi 1: Menggunakan Script (Recommended)

#### Windows (PowerShell):
```powershell
.\scripts\create-release.ps1
```

#### Linux/Mac (Bash):
```bash
chmod +x scripts/create-release.sh
./scripts/create-release.sh
```

Script akan:
1. Menampilkan versi saat ini
2. Meminta input versi baru
3. Update `versionName` dan `versionCode` di `build.gradle.kts`
4. Commit perubahan
5. Membuat dan push tag
6. Trigger GitHub Actions untuk build dan release

### Opsi 2: Manual

1. **Update version di `app/build.gradle.kts`**:
   ```kotlin
   versionCode = 7  // increment by 1
   versionName = "9.3.0"  // new version
   ```

2. **Commit dan push**:
   ```bash
   git add app/build.gradle.kts
   git commit -m "Bump version to 9.3.0"
   git push origin main
   ```

3. **Buat dan push tag**:
   ```bash
   git tag -a v9.3.0 -m "Release version 9.3.0"
   git push origin v9.3.0
   ```

4. **Monitor GitHub Actions**:
   - Buka: https://github.com/Bsraccc1/daily-quran/actions
   - Tunggu workflow selesai (~5-10 menit)
   - APK akan tersedia di: https://github.com/Bsraccc1/daily-quran/releases

## 📥 Download APK

Setelah release dibuat, APK dapat didownload dari:
- **Releases page**: https://github.com/Bsraccc1/daily-quran/releases
- **Latest release**: https://github.com/Bsraccc1/daily-quran/releases/latest

Dua file akan tersedia:
- `daily-quran-X.X.X-debug.apk` - Debug build (untuk testing)
- `daily-quran-X.X.X-release-unsigned.apk` - Release build (belum signed)

## 🔧 Manual Trigger

Untuk trigger build tanpa membuat tag:

1. Buka: https://github.com/Bsraccc1/daily-quran/actions
2. Pilih workflow "Build and Release APK"
3. Klik "Run workflow"
4. Pilih branch
5. Klik "Run workflow"

APK akan tersedia sebagai artifact (bukan release).

## 📝 Version Naming Convention

Gunakan [Semantic Versioning](https://semver.org/):
- **MAJOR.MINOR.PATCH** (contoh: `9.3.0`)
- **MAJOR**: Breaking changes
- **MINOR**: New features (backward compatible)
- **PATCH**: Bug fixes

Contoh:
- `9.2.0` → `9.2.1` (bug fix)
- `9.2.1` → `9.3.0` (new feature)
- `9.3.0` → `10.0.0` (breaking change)

## 🐛 Troubleshooting

### Build gagal di GitHub Actions
1. Cek logs di Actions tab
2. Pastikan `gradlew` executable: `git update-index --chmod=+x gradlew`
3. Test build lokal: `./gradlew assembleRelease`

### Tag sudah ada
```bash
# Delete local tag
git tag -d v9.3.0

# Delete remote tag
git push origin :refs/tags/v9.3.0

# Create new tag
git tag -a v9.3.0 -m "Release version 9.3.0"
git push origin v9.3.0
```

### Script permission denied (Linux/Mac)
```bash
chmod +x scripts/create-release.sh
```
