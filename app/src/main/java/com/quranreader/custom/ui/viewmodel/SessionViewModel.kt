package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.preferences.ReadingSession
import com.quranreader.custom.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val userPreferences: UserPreferences
) : ViewModel() {

    val sessions: StateFlow<List<ReadingSession>> = userPreferences.sessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeSessionId: StateFlow<String?> = userPreferences.activeSessionId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val lastPage: StateFlow<Int> = userPreferences.lastPage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1)

    fun createSession(name: String, startPage: Int, targetPages: Int) {
        viewModelScope.launch {
            val session = ReadingSession(
                id = UUID.randomUUID().toString(),
                name = name,
                startPage = startPage,
                targetPages = targetPages,
                pagesRead = 0,
                isActive = true,
                isCompleted = false
            )
            userPreferences.addSession(session)
            // Auto-activate the newly created session
            userPreferences.setActiveSession(session.id)
            // Sync legacy keys for ReadingViewModel compatibility
            userPreferences.startSession(session.startPage, session.targetPages)
        }
    }

    /** Activate a session and sync the legacy single-session keys for ReadingViewModel */
    fun activateSession(session: ReadingSession) {
        viewModelScope.launch {
            userPreferences.setActiveSession(session.id)
            // Sync legacy keys so ReadingViewModel limit check still works
            userPreferences.startSession(session.startPage, session.targetPages)
            // Mark as active in the list
            userPreferences.updateSession(session.copy(isActive = true))
        }
    }

    fun extendSession(sessionId: String, additionalPages: Int) {
        viewModelScope.launch {
            val session = sessions.value.find { it.id == sessionId } ?: return@launch
            userPreferences.updateSession(session.copy(targetPages = session.targetPages + additionalPages))
            // Sync legacy key if this is the active session
            if (sessionId == activeSessionId.value) {
                userPreferences.extendSession(additionalPages)
            }
        }
    }

    fun completeSession(sessionId: String) {
        viewModelScope.launch {
            val session = sessions.value.find { it.id == sessionId } ?: return@launch
            userPreferences.updateSession(session.copy(isCompleted = true, isActive = false))
            if (sessionId == activeSessionId.value) {
                userPreferences.setActiveSession(null)
                userPreferences.endSession()
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            userPreferences.deleteSession(sessionId)
        }
    }

    /** Update session (for rename or other modifications) */
    fun updateSession(session: ReadingSession) {
        viewModelScope.launch {
            userPreferences.updateSession(session)
        }
    }

    /** Called by ReadingViewModel when a page is read — updates pagesRead on active session */
    fun recordPageRead(currentPage: Int) {
        viewModelScope.launch {
            val id = activeSessionId.value ?: return@launch
            val session = sessions.value.find { it.id == id } ?: return@launch
            val pagesRead = (currentPage - session.startPage + 1).coerceAtLeast(0)
            userPreferences.updateSession(session.copy(pagesRead = pagesRead))
        }
    }
}
