package com.quranreader.custom.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.model.Hizb
import com.quranreader.custom.data.model.Juz
import com.quranreader.custom.data.model.Surah
import com.quranreader.custom.data.preferences.UserPreferences
import com.quranreader.custom.data.repository.QuranRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val quranRepository: QuranRepository,
    private val userPreferences: UserPreferences
) : ViewModel() {

    val surahs = quranRepository.getAllSurahs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val juzList = quranRepository.getAllJuz()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val hizbList = quranRepository.getAllHizb()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val lastPage = userPreferences.lastPage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1
        )

    // F-05: Session management
    val isSessionActive = userPreferences.isSessionActive
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val sessionStartPage = userPreferences.sessionStartPage
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 1
        )

    val sessionTargetPages = userPreferences.sessionTargetPages
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )

    fun startSession(startPage: Int, targetPages: Int) {
        viewModelScope.launch {
            userPreferences.startSession(startPage, targetPages)
        }
    }

    fun extendSession(additionalPages: Int) {
        viewModelScope.launch {
            userPreferences.extendSession(additionalPages)
        }
    }

    fun endSession() {
        viewModelScope.launch {
            userPreferences.endSession()
        }
    }
}
