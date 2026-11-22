package dev.yuyuyuyuyu.djmini.music

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media

class MusicLibrary(
    private val context: Context,
) {
    fun findSong(
        title: String?,
        artist: String?,
        album: String?,
    ): Song? {
        val filters =
            listOfNotNull(
                title?.let { Media.TITLE to it },
                artist?.let { Media.ARTIST to it },
                album?.let { Media.ALBUM to it },
            )
        val selection =
            (listOf("${Media.IS_MUSIC} != 0") + filters.map { (column, _) -> "$column LIKE ? ESCAPE '\\'" })
                .joinToString(" AND ")
        val selectionArgs = filters.map { (_, value) -> "%${value.escapeForLike()}%" }.toTypedArray()

        val songs =
            context.contentResolver
                .query(
                    Media.EXTERNAL_CONTENT_URI,
                    arrayOf(Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM),
                    selection,
                    selectionArgs,
                    "${Media.TITLE} COLLATE NOCASE ASC",
                )?.use { it.toSongs() }
                .orEmpty()

        return songs.firstOrNull { title != null && it.title.equals(title, ignoreCase = true) } ?: songs.firstOrNull()
    }

    private fun Cursor.toSongs(): List<Song> {
        val idColumn = getColumnIndexOrThrow(Media._ID)
        val titleColumn = getColumnIndexOrThrow(Media.TITLE)
        val artistColumn = getColumnIndexOrThrow(Media.ARTIST)
        val albumColumn = getColumnIndexOrThrow(Media.ALBUM)
        return buildList {
            while (moveToNext()) {
                add(
                    Song(
                        uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, getLong(idColumn)),
                        title = getString(titleColumn),
                        artist = getString(artistColumn).knownOrNull(),
                        album = getString(albumColumn).knownOrNull(),
                    ),
                )
            }
        }
    }

    private fun String.escapeForLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun String?.knownOrNull(): String? = takeUnless { it.isNullOrBlank() || it == MediaStore.UNKNOWN_STRING }
}
