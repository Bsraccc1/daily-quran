"""Build bundled Mushaf page images (transparent WebP) for the Quran Reader.

Pipeline (run once, output is committed to the repo):

  1. Download 604 page PNGs from the public quran.app CDN
     (https://files.quran.app/hafs/madani/width_<W>/page<NNN>.png).
  2. Convert each from "white background + dark ink" to
     "transparent background + alpha-encoded ink" so the runtime can
     tint the calligraphy with any theme colour via Compose
     ColorFilter.tint(SrcIn) without ever showing a white card.
  3. Encode as lossless WebP for the smallest size that preserves the
     calligraphy's edges (lossy WebP introduces visible halos around
     Arabic letters because of their thin strokes).
  4. Drop the result into app/src/main/assets/mushaf_pages/.

We also pull `ayahinfo_<W>.db` so tap-to-highlight on a page knows
which rectangle belongs to which ayah. That database lives next to
the existing bundled DBs in app/src/main/assets/quran_data/.

Usage:
    python tools/build_mushaf_pages.py            # default width 1024
    python tools/build_mushaf_pages.py --width 1280
    python tools/build_mushaf_pages.py --no-download   # only re-encode
"""
from __future__ import annotations

import argparse
import concurrent.futures
import shutil
import sys
import time
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parent.parent
RAW_DIR = REPO / "tools" / ".cache" / "mushaf_pages_raw"
OUT_DIR = REPO / "app" / "src" / "main" / "assets" / "mushaf_pages"
DB_OUT = REPO / "app" / "src" / "main" / "assets" / "quran_data" / "ayahinfo.db"

CDN = "https://files.quran.app/hafs/madani"
TOTAL_PAGES = 604
USER_AGENT = "Mozilla/5.0 (QuranReaderBuild)"


def download_page(width: int, page: int) -> Path:
    """Download one PNG page; return cached path. Skip if cached & non-empty."""
    target = RAW_DIR / f"page{page:03d}.png"
    if target.exists() and target.stat().st_size > 0:
        return target
    url = f"{CDN}/width_{width}/page{page:03d}.png"
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=60) as resp:
        data = resp.read()
    target.write_bytes(data)
    return target


def download_ayahinfo(width: int) -> None:
    """Download ayahinfo_<W>.zip, extract the .db, and rebuild it for Room.

    The CDN ships the database zipped (~2 MB instead of ~6-8 MB on the
    wire). The zip contains a single file named `ayahinfo_<W>.db` whose
    schema looks like::

        CREATE TABLE glyphs(glyph_id INT PRIMARY KEY, ...);
        CREATE INDEX page_idx ON glyphs(page_number);
        CREATE INDEX sura_ayah_idx ON glyphs(sura_number, ayah_number);

    Room's pre-packaged-database validator is strict about column type
    NAMES — it wants `INTEGER`, not `INT`. We reopen the db, copy all
    rows into a fresh table that uses the canonical Room schema, drop
    the old table, then VACUUM. The output lives at a width-agnostic
    name (`ayahinfo.db`) so the runtime doesn't care which resolution
    was bundled.
    """
    import io, sqlite3, tempfile, zipfile

    DB_OUT.parent.mkdir(parents=True, exist_ok=True)
    if DB_OUT.exists() and DB_OUT.stat().st_size > 100_000:
        print(f"  [cache] {DB_OUT.name}")
        return

    url = f"{CDN}/databases/ayahinfo/ayahinfo_{width}.zip"
    print(f"  GET {url}")
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(req, timeout=120) as resp:
        zip_bytes = resp.read()
    with zipfile.ZipFile(io.BytesIO(zip_bytes)) as zf:
        names = zf.namelist()
        db_name = next((n for n in names if n.endswith(".db")), None)
        if db_name is None:
            raise RuntimeError(f"ayahinfo zip contained no .db: {names}")
        raw_bytes = zf.read(db_name)

    # Drop the raw bytes into a temp file, rebuild into DB_OUT.
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as tf:
        tf.write(raw_bytes)
        raw_path = tf.name

    if DB_OUT.exists():
        DB_OUT.unlink()

    src = sqlite3.connect(raw_path)
    dst = sqlite3.connect(str(DB_OUT))
    dst.executescript(
        """
        CREATE TABLE glyphs (
            glyph_id INTEGER NOT NULL PRIMARY KEY,
            page_number INTEGER NOT NULL,
            line_number INTEGER NOT NULL,
            sura_number INTEGER NOT NULL,
            ayah_number INTEGER NOT NULL,
            position INTEGER NOT NULL,
            min_x INTEGER NOT NULL,
            max_x INTEGER NOT NULL,
            min_y INTEGER NOT NULL,
            max_y INTEGER NOT NULL
        );
        CREATE INDEX page_idx ON glyphs(page_number);
        CREATE INDEX sura_ayah_idx ON glyphs(sura_number, ayah_number);
        """
    )
    rows = src.execute(
        "SELECT glyph_id, page_number, line_number, sura_number, ayah_number,"
        "       position, min_x, max_x, min_y, max_y FROM glyphs"
    ).fetchall()
    dst.executemany(
        "INSERT INTO glyphs (glyph_id, page_number, line_number, sura_number, ayah_number,"
        "                    position, min_x, max_x, min_y, max_y)"
        " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        rows,
    )
    # Room reads room_master_table to verify the schema hash; without it
    # Room will treat the DB as an unknown legacy schema and require an
    # ON CREATE callback. Easier route: leave the table out and let
    # `fallbackToDestructiveMigrationOnDowngrade()` accept it on first
    # open. Either works because this DB is read-only at runtime.
    dst.commit()
    dst.execute("VACUUM")
    dst.close()
    src.close()
    Path(raw_path).unlink(missing_ok=True)
    print(f"  rebuilt {DB_OUT.relative_to(REPO)} "
          f"({DB_OUT.stat().st_size/1_000_000:.1f} MB, {len(rows)} glyphs)")


