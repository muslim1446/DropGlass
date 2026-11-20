package com.example.system

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Fallback foreground overlay host used only when replaying drops manually while the
 * NotificationListenerService isn't attached. Real incoming alerts render directly
 * from DropNotificationListenerService via LiquidOverlayController without any persistent
 * foreground service notice.
 */
class LiquidOverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var overlayController: LiquidOverlayController

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        overlayController = LiquidOverlayController(this) {
            stopSelf()
        }
        SystemBridgeHelper.ensureChannels(this)
        startForegroundCompat()
        observeStore()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP, ACTION_HIDE -> {
                overlayController.detach()
                stopSelf()
            }
            else -> {
                if (!canHostOverlay() || LiveNotificationStore.activeSystemDrop.value == null) {
                    stopSelf()
                } else {
                    overlayController.showOrUpdate()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun canHostOverlay(): Boolean =
        SystemBridgeHelper.canDrawOverlays(this) &&
            LiveNotificationStore.latestConfig.value.enableSystemOverlay

    private fun observeStore() {
        serviceScope.launch {
            LiveNotificationStore.activeSystemDrop.collect { drop ->
                if (drop == null) {
                    overlayController.detach()
                    stopSelf()
                } else if (canHostOverlay()) {
                    overlayController.showOrUpdate()
                }
            }
        }
    }

    private fun startForegroundCompat() {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, SystemBridgeHelper.OVERLAY_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.overlay_channel_desc))
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                FOREGROUND_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(FOREGROUND_ID, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayController.detach()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val FOREGROUND_ID = 7731
        private const val ACTION_SHOW = "com.example.ACTION_SHOW_DROP"
        private const val ACTION_HIDE = "com.example.ACTION_HIDE_DROP"
        private const val ACTION_STOP = "com.example.ACTION_STOP_OVERLAY_ENGINE"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun showCurrentDrop(context: Context) {
            val intent = Intent(context, LiquidOverlayService::class.java).apply {
                action = ACTION_SHOW
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun hideDrop(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, LiquidOverlayService::class.java).apply { action = ACTION_HIDE }
                )
            }
        }

        fun stop(context: Context) {
            if (!isRunning) return
            runCatching {
                context.startService(
                    Intent(context, LiquidOverlayService::class.java).apply { action = ACTION_STOP }
                )
            }
        }
    }
}
