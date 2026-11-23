package dev.yuyuyuyuyu.djmini.ui.main

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import dev.yuyuyuyuyu.djmini.appfunctions.PlaySongRegistration

internal enum class GeminiAvailability {
    Checking,
    Available,
    Unavailable,
}

@Composable
internal fun rememberGeminiAvailability(): GeminiAvailability {
    val context = LocalContext.current
    val availability by produceState(GeminiAvailability.Checking, context) {
        value =
            if (PlaySongRegistration(context).awaitRegistered()) {
                GeminiAvailability.Available
            } else {
                GeminiAvailability.Unavailable
            }
    }
    return availability
}