def whiten_to_transparent(src: Path, dst: Path) -> int:
    """Read a white-bg + black-ink page, output transparent-bg WebP.

    Algorithm:
      alpha = 255 - mean(R, G, B)          # darker pixels -> more opaque
      rgb   = (0, 0, 0)                    # any colour works; runtime tints

    Lossless WebP keeps the calligraphy razor-sharp while still being
    ~30% smaller than the original PNG.
    """
    img = Image.open(src).convert("RGB")
    arr = np.asarray(img, dtype=np.int32)
    lum = (arr[..., 0] + arr[..., 1] + arr[..., 2]) // 3
    alpha = (255 - lum).astype(np.uint8)
    rgba = np.zeros((arr.shape[0], arr.shape[1], 4), dtype=np.uint8)
    rgba[..., 3] = alpha
    out = Image.fromarray(rgba, mode="RGBA")
    # method=4 is the sweet spot: ~5x faster than method=6 and only
    # ~5% larger output. quality=100 with lossless=True keeps strokes
    # razor-sharp; we don't want to introduce halos around the alif.
    out.save(dst, format="WEBP", lossless=True, method=4, quality=100)
    return dst.stat().st_size


def process(args: argparse.Namespace) -> int:
    RAW_DIR.mkdir(parents=True, exist_ok=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    if not args.no_download:
        print(f"[1/3] Downloading {TOTAL_PAGES} mushaf pages (width={args.width}) ...")
        t0 = time.time()
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as pool:
            futures = [
                pool.submit(download_page, args.width, p)
                for p in range(1, TOTAL_PAGES + 1)
            ]
            for i, fut in enumerate(concurrent.futures.as_completed(futures), 1):
                fut.result()  # raises if download failed
                if i % 50 == 0 or i == TOTAL_PAGES:
                    print(f"  downloaded {i}/{TOTAL_PAGES}")
        print(f"  done in {time.time() - t0:.1f}s")
    else:
        print("[1/3] --no-download, skipping page fetch")

    # ayahinfo is always (re)built if missing — its rebuild is cheap and
    # it's needed for tap/highlight detection so the app can't run
    # without it.
    print("[2/3] Building ayahinfo.db ...")
    download_ayahinfo(args.width)

    print(f"[3/3] Re-encoding {TOTAL_PAGES} pages as transparent WebP ...")
    t0 = time.time()
    total_bytes = 0
    re_encoded = 0
    for p in range(1, TOTAL_PAGES + 1):
        src = RAW_DIR / f"page{p:03d}.png"
        dst = OUT_DIR / f"page_{p:03d}.webp"
        if not src.exists():
            print(f"  MISSING raw page {p}", file=sys.stderr)
            continue
        # Skip pages whose webp already exists and is newer than the
        # source PNG — saves ~4 minutes when re-running for ayahinfo
        # changes only.
        if dst.exists() and dst.stat().st_mtime >= src.stat().st_mtime and not args.force_encode:
            total_bytes += dst.stat().st_size
        else:
            total_bytes += whiten_to_transparent(src, dst)
            re_encoded += 1
        if p % 50 == 0 or p == TOTAL_PAGES:
            print(f"  processed {p}/{TOTAL_PAGES}  total={total_bytes/1_000_000:.1f} MB  re-encoded={re_encoded}")
    print(f"  done in {time.time() - t0:.1f}s")
    print(f"  bundled {TOTAL_PAGES} pages = {total_bytes/1_000_000:.1f} MB into {OUT_DIR.relative_to(REPO)}")
    return 0


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--width", type=int, default=1024,
                    help="page image width to fetch from the CDN (default 1024)")
    ap.add_argument("--no-download", action="store_true",
                    help="skip downloading PNG pages (ayahinfo is always rebuilt if missing)")
    ap.add_argument("--force-encode", action="store_true",
                    help="re-encode all WebP pages even if they already exist and are up-to-date")
    return process(ap.parse_args())


if __name__ == "__main__":
    sys.exit(main())
