package com.mekronize.app.play

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import java.io.File

/**
 * "<id>.v.mp4" (sessiz video) yanında "<id>.a.m4a" (ses) varsa ikisini birleştirerek çalar.
 * Birleştirme (mux) yapılmaz; eski cihazlarda MediaMuxer kilitlenmesinden kaçınılır.
 */
@UnstableApi
class SidecarSourceFactory(ctx: Context) : MediaSource.Factory {
    private val inner = DefaultMediaSourceFactory(ctx)
    private val progressive = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(ctx))

    override fun setDrmSessionManagerProvider(p: DrmSessionManagerProvider): MediaSource.Factory {
        inner.setDrmSessionManagerProvider(p); progressive.setDrmSessionManagerProvider(p); return this
    }

    override fun setLoadErrorHandlingPolicy(p: LoadErrorHandlingPolicy): MediaSource.Factory {
        inner.setLoadErrorHandlingPolicy(p); progressive.setLoadErrorHandlingPolicy(p); return this
    }

    override fun getSupportedTypes(): IntArray = inner.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val path = mediaItem.localConfiguration?.uri?.path
        if (path != null && path.endsWith(".v.mp4")) {
            val audio = File(path.removeSuffix(".v.mp4") + ".a.m4a")
            if (audio.exists()) {
                return MergingMediaSource(
                    progressive.createMediaSource(mediaItem),
                    progressive.createMediaSource(MediaItem.fromUri(Uri.fromFile(audio))),
                )
            }
        }
        return inner.createMediaSource(mediaItem)
    }
}
