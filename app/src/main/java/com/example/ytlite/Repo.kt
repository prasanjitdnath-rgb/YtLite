package com.example.ytlite

import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Talks to YouTube through the NewPipe extractor: no ads are ever returned. */
object Repo {
    private val yt = ServiceList.YouTube

    suspend fun search(q: String): List<StreamInfoItem> = withContext(Dispatchers.IO) {
        SearchInfo.getInfo(yt, yt.searchQHFactory.fromQuery(q))
            .relatedItems.filterIsInstance<StreamInfoItem>()
    }

    suspend fun resolve(item: StreamInfoItem): MediaItem = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(yt, item.url)
        val extras = Bundle()
        val uri: String

        val live = info.streamType == StreamType.LIVE_STREAM && info.hlsUrl.isNotEmpty()
        if (live) {
            uri = info.hlsUrl
        } else {
            val video = info.videoOnlyStreams
                .filter { it.format == MediaFormat.MPEG_4 && it.height <= 1080 }
                .maxByOrNull { it.height }
            val audio = info.audioStreams
                .filter { it.format == MediaFormat.M4A }
                .maxByOrNull { it.averageBitrate }
                ?: info.audioStreams.maxByOrNull { it.averageBitrate }
            val muxed = info.videoStreams.maxByOrNull { it.height }
            when {
                video != null && audio != null -> {
                    uri = video.content; extras.putString("audio", audio.content)
                }
                muxed != null -> uri = muxed.content
                audio != null -> uri = audio.content
                else -> error("No playable stream found")
            }
        }
        val art = info.thumbnails.maxByOrNull { it.height }?.url
        MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(info.name)
                    .setArtist(info.uploaderName)
                    .setArtworkUri(art?.toUri())
                    .setExtras(extras)
                    .build()
            ).build()
    }
}
