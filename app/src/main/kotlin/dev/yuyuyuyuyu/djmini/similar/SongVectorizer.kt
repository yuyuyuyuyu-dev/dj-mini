package dev.yuyuyuyuyu.djmini.similar

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.Executors

internal class SongVectorizer private constructor(
    private val context: Context,
    private val scorer: SoundScorer,
    private val modelDispatcher: CoroutineDispatcher,
) {
    suspend fun vectorOf(uri: Uri): FloatArray? {
        val windows =
            withContext(Dispatchers.IO) {
                try {
                    SongAudio(context).windows(uri)
                } catch (_: IOException) {
                    emptyList()
                } catch (_: IllegalStateException) {
                    emptyList()
                } catch (_: IllegalArgumentException) {
                    emptyList()
                } catch (_: SecurityException) {
                    emptyList()
                }
            }
        if (windows.isEmpty()) return null
        return withContext(modelDispatcher) { windows.map(scorer::scores).average() }
    }

    private fun List<FloatArray>.average(): FloatArray =
        FloatArray(first().size) { index -> sumOf { it[index].toDouble() }.toFloat() / size }

    companion object {
        suspend fun <T> use(
            context: Context,
            block: suspend (SongVectorizer) -> T,
        ): T {
            val environment = LiteRtEnvironment.get(context)
            val executor = Executors.newSingleThreadExecutor()
            try {
                val dispatcher = executor.asCoroutineDispatcher()
                val scorer = withContext(dispatcher) { SoundScorer(context, environment) }
                try {
                    return block(SongVectorizer(context, scorer, dispatcher))
                } finally {
                    withContext(NonCancellable + dispatcher) { scorer.close() }
                }
            } finally {
                executor.shutdown()
            }
        }
    }
}
