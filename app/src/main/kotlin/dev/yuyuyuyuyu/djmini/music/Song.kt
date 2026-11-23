package dev.yuyuyuyuyu.djmini.music

import android.net.Uri

data class Song(
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
)
