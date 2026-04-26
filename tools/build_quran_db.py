"""
Build bundled Quran SQLite database (quran_uthmani.db) for Quran Reader v4.0.

Sources:
  - alquran.cloud API (Tanzil text, public-domain CC-BY-3.0): full Quran text + page numbers
  - quran.com API (chapters metadata): surah metadata

Output: app/src/main/assets/quran_data/quran_uthmani.db (~6 MB target)

Schema:
  surahs          (id, name_arabic, name_transliterated, name_translated_en,
                   name_translated_id, ayah_count, revelation_type,
                   revelation_order, sajdah_count)
  ayahs           (id, surah_id, ayah_number, text_uthmani, text_simple,
                   juz_id, hizb_id, page_number, line_number, sajdah)
  mushaf_pages    (page_number PK, juz_id, hizb_id, surah_start_id,
                   ayah_start_id, surah_end_id, ayah_end_id)
  mushaf_lines    (page_number, line_number, line_type, surah_id, ayah_start,
                   ayah_end, line_text)  -- composite PK
  juzs            (id, name_arabic, page_start, page_end)
  hizbs           (id, juz_id, page_start, page_end)
  schema_versions (version, applied_at)

Usage:
  python tools/build_quran_db.py
"""
import json
import os
import sqlite3
import sys
import time
import urllib.request
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
ASSETS_DIR = REPO / "app" / "src" / "main" / "assets" / "quran_data"
ASSETS_DIR.mkdir(parents=True, exist_ok=True)
OUT_DB = ASSETS_DIR / "quran_uthmani.db"
CACHE_DIR = REPO / "tools" / ".cache"
CACHE_DIR.mkdir(parents=True, exist_ok=True)


def fetch_json(url, cache_name=None, headers=None, retries=3):
    """Fetch JSON, cache on disk."""
    cache = CACHE_DIR / (cache_name or "_temp.json") if cache_name else None
    if cache and cache.exists():
        print(f"  [cache] {cache_name}")
        return json.loads(cache.read_text(encoding="utf-8"))
    headers = headers or {"User-Agent": "Mozilla/5.0 (QuranReaderBuild)"}
    last_err = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(url, headers=headers)
            with urllib.request.urlopen(req, timeout=60) as r:
                data = json.load(r)
            if cache:
                cache.write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
            return data
        except Exception as e:
            last_err = e
            print(f"  attempt {attempt+1} failed: {e}")
            time.sleep(2 ** attempt)
    raise RuntimeError(f"Failed to fetch {url}: {last_err}")


def fetch_uthmani_quran():
    """Fetch full Quran in Uthmani script with page/juz/hizb info from alquran.cloud."""
    print("[1/4] Fetching Quran Uthmani text (alquran.cloud)...")
    data = fetch_json("http://api.alquran.cloud/v1/quran/quran-uthmani",
                       cache_name="quran_uthmani.json")
    return data["data"]["surahs"]


def fetch_simple_quran():
    """Fetch Quran in simple (Tashkeel-stripped) script for search/comparison."""
    print("[2/4] Fetching Quran simple text (alquran.cloud)...")
    data = fetch_json("http://api.alquran.cloud/v1/quran/quran-simple-clean",
                       cache_name="quran_simple.json")
    return data["data"]["surahs"]


def fetch_chapters_meta():
    """Fetch chapter metadata from quran.com (transliterated names + Indonesian)."""
    print("[3/4] Fetching chapter metadata (quran.com)...")
    en = fetch_json("https://api.quran.com/api/v4/chapters?language=en",
                    cache_name="chapters_en.json")
    id_data = fetch_json("https://api.quran.com/api/v4/chapters?language=id",
                         cache_name="chapters_id.json")
    return en["chapters"], id_data["chapters"]


