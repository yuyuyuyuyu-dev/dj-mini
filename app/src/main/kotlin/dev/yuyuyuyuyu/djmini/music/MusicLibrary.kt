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
            (listOf(IS_MUSIC) + filters.map { (column, _) -> "$column $LIKE" })
                .joinToString(" AND ")
        val selectionArgs = filters.map { (_, value) -> "%${value.escapeForLike()}%" }.toTypedArray()

        val songs = querySongs(selection, selectionArgs, "${Media.TITLE} COLLATE NOCASE ASC")

        return songs.firstOrNull { title != null && it.title.equals(title, ignoreCase = true) } ?: songs.firstOrNull()
    }

    fun findAlbum(
        title: String,
        artist: String?,
    ): Album? {
        val selection =
            listOfNotNull(
                IS_MUSIC,
                "${Media.ALBUM} $LIKE",
                artist?.let { "(${Media.ARTIST} $LIKE OR ${Media.ALBUM_ARTIST} $LIKE)" },
            ).joinToString(" AND ")
        val selectionArgs = (listOf(title) + listOfNotNull(artist, artist)).map { "%${it.escapeForLike()}%" }.toTypedArray()

        val albums =
            context.contentResolver
                .query(
                    Media.EXTERNAL_CONTENT_URI,
                    arrayOf(Media.ALBUM_ID, Media.ALBUM, Media.ALBUM_ARTIST),
                    selection,
                    selectionArgs,
                    "${Media.ALBUM} COLLATE NOCASE ASC",
                )?.use { it.toAlbumRows() }
                .orEmpty()
        val album = albums.firstOrNull { it.title.equals(title, ignoreCase = true) } ?: albums.firstOrNull() ?: return null

        val songs =
            querySongs(
                "$IS_MUSIC AND ${Media.ALBUM_ID} = ?",
                arrayOf(album.id.toString()),
                "${Media.TRACK} ASC, ${Media.TITLE} COLLATE NOCASE ASC",
            )
        return Album(
            title = album.title,
            artist = album.artist ?: songs.mapNotNull { it.artist }.distinct().singleOrNull(),
            songs = songs,
        )
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

    private fun Cursor.toAlbumRows(): List<AlbumRow> {
        val idColumn = getColumnIndexOrThrow(Media.ALBUM_ID)
        val titleColumn = getColumnIndexOrThrow(Media.ALBUM)
        val artistColumn = getColumnIndexOrThrow(Media.ALBUM_ARTIST)
        return buildList {
            while (moveToNext()) {
                val title = getString(titleColumn).knownOrNull() ?: continue
                add(AlbumRow(getLong(idColumn), title, getString(artistColumn).knownOrNull()))
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

    private data class AlbumRow(
        val id: Long,
        val title: String,
        val artist: String?,
    )

    private companion object {
        const val IS_MUSIC = "${Media.IS_MUSIC} != 0"
        const val LIKE = "LIKE ? ESCAPE '\\'"
    }
}
