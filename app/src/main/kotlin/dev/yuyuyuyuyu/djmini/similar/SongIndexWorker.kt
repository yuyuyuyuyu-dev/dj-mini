package dev.yuyuyuyuyu.djmini.similar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Operation
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import kotlinx.coroutines.flow.first

class SongIndexWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (applicationContext.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            SongIndex(applicationContext).update()
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "song-index"

        fun enqueue(context: Context): Operation =
            WorkManager
                .getInstance(context)
                .enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<SongIndexWorker>().build())

        suspend fun update(context: Context) {
            enqueue(context).await()
            WorkManager
                .getInstance(context)
                .getWorkInfosForUniqueWorkFlow(NAME)
                .first { infos -> infos.all { it.state.isFinished } }
        }
    }
}
