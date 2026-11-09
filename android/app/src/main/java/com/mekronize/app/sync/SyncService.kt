package com.mekronize.app.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.mekronize.app.R
import com.mekronize.app.data.Repo
import com.mekronize.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Senkronu uygulama arka plana gitse de sürdürür (ön plan servisi + bildirim). */
class SyncService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var notifJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Repo.init(this)
        if (intent?.action == ACTION_CANCEL) {
            if (job?.isActive == true) job?.cancel() else stopSelf()
            return START_NOT_STICKY
        }
        createChannel()
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification("Senkron başlıyor…", 0, 0, true), type)

        if (job?.isActive == true) return START_NOT_STICKY
        job = scope.launch {
            notifJob = launch {
                SyncState.flow.collect { s ->
                    if (s.running) {
                        val title = if (s.itemName.isNotEmpty()) "${s.doneItems + 1}/${s.totalItems} · ${s.itemName}" else s.message
                        notify(buildNotification(title, s.itemPct, 100, s.itemName.isEmpty()))
                    }
                }
            }
            try {
                Syncer.run(applicationContext)
            } finally {
                notifJob?.cancel()
                ServiceCompat.stopForeground(this@SyncService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notify(n: Notification) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)
    }

    private fun buildNotification(text: String, progress: Int, max: Int, indeterminate: Boolean): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            this, 1, Intent(this, SyncService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Mekronize senkron")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .setProgress(max, progress, indeterminate)
            .addAction(0, "İptal", cancel)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Senkron", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "sync"
        private const val NOTIF_ID = 41
        const val ACTION_CANCEL = "com.mekronize.app.CANCEL_SYNC"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, SyncService::class.java))
        }

        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, SyncService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
