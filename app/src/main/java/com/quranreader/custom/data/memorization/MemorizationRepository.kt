package com.quranreader.custom.data.memorization

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin repository wrapping [MemorizationSessionDao] with helper builders.
 *
 * Most queries already return Flow from the DAO; this layer adds:
 * - [createSession] convenience for the common single-ayah hifz start case
 * - [completeSession] sets `completedAt` to now
 */
@Singleton
class MemorizationRepository @Inject constructor(
    private val dao: MemorizationSessionDao
) {

    suspend fun createSession(
        surah: Int,
        ayah: Int,
        repeatTarget: Int,
        autoAdvance: Boolean
    ): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            MemorizationSession(
                surahStart = surah,
                ayahStart = ayah,
                surahEnd = surah,
                ayahEnd = ayah,
                repeatTarget = repeatTarget,
                repeatsCompleted = 0,
                totalSeconds = 0,
                autoAdvance = autoAdvance,
                startedAt = now,
                completedAt = null
            )
        )
    }

    suspend fun updateSession(session: MemorizationSession) {
        dao.update(session)
    }

    suspend fun completeSession(session: MemorizationSession) {
        dao.update(session.copy(completedAt = System.currentTimeMillis()))
    }

    fun observeAll() = dao.observeAll()
    fun observeInProgress() = dao.observeInProgress()

    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun delete(id: Long) = dao.delete(id)

    /** Wipe every memorization session row. Called by Settings → Clear all history. */
    suspend fun clearAll() = dao.deleteAll()
}
