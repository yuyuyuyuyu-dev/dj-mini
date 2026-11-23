package dev.yuyuyuyuyu.djmini.appfunctions

import android.content.Context
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

class PlaySongRegistration(
    private val context: Context,
) {
    suspend fun awaitRegistered(): Boolean {
        val manager = AppFunctionManager.getInstance(context) ?: return false
        var registered = isRegistered(manager)
        var attempts = 1
        while (!registered && attempts < ATTEMPTS) {
            delay(RETRY_INTERVAL)
            registered = isRegistered(manager)
            attempts++
        }
        return registered
    }

    private suspend fun isRegistered(manager: AppFunctionManager): Boolean =
        runCatching {
            manager
                .searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
                .any { it.id == PLAY_SONG_ID }
        }.getOrDefault(false)

    private companion object {
        const val ATTEMPTS = 10
        val RETRY_INTERVAL = 3.seconds
        val PLAY_SONG_ID = "${BaseMusicAppFunctionService::class.java.name}#${BaseMusicAppFunctionService::playSong.name}"
    }
}
