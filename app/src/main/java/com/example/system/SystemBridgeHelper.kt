package com.example.system

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowInsets
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import com.example.model.DetectedCutoutInfo

object SystemBridgeHelper {

    const val TEST_CHANNEL_ID = "dropglass_test_alerts"
    const val OVERLAY_CHANNEL_ID = "dropglass_overlay_engine"
    const val EXTRA_IS_TEST_DROP = "com.example.EXTRA_IS_TEST_DROP"
    const val EXTRA_TEST_CATEGORY = "com.example.EXTRA_TEST_CATEGORY"
    const val EXTRA_DELIVERY_MILESTONES = "com.example.EXTRA_DELIVERY_MILESTONES"
    const val EXTRA_DELIVERY_ACTIVE_STEP = "com.example.EXTRA_DELIVERY_ACTIVE_STEP"
    const val EXTRA_DELIVERY_ETA = "com.example.EXTRA_DELIVERY_ETA"
    const val KEY_TEST_REPLY = "key_test_reply"

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
        return enabledPackages.contains(context.packageName)
    }

    fun openNotificationListenerSettings(context: Context) {
        val componentName = ComponentName(context, DropNotificationListenerService::class.java)
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, componentName.flattenToString())
            }
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(fallback) }
        }
    }

    fun detectHardwareCutout(activity: Activity): DetectedCutoutInfo {
        val metrics = activity.resources.displayMetrics
        val density = metrics.density.coerceAtLeast(1f)
        val screenWidthPx = metrics.widthPixels
        val screenHeightPx = metrics.heightPixels
        val screenWidthDp = screenWidthPx / density
        val screenHeightDp = screenHeightPx / density

        var statusBarDp = 38f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets
            val statusInsets = insets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())
            if (statusInsets.top > 0) {
                statusBarDp = statusInsets.top / density
            }
            val cutout = insets.displayCutout
            if (cutout != null) {
                val rectF = RectF()
                var found = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val path = cutout.cutoutPath
                    if (path != null && !path.isEmpty) {
                        path.computeBounds(rectF, true)
                        if (rectF.width() > 4f && rectF.height() > 4f) {
                            found = true
                        }
                    }
                }
                if (!found) {
                    val rect = cutout.boundingRects.firstOrNull()
                    if (rect != null && rect.width() > 0 && rect.height() > 0) {
                        rectF.set(rect)
                        found = true
                    }
                }
                if (found) {
                    val rectCenterX = (rectF.left + rectF.right) / 2f
                    val screenCenterX = windowMetrics.bounds.width() / 2f
                    return DetectedCutoutInfo(
                        hasHardwareCutout = true,
                        centerXdp = (rectCenterX - screenCenterX) / density,
                        topDp = rectF.top / density,
                        widthDp = rectF.width() / density,
                        heightDp = rectF.height() / density,
                        statusBarHeightDp = statusBarDp,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        screenWidthDp = screenWidthDp,
                        screenHeightDp = screenHeightDp
                    )
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cutout = activity.window.decorView.rootWindowInsets?.displayCutout
            val rect = cutout?.boundingRects?.firstOrNull()
            if (rect != null && rect.width() > 0 && rect.height() > 0) {
                val rectCenterX = (rect.left + rect.right) / 2f
                val screenCenterX = screenWidthPx / 2f
                return DetectedCutoutInfo(
                    hasHardwareCutout = true,
                    centerXdp = (rectCenterX - screenCenterX) / density,
                    topDp = rect.top / density,
                    widthDp = rect.width() / density,
                    heightDp = rect.height() / density,
                    statusBarHeightDp = statusBarDp,
                    deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                    screenWidthDp = screenWidthDp,
                    screenHeightDp = screenHeightDp
                )
            }
        }

        val statusResId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (statusResId > 0) {
            statusBarDp = activity.resources.getDimensionPixelSize(statusResId) / density
        }

        return DetectedCutoutInfo(
            hasHardwareCutout = false,
            centerXdp = 0f,
            topDp = 12f,
            widthDp = 23f,
            heightDp = 23f,
            statusBarHeightDp = statusBarDp,
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            screenWidthDp = screenWidthDp,
            screenHeightDp = screenHeightDp
        )
    }

    fun detectHardwareCutoutFromService(context: Context): DetectedCutoutInfo {
        val metrics = context.resources.displayMetrics
        val density = metrics.density.coerceAtLeast(1f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = context.getSystemService(WindowManager::class.java)
            val windowMetrics = wm?.currentWindowMetrics
            val cutout = windowMetrics?.windowInsets?.displayCutout
            if (cutout != null) {
                val rectF = RectF()
                var found = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val path = cutout.cutoutPath
                    if (path != null && !path.isEmpty) {
                        path.computeBounds(rectF, true)
                        if (rectF.width() > 4f && rectF.height() > 4f) {
                            found = true
                        }
                    }
                }
                if (!found) {
                    val rect = cutout.boundingRects.firstOrNull()
                    if (rect != null && rect.width() > 0 && rect.height() > 0) {
                        rectF.set(rect)
                        found = true
                    }
                }
                if (found) {
                    val rectCenterX = (rectF.left + rectF.right) / 2f
                    val screenCenterX = windowMetrics.bounds.width() / 2f
                    return DetectedCutoutInfo(
                        hasHardwareCutout = true,
                        centerXdp = (rectCenterX - screenCenterX) / density,
                        topDp = rectF.top / density,
                        widthDp = rectF.width() / density,
                        heightDp = rectF.height() / density,
                        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
                        screenWidthDp = windowMetrics.bounds.width() / density,
                        screenHeightDp = windowMetrics.bounds.height() / density
                    )
                }
            }
        }
        return DetectedCutoutInfo(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            screenWidthDp = metrics.widthPixels / density,
            screenHeightDp = metrics.heightPixels / density
        )
    }

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(TEST_CHANNEL_ID) == null) {
                val testChannel = NotificationChannel(
                    TEST_CHANNEL_ID,
                    context.getString(R.string.test_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.test_channel_desc)
                }
                nm.createNotificationChannel(testChannel)
            }
            if (nm.getNotificationChannel(OVERLAY_CHANNEL_ID) == null) {
                val overlayChannel = NotificationChannel(
                    OVERLAY_CHANNEL_ID,
                    context.getString(R.string.overlay_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = context.getString(R.string.overlay_channel_desc)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(overlayChannel)
            }
        }
    }

    private fun appOpenIntent(context: Context, requestCode: Int): PendingIntent {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun postTestSystemNotification(context: Context) {
        ensureChannels(context)
        val notifId = DropTestActionReceiver.DEFAULT_MSG_NOTIF_ID

        val replyIntent = Intent(context, DropTestActionReceiver::class.java).apply {
            action = DropTestActionReceiver.ACTION_TEST_REPLY
            putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val replyPending = PendingIntent.getBroadcast(
            context,
            301,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val remoteInput = RemoteInput.Builder(KEY_TEST_REPLY)
            .setLabel(context.getString(R.string.reply_hint))
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            context.getString(R.string.action_reply),
            replyPending
        ).addRemoteInput(remoteInput).build()

        val markReadIntent = Intent(context, DropTestActionReceiver::class.java).apply {
            action = DropTestActionReceiver.ACTION_MARK_READ
            putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val markReadPending = PendingIntent.getBroadcast(
            context,
            302,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val markReadAction = NotificationCompat.Action.Builder(
            android.R.drawable.checkbox_on_background,
            "Mark as Read",
            markReadPending
        ).build()

        val archiveIntent = Intent(context, DropTestActionReceiver::class.java).apply {
            action = DropTestActionReceiver.ACTION_ARCHIVE
            putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val archivePending = PendingIntent.getBroadcast(
            context,
            303,
            archiveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val archiveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Archive",
            archivePending
        ).build()

        val sender = Person.Builder().setName("Elena Vance").build()
        val me = Person.Builder().setName("You").build()
        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_TEST_DROP, true)
        }

        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Elena Vance")
            .setContentText("Just landed at Terminal 3 — meeting by the glass atrium in 10?")
            .setStyle(
                NotificationCompat.MessagingStyle(me)
                    .addMessage("Flight touched down early!", System.currentTimeMillis() - 45_000L, sender)
                    .addMessage("Just landed at Terminal 3 — meeting by the glass atrium in 10?", System.currentTimeMillis(), sender)
            )
            .setSubText("Messages · Live Drop")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF7AD7FF.toInt())
            .setAutoCancel(true)
            .setContentIntent(appOpenIntent(context, 101))
            .addAction(replyAction)
            .addAction(markReadAction)
            .addAction(archiveAction)
            .addExtras(extras)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(notifId, notification)
    }

    fun postTestDeliveryNotification(
        context: Context,
        step: Int = 2,
        notifId: Int = DropTestActionReceiver.DEFAULT_DELIVERY_NOTIF_ID
    ) {
        ensureChannels(context)
        val clampedStep = step.coerceIn(0, 3)
        val progressPct = when (clampedStep) {
            0 -> 20
            1 -> 50
            2 -> 80
            else -> 100
        }
        val etaLabels = listOf("14 min", "8 min", "2 min", "Arrived")
        val statusTexts = listOf(
            "Kitchen is packing your Matcha & Croissant",
            "Courier Marcus picked up your order",
            "Marcus is 220m away on Orchard Blvd",
            "Order arrived at Main Lobby reception"
        )
        val milestones = arrayListOf("Ordered", "Picked Up", "En Route", "Arrived")

        val advanceIntent = Intent(context, DropTestActionReceiver::class.java).apply {
            action = DropTestActionReceiver.ACTION_DELIVERY_ADVANCE
            putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, notifId)
            putExtra(DropTestActionReceiver.EXTRA_DELIVERY_STEP, clampedStep)
        }
        val advancePending = PendingIntent.getBroadcast(
            context,
            310,
            advanceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, DropTestActionReceiver::class.java).apply {
            action = DropTestActionReceiver.ACTION_MARK_READ
            putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            311,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_TEST_DROP, true)
            putString(EXTRA_TEST_CATEGORY, "DELIVERY")
            putStringArrayList(EXTRA_DELIVERY_MILESTONES, milestones)
            putInt(EXTRA_DELIVERY_ACTIVE_STEP, clampedStep)
            putString(EXTRA_DELIVERY_ETA, etaLabels[clampedStep])
        }

        val builder = Notification.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Delivery · ETA ${etaLabels[clampedStep]}")
            .setContentText(statusTexts[clampedStep])
            .setSubText("Courier Live")
            .setColor(0xFF4BE39D.toInt())
            .setOnlyAlertOnce(clampedStep != 0)
            .setContentIntent(appOpenIntent(context, 102))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_media_next),
                    "Next Milestone",
                    advancePending
                ).build()
            )
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, android.R.drawable.ic_menu_close_clear_cancel),
                    "Mark Delivered",
                    donePending
                ).build()
            )
            .addExtras(extras)

        if (Build.VERSION.SDK_INT >= 36) {
            val ps = Notification.ProgressStyle()
                .setStyledByProgress(false)
                .setProgress(progressPct)
                .setProgressSegments(
                    listOf(
                        Notification.ProgressStyle.Segment(25).setColor(Color.parseColor("#4BE39D")),
                        Notification.ProgressStyle.Segment(25).setColor(Color.parseColor("#7AD7FF")),
                        Notification.ProgressStyle.Segment(25).setColor(Color.parseColor("#7AF0DE")),
                        Notification.ProgressStyle.Segment(25).setColor(Color.parseColor("#BBEBFF"))
                    )
                )
                .setProgressPoints(
                    listOf(
                        Notification.ProgressStyle.Point(25).setColor(Color.parseColor("#4BE39D")),
                        Notification.ProgressStyle.Point(50).setColor(Color.parseColor("#7AD7FF")),
                        Notification.ProgressStyle.Point(80).setColor(Color.parseColor("#7AF0DE"))
                    )
                )
            builder.setStyle(ps)
        } else {
            builder.setProgress(100, progressPct, false)
        }

        context.getSystemService(NotificationManager::class.java)?.notify(notifId, builder.build())
    }

    fun postTestOtpNotification(context: Context) {
        ensureChannels(context)
        val notifId = DropTestActionReceiver.DEFAULT_OTP_NOTIF_ID
        val markReadIntent = Intent(context, DropTestActionReceiver::class.java).apply {
            action = DropTestActionReceiver.ACTION_MARK_READ
            putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, notifId)
        }
        val markReadPending = PendingIntent.getBroadcast(
            context,
            320,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_TEST_DROP, true)
            putString(EXTRA_TEST_CATEGORY, "OTP")
        }

        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Security Verification")
            .setContentText("Your one-time verification code is 849201. Expires in 5 minutes.")
            .setSubText("2FA Auth")
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFFFFC46B.toInt())
            .setAutoCancel(true)
            .setContentIntent(appOpenIntent(context, 103))
            .addAction(
                NotificationCompat.Action.Builder(
                    android.R.drawable.checkbox_on_background,
                    "Mark as Read",
                    markReadPending
                ).build()
            )
            .addExtras(extras)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(notifId, notification)
    }

    fun postTestMediaNotification(
        context: Context,
        isPlaying: Boolean = true,
        trackIndex: Int = 0,
        notifId: Int = DropTestActionReceiver.DEFAULT_MEDIA_NOTIF_ID
    ) {
        ensureChannels(context)
        val tracks = listOf(
            "Midnight Refraction" to "Solstice · Lossless 24-bit",
            "Liquid Horizon" to "Klangwelt · Spatial Mix",
            "Glass Prism" to "Aether · Live Session"
        )
        val track = tracks[trackIndex % tracks.size]

        fun mediaPending(actionName: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, DropTestActionReceiver::class.java).apply {
                action = actionName
                putExtra(DropTestActionReceiver.EXTRA_NOTIF_ID, notifId)
                putExtra(DropTestActionReceiver.EXTRA_MEDIA_PLAYING, isPlaying)
                putExtra(DropTestActionReceiver.EXTRA_MEDIA_TRACK, trackIndex)
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val extras = Bundle().apply {
            putBoolean(EXTRA_IS_TEST_DROP, true)
            putString(EXTRA_TEST_CATEGORY, "MEDIA")
        }

        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(track.first)
            .setContentText(track.second)
            .setSubText(if (isPlaying) "Playing" else "Paused")
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(0xFF7AF0DE.toInt())
            .setOnlyAlertOnce(true)
            .setContentIntent(appOpenIntent(context, 104))
            .addAction(android.R.drawable.ic_media_previous, "Previous", mediaPending(DropTestActionReceiver.ACTION_MEDIA_PREV, 330))
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                mediaPending(DropTestActionReceiver.ACTION_MEDIA_TOGGLE, 331)
            )
            .addAction(android.R.drawable.ic_media_next, "Next", mediaPending(DropTestActionReceiver.ACTION_MEDIA_NEXT, 332))
            .addExtras(extras)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(notifId, notification)
    }
}
