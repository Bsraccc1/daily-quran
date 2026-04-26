package com.quranreader.custom.ui.screens.memorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.memorization.MemorizationRepository
import com.quranreader.custom.data.memorization.MemorizationSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model for [MemorizationHistoryScreen].
 *
 * Streams all sessions (most recent first) and exposes deletion + resume hooks.
 */
@HiltViewModel
class MemorizationHistoryViewModel @Inject constructor(
    private val repo: MemorizationRepository
) : ViewModel() {

    val sessions: StateFlow<List<MemorizationSession>> = repo.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun delete(id: Long) {
        viewModelScope.launch { repo.delete(id) }
    }
}
