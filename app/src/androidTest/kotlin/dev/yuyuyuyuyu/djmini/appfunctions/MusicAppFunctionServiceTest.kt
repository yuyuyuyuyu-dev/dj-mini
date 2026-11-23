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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
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

    private suspend fun executePlaySong(title: String): ExecuteAppFunctionResponse {
        val manager = AppFunctionManager.getInstance(context)!!
        val metadata =
            manager
                .searchAppFunctions(AppFunctionSearchSpec(packageNames = setOf(context.packageName)))
                .single { it.id.endsWith("#playSong") }
        val parameters =
            AppFunctionData
                .Builder(metadata.parameters, metadata.components)
                .setString("title", title)
                .build()
        return manager.executeAppFunction(ExecuteAppFunctionRequest(context.packageName, metadata.id, parameters))
    }

    private fun insertSong(title: String) {
        val resolver = context.contentResolver
        val values =
            ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$title.wav")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/x-wav")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/DJ Mini Test")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        insertedSongs += uri
        resolver.openOutputStream(uri)!!.use { it.write(silentWav(seconds = 30)) }
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
}
