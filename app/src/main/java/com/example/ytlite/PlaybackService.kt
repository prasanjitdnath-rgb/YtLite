package com.example.ytlite

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Foreground media service: keeps playing with screen off / app closed, shows lock-screen controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(MergingFactory())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)   // keep CPU + Wi-Fi awake while locked
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(info: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}

/** Merges separate video + audio streams (needed for >360p on YouTube). */
private class MergingFactory : MediaSource.Factory {
    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent(AppDownloader.USER_AGENT).setAllowCrossProtocolRedirects(true)
    private val default = DefaultMediaSourceFactory(http)
    private val progressive = ProgressiveMediaSource.Factory(http)

    override fun createMediaSource(item: MediaItem): MediaSource {
        val audio = item.mediaMetadata.extras?.getString("audio")
            ?: return default.createMediaSource(item)
        return MergingMediaSource(
            progressive.createMediaSource(item),
            progressive.createMediaSource(MediaItem.fromUri(audio))
        )
    }
    override fun getSupportedTypes() = default.supportedTypes
    override fun setDrmSessionManagerProvider(p: DrmSessionManagerProvider) = apply { default.setDrmSessionManagerProvider(p) }
    override fun setLoadErrorHandlingPolicy(p: LoadErrorHandlingPolicy) = apply { default.setLoadErrorHandlingPolicy(p) }
}
