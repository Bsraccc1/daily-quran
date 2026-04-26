package com.quranreader.custom.util

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Safe coroutine launch with automatic error handling
 */
fun ViewModel.safeLaunch(
    onError: (Exception) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
) {
    viewModelScope.launch {
        try {
            block()
        } catch (e: Exception) {
            Log.e(this@safeLaunch::class.simpleName, "Error in coroutine", e)
            onError(e)
        }
    }
}
