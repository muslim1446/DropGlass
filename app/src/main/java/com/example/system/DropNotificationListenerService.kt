package com.example.system

import android.app.Notification
import android.app.Person
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.data.OpticsPreferencesRepository
import com.example.model.ActionSemantic
import com.example.model.DropAction
import com.example.model.DropCategory
import com.example.model.DropMessageLine
import com.example.model.DropNotification
import com.example.model.DropProgressInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DropNotificationListenerService : NotificationListenerService(),
    LiveNotificationStore.SystemNotificationBridge {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefsRepository: OpticsPreferencesRepository

    override fun onCreate() {
        super.onCreate()
        prefsRepository = OpticsPreferencesRepository(applicationContext)
        LiveNotificationStore.listenerCallback = this
        serviceScope.launch {
            prefsRepository.configFlow.collect { config ->
                LiveNotificationStore.updateConfig(config)
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        LiveNotificationStore.setListenerConnected(true)
        LiveNotificationStore.listenerCallback = this
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        LiveNotificationStore.setListenerConnected(false)
        // The system can drop the binding after an app update or memory trim.
        // Ask it to reconnect so the overlay keeps receiving alerts.
        serviceScope.launch {
            kotlinx.coroutines.delay(750)
            runCatching {
                requestRebind(
                    ComponentName(
                        this@DropNotificationListenerService,
                        DropNotificationListenerService::class.java
                    )
                )
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: Bundle.EMPTY
        val isSelfTest = extras.getBoolean(SystemBridgeHelper.EXTRA_IS_TEST_DROP, false)
        if (sbn.packageName == packageName && !isSelfTest) {
            return
        }

        serviceScope.launch {
            val config = runCatching { prefsRepository.configFlow.first() }
                .getOrDefault(LiveNotificationStore.latestConfig.value)
            LiveNotificationStore.updateConfig(config)

            if (!isSelfTest && sbn.packageName in config.blockedPackages) {
                return@launch
            }

            val hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
                extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

            if (config.suppressOngoing && sbn.isOngoing &&
                notification.category != Notification.CATEGORY_CALL &&
                notification.category != Notification.CATEGORY_TRANSPORT &&
                !hasProgress &&
                !isDeliveryPackage(sbn.packageName)
            ) {
                return@launch
            }

            val messageLines = extractMessageOrInboxLines(extras)
            var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
            val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
            val bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
            val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
            var message = bigText?.takeIf { it.isNotEmpty() }
                ?: bodyText?.takeIf { it.isNotEmpty() }
                ?: messageLines.lastOrNull()?.text.orEmpty()

            // MediaSession integration for real active playback state + metadata
            val mediaToken = getParcelableCompat(extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
            val mediaController = mediaToken?.let { runCatching { MediaController(applicationContext, it) }.getOrNull() }
            val playbackPlaying = mediaController?.playbackState?.state == PlaybackState.STATE_PLAYING
            if (mediaController != null) {
                val meta = mediaController.metadata
                if (title.isBlank()) {
                    title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
                }
                if (message.isBlank()) {
                    message = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
                }
            }

            if (title.isEmpty() && message.isEmpty() && messageLines.isEmpty()) return@launch

            val appName = resolveAppName(sbn.packageName)
            val iconBitmap = resolveAppIcon(sbn)
            val bigPicture = extractBigPicture(extras, mediaController)
            val progressInfo = extractProgressInfo(sbn.packageName, extras, title, message)
            val otpCode = extractOtpCode("$title $message ${messageLines.joinToString(" ") { it.text }}")

            val testCategoryOverride = extras.getString(SystemBridgeHelper.EXTRA_TEST_CATEGORY)
            val category = when {
                testCategoryOverride == "DELIVERY" -> DropCategory.DELIVERY
                testCategoryOverride == "OTP" -> DropCategory.OTP
                testCategoryOverride == "MEDIA" -> DropCategory.MEDIA
                notification.category == Notification.CATEGORY_CALL -> DropCategory.CALL
                notification.category == Notification.CATEGORY_TRANSPORT || mediaController != null -> DropCategory.MEDIA
                progressInfo != null && isDeliveryNotification(sbn.packageName, extras) -> DropCategory.DELIVERY
                otpCode != null -> DropCategory.OTP
                notification.category == Notification.CATEGORY_MESSAGE ||
                    notification.category == Notification.CATEGORY_SOCIAL ||
                    messageLines.isNotEmpty() -> DropCategory.MESSAGE
                else -> DropCategory.SYSTEM
            }

            val rawActions = notification.actions?.toList().orEmpty()
            val dropActions = rawActions.mapIndexedNotNull { idx, action ->
                val actionTitle = action.title?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapIndexedNotNull null
                val hasRemoteInput = !action.remoteInputs.isNullOrEmpty()
                DropAction(
                    index = idx,
                    title = actionTitle,
                    isReply = hasRemoteInput,
                    semantic = classifyActionSemantic(actionTitle, hasRemoteInput)
                )
            }
            val replyDropAction = dropActions.firstOrNull { it.isReply }
            val replyAction = replyDropAction?.let { rawActions.getOrNull(it.index) }
            val remoteInput = replyAction?.remoteInputs?.firstOrNull()

            val accentColor = resolveAccentColor(notification.color, iconBitmap, category)

            val isMediaPlaying = if (mediaController != null) {
                playbackPlaying
            } else {
                subText?.equals("Playing", ignoreCase = true) == true ||
                    dropActions.any { it.title.equals("Pause", ignoreCase = true) }
            }

            val drop = DropNotification(
                id = sbn.key ?: "${sbn.packageName}_${sbn.id}",
                key = sbn.key,
                packageName = sbn.packageName,
                appName = appName,
                title = title.ifEmpty { conversationTitle ?: appName },
                text = message.ifEmpty { subText ?: "New notification" },
                subText = subText,
                conversationTitle = conversationTitle,
                timestampMillis = sbn.postTime,
                category = category,
                accentColorArgb = accentColor,
                isLiveSystem = true,
                hasReplyAction = replyAction != null && remoteInput != null,
                replyLabel = replyDropAction?.title,
                replyActionIndex = replyDropAction?.index ?: -1,
                actions = dropActions,
                progressInfo = progressInfo,
                messageLines = messageLines,
                detectedOtpCode = otpCode,
                appIconBitmap = iconBitmap,
                largePictureBitmap = bigPicture,
                isMediaPlaying = isMediaPlaying,
                hasMediaSession = mediaController != null
            )

            LiveNotificationStore.publishSystemDrop(
                drop = drop,
                actions = LiveSystemActions(
                    contentIntent = notification.contentIntent,
                    actions = rawActions,
                    replyAction = replyAction,
                    remoteInput = remoteInput,
                    mediaController = mediaController
                )
            )

            if (config.enableSystemOverlay && SystemBridgeHelper.canDrawOverlays(applicationContext)) {
                LiquidOverlayService.showCurrentDrop(applicationContext)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn?.key != null) {
            LiveNotificationStore.clearActiveSystemDrop(sbn.key)
        }
    }

    override fun dismissFromSystem(key: String) {
        runCatching { cancelNotification(key) }
    }

    override fun snoozeFromSystem(key: String, durationMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { snoozeNotification(key, durationMillis) }
        } else {
            runCatching { cancelNotification(key) }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (LiveNotificationStore.listenerCallback === this) {
            LiveNotificationStore.listenerCallback = null
        }
        serviceScope.cancel()
    }

    private fun extractMessageOrInboxLines(extras: Bundle): List<DropMessageLine> {
        val result = mutableListOf<DropMessageLine>()
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Bundle::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? Bundle }?.toTypedArray()
        }
        if (!rawMessages.isNullOrEmpty()) {
            rawMessages.takeLast(3).forEach { msgBundle ->
                val msgText = msgBundle.getCharSequence("text")?.toString()?.trim().orEmpty()
                val senderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val person = getParcelableCompat(msgBundle, "sender_person", Person::class.java)
                    person?.name?.toString()?.trim()
                } else {
                    null
                } ?: msgBundle.getCharSequence("sender")?.toString()?.trim()
                val time = msgBundle.getLong("time", 0L)
                if (msgText.isNotEmpty()) {
                    result.add(DropMessageLine(sender = senderName.orEmpty(), text = msgText, timestampMillis = time))
                }
            }
            return result
        }

        val inboxLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (!inboxLines.isNullOrEmpty()) {
            inboxLines.take(3).forEach { line ->
                val str = line?.toString()?.trim().orEmpty()
                if (str.isNotEmpty()) {
                    result.add(DropMessageLine(sender = "", text = str))
                }
            }
        }
        return result
    }

    private fun extractProgressInfo(
        packageName: String,
        extras: Bundle,
        title: String,
        message: String
    ): DropProgressInfo? {
        val customMilestones = extras
            .getStringArrayList(SystemBridgeHelper.EXTRA_DELIVERY_MILESTONES)
            ?.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            ?.takeIf { it.isNotEmpty() }
        val customStep = extras.getInt(SystemBridgeHelper.EXTRA_DELIVERY_ACTIVE_STEP, -1)
        val customEta = extras.getString(SystemBridgeHelper.EXTRA_DELIVERY_ETA)

        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        val hasStandardProgress = max > 0 || indeterminate
        val hasCustomStep = customStep >= 0
        if (!hasStandardProgress && !hasCustomStep) return null

        // Report the real values the posting app supplied. No synthetic minimum fill.
        val effectiveMax = if (max > 0) max else 100
        val effectiveProgress = when {
            max > 0 -> progress.coerceIn(0, max)
            hasCustomStep -> when (customStep) {
                0 -> 20
                1 -> 50
                2 -> 80
                else -> 100
            }
            else -> 0
        }

        // Milestones are a delivery-only concept. Don't fabricate "Ordered / Picked up"
        // for downloads, media playback, or unrelated progress notifications.
        val isDelivery = isDeliveryNotification(packageName, extras) ||
            hasCustomStep ||
            customMilestones != null
        val milestones = when {
            !isDelivery -> emptyList()
            customMilestones != null -> customMilestones
            else -> listOf("Ordered", "Picked up", "On the way", "Delivered")
        }

        val activeIndex = when {
            milestones.isEmpty() -> -1
            hasCustomStep && !indeterminate ->
                customStep.coerceIn(0, milestones.lastIndex)
            indeterminate -> -1
            effectiveMax > 0 -> {
                val ratio = effectiveProgress.toFloat() / effectiveMax.toFloat()
                (ratio * milestones.size).toInt().coerceIn(0, milestones.lastIndex)
            }
            else -> 0
        }

        // Only surface text that actually reads like an arrival estimate.
        val etaCandidates = listOfNotNull(
            customEta,
            extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
            extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        )
        val etaLabel = etaCandidates
            .firstOrNull { looksLikeEta(it) }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        return DropProgressInfo(
            progress = effectiveProgress,
            max = effectiveMax,
            indeterminate = indeterminate,
            milestoneLabels = milestones,
            activeMilestoneIndex = activeIndex,
            etaLabel = etaLabel
        )
    }

    private fun looksLikeEta(raw: String?): Boolean {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > 48) return false
        val timePattern = Regex(
            """(?i)\b(\d{1,3}\s*(?:sec|secs|second|seconds|min|mins|minute|minutes|hr|hrs|hour|hours)|\d{1,2}:\d{2}\s*(?:am|pm)?|arriving|arrival|due|now|delivered)\b"""
        )
        return timePattern.containsMatchIn(text)
    }

    private fun isDeliveryNotification(packageName: String, extras: Bundle): Boolean {
        if (extras.getString(SystemBridgeHelper.EXTRA_TEST_CATEGORY) == "DELIVERY") return true
        val hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        return hasProgress && isDeliveryPackage(packageName)
    }

    private fun isDeliveryPackage(packageName: String): Boolean {
        val pkgLower = packageName.lowercase()
        return pkgLower.containsAny(
            "uber", "ubereats", "grab", "gojek", "doordash", "deliveroo", "foodpanda",
            "glovo", "instacart", "postmates", "swiggy", "zomato", "talabat", "careem",
            "delivery", "courier", "shopeefood", "lalamove", "ninjavan", "ninja.van",
            "poslaju", "j&t", "jnt", "fedex", "dhl", "ups", "amazon", "shopee", "lazada",
            "lyft", "bolt"
        )
    }

    private fun resolveAccentColor(rawColor: Int, icon: Bitmap?, category: DropCategory): Int {
        if (Color.alpha(rawColor) != 0 && rawColor != 0) {
            return pastelize(rawColor)
        }
        icon?.let { bitmap ->
            dominantPastelColor(bitmap)?.let { return it }
        }
        // Restrained fallbacks from the app palette, not neon category colors.
        return when (category) {
            DropCategory.CALL -> 0xFF9DC5AA.toInt()
            DropCategory.MEDIA -> 0xFF9DC8C2.toInt()
            DropCategory.DELIVERY -> 0xFF9DC5AA.toInt()
            DropCategory.OTP -> 0xFFD8B98B.toInt()
            DropCategory.MESSAGE -> 0xFFAFC8D8.toInt()
            DropCategory.SYSTEM -> 0xFFA9B2BA.toInt()
        }
    }

    /** Desaturate and lift a brand color into the quiet pastel accent range. */
    private fun pastelize(argb: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        val pastel = Color.HSVToColor(
            floatArrayOf(
                hsv[0],
                hsv[1].coerceIn(0.18f, 0.52f),
                hsv[2].coerceIn(0.72f, 0.95f)
            )
        )
        val r = Color.red(pastel)
        val g = Color.green(pastel)
        val b = Color.blue(pastel)
        return Color.rgb(
            (r + (255 - r) * 0.22f).toInt().coerceIn(0, 255),
            (g + (255 - g) * 0.22f).toInt().coerceIn(0, 255),
            (b + (255 - b) * 0.22f).toInt().coerceIn(0, 255)
        )
    }

    /** Pull a representative saturated hue from the app icon for a subtle brand tint. */
    private fun dominantPastelColor(bitmap: Bitmap): Int? {
        val step = (minOf(bitmap.width, bitmap.height) / 20).coerceAtLeast(1)
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var samples = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) >= 200) {
                    val r = Color.red(color)
                    val g = Color.green(color)
                    val b = Color.blue(color)
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val saturation = if (max == 0) 0f else (max - min) / max.toFloat()
                    // Ignore white glyphs, shadows, and fully neutral icon areas.
                    if (saturation >= 0.2f && max >= 40 && min <= 235) {
                        red += r
                        green += g
                        blue += b
                        samples++
                    }
                }
                x += step
            }
            y += step
        }
        if (samples < 4) return null
        return pastelize(
            Color.rgb(
                (red / samples).toInt().coerceIn(0, 255),
                (green / samples).toInt().coerceIn(0, 255),
                (blue / samples).toInt().coerceIn(0, 255)
            )
        )
    }

    private fun extractOtpCode(content: String): String? {
        if (content.isBlank()) return null
        val prefixPattern = Regex("""(?i)(?:code|otp|pin|verification|passcode|token)[^0-9]{0,20}(\d{4,8})\b""")
        prefixPattern.find(content)?.groupValues?.getOrNull(1)?.let { return it }
        val suffixPattern = Regex("""(?i)\b(\d{4,8})\b[^.]{0,28}(?:code|otp|verification|expires|valid)""")
        return suffixPattern.find(content)?.groupValues?.getOrNull(1)
    }

    private fun classifyActionSemantic(title: String, isReply: Boolean): ActionSemantic {
        if (isReply) return ActionSemantic.REPLY
        val lower = title.lowercase()
        return when {
            lower.contains("read") || lower.contains("seen") -> ActionSemantic.MARK_READ
            lower.contains("archive") -> ActionSemantic.ARCHIVE
            lower.contains("delete") || lower.contains("trash") -> ActionSemantic.DELETE
            lower.contains("answer") || lower.contains("accept") -> ActionSemantic.ANSWER
            lower.contains("decline") || lower.contains("reject") || lower.contains("hang up") -> ActionSemantic.DECLINE
            lower.contains("prev") -> ActionSemantic.MEDIA_PREV
            lower.contains("pause") || lower.contains("play") -> ActionSemantic.MEDIA_PLAY_PAUSE
            lower.contains("next") && !lower.contains("milestone") -> ActionSemantic.MEDIA_NEXT
            lower.contains("track") || lower.contains("map") || lower.contains("milestone") -> ActionSemantic.TRACK_ORDER
            lower.contains("copy") -> ActionSemantic.COPY_OTP
            else -> ActionSemantic.GENERIC
        }
    }

    private fun extractBigPicture(extras: Bundle, mediaController: MediaController?): Bitmap? {
        val pic = getParcelableCompat(extras, Notification.EXTRA_PICTURE, Bitmap::class.java)
            ?: mediaController?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: mediaController?.metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: return null
        return scaleDownBitmap(pic, 240)
    }

    private fun scaleDownBitmap(source: Bitmap, maxDim: Int): Bitmap {
        if (source.width <= maxDim && source.height <= maxDim) return source
        val scale = maxDim.toFloat() / maxOf(source.width, source.height).toFloat()
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
    }

    private fun resolveAppName(pkg: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrElse { pkg.substringAfterLast('.') }
    }

    private fun resolveAppIcon(sbn: StatusBarNotification): Bitmap? {
        return runCatching {
            val notification = sbn.notification
            val drawable: Drawable? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                notification.getLargeIcon()?.loadDrawable(applicationContext)
                    ?: packageManager.getApplicationIcon(sbn.packageName)
            } else {
                packageManager.getApplicationIcon(sbn.packageName)
            }
            drawable?.toBitmap(96)
        }.getOrNull()
    }

    private fun Drawable.toBitmap(sizePx: Int): Bitmap {
        if (this is BitmapDrawable && bitmap != null) {
            return Bitmap.createScaledBitmap(bitmap, sizePx, sizePx, true)
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bmp
    }

    private fun <T> getParcelableCompat(bundle: Bundle, key: String, clazz: Class<T>): T? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getParcelable(key, clazz)
        } else {
            @Suppress("DEPRECATION")
            bundle.getParcelable(key) as? T
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it, ignoreCase = true) }
}