def build_database(surahs_uthmani, surahs_simple, chapters_en, chapters_id):
    """Build the SQLite database from fetched data."""
    print("[4/4] Building SQLite database...")

    if OUT_DB.exists():
        OUT_DB.unlink()

    con = sqlite3.connect(str(OUT_DB))
    cur = con.cursor()

    # Schema. NOTE: Room expects `id INTEGER PRIMARY KEY NOT NULL` (the
    # SQLite implicit nullability of INTEGER PRIMARY KEY without explicit
    # NOT NULL trips Room's schema validator). All sajdah_count / sajdah
    # columns omit DEFAULT to match the Room entity declarations exactly.
    cur.executescript("""
        CREATE TABLE schema_versions (
            version INTEGER NOT NULL PRIMARY KEY,
            applied_at INTEGER NOT NULL
        );

        CREATE TABLE surahs (
            id INTEGER NOT NULL PRIMARY KEY,
            name_arabic TEXT NOT NULL,
            name_transliterated TEXT NOT NULL,
            name_translated_en TEXT NOT NULL,
            name_translated_id TEXT NOT NULL,
            ayah_count INTEGER NOT NULL,
            revelation_type TEXT NOT NULL,
            revelation_order INTEGER NOT NULL,
            sajdah_count INTEGER NOT NULL,
            page_start INTEGER NOT NULL,
            page_end INTEGER NOT NULL
        );

        CREATE TABLE ayahs (
            id INTEGER NOT NULL PRIMARY KEY,
            surah_id INTEGER NOT NULL,
            ayah_number INTEGER NOT NULL,
            text_uthmani TEXT NOT NULL,
            text_simple TEXT NOT NULL,
            juz_id INTEGER NOT NULL,
            hizb_id INTEGER NOT NULL,
            page_number INTEGER NOT NULL,
            line_number INTEGER,
            sajdah INTEGER NOT NULL
        );

        CREATE INDEX index_ayahs_surah_id_ayah_number ON ayahs(surah_id, ayah_number);
        CREATE INDEX index_ayahs_page_number ON ayahs(page_number);
        CREATE INDEX index_ayahs_juz_id ON ayahs(juz_id);

        CREATE TABLE mushaf_pages (
            page_number INTEGER NOT NULL PRIMARY KEY,
            juz_id INTEGER NOT NULL,
            hizb_id INTEGER,
            surah_start_id INTEGER NOT NULL,
            ayah_start_id INTEGER NOT NULL,
            surah_end_id INTEGER NOT NULL,
            ayah_end_id INTEGER NOT NULL
        );

        CREATE TABLE mushaf_lines (
            page_number INTEGER NOT NULL,
            line_number INTEGER NOT NULL,
            line_type TEXT NOT NULL,
            surah_id INTEGER,
            ayah_start INTEGER,
            ayah_end INTEGER,
            line_text TEXT,
            PRIMARY KEY (page_number, line_number)
        );

        CREATE TABLE juzs (
            id INTEGER NOT NULL PRIMARY KEY,
            name_arabic TEXT NOT NULL,
            page_start INTEGER NOT NULL,
            page_end INTEGER NOT NULL,
            ayah_start_id INTEGER NOT NULL,
            ayah_end_id INTEGER NOT NULL
        );

        CREATE TABLE hizbs (
            id INTEGER NOT NULL PRIMARY KEY,
            juz_id INTEGER NOT NULL,
            page_start INTEGER NOT NULL,
            page_end INTEGER NOT NULL
        );
    """)

    # Insert schema version
    cur.execute("INSERT INTO schema_versions VALUES (1, ?)", (int(time.time()),))

    # Build chapters lookup by id (quran.com chapters list)
    chapters_en_by_id = {c["id"]: c for c in chapters_en}
    chapters_id_by_id = {c["id"]: c for c in chapters_id}

    # Build a map: surah_number -> {page_start, page_end} from alquran.cloud uthmani
    print("  Building surah/ayah/page maps...")
    surah_ayah_to_simple = {}
    for s in surahs_simple:
        for a in s["ayahs"]:
            surah_ayah_to_simple[(s["number"], a["numberInSurah"])] = a["text"]

    # Compute surah page ranges from ayah pages
    surah_pages = {}  # surah_num -> (page_start, page_end)
    for s in surahs_uthmani:
        pages = [a["page"] for a in s["ayahs"]]
        surah_pages[s["number"]] = (min(pages), max(pages))

    # Insert surahs
    print("  Inserting surahs...")
    for s in surahs_uthmani:
        sid = s["number"]
        en_meta = chapters_en_by_id.get(sid, {})
        id_meta = chapters_id_by_id.get(sid, {})
        page_start, page_end = surah_pages[sid]
        sajdah_count = sum(1 for a in s["ayahs"] if a.get("sajda") and (
            (isinstance(a["sajda"], dict) and a["sajda"].get("recommended", False) is not None)
            or a["sajda"] is True
        ))
        cur.execute("""
            INSERT INTO surahs (id, name_arabic, name_transliterated,
                                 name_translated_en, name_translated_id,
                                 ayah_count, revelation_type, revelation_order,
                                 sajdah_count, page_start, page_end)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            sid,
            s.get("name") or en_meta.get("name_arabic", ""),
            en_meta.get("name_simple") or s.get("englishName", ""),
            en_meta.get("translated_name", {}).get("name") or s.get("englishNameTranslation", ""),
            id_meta.get("translated_name", {}).get("name") or s.get("englishNameTranslation", ""),
            len(s["ayahs"]),
            s["revelationType"],
            en_meta.get("revelation_order", sid),
            sajdah_count,
            page_start,
            page_end,
        ))

    # Insert ayahs (id is global verse number 1..6236)
    print("  Inserting ayahs (6,236 expected)...")
    ayah_count = 0
    for s in surahs_uthmani:
        sid = s["number"]
        for a in s["ayahs"]:
            text_simple = surah_ayah_to_simple.get((sid, a["numberInSurah"]), "")
            sajdah = 0
            if a.get("sajda"):
                sajdah = 1 if a["sajda"] is True else (
                    1 if isinstance(a["sajda"], dict) and a["sajda"].get("recommended") is not None else 0
                )
            cur.execute("""
                INSERT INTO ayahs (id, surah_id, ayah_number, text_uthmani,
                                    text_simple, juz_id, hizb_id, page_number,
                                    line_number, sajdah)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (
                a["number"],
                sid,
                a["numberInSurah"],
                a["text"],
                text_simple,
                a["juz"],
                a["hizbQuarter"],
                a["page"],
                None,  # line_number - computed at runtime via Compose layout
                sajdah,
            ))
            ayah_count += 1

    # Insert mushaf_pages (1..604)
    print("  Inserting mushaf_pages (604 expected)...")
    pages_data = {}
    for s in surahs_uthmani:
        sid = s["number"]
        for a in s["ayahs"]:
            p = a["page"]
            if p not in pages_data:
                pages_data[p] = {
                    "juz_id": a["juz"],
                    "hizb_id": a["hizbQuarter"],
                    "surah_start_id": sid,
                    "ayah_start_id": a["number"],
                    "surah_end_id": sid,
                    "ayah_end_id": a["number"],
                }
            else:
                pages_data[p]["surah_end_id"] = sid
                pages_data[p]["ayah_end_id"] = a["number"]

    for p in sorted(pages_data.keys()):
        d = pages_data[p]
        cur.execute("""
            INSERT INTO mushaf_pages (page_number, juz_id, hizb_id,
                                      surah_start_id, ayah_start_id,
                                      surah_end_id, ayah_end_id)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (p, d["juz_id"], d["hizb_id"],
              d["surah_start_id"], d["ayah_start_id"],
              d["surah_end_id"], d["ayah_end_id"]))

    # Insert mushaf_lines: a page-level breakdown (line_type per ayah/section)
    # Logic: for each page, emit one row per "section":
    #   - If a new surah starts on this page: emit SURAH_HEADER + BISMILLAH (except At-Tawbah)
    #   - Then emit AYAH_TEXT rows (one per ayah on this page)
    print("  Inserting mushaf_lines...")
    surah_first_ayah_page = {}
    for s in surahs_uthmani:
        sid = s["number"]
        first_ayah = s["ayahs"][0]
        surah_first_ayah_page[sid] = first_ayah["page"]

    for p in sorted(pages_data.keys()):
        line_no = 1
        # Detect new surahs starting on this page
        starting_surahs = [sid for sid, fp in surah_first_ayah_page.items() if fp == p]
        for sid in sorted(starting_surahs):
            # SURAH_HEADER
            cur.execute("""
                INSERT INTO mushaf_lines (page_number, line_number, line_type,
                                          surah_id, ayah_start, ayah_end, line_text)
                VALUES (?, ?, 'SURAH_HEADER', ?, NULL, NULL, NULL)
            """, (p, line_no, sid))
            line_no += 1
            # BISMILLAH (except At-Tawbah surah 9, and Al-Fatiha 1 has it as ayah 1)
            if sid != 9 and sid != 1:
                cur.execute("""
                    INSERT INTO mushaf_lines (page_number, line_number, line_type,
                                              surah_id, ayah_start, ayah_end, line_text)
                    VALUES (?, ?, 'BISMILLAH', ?, NULL, NULL,
                            'بِسْمِ ٱللَّهِ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ')
                """, (p, line_no, sid))
                line_no += 1

        # AYAH_TEXT rows: group consecutive ayahs of the same surah on this page
        ayahs_on_page = []
        for s in surahs_uthmani:
            for a in s["ayahs"]:
                if a["page"] == p:
                    ayahs_on_page.append((s["number"], a["numberInSurah"], a["text"]))

        # Group by surah on this page
        cur_surah = None
        cur_ayah_start = None
        cur_text = []
        cur_ayah_end = None
        for sid_a, ayah_n, text in ayahs_on_page:
            if cur_surah is None:
                cur_surah = sid_a
                cur_ayah_start = ayah_n
                cur_text = [text]
                cur_ayah_end = ayah_n
            elif sid_a == cur_surah:
                cur_text.append(text)
                cur_ayah_end = ayah_n
            else:
                # Flush
                cur.execute("""
                    INSERT INTO mushaf_lines (page_number, line_number, line_type,
                                              surah_id, ayah_start, ayah_end, line_text)
                    VALUES (?, ?, 'AYAH_TEXT', ?, ?, ?, ?)
                """, (p, line_no, cur_surah, cur_ayah_start, cur_ayah_end,
                      "\n".join(cur_text)))
                line_no += 1
                cur_surah = sid_a
                cur_ayah_start = ayah_n
                cur_text = [text]
                cur_ayah_end = ayah_n
        if cur_surah is not None:
            cur.execute("""
                INSERT INTO mushaf_lines (page_number, line_number, line_type,
                                          surah_id, ayah_start, ayah_end, line_text)
                VALUES (?, ?, 'AYAH_TEXT', ?, ?, ?, ?)
            """, (p, line_no, cur_surah, cur_ayah_start, cur_ayah_end,
                  "\n".join(cur_text)))

    # Insert juzs (30)
    print("  Inserting juzs (30 expected)...")
    juz_data = {}
    for s in surahs_uthmani:
        for a in s["ayahs"]:
            j = a["juz"]
            if j not in juz_data:
                juz_data[j] = {
                    "page_start": a["page"],
                    "page_end": a["page"],
                    "ayah_start_id": a["number"],
                    "ayah_end_id": a["number"],
                }
            else:
                juz_data[j]["page_end"] = max(juz_data[j]["page_end"], a["page"])
                juz_data[j]["ayah_end_id"] = max(juz_data[j]["ayah_end_id"], a["number"])

    juz_arabic_names = [
        "آلم", "سيقول", "تلك الرسل", "لن تنالوا", "والمحصنات",
        "لا يحب الله", "وإذا سمعوا", "ولو أننا", "قال الملأ", "واعلموا",
        "يعتذرون", "وما من دابة", "وما أبرئ", "ربما", "سبحان الذي",
        "قال ألم", "اقترب", "قد أفلح", "وقال الذين", "أمن خلق",
        "اتل ما أوحي", "ومن يقنت", "ومن يقنت", "فمن أظلم", "إليه يرد",
        "حم", "قال فما خطبكم", "قد سمع الله", "تبارك الذي", "عمَّ",
    ]
    for j in sorted(juz_data.keys()):
        d = juz_data[j]
        cur.execute("""
            INSERT INTO juzs (id, name_arabic, page_start, page_end,
                              ayah_start_id, ayah_end_id)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (j, juz_arabic_names[j-1], d["page_start"], d["page_end"],
              d["ayah_start_id"], d["ayah_end_id"]))

    # Insert hizbs (240): hizbQuarter values 1..240 mapped to juz (1 juz = 8 quarters)
    print("  Inserting hizbs (240 expected)...")
    hizb_data = {}
    for s in surahs_uthmani:
        for a in s["ayahs"]:
            h = a["hizbQuarter"]
            j = a["juz"]
            if h not in hizb_data:
                hizb_data[h] = {
                    "juz_id": j,
                    "page_start": a["page"],
                    "page_end": a["page"],
                }
            else:
                hizb_data[h]["page_end"] = max(hizb_data[h]["page_end"], a["page"])

    for h in sorted(hizb_data.keys()):
        d = hizb_data[h]
        cur.execute("""
            INSERT INTO hizbs (id, juz_id, page_start, page_end)
            VALUES (?, ?, ?, ?)
        """, (h, d["juz_id"], d["page_start"], d["page_end"]))

    con.commit()

    # Validate
    print("\n[Validation]")
    cur.execute("SELECT COUNT(*) FROM surahs"); c_s = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM ayahs"); c_a = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM mushaf_pages"); c_p = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM mushaf_lines"); c_l = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM juzs"); c_j = cur.fetchone()[0]
    cur.execute("SELECT COUNT(*) FROM hizbs"); c_h = cur.fetchone()[0]
    print(f"  surahs:        {c_s} (expected 114)")
    print(f"  ayahs:         {c_a} (expected 6236)")
    print(f"  mushaf_pages:  {c_p} (expected 604)")
    print(f"  mushaf_lines:  {c_l}")
    print(f"  juzs:          {c_j} (expected 30)")
    print(f"  hizbs:         {c_h} (expected 240)")

    assert c_s == 114, f"Expected 114 surahs, got {c_s}"
    assert c_a == 6236, f"Expected 6236 ayahs, got {c_a}"
    assert c_p == 604, f"Expected 604 pages, got {c_p}"
    assert c_j == 30, f"Expected 30 juzs, got {c_j}"
    assert c_h == 240, f"Expected 240 hizbs, got {c_h}"

    # Vacuum to minimize size
    cur.execute("VACUUM")
    con.commit()
    con.close()

    size = OUT_DB.stat().st_size
    print(f"\n[OK] Database written to {OUT_DB}")
    print(f"     Size: {size:,} bytes ({size/1024/1024:.2f} MB)")
    if size > 8 * 1024 * 1024:
        print(f"     WARNING: Size exceeds 8 MB target")


def main():
    surahs_uthmani = fetch_uthmani_quran()
    surahs_simple = fetch_simple_quran()
    chapters_en, chapters_id = fetch_chapters_meta()
    build_database(surahs_uthmani, surahs_simple, chapters_en, chapters_id)
    print("\nDone.")


if __name__ == "__main__":
    main()
