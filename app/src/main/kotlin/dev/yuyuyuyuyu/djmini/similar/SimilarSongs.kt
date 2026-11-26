package dev.yuyuyuyuyu.djmini.similar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import dev.yuyuyuyuyu.djmini.music.MusicLibrary
import dev.yuyuyuyuyu.djmini.music.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

class SimilarSongs(
    private val context: Context,
) {
    suspend fun pickNext(
        songId: Long,
        excludedSongIds: Set<Long>,
    ): Song? {
        val vectors = if (hasMusicAccess()) SongIndex(context).allVectors() else emptyMap()
        val vector = vectors[songId] ?: return null
        val ranked =
            withContext(Dispatchers.Default) {
                vectors
                    .filterKeys { it != songId && it !in excludedSongIds }
                    .map { (id, candidate) -> id to cosineSimilarity(vector, candidate) }
                    .sortedByDescending { (_, similarity) -> similarity }
                    .map { (id, _) -> id }
            }
        val library = MusicLibrary(context)
        val candidates =
            withContext(Dispatchers.IO) {
                ranked
                    .asSequence()
                    .mapNotNull { library.findSong(it) }
                    .take(CANDIDATES)
                    .toList()
            }
        return candidates.randomOrNull()
    }

    private fun hasMusicAccess(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun cosineSimilarity(
        a: FloatArray,
        b: FloatArray,
    ): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        return if (normA == 0.0 || normB == 0.0) 0.0 else dot / sqrt(normA * normB)
    }

    private companion object {
        const val CANDIDATES = 3
    }
}
