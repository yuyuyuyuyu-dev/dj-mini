package dev.yuyuyuyuyu.djmini.appfunctions

import android.Manifest
import android.content.pm.PackageManager
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionPermissionRequiredException
import androidx.appfunctions.AppFunctionSerializable
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import dev.yuyuyuyuyu.djmini.music.MusicLibrary
import dev.yuyuyuyuyu.djmini.music.MusicPlayer
import dev.yuyuyuyuyu.djmini.music.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@AppFunctionServiceEntryPoint(
    serviceName = "MusicAppFunctionService",
    appFunctionXmlFileName = "music_app_function_service",
)
abstract class BaseMusicAppFunctionService : AppFunctionService() {
    /**
     * Plays one song from the music files saved on this device, and then keeps playing the songs on this device that sound
     * most like the song before, without playing any song twice.
     *
     * Matching is case-insensitive and accepts partial text. When several songs match, the song whose title is exactly the
     * given title is played; otherwise the first match in title order is played.
     *
     * @param title Text in the title of the song to play, such as "Clair de Lune". Null to match any title.
     * @param artist Text in the name of the artist of the song to play, such as "Debussy". Null to match any artist.
     * @param album Text in the title of the album of the song to play. Null to match any album.
     * @return The song that started playing first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun playSong(
        title: String? = null,
        artist: String? = null,
        album: String? = null,
    ): PlayedSong {
        val song = findSong(title?.trim()?.ifEmpty { null }, artist?.trim()?.ifEmpty { null }, album?.trim()?.ifEmpty { null })
        MusicPlayer(this).playAndContinueWithSimilarSongs(song)
        return song.toPlayedSong()
    }

    private suspend fun findSong(
        title: String?,
        artist: String?,
        album: String?,
    ): Song {
        if (title == null && artist == null && album == null) {
            throw AppFunctionInvalidArgumentException("Give at least one of title, artist, or album.")
        }
        requireMusicAccess()
        return withContext(Dispatchers.IO) { MusicLibrary(this@BaseMusicAppFunctionService).findSong(title, artist, album) }
            ?: throw AppFunctionElementNotFoundException("No song on this device matches the given title, artist, and album.")
    }

    private fun requireMusicAccess() {
        if (checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw AppFunctionPermissionRequiredException(
                "DJ Mini is not allowed to read music files. Open DJ Mini and allow access to music.",
            )
        }
    }

    private fun Song.toPlayedSong(): PlayedSong = PlayedSong(title = title, artist = artist, album = album)
}

/** A song that DJ Mini started playing. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class PlayedSong(
    /** The title of the song. */
    val title: String,
    /** The name of the artist, or null when the music file does not name one. */
    val artist: String?,
    /** The title of the album, or null when the music file does not name one. */
    val album: String?,
)
