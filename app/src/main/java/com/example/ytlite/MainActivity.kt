package com.example.ytlite

import android.Manifest
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var future: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Screen(controller) } }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({
            val c = f.get()
            setVideoEnabled(c, true)       // video back on when app is visible
            controller = c
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        // Screen off / app hidden: drop video decoding, audio keeps playing in the service.
        controller?.let { setVideoEnabled(it, false) }
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }

    private fun setVideoEnabled(c: Player, on: Boolean) {
        c.trackSelectionParameters = c.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, !on).build()
    }
}

@Composable
private fun Screen(player: Player?) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StreamInfoItem>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var hasMedia by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(m: androidx.media3.common.MediaItem?, r: Int) { hasMedia = m != null }
        }
        player?.addListener(l); hasMedia = (player?.mediaItemCount ?: 0) > 0
        onDispose { player?.removeListener(l) }
    }

    fun search() = scope.launch {
        busy = true; msg = null
        runCatching { Repo.search(query) }
            .onSuccess { results = it }.onFailure { msg = it.message }
        busy = false
    }
    fun play(i: StreamInfoItem) = scope.launch {
        busy = true; msg = null
        runCatching { Repo.resolve(i) }.onSuccess {
            player?.apply { setMediaItem(it); prepare(); play() }
        }.onFailure { msg = it.message }
        busy = false
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        if (hasMedia && player != null) {
            AndroidView(
                factory = { PlayerView(it).apply { this.player = player } },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().aspectRatio(16 / 9f)
            )
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Search YouTube") }, modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { search() }, enabled = query.isNotBlank()) { Text("Go") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        msg?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        LazyColumn(Modifier.weight(1f)) {
            items(results) { v ->
                Row(Modifier.fillMaxWidth().clickable { play(v) }.padding(8.dp)) {
                    AsyncImage(
                        model = v.thumbnails.firstOrNull()?.url, contentDescription = null,
                        modifier = Modifier.width(140.dp).aspectRatio(16 / 9f).clip(MaterialTheme.shapes.small)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(v.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(v.uploaderName ?: "", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
