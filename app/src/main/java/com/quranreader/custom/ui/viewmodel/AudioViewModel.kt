package com.quranreader.custom.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quranreader.custom.data.audio.*
import com.quranreader.custom.data.preferences.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPreferences: UserPreferences
) : ViewModel() {

    private var audioService: AudioService? = null
    private var bound = false

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentAyah = MutableStateFlow<AyahInfo?>(null)
    val currentAyah: StateFlow<AyahInfo?> = _currentAyah.asStateFlow()

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering.asStateFlow()

    private val _currentReciter = MutableStateFlow(Reciters.DEFAULT_RECITERS[0])
    val currentReciter: StateFlow<ReciterConfig> = _currentReciter.asStateFlow()

    val availableReciters = Reciters.DEFAULT_RECITERS

    private var serviceBindings: MutableList<ServiceBindingCallbacks> = mutableListOf()

    interface ServiceBindingCallbacks {
        fun onBound(service: AudioService)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as AudioService.AudioBinder
            audioService = binder.getService()
            bound = true

            serviceBindings.forEach { it.onBound(audioService!!) }

            viewModelScope.launch {
                audioService?.playbackState?.collect { _playbackState.value = it }
            }
            viewModelScope.launch {
                audioService?.currentAyah?.collect { _currentAyah.value = it }
            }
            viewModelScope.launch {
                audioService?.isBuffering?.collect { _isBuffering.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            bound = false
        }
    }

    init {
        bindService()
        viewModelScope.launch {
            userPreferences.selectedReciter.collect { reciterId ->
                val reciter = Reciters.getReciter(reciterId)
                _currentReciter.value = reciter
                audioService?.setReciter(reciter)
            }
        }
    }

    private fun bindService() {
        val intent = Intent(context, AudioService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun onServiceReady(callback: ServiceBindingCallbacks) {
        val service = audioService
        if (service != null) {
            callback.onBound(service)
        } else {
            serviceBindings.add(callback)
        }
    }

    fun playPage(page: Int) {
        audioService?.setReciter(_currentReciter.value)
        audioService?.playPage(page)
    }

    fun playRange(surah: Int, fromAyah: Int, toAyah: Int, repeat: Int = 0) {
        audioService?.setReciter(_currentReciter.value)
        audioService?.playRange(surah, fromAyah, toAyah, repeat)
    }

    fun togglePlayPause() {
        audioService?.let {
            if (it.isPlaying()) it.pause() else it.play()
        }
    }

    fun stop() {
        audioService?.stop()
    }

    fun setReciter(reciterId: String) {
        viewModelScope.launch {
            userPreferences.setSelectedReciter(reciterId)
        }
    }

    override fun onCleared() {
        if (bound) {
            runCatching { context.unbindService(connection) }
            bound = false
        }
        super.onCleared()
    }
}
