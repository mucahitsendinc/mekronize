package com.mekronize.app.sync

import com.mekronize.app.net.FileDownloader
import com.mekronize.app.net.YtDownloader
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.File
import kotlin.math.abs

/** YouTube'dan doğrudan (cihazda) çözüp indirir. Sunucu yalnızca linki tutar. */
object YouTubeFetcher {
    class Result(val file: File, val durationMs: Long)

    @Volatile private var inited = false

    @Synchronized
    private fun init() {
        if (!inited) {
            NewPipe.init(YtDownloader)
            inited = true
        }
    }

    fun info(videoId: String): StreamInfo {
        init()
        return StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
    }

    private fun AudioStream.ok() =
        deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isUrl && content.isNotBlank() &&
            (audioTrackType == null || audioTrackType == AudioTrackType.ORIGINAL)

    private fun VideoStream.ok() = deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && isUrl && content.isNotBlank()

    fun pickAudio(list: List<AudioStream>): AudioStream? {
        val prog = list.filter { it.ok() }
        val m4a = prog.filter { it.format == MediaFormat.M4A }
        val pool = if (m4a.isNotEmpty()) m4a else prog
        return pool.minByOrNull { abs((if (it.averageBitrate > 0) it.averageBitrate else it.bitrate / 1000) - 128) }
    }

    private fun isAvc(v: VideoStream) = v.codec?.startsWith("avc1") == true

    /** @return (sesli hazır video, sessiz H.264 video) — ikincisi seçilirse ses ayrı indirilip birleştirilir. */
    fun pickVideo(info: StreamInfo, maxHeight: Int): Pair<VideoStream?, VideoStream?> {
        val muxed = info.videoStreams.filter { it.ok() && it.format == MediaFormat.MPEG_4 && it.height in 1..maxHeight }
            .maxByOrNull { it.height }
        val only = info.videoOnlyStreams.filter { it.ok() && it.format == MediaFormat.MPEG_4 && isAvc(it) && it.height in 1..maxHeight }
            .maxByOrNull { it.height }
        if (only != null && (muxed == null || only.height > muxed.height)) return null to only
        return muxed to null
    }

    suspend fun fetch(
        videoId: String,
        isVideo: Boolean,
        quality: Int,
        id: Long,
        dir: File,
        onProgress: (pct: Int) -> Unit,
    ): Result {
        val info = info(videoId)
        val durationMs = info.duration * 1000

        if (!isVideo) {
            val a = pickAudio(info.audioStreams) ?: throw IllegalStateException("Ses akışı bulunamadı")
            val ext = if (a.format == MediaFormat.M4A) "m4a" else "webm"
            val part = File(dir, "$id.$ext.part")
            FileDownloader.download(a.content, part) { d, t -> onProgress(pct(d, t)) }
            val dest = File(dir, "$id.$ext")
            dest.delete(); part.renameTo(dest)
            return Result(dest, durationMs)
        }

        val maxH = if (quality == 1) 720 else 360
        val (muxed, only) = pickVideo(info, maxH)
        if (muxed != null) {
            val part = File(dir, "$id.mp4.part")
            FileDownloader.download(muxed.content, part) { d, t -> onProgress(pct(d, t)) }
            val dest = File(dir, "$id.mp4")
            dest.delete(); part.renameTo(dest)
            return Result(dest, durationMs)
        }
        if (only != null) {
            // Sessiz H.264 video + ayrı AAC ses: birleştirmeden iki dosya olarak saklanır,
            // oynatıcı (MergingMediaSource) ikisini birlikte çalar. Eski cihazlarda MediaMuxer'a bağımlı değiliz.
            val a = info.audioStreams.filter { it.ok() && it.format == MediaFormat.M4A }
                .minByOrNull { abs((if (it.averageBitrate > 0) it.averageBitrate else it.bitrate / 1000) - 128) }
                ?: throw IllegalStateException("Video için AAC ses bulunamadı")
            val vPart = File(dir, "$id.v.mp4.part"); val aPart = File(dir, "$id.a.m4a.part")
            FileDownloader.download(only.content, vPart) { d, t -> onProgress(pct(d, t) * 9 / 10) }
            FileDownloader.download(a.content, aPart) { d, t -> onProgress(90 + pct(d, t) / 10) }
            val aDest = File(dir, "$id.a.m4a"); aDest.delete(); aPart.renameTo(aDest)
            val vDest = File(dir, "$id.v.mp4"); vDest.delete(); vPart.renameTo(vDest)
            return Result(vDest, durationMs)
        }
        throw IllegalStateException("Uygun video akışı bulunamadı")
    }

    private fun pct(d: Long, t: Long) = if (t > 0) ((d * 100) / t).toInt().coerceIn(0, 100) else 0
}
