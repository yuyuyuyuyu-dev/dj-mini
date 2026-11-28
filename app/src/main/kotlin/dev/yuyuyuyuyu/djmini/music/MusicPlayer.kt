package dev.yuyuyuyuyu.djmini.music

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.yuyuyuyuyu.djmini.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class MusicPlayer(
    private val context: Context,
) {
    suspend fun playAndContinueWithSimilarSongs(song: Song) = play(listOf(song.toMediaItemContinuedBySimilarSongs()))

    suspend fun playToTheEnd(album: Album) = play(album.songs.map { it.toMediaItem() })

    private suspend fun play(mediaItems: List<MediaItem>) =
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            try {
                controller.setMediaItems(mediaItems)
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.shuffleModeEnabled = false
                controller.prepare()
                controller.play()
            } finally {
                controller.release()
            }
        }
}
