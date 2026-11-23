package dev.yuyuyuyuyu.djmini.similar

import android.content.Context
import dev.yuyuyuyuyu.djmini.music.MusicLibrary
import dev.yuyuyuyuyu.djmini.music.SongFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class SongIndex(
    private val context: Context,
) {
    private val vectors = SongVectorDatabase.get(context).songVectors()

    suspend fun update() {
        val files = withContext(Dispatchers.IO) { MusicLibrary(context).songFiles() }
        val analyzed = vectors.analyzedSongs().associate { it.songId to it.modified }
        val fileIds = files.mapTo(HashSet()) { it.id }
        analyzed.keys
            .filterNot { it in fileIds }
            .chunked(DELETE_CHUNK)
            .forEach { vectors.delete(it) }
        val changed = files.filter { analyzed[it.id] != it.modified }
        if (changed.isEmpty()) return
        SongVectorizer.use(context) { vectorizer -> changed.forEach { analyze(it, vectorizer) } }
    }

    suspend fun allVectors(): Map<Long, FloatArray> =
        vectors.allWithVectors().associate {
            it.songId to
                checkNotNull(it.vector).toFloatArray()
        }

    private suspend fun analyze(
        file: SongFile,
        vectorizer: SongVectorizer,
    ) {
        val vector = vectorizer.vectorOf(file.uri)
        vectors.upsert(SongVectorEntity(file.id, file.modified, vector?.toByteArray()))
    }

    private fun FloatArray.toByteArray(): ByteArray =
        ByteBuffer
            .allocate(size * Float.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { asFloatBuffer().put(this@toByteArray) }
            .array()

    private fun ByteArray.toFloatArray(): FloatArray {
        val floats = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        return FloatArray(floats.remaining()).also { floats.get(it) }
    }

    private companion object {
        const val DELETE_CHUNK = 500
    }
}
