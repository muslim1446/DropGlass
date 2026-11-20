package com.example.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.R

class DropTestActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, DEFAULT_MSG_NOTIF_ID)

        when (intent.action) {
            ACTION_TEST_REPLY -> {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(SystemBridgeHelper.KEY_TEST_REPLY)
                    ?.toString()
                    ?.trim()
                    .orEmpty()
                if (replyText.isNotEmpty()) {
                    val sender = Person.Builder().setName("Elena Vance").build()
                    val me = Person.Builder().setName("You").build()
                    val updated = NotificationCompat.Builder(context, SystemBridgeHelper.TEST_CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Elena Vance")
                        .setStyle(
                            NotificationCompat.MessagingStyle(me)
                                .addMessage("Just landed at Terminal 3 — meeting by the glass atrium in 10?", System.currentTimeMillis() - 5000L, sender)
                                .addMessage(replyText, System.currentTimeMillis(), me)
                        )
                        .setSubText("Replied via DropGlass")
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setOnlyAlertOnce(true)
                        .setAutoCancel(true)
                        .setColor(0xFFAFC8D8.toInt())
                        .addExtras(Bundle().apply {
                            putBoolean(SystemBridgeHelper.EXTRA_IS_TEST_DROP, true)
                        })
                        .build()
                    nm.notify(notifId, updated)
                }
            }

            ACTION_MARK_READ, ACTION_ARCHIVE -> {
                nm.cancel(notifId)
                LiveNotificationStore.clearActiveSystemDrop()
            }

            ACTION_DELIVERY_ADVANCE -> {
                val currentStep = intent.getIntExtra(EXTRA_DELIVERY_STEP, 1)
                // Advance monotonically; delivered is terminal instead of looping back to "Ordered".
                val nextStep = (currentStep + 1).coerceAtMost(3)
                SystemBridgeHelper.postTestDeliveryNotification(context, step = nextStep, notifId = notifId)
            }

            ACTION_MEDIA_TOGGLE -> {
                val isPlaying = intent.getBooleanExtra(EXTRA_MEDIA_PLAYING, true)
                val trackIndex = intent.getIntExtra(EXTRA_MEDIA_TRACK, 0)
                SystemBridgeHelper.postTestMediaNotification(
                    context = context,
                    isPlaying = !isPlaying,
                    trackIndex = trackIndex,
                    notifId = notifId
                )
            }

            ACTION_MEDIA_NEXT -> {
                val isPlaying = intent.getBooleanExtra(EXTRA_MEDIA_PLAYING, true)
                val trackIndex = intent.getIntExtra(EXTRA_MEDIA_TRACK, 0) + 1
                SystemBridgeHelper.postTestMediaNotification(
                    context = context,
                    isPlaying = isPlaying,
                    trackIndex = trackIndex,
                    notifId = notifId
                )
            }

            ACTION_MEDIA_PREV -> {
                val isPlaying = intent.getBooleanExtra(EXTRA_MEDIA_PLAYING, true)
                val trackIndex = (intent.getIntExtra(EXTRA_MEDIA_TRACK, 0) - 1).coerceAtLeast(0)
                SystemBridgeHelper.postTestMediaNotification(
                    context = context,
                    isPlaying = isPlaying,
                    trackIndex = trackIndex,
                    notifId = notifId
                )
            }
        }
    }

    companion object {
        const val ACTION_TEST_REPLY = "com.example.ACTION_TEST_REPLY"
        const val ACTION_MARK_READ = "com.example.ACTION_MARK_READ"
        const val ACTION_ARCHIVE = "com.example.ACTION_ARCHIVE"
        const val ACTION_DELIVERY_ADVANCE = "com.example.ACTION_DELIVERY_ADVANCE"
        const val ACTION_MEDIA_TOGGLE = "com.example.ACTION_MEDIA_TOGGLE"
        const val ACTION_MEDIA_NEXT = "com.example.ACTION_MEDIA_NEXT"
        const val ACTION_MEDIA_PREV = "com.example.ACTION_MEDIA_PREV"

        const val EXTRA_NOTIF_ID = "extra_notif_id"
        const val EXTRA_DELIVERY_STEP = "extra_delivery_step"
        const val EXTRA_MEDIA_PLAYING = "extra_media_playing"
        const val EXTRA_MEDIA_TRACK = "extra_media_track"

        const val DEFAULT_MSG_NOTIF_ID = 2101
        const val DEFAULT_DELIVERY_NOTIF_ID = 2102
        const val DEFAULT_OTP_NOTIF_ID = 2103
        const val DEFAULT_MEDIA_NOTIF_ID = 2104
    }
}
