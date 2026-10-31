package com.actuallyrizzn.monocle

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.IOException

class MonocleService : Service() {

    companion object {
        const val ACTION_STOP = "com.actuallyrizzn.monocle.STOP"
        private const val CHANNEL_ID = "monocle_bridge_fg"
        private const val NOTIFICATION_ID = 42

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        var ble: MonocleBleClient? = null

        @Volatile
        var presence: PresenceLoop? = null

        @Volatile
        var presenceClient: PresenceClient? = null
    }

    private var http: MonocleHttpServer? = null
    private var client: MonocleBleClient? = null
    private var listen: ListenLoop? = null
    private var presenceLoop: PresenceLoop? = null
    private val notifyUi = Handler(Looper.getMainLooper())
    private val notifyTick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification())
            } catch (_: Exception) {
            }
            notifyUi.postDelayed(this, 2_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopEverything()
            return START_NOT_STICKY
        }
        if (http != null) return START_STICKY

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
        }

        val bleClient = MonocleBleClient(applicationContext)
        client = bleClient
        ble = bleClient
        val loop = ListenLoop(bleClient)
        listen = loop
        try {
            val server = MonocleHttpServer(bleClient, applicationContext, loop)
            val prefs = PresencePrefs(applicationContext)
            val presenceClient = PresenceClient(prefs.url(), prefs.apiKey())
            val hb = PresenceLoop(
                client = presenceClient,
                connected = { bleClient.isConnected() },
                deviceName = { bleClient.deviceName },
                paint = { text, subject, sender ->
                    val out = server.paint(
                        text = text,
                        subject = subject.ifBlank { null },
                        sender = sender.ifBlank { null },
                    )
                    !out.startsWith("ERROR:")
                },
                ask = { text, subject, sender ->
                    server.askCard(text, subject, sender)
                    true
                },
            )
            server.presenceLoop = hb
            server.presenceClient = presenceClient
            server.presencePrefs = prefs
            server.start()
            http = server
            bleClient.onConnected = { http?.onLensUp() }
            presenceLoop = hb
            presence = hb
            Companion.presenceClient = presenceClient
            hb.start()
            isRunning = true
            notifyUi.removeCallbacks(notifyTick)
            notifyUi.post(notifyTick)
        } catch (_: IOException) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        Thread {
            bleClient.connectBlocking(20_000)
            if (bleClient.isConnected()) {
                http?.onLensUp()
            }
        }.start()
        return START_STICKY
    }

    override fun onDestroy() {
        stopEverything()
        super.onDestroy()
    }

    private fun stopEverything() {
        isRunning = false
        notifyUi.removeCallbacks(notifyTick)
        client?.onConnected = null
        ble = null
        presenceLoop?.stop()
        presenceLoop = null
        presence = null
        presenceClient = null
        listen?.stop()
        listen = null
        try {
            http?.stop()
        } catch (_: Exception) {
        }
        http = null
        client?.close()
        client = null
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Monocle bridge",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun buildNotification(): Notification {
        ensureChannel()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monocle bridge ${BuildConfig.VERSION_NAME}")
            .setContentText(BridgeStatus.notification(
                client?.isConnected() == true || ble?.isConnected() == true,
                BuildConfig.VERSION_NAME,
            ))
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }
}
