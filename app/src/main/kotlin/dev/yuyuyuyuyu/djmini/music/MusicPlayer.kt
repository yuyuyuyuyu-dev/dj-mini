package dev.yuyuyuyuyu.djmini.music

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.yuyuyuyuyu.djmini.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class MusicPlayer(
    private val context: Context,
) {
    suspend fun play(song: Song) =
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            try {
                controller.setMediaItem(song.toMediaItem())
                controller.prepare()
                controller.play()
            } finally {
                controller.release()
            }
        }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setAlbumTitle(album)
                    .build(),
            ).build()
}
