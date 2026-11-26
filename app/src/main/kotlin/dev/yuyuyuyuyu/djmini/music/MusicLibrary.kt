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
            (listOf(IS_MUSIC) + filters.map { (column, _) -> "$column LIKE ? ESCAPE '\\'" })
                .joinToString(" AND ")
        val selectionArgs = filters.map { (_, value) -> "%${value.escapeForLike()}%" }.toTypedArray()

        val songs = querySongs(selection, selectionArgs, "${Media.TITLE} COLLATE NOCASE ASC")

        return songs.firstOrNull { title != null && it.title.equals(title, ignoreCase = true) } ?: songs.firstOrNull()
    }

    fun findSong(id: Long): Song? = querySongs("$IS_MUSIC AND ${Media._ID} = ?", arrayOf(id.toString()), null).firstOrNull()

    fun songFiles(): List<SongFile> =
        context.contentResolver
            .query(Media.EXTERNAL_CONTENT_URI, arrayOf(Media._ID, Media.DATE_MODIFIED), IS_MUSIC, null, null)
            ?.use { it.toSongFiles() }
            .orEmpty()

    private fun querySongs(
        selection: String,
        selectionArgs: Array<String>,
        sortOrder: String?,
    ): List<Song> =
        context.contentResolver
            .query(
                Media.EXTERNAL_CONTENT_URI,
                arrayOf(Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM),
                selection,
                selectionArgs,
                sortOrder,
            )?.use { it.toSongs() }
            .orEmpty()

    private fun Cursor.toSongs(): List<Song> {
        val idColumn = getColumnIndexOrThrow(Media._ID)
        val titleColumn = getColumnIndexOrThrow(Media.TITLE)
        val artistColumn = getColumnIndexOrThrow(Media.ARTIST)
        val albumColumn = getColumnIndexOrThrow(Media.ALBUM)
        return buildList {
            while (moveToNext()) {
                val id = getLong(idColumn)
                add(
                    Song(
                        id = id,
                        uri = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id),
                        title = getString(titleColumn),
                        artist = getString(artistColumn).knownOrNull(),
                        album = getString(albumColumn).knownOrNull(),
                    ),
                )
            }
        }
    }

    private fun Cursor.toSongFiles(): List<SongFile> {
        val idColumn = getColumnIndexOrThrow(Media._ID)
        val modifiedColumn = getColumnIndexOrThrow(Media.DATE_MODIFIED)
        return buildList {
            while (moveToNext()) {
                val id = getLong(idColumn)
                add(SongFile(id, ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id), getLong(modifiedColumn)))
            }
        }
    }

    private fun String.escapeForLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private fun String?.knownOrNull(): String? = takeUnless { it.isNullOrBlank() || it == MediaStore.UNKNOWN_STRING }

    private companion object {
        const val IS_MUSIC = "${Media.IS_MUSIC} != 0"
    }
}
