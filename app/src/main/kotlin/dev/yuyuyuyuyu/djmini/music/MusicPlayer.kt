package dev.yuyuyuyuyu.djmini.music

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.yuyuyuyuyu.djmini.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class MusicPlayer(
    private val context: Context,
) {
    suspend fun playAndContinueWithSimilarSongs(song: Song) =
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            try {
                controller.setMediaItem(song.toMediaItemContinuedBySimilarSongs())
                controller.prepare()
                controller.play()
            } finally {
                controller.release()
            }
        }
}
