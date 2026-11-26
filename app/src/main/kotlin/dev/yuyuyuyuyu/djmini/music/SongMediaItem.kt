package dev.yuyuyuyuyu.djmini.music

import android.content.ContentUris
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

private const val CONTINUES_WITH_SIMILAR_SONGS = "dev.yuyuyuyuyu.djmini.CONTINUES_WITH_SIMILAR_SONGS"

fun Song.toMediaItemContinuedBySimilarSongs(): MediaItem =
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
        ).setRequestMetadata(
            MediaItem.RequestMetadata
                .Builder()
                .setExtras(Bundle().apply { putBoolean(CONTINUES_WITH_SIMILAR_SONGS, true) })
                .build(),
        ).build()

val MediaItem.songId: Long
    get() = ContentUris.parseId(mediaId.toUri())

val MediaItem.continuesWithSimilarSongs: Boolean
    get() = requestMetadata.extras?.getBoolean(CONTINUES_WITH_SIMILAR_SONGS) == true
