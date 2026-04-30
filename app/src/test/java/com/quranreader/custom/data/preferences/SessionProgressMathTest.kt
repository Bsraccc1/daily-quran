package com.quranreader.custom.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure-logic regression checks for [SessionProgressMath.nextPagesReadWatermark].
 *
 * The watermark function is the single source of truth for "should we
 * actually write this `pagesRead` value to DataStore?". It enforces
 * two invariants the user-facing session feature depends on:
 *
 *  1. **Monotonic** — `pagesRead` never regresses. The pre-fix bug was
 *     that auto-save fired on backward navigation, overwriting a
 *     mid-session high-water mark with the lower current page.
 *
 *  2. **Capped** — `pagesRead` never exceeds the session's
 *     `targetPages`. The resume math (`startPage + pagesRead - 1`)
 *     would otherwise place the user past the session's last page
 *     after the completion sheet auto-fired one page after the end.
 *
 * Returning null is the "skip the write" sentinel — the DataStore
 * edit is a no-op in that case so we don't churn the file or wake
 * other observers for a no-op update.
 */
class SessionProgressMathTest {

    @Test fun `first save persists the new value`() {
        // Brand-new session: existing pagesRead is 0, the first
        // persistSessionProgress call should land the new mark.
        assertEquals(5, SessionProgressMath.nextPagesReadWatermark(existing = 0, newRaw = 5, targetPages = 10))
    }

    @Test fun `forward progress persists the new value`() {
        assertEquals(10, SessionProgressMath.nextPagesReadWatermark(existing = 5, newRaw = 10, targetPages = 10))
    }

    @Test fun `backward navigation does not regress (returns null)`() {
        // The bug: user reads to page 20 (pagesRead=20), navigates
        // back to page 5 (raw=5), auto-save fires, OLD code wrote 5,
        // erasing 20. New code skips the write.
        assertNull(SessionProgressMath.nextPagesReadWatermark(existing = 20, newRaw = 5, targetPages = 30))
    }

    @Test fun `over-target value is capped at targetPages`() {
        // User over-shoots end-page on the auto-stop frame; we cap at
        // target so the resume math doesn't land them past the session.
        assertEquals(10, SessionProgressMath.nextPagesReadWatermark(existing = 5, newRaw = 20, targetPages = 10))
    }

    @Test fun `same value as existing is a no-op`() {
        // Already at high-water mark; another auto-save tick on the
        // same page shouldn't churn DataStore.
        assertNull(SessionProgressMath.nextPagesReadWatermark(existing = 10, newRaw = 10, targetPages = 10))
    }

    @Test fun `negative raw is coerced to zero`() {
        // Page < startPage (user back-swiped before the session anchor)
        // shouldn't crash or persist a negative count.
        assertNull(SessionProgressMath.nextPagesReadWatermark(existing = 0, newRaw = -3, targetPages = 10))
    }

    @Test fun `targetPages floor of zero treated as 1`() {
        // Defensive: a corrupted session row with targetPages=0 still
        // allows a single-page save instead of dividing by zero.
        assertEquals(1, SessionProgressMath.nextPagesReadWatermark(existing = 0, newRaw = 5, targetPages = 0))
    }
}
