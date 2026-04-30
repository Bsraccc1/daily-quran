package com.quranreader.custom.data.preferences

/**
 * Pure session-progress math helpers. Centralised here (instead of
 * inlined in [UserPreferences]) so the watermark invariants can be
 * unit-tested without bringing up DataStore, and so any future
 * persistence call site (memorisation, audio, etc.) can apply the
 * same rules.
 */
internal object SessionProgressMath {

    /**
     * Decide whether to persist a new `pagesRead` value for the
     * active session, and what the persisted value should be.
     *
     * The function enforces two invariants:
     *
     *  - **Monotonic** — never regress. Auto-save fires on a debounce
     *    of [_currentPage][com.quranreader.custom.ui.viewmodel.ReadingViewModel],
     *    so navigating backwards (re-reading a verse) inside a
     *    session previously caused `pagesRead` to drop from the
     *    highest mark back to the smaller current value. The user
     *    visible symptom: "I was on page 20, the system rewrote my
     *    progress to 5". Returning `null` here makes the DataStore
     *    edit a no-op so the high-water mark survives.
     *
     *  - **Capped** — never exceed [targetPages]. The resume math
     *    (`startPage + pagesRead - 1`) is what places the user back
     *    on their last page in the Dashboard's "Continue Reading"
     *    button; a `pagesRead > targetPages` would overshoot the
     *    session's actual last page (the auto-stop sheet fires *one
     *    page past* the end, so during that single frame `newRaw`
     *    can be `targetPages + 1`).
     *
     * Returns the value to write, or `null` to skip the write.
     */
    fun nextPagesReadWatermark(existing: Int, newRaw: Int, targetPages: Int): Int? {
        val target = targetPages.coerceAtLeast(1)
        val capped = newRaw.coerceIn(0, target)
        return if (capped > existing) capped else null
    }
}
