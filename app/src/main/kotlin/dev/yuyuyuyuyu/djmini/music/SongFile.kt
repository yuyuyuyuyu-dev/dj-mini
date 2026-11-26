package dev.yuyuyuyuyu.djmini.music

import android.net.Uri

data class SongFile(
    val id: Long,
    val uri: Uri,
    val modified: Long,
)
