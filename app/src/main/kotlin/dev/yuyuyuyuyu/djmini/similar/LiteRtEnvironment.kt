package dev.yuyuyuyuyu.djmini.similar

import android.content.Context
import com.google.ai.edge.litert.Environment
import com.google.android.gms.litert.LiteRt
import com.google.android.gms.litert.client.LiteRtInitializationOptions
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

internal object LiteRtEnvironment {
    private val mutex = Mutex()
    private var environment: Environment? = null

    suspend fun get(context: Context): Environment =
        mutex.withLock {
            environment ?: LiteRt
                .initialize(context.applicationContext, LiteRtInitializationOptions.builder().build())
                .await()
                .also { environment = it }
        }
}
