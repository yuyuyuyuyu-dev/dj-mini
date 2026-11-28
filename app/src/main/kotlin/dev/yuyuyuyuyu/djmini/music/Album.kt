package dev.yuyuyuyuyu.djmini.music

data class Album(
    val title: String,
    val artist: String?,
    val songs: List<Song>,
)
