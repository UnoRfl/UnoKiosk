package com.uno.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var topOverlay: View? = null
    private var bottomOverlay: View? = null

    override fun onCreate() {
        super.onCreate()
        startAsForeground()
        installOverlays()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun startAsForeground() {
        val channelId = "uno_kiosk_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                val ch = NotificationChannel(
                    channelId,
                    "Kiosk lockdown",
                    NotificationManager.IMPORTANCE_MIN
                )
                ch.description = "Keeps the kiosk overlay active"
                nm.createNotificationChannel(ch)
            }
        }
        val tapIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Kiosk locked")
            .setContentText("Tap for kiosk")
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun installOverlays() {
        if (!canDrawOverlays()) {
            stopSelf()
            return
        }
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        val flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val density = resources.displayMetrics.density
        val topHeightPx = (48 * density).toInt()   // blocks status bar pulldown
        val bottomHeightPx = (24 * density).toInt() // blocks gesture nav bottom edge

        val topParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            topHeightPx,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }

        val bottomParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            bottomHeightPx,
            overlayType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM }

        topOverlay = View(this).apply { setBackgroundColor(0x00000000) }
        bottomOverlay = View(this).apply { setBackgroundColor(0x00000000) }

        try {
            windowManager?.addView(topOverlay, topParams)
            windowManager?.addView(bottomOverlay, bottomParams)
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    override fun onDestroy() {
        try { topOverlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        try { bottomOverlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        topOverlay = null
        bottomOverlay = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val NOTIF_ID = 8421

        fun start(ctx: android.content.Context) {
            val i = Intent(ctx, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }

        fun stop(ctx: android.content.Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }
}
