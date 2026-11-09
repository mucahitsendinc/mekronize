package com.mekronize.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.flow.MutableStateFlow

private class DbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "mekronize.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE media(
                id INTEGER PRIMARY KEY, title TEXT NOT NULL, type TEXT NOT NULL, source TEXT NOT NULL,
                youtube_id TEXT, thumb TEXT, ext TEXT, size INTEGER DEFAULT 0, sha TEXT, url TEXT,
                remote_updated INTEGER DEFAULT 0, status INTEGER DEFAULT 0, file TEXT,
                duration_ms INTEGER DEFAULT 0, error TEXT,
                favorite INTEGER DEFAULT 0, play_count INTEGER DEFAULT 0, last_played INTEGER DEFAULT 0)""",
        )
        db.execSQL("CREATE TABLE playlist(id INTEGER PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL(
            """CREATE TABLE playlist_item(playlist_id INTEGER NOT NULL, media_id INTEGER NOT NULL,
                position INTEGER NOT NULL, PRIMARY KEY(playlist_id, media_id))""",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun onConfigure(db: SQLiteDatabase) {
        db.enableWriteAheadLogging()
    }
}

/** Tüm yerel veri. Metotlar bloklayıcıdır: arka plan iş parçacığından çağır. */
object Repo {
    private lateinit var db: SQLiteDatabase
    private lateinit var ctx: Context
    val changes = MutableStateFlow(0)

    @Synchronized
    fun init(context: Context) {
        if (::db.isInitialized) return
        ctx = context.applicationContext
        db = DbHelper(ctx).writableDatabase
    }

    private fun bump() {
        changes.value = changes.value + 1
    }

    private const val COLS =
        "id,title,type,source,file,duration_ms,favorite,play_count,status,error"

    private fun Cursor.toTrack() = Track(
        id = getLong(0), title = getString(1), type = getString(2), source = getString(3),
        file = getString(4), durationMs = getLong(5), favorite = getInt(6) == 1,
        playCount = getInt(7), status = getInt(8), error = getString(9),
    )

    private fun query(where: String?, args: Array<String>?, order: String, limit: String? = null): List<Track> {
        val out = ArrayList<Track>()
        db.query("media", COLS.split(",").toTypedArray(), where, args, null, null, order, limit).use { c ->
            while (c.moveToNext()) out.add(c.toTrack())
        }
        return out
    }

    private const val READY = "status=1"

    fun all(): List<Track> = query(READY, null, "title COLLATE NOCASE")
    fun videos(): List<Track> = query("$READY AND type='video'", null, "title COLLATE NOCASE")
    fun music(): List<Track> = query("$READY AND type='audio'", null, "title COLLATE NOCASE")

    /** Elle işaretlenenler önce; sonra en çok çalınanlar. */
    fun favorites(): List<Track> = query(
        "$READY AND (favorite=1 OR play_count>0)", null,
        "favorite DESC, play_count DESC, last_played DESC", "200",
    )

    fun audioOnly(list: List<Track>) = list.filter { !it.isVideo }

    fun track(id: Long): Track? = query("id=?", arrayOf(id.toString()), "id").firstOrNull()

    fun playlists(): List<PlaylistInfo> {
        val out = ArrayList<PlaylistInfo>()
        db.rawQuery(
            """SELECT p.id, p.name, (SELECT COUNT(*) FROM playlist_item i JOIN media m ON m.id=i.media_id
               WHERE i.playlist_id=p.id AND m.status=1) FROM playlist p ORDER BY p.name COLLATE NOCASE""",
            null,
        ).use { c -> while (c.moveToNext()) out.add(PlaylistInfo(c.getLong(0), c.getString(1), c.getInt(2))) }
        return out
    }

    fun playlistName(id: Long): String? =
        db.rawQuery("SELECT name FROM playlist WHERE id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }

    fun playlistTracks(id: Long): List<Track> = query(
        "$READY AND id IN (SELECT media_id FROM playlist_item WHERE playlist_id=?)", arrayOf(id.toString()),
        "(SELECT position FROM playlist_item WHERE playlist_id=$id AND media_id=media.id)",
    )

    // ---- Çalma istatistikleri (cihaza özel, senkronda silinmez) ----
    fun toggleFavorite(id: Long) {
        db.execSQL("UPDATE media SET favorite = 1 - favorite WHERE id=?", arrayOf(id))
        bump()
    }

    fun incrementPlay(id: Long) {
        db.execSQL(
            "UPDATE media SET play_count = play_count + 1, last_played=? WHERE id=?",
            arrayOf(System.currentTimeMillis(), id),
        )
        bump()
    }

    // ---- Senkron ----
    fun counts(): Triple<Int, Int, Int> {
        var ready = 0; var pending = 0; var failed = 0
        db.rawQuery("SELECT status, COUNT(*) FROM media GROUP BY status", null).use { c ->
            while (c.moveToNext()) when (c.getInt(0)) {
                Status.READY -> ready = c.getInt(1)
                Status.PENDING -> pending = c.getInt(1)
                else -> failed = c.getInt(1)
            }
        }
        return Triple(ready, pending, failed)
    }

    /** İndirilmesi gerekenler (bekleyen + hatalı). */
    fun toDownload(): List<Track> = query("status!=1", null, "id")

    fun remoteInfo(id: Long): RemoteItem? =
        db.query("media", arrayOf("id", "title", "type", "source", "remote_updated", "youtube_id", "thumb", "size", "sha", "ext", "url"),
            "id=?", arrayOf(id.toString()), null, null, null).use { c ->
            if (!c.moveToFirst()) null else RemoteItem(
                c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4),
                c.getString(5), c.getString(6), c.getLong(7), c.getString(8), c.getString(9), c.getString(10),
            )
        }

    /** Manifesti yerel veritabanına uygular: yeniler eklenir, sunucuda kalmayanlar cihazdan silinir. */
    fun applyManifest(m: Manifest) {
        db.beginTransaction()
        try {
            val existing = HashMap<Long, Triple<Long, String?, String?>>() // id -> (remote_updated, sha, youtube_id)
            db.rawQuery("SELECT id, remote_updated, sha, youtube_id FROM media", null).use { c ->
                while (c.moveToNext()) existing[c.getLong(0)] = Triple(c.getLong(1), c.getString(2), c.getString(3))
            }
            val remoteIds = m.items.map { it.id }.toHashSet()

            for (id in existing.keys) if (id !in remoteIds) {
                deleteFile(id)
                db.delete("media", "id=?", arrayOf(id.toString()))
            }

            for (it in m.items) {
                val cv = ContentValues().apply {
                    put("title", it.title); put("type", it.type); put("source", it.source)
                    put("youtube_id", it.youtubeId); put("thumb", it.thumbnail); put("ext", it.ext)
                    put("size", it.size); put("sha", it.sha256); put("url", it.url)
                    put("remote_updated", it.updatedAt)
                }
                val old = existing[it.id]
                if (old == null) {
                    cv.put("id", it.id)
                    db.insert("media", null, cv)
                } else {
                    val changed = old.second != it.sha256 || old.third != it.youtubeId
                    if (changed) {
                        deleteFile(it.id)
                        cv.put("status", Status.PENDING); cv.putNull("file"); cv.putNull("error")
                    }
                    db.update("media", cv, "id=?", arrayOf(it.id.toString()))
                }
            }

            db.delete("playlist_item", null, null)
            db.delete("playlist", null, null)
            for (p in m.playlists) {
                db.insert("playlist", null, ContentValues().apply { put("id", p.id); put("name", p.name) })
                p.mediaIds.forEachIndexed { pos, mid ->
                    db.insert("playlist_item", null, ContentValues().apply {
                        put("playlist_id", p.id); put("media_id", mid); put("position", pos)
                    })
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        bump()
    }

    fun markReady(id: Long, file: String, durationMs: Long) {
        db.update("media", ContentValues().apply {
            put("status", Status.READY); put("file", file); put("duration_ms", durationMs); putNull("error")
        }, "id=?", arrayOf(id.toString()))
        bump()
    }

    fun markFailed(id: Long, error: String) {
        db.update("media", ContentValues().apply { put("status", Status.FAILED); put("error", error.take(200)) }, "id=?", arrayOf(id.toString()))
        bump()
    }

    private fun deleteFile(id: Long) = Storage.removeFor(ctx, id)

    /** Hazır içeriklerin kimlikleri (artık dosya temizliği için). */
    fun readyIds(): Set<Long> {
        val s = HashSet<Long>()
        db.rawQuery("SELECT id FROM media WHERE status=1", null).use { c -> while (c.moveToNext()) s.add(c.getLong(0)) }
        return s
    }

    fun clearAll() {
        db.delete("playlist_item", null, null); db.delete("playlist", null, null); db.delete("media", null, null)
        Storage.dir(ctx).listFiles()?.forEach { it.delete() }
        bump()
    }
}
