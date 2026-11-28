package dev.yuyuyuyuyu.djmini.appfunctions

import android.Manifest
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.appfunctions.AppFunctionData
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionManager
import androidx.appfunctions.AppFunctionSearchSpec
import androidx.appfunctions.ExecuteAppFunctionRequest
import androidx.appfunctions.ExecuteAppFunctionResponse
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import dev.yuyuyuyuyu.djmini.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class MusicAppFunctionServiceTest {
    @get:Rule
    val grantPermissionRule: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val insertedSongs = mutableListOf<Uri>()

    @After
    fun tearDown() =
        runBlocking {
            withMediaController { it.stop() }
            insertedSongs.forEach { context.contentResolver.delete(it, null, null) }
        }

    @Test
    fun shouldPlayTheSongWhoseTitleMatches() =
        runBlocking {
            // Arrange
            val title = "DJ Mini Test Song ${UUID.randomUUID()}"
            insertSong(title)

            // Act
            val response = executePlaySong(title = title.lowercase())

            // Assert
            val playedSong =
                (response as ExecuteAppFunctionResponse.Success)
                    .returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)!!
                    .deserialize(PlayedSong::class.java)
            assertEquals(title, playedSong.title)
            withMediaController { controller ->
                withTimeout(10.seconds) {
                    while (!controller.isPlaying) delay(100)
                }
                assertEquals(title, controller.mediaMetadata.title.toString())
            }
        }

    @Test
    fun shouldPlayOneOfTheThreeSongsThatSoundMostLikeTheFirstSongNext() =
        runBlocking {
            // Arrange
            val id = UUID.randomUUID()
            val first = "DJ Mini Test Tone $id"
            insertSong("DJ Mini Test Noise $id", noiseWav())
            insertSong(first, toneWav(frequency = 440.0))
            val similar = insertSimilarTones(id)

            // Act
            executePlaySong(title = first)

            // Assert
            assertTrue(nextSongTitle() in similar)
        }

    @Test
    fun shouldNotAlwaysPlayTheSameSongNext() =
        runBlocking {
            // Arrange
            val id = UUID.randomUUID()
            val first = "DJ Mini Test Tone $id"
            insertSong(first, toneWav(frequency = 440.0))
            insertSimilarTones(id)

            // Act
            val nextTitles =
                List(10) {
                    executePlaySong(title = first)
                    nextSongTitle().also { withMediaController { controller -> controller.stop() } }
                }

            // Assert
            assertTrue(nextTitles.toSet().size > 1)
        }

    @Test
    fun shouldNotPlayASongThatAlreadyPlayed() =
        runBlocking {
            // Arrange
            val id = UUID.randomUUID()
            val first = "DJ Mini Test Low Tone $id"
            insertSong("DJ Mini Test Noise $id", noiseWav())
            insertSong(first, toneWav(frequency = 440.0))
            insertSong("DJ Mini Test High Tone $id", toneWav(frequency = 660.0))
            executePlaySong(title = first)
            withMediaController { awaitMediaItemCount(it, 2) }

            // Act
            withMediaController { it.seekToNextMediaItem() }

            // Assert
            withMediaController { controller ->
                awaitMediaItemCount(controller, 3)
                assertNotEquals(
                    first,
                    controller
                        .getMediaItemAt(2)
                        .mediaMetadata.title
                        .toString(),
                )
            }
        }

    @Test
    fun shouldFailWhenNoSongMatches() =
        runBlocking {
            // Arrange
            val title = "DJ Mini Missing Song ${UUID.randomUUID()}"

            // Act
            val response = executePlaySong(title = title)

            // Assert
            assertTrue((response as ExecuteAppFunctionResponse.Error).error is AppFunctionElementNotFoundException)
        }

    @Test
    fun shouldFailWhenNoTitleArtistOrAlbumIsGiven() =
        runBlocking {
            // Act
            val response = executePlaySong(title = " ")

            // Assert
            assertTrue((response as ExecuteAppFunctionResponse.Error).error is AppFunctionInvalidArgumentException)
        }

    @Test
    fun shouldPlayTheSongsOfTheAlbumInTrackOrder() =
        runBlocking {
            // Arrange
            val album = "DJ Mini Test Album ${UUID.randomUUID()}"
            insertAlbumSong(album, track = 2, title = "B Second $album")
            insertAlbumSong(album, track = 3, title = "A Third $album")
            insertAlbumSong(album, track = 1, title = "C First $album")

            // Act
            val response = executePlayAlbum(album = album.lowercase())

            // Assert
            val playedAlbum =
                (response as ExecuteAppFunctionResponse.Success)
                    .returnValue
                    .getAppFunctionData(ExecuteAppFunctionResponse.Success.PROPERTY_RETURN_VALUE)!!
                    .deserialize(PlayedAlbum::class.java)
            val trackOrder = listOf("C First $album", "B Second $album", "A Third $album")
            assertEquals(album, playedAlbum.title)
            assertEquals(trackOrder, playedAlbum.songs.map { it.title })
            withMediaController { controller ->
                awaitMediaItemCount(controller, 3)
                assertEquals(
                    trackOrder,
                    List(controller.mediaItemCount) {
                        controller
                            .getMediaItemAt(it)
                            .mediaMetadata.title
                            .toString()
                    },
                )
            }
        }

    @Test
    fun shouldStopAfterTheLastSongOfTheAlbum() =
        runBlocking {
            // Arrange
            val album = "DJ Mini Test Album ${UUID.randomUUID()}"
            insertAlbumSong(album, track = 1, title = "First $album")
            insertAlbumSong(album, track = 2, title = "Last $album")
            insertSong("DJ Mini Test Tone ${UUID.randomUUID()}", toneWav(frequency = 440.0))

            // Act
            executePlayAlbum(album = album)

            // Assert
            withMediaController { controller ->
                awaitMediaItemCount(controller, 2)
                withTimeout(1.minutes) {
                    while (controller.playbackState != Player.STATE_ENDED) delay(100)
                }
                delay(5.seconds)
                assertEquals(Player.STATE_ENDED, controller.playbackState)
                assertEquals(2, controller.mediaItemCount)
                assertEquals("Last $album", controller.mediaMetadata.title.toString())
            }
        }

    @Test
    fun shouldFailWhenNoAlbumMatches() =
        runBlocking {
            // Arrange
            val album = "DJ Mini Missing Album ${UUID.randomUUID()}"

            // Act
            val response = executePlayAlbum(album = album)

            // Assert
            assertTrue((response as ExecuteAppFunctionResponse.Error).error is AppFunctionElementNotFoundException)
        }

    @Test
    fun shouldFailWhenNoAlbumTitleIsGiven() =
        runBlocking {
            // Act
            val response = executePlayAlbum(album = " ")

            // Assert
            assertTrue((response as ExecuteAppFunctionResponse.Error).error is AppFunctionInvalidArgumentException)
        }

    private suspend fun executePlaySong(title: String): ExecuteAppFunctionResponse = executeAppFunction("playSong", "title" to title)

    private suspend fun executePlayAlbum(album: String): ExecuteAppFunctionResponse = executeAppFunction("playAlbum", "album" to album)

    private suspend fun executeAppFunction(
        name: String,
        parameter: Pair<String, String>,
    ): ExecuteAppFunctionResponse {
        val manager = AppFunctionManager.getInstance(context)!!
        val metadata =
            manager
                .searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
                .single { it.id.endsWith("#$name") }
        val parameters =
            AppFunctionData
                .Builder(metadata.parameters, metadata.components)
                .setString(parameter.first, parameter.second)
                .build()
        return manager.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, metadata.id, parameters))
    }

    private fun insertSimilarTones(id: UUID): List<String> =
        listOf(445.0, 450.0, 455.0).map { frequency ->
            "DJ Mini Test Tone $frequency Hz $id".also { insertSong(it, toneWav(frequency)) }
        }

    private suspend fun nextSongTitle(): String =
        withMediaController { controller ->
            awaitMediaItemCount(controller, 2)
            controller
                .getMediaItemAt(1)
                .mediaMetadata.title
                .toString()
        }

    private suspend fun awaitMediaItemCount(
        controller: MediaController,
        count: Int,
    ) {
        withTimeout(2.minutes) {
            while (controller.mediaItemCount < count) delay(100)
        }
    }

    private fun insertSong(
        title: String,
        wav: ByteArray = silentWav(seconds = 30),
    ) = insertSongFile("$title.wav", "audio/x-wav", wav)

    private fun insertAlbumSong(
        album: String,
        track: Int,
        title: String,
    ) = insertSongFile(
        "$title.flac",
        "audio/flac",
        silentFlac(mapOf("TITLE" to title, "ALBUM" to album, "ARTIST" to "DJ Mini Test Artist", "TRACKNUMBER" to "$track")),
    )

    private fun insertSongFile(
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/DJ Mini Test")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        insertedSongs += uri
        resolver.openOutputStream(uri)!!.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
    }

    private fun silentWav(seconds: Int): ByteArray {
        val sampleRate = 8000
        val samples = ByteArray(sampleRate * seconds) { 0x80.toByte() }
        val header =
            ByteBuffer
                .allocate(44)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray())
                .putInt(36 + samples.size)
                .put("WAVE".toByteArray())
                .put("fmt ".toByteArray())
                .putInt(16)
                .putShort(1)
                .putShort(1)
                .putInt(sampleRate)
                .putInt(sampleRate)
                .putShort(1)
                .putShort(8)
                .put("data".toByteArray())
                .putInt(samples.size)
                .array()
        return ByteArrayOutputStream()
            .apply {
                write(header)
                write(samples)
            }.toByteArray()
    }

    private fun toneWav(frequency: Double): ByteArray =
        wav16Bit(FloatArray(SAMPLE_RATE * SECONDS) { (sin(2 * PI * frequency * it / SAMPLE_RATE) * 0.5).toFloat() })

    private fun noiseWav(): ByteArray {
        val random = Random(0)
        return wav16Bit(FloatArray(SAMPLE_RATE * SECONDS) { random.nextFloat() - 0.5f })
    }

    private fun wav16Bit(samples: FloatArray): ByteArray {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { data.putShort((it * Short.MAX_VALUE).toInt().toShort()) }
        val header =
            ByteBuffer
                .allocate(44)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put("RIFF".toByteArray())
                .putInt(36 + data.capacity())
                .put("WAVE".toByteArray())
                .put("fmt ".toByteArray())
                .putInt(16)
                .putShort(1)
                .putShort(1)
                .putInt(SAMPLE_RATE)
                .putInt(SAMPLE_RATE * 2)
                .putShort(2)
                .putShort(16)
                .put("data".toByteArray())
                .putInt(data.capacity())
                .array()
        return header + data.array()
    }

    private fun silentFlac(tags: Map<String, String>): ByteArray {
        val streamInfo =
            ByteBuffer
                .allocate(34)
                .putShort(FLAC_BLOCK_SIZE.toShort())
                .putShort(FLAC_BLOCK_SIZE.toShort())
                .put(ByteArray(6))
                .putLong((SAMPLE_RATE.toLong() shl 44) or (15L shl 36) or FLAC_FRAMES.toLong() * FLAC_BLOCK_SIZE)
                .put(ByteArray(16))
                .array()
        val vendor = "DJ Mini Test".toByteArray()
        val comments = tags.map { (key, value) -> "$key=$value".toByteArray() }
        val vorbisComment =
            ByteBuffer
                .allocate(8 + vendor.size + comments.sumOf { 4 + it.size })
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(vendor.size)
                .put(vendor)
                .putInt(comments.size)
                .apply { comments.forEach { putInt(it.size).put(it) } }
                .array()
        return ByteArrayOutputStream()
            .apply {
                write("fLaC".toByteArray())
                write(flacMetadataBlockHeader(type = 0, isLast = false, length = streamInfo.size))
                write(streamInfo)
                write(flacMetadataBlockHeader(type = 4, isLast = true, length = vorbisComment.size))
                write(vorbisComment)
                repeat(FLAC_FRAMES) { write(silentFlacFrame(number = it)) }
            }.toByteArray()
    }

    private fun flacMetadataBlockHeader(
        type: Int,
        isLast: Boolean,
        length: Int,
    ): ByteArray =
        byteArrayOf((type or if (isLast) 0x80 else 0).toByte(), (length shr 16).toByte(), (length shr 8).toByte(), length.toByte())

    private fun silentFlacFrame(number: Int): ByteArray {
        val header = byteArrayOf(0xFF.toByte(), 0xF8.toByte(), 0xC5.toByte(), 0x08, number.toByte())
        val verbatimSubframe = byteArrayOf(0x02) + ByteArray(FLAC_BLOCK_SIZE * 2)
        val frame = header + crc8(header) + verbatimSubframe
        val crc = crc16(frame)
        return frame + byteArrayOf((crc shr 8).toByte(), crc.toByte())
    }

    private fun crc8(bytes: ByteArray): Byte {
        var crc = 0
        bytes.forEach { byte ->
            crc = crc xor (byte.toInt() and 0xFF)
            repeat(8) { crc = if (crc and 0x80 != 0) (crc shl 1) xor 0x07 else crc shl 1 }
            crc = crc and 0xFF
        }
        return crc.toByte()
    }

    private fun crc16(bytes: ByteArray): Int {
        var crc = 0
        bytes.forEach { byte ->
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) { crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x8005 else crc shl 1 }
            crc = crc and 0xFFFF
        }
        return crc
    }

    private suspend fun <T> withMediaController(block: suspend (MediaController) -> T): T =
        withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            try {
                block(controller)
            } finally {
                controller.release()
            }
        }

    private companion object {
        const val SAMPLE_RATE = 16_000
        const val SECONDS = 10
        const val FLAC_BLOCK_SIZE = 4096
        const val FLAC_FRAMES = 4
    }
}
