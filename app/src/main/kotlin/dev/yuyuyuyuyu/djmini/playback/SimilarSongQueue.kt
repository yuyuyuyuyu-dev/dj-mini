package dev.yuyuyuyuyu.djmini.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import dev.yuyuyuyuyu.djmini.music.continuesWithSimilarSongs
import dev.yuyuyuyuyu.djmini.music.songId
import dev.yuyuyuyuyu.djmini.music.toMediaItemContinuedBySimilarSongs
import dev.yuyuyuyuyu.djmini.similar.SimilarSongs
import dev.yuyuyuyuyu.djmini.similar.SongIndexWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class SimilarSongQueue(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
) : Player.Listener {
    private var search: Job? = null

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        search?.cancel()
        if (mediaItem == null || !mediaItem.continuesWithSimilarSongs || player.hasNextMediaItem()) return
        val playedSongIds = (0 until player.mediaItemCount).mapTo(HashSet()) { player.getMediaItemAt(it).songId }
        search =
            scope.launch {
                SongIndexWorker.update(context)
                val next = SimilarSongs(context).pickNext(mediaItem.songId, playedSongIds) ?: return@launch
                if (player.currentMediaItem?.mediaId != mediaItem.mediaId || player.hasNextMediaItem()) return@launch
                player.addMediaItem(next.toMediaItemContinuedBySimilarSongs())
                if (player.playbackState == Player.STATE_ENDED) player.seekToNextMediaItem()
            }
    }
}
