package com.quranreader.custom.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Helper composable to get string resources with proper locale support
 */
@Composable
fun stringResource(resId: Int): String {
    return LocalContext.current.getString(resId)
}

@Composable
fun stringResource(resId: Int, vararg formatArgs: Any): String {
    return LocalContext.current.getString(resId, *formatArgs)
}
