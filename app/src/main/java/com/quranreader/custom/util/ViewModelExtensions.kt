package com.quranreader.custom.util

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Safe coroutine launch with automatic error handling.
 *
 * **Critical**: [CancellationException] is always rethrown so that
 * structured concurrency works — when the [ViewModel] is cleared,
 * child coroutines actually cancel instead of being silently
 * swallowed by the generic [catch].
 */
fun ViewModel.safeLaunch(
    onError: (Exception) -> Unit = {},
    block: suspend CoroutineScope.() -> Unit
) {
    viewModelScope.launch {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(this@safeLaunch::class.simpleName, "Error in coroutine", e)
            onError(e)
        }
    }
}
