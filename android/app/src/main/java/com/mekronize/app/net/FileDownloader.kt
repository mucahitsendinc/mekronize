package com.mekronize.app.net

import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

object FileDownloader {
    private const val GV_CHUNK = 2L * 1024 * 1024

    /**
     * Bir URL'yi dosyaya indirir; yarım kalan dosyadan devam eder (Range).
     * googlevideo adresleri YouTube'un hız sınırına takılmasın diye parça parça (range=) çekilir.
     */
    suspend fun download(
        url: String,
        dest: File,
        userAgent: String? = null,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        if (url.contains("googlevideo.com")) downloadChunked(url, dest, userAgent, onProgress)
        else downloadRanged(url, dest, userAgent, onProgress)
    }

    private suspend fun downloadRanged(url: String, dest: File, ua: String?, onProgress: (Long, Long) -> Unit) {
        var have = if (dest.exists()) dest.length() else 0L
        val rb = Request.Builder().url(url)
        ua?.let { rb.header("User-Agent", it) }
        if (have > 0) rb.header("Range", "bytes=$have-")
        Http.client.newCall(rb.build()).execute().use { resp ->
            if (resp.code == 416) { // zaten tamamlanmış olabilir
                onProgress(have, have); return
            }
            if (!resp.isSuccessful) throw IOException("Sunucu hatası ${resp.code}")
            val append = resp.code == 206
            if (!append) have = 0
            val body = resp.body ?: throw IOException("Boş yanıt")
            val total = have + body.contentLength().let { if (it < 0) 0 else it }
            copy(body.byteStream(), dest, append, have, total, onProgress)
        }
    }

    private suspend fun downloadChunked(url: String, dest: File, ua: String?, onProgress: (Long, Long) -> Unit) {
        val total = Regex("[?&]clen=(\\d+)").find(url)?.groupValues?.get(1)?.toLongOrNull() ?: -1L
        var pos = if (dest.exists()) dest.length() else 0L
        if (total in 1 until pos) { dest.delete(); pos = 0 }
        while (total < 0 || pos < total) {
            coroutineContext.ensureActive()
            val end = if (total > 0) minOf(pos + GV_CHUNK - 1, total - 1) else pos + GV_CHUNK - 1
            val rb = Request.Builder().url("$url&range=$pos-$end")
            ua?.let { rb.header("User-Agent", it) }
            var got = 0L
            Http.client.newCall(rb.build()).execute().use { resp ->
                if (resp.code == 403) throw IOException("YouTube indirmeye izin vermedi (403)")
                if (!resp.isSuccessful) throw IOException("YouTube hatası ${resp.code}")
                val body = resp.body ?: throw IOException("Boş yanıt")
                FileOutputStream(dest, true).use { out ->
                    val buf = ByteArray(32 * 1024)
                    val ins = body.byteStream()
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                    }
                }
            }
            pos += got
            onProgress(pos, if (total > 0) total else pos)
            if (got == 0L || (total < 0 && got < GV_CHUNK)) break
        }
    }

    private suspend fun copy(ins: java.io.InputStream, dest: File, append: Boolean, startAt: Long, total: Long, onProgress: (Long, Long) -> Unit) {
        FileOutputStream(dest, append).use { out ->
            val buf = ByteArray(64 * 1024)
            var done = startAt
            var last = 0L
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                val now = System.nanoTime()
                if (now - last > 250_000_000L) {
                    last = now
                    coroutineContext.ensureActive()
                    onProgress(done, total)
                }
            }
            onProgress(done, total)
        }
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
