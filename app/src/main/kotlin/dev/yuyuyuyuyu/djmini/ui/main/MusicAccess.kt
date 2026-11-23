package dev.yuyuyuyuyu.djmini.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.yuyuyuyuyu.djmini.similar.SongIndexWorker

internal enum class MusicAccess {
    Granted,
    NotGranted,
}

internal class MusicAccessHolder(
    val state: MusicAccess,
    val request: () -> Unit,
)

@Composable
internal fun rememberMusicAccess(): MusicAccessHolder {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(granted) {
        if (granted) SongIndexWorker.enqueue(context)
    }

    return MusicAccessHolder(if (granted) MusicAccess.Granted else MusicAccess.NotGranted) {
        launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }
}
