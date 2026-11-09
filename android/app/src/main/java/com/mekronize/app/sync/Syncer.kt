package com.mekronize.app.sync

import android.content.Context
import android.media.MediaMetadataRetriever
import com.mekronize.app.data.Manifest
import com.mekronize.app.data.Prefs
import com.mekronize.app.data.RemoteItem
import com.mekronize.app.data.RemotePlaylist
import com.mekronize.app.data.Repo
import com.mekronize.app.data.Status
import com.mekronize.app.data.Storage
import com.mekronize.app.net.FileDownloader
import com.mekronize.app.net.Http
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

object Syncer {
    class SyncException(msg: String) : Exception(msg)

    fun fetchManifest(base: String, key: String): Manifest {
        val body = JSONObject().put("key", key).toString().toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url("$base/api/v1/sync").post(body).header("Accept", "application/json").build()
        Http.client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            when {
                r.code == 404 -> throw SyncException("Geçersiz senkron key.")
                r.code == 429 -> throw SyncException("Çok fazla deneme. Biraz bekleyip tekrar dene.")
                !r.isSuccessful -> throw SyncException("Sunucu hatası (${r.code}).")
            }
            val j = try { JSONObject(text) } catch (e: Exception) { throw SyncException("Sunucudan geçersiz yanıt geldi.") }
            val items = ArrayList<RemoteItem>()
            val arr = j.getJSONArray("items")
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    RemoteItem(
                        id = o.getLong("id"), title = o.getString("title"), type = o.getString("type"),
                        source = o.getString("source"), updatedAt = o.optLong("updated_at"),
                        youtubeId = o.optString("youtube_id").ifEmpty { null },
                        thumbnail = o.optString("thumbnail").ifEmpty { null },
                        size = o.optLong("size"), sha256 = o.optString("sha256").ifEmpty { null },
                        ext = o.optString("ext").ifEmpty { null }, url = o.optString("url").ifEmpty { null },
                    ),
                )
            }
            val pls = ArrayList<RemotePlaylist>()
            val pa = j.getJSONArray("playlists")
            for (i in 0 until pa.length()) {
                val o = pa.getJSONObject(i)
                val ids = o.getJSONArray("media_ids")
                pls.add(RemotePlaylist(o.getLong("id"), o.getString("name"), List(ids.length()) { ids.getLong(it) }))
            }
            return Manifest(items, pls)
        }
    }

    /** Tam senkron: manifest → yerel eşitleme → eksikleri indir. */
    suspend fun run(ctx: Context) {
        val prefs = Prefs(ctx)
        val base = Prefs.normalizeBase(prefs.baseUrl)
        val key = prefs.key.filter { it.isDigit() }
        val errors = ArrayList<String>()
        SyncState.flow.value = SyncUi(running = true, message = "Sunucuya bağlanılıyor…")
        try {
            val manifest = fetchManifest(base, key)
            // Farklı bir key'e geçildiyse eski kütüphane temizlenir.
            val lastKey = ctx.getSharedPreferences("mekronize", Context.MODE_PRIVATE).getString("last_key", null)
            if (lastKey != null && lastKey != key) Repo.clearAll()
            ctx.getSharedPreferences("mekronize", Context.MODE_PRIVATE).edit().putString("last_key", key).apply()

            SyncState.update { it.copy(message = "Liste eşitleniyor…") }
            Repo.applyManifest(manifest)

            val todo = Repo.toDownload()
            SyncState.update { it.copy(totalItems = todo.size, doneItems = 0, message = if (todo.isEmpty()) "Her şey güncel." else "İndiriliyor…") }

            val dir = Storage.dir(ctx)
            for ((n, t) in todo.withIndex()) {
                coroutineContext.ensureActive()
                SyncState.update { it.copy(itemName = t.title, itemPct = 0, doneItems = n) }
                val remote = Repo.remoteInfo(t.id) ?: continue
                try {
                    val (file, dur) = downloadOne(ctx, dir, remote, prefs.quality) { pct -> SyncState.update { s -> s.copy(itemPct = pct) } }
                    Repo.markReady(t.id, file.name, dur)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    val msg = e.message ?: e.javaClass.simpleName
                    Repo.markFailed(t.id, msg)
                    errors.add("${t.title}: $msg")
                    SyncState.update { it.copy(errors = errors.toList()) }
                }
                SyncState.update { it.copy(doneItems = n + 1) }
            }

            // Kayıtta olmayan artık dosyaları temizle
            val readyIds = Repo.readyIds()
            dir.listFiles()?.forEach { f ->
                val id = f.name.substringBefore('.').toLongOrNull()
                if (!f.name.endsWith(".part") && (id == null || id !in readyIds)) f.delete()
            }

            val (ready, _, failed) = Repo.counts()
            SyncState.update {
                it.copy(
                    running = false, finished = true, itemName = "", itemPct = 0,
                    message = "Tamamlandı: $ready içerik hazır" + if (failed > 0) ", $failed indirilemedi." else ".",
                )
            }
        } catch (e: CancellationException) {
            SyncState.update { it.copy(running = false, finished = true, message = "İptal edildi.") }
            throw e
        } catch (e: SyncException) {
            SyncState.update { it.copy(running = false, finished = true, message = e.message ?: "Hata") }
        } catch (e: IOException) {
            SyncState.update { it.copy(running = false, finished = true, message = "Bağlantı hatası: ${e.message ?: "sunucuya ulaşılamadı"}") }
        } catch (e: Throwable) {
            SyncState.update { it.copy(running = false, finished = true, message = "Hata: ${e.message ?: e.javaClass.simpleName}") }
        }
    }

    private suspend fun downloadOne(
        ctx: Context, dir: File, r: RemoteItem, quality: Int, onPct: (Int) -> Unit,
    ): Pair<File, Long> {
        val free = dir.usableSpace
        val file: File
        var durationMs = 0L
        if (r.source == "youtube") {
            if (free < 150L * 1024 * 1024) throw IOException("Depolama alanı yetersiz")
            val res = YouTubeFetcher.fetch(r.youtubeId ?: throw IOException("YouTube kimliği yok"), r.type == "video", quality, r.id, dir, onPct)
            file = res.file; durationMs = res.durationMs
        } else {
            if (free < r.size + 50L * 1024 * 1024) throw IOException("Depolama alanı yetersiz")
            val ext = r.ext?.takeIf { it.isNotBlank() } ?: "bin"
            val part = File(dir, "${r.id}.$ext.part")
            FileDownloader.download(r.url ?: throw IOException("İndirme adresi yok"), part) { d, t ->
                onPct(if (t > 0) ((d * 100) / t).toInt().coerceIn(0, 100) else 0)
            }
            if (!r.sha256.isNullOrEmpty() && FileDownloader.sha256(part) != r.sha256) {
                part.delete(); throw IOException("Dosya doğrulaması başarısız")
            }
            file = File(dir, "${r.id}.$ext")
            file.delete(); part.renameTo(file)
        }
        if (durationMs <= 0) durationMs = readDuration(file)
        return file to durationMs
    }

    private fun readDuration(f: File): Long = try {
        val m = MediaMetadataRetriever()
        try {
            m.setDataSource(f.absolutePath)
            m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally { m.release() }
    } catch (_: Throwable) { 0L }
}
