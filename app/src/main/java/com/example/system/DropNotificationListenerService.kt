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
import com.example.model.GlassOpticsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DropNotificationListenerService : NotificationListenerService(),
    LiveNotificationStore.SystemNotificationBridge {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefsRepository: OpticsPreferencesRepository
    private var overlayController: LiquidOverlayController? = null

    /** Notifications whose native banner we cancelled so onNotificationRemoved does not wipe actions/overlay. */
    private val suppressedSystemKeys = ConcurrentHashMap.newKeySet<String>()

    /** Notifications dismissed by user swipe/close so 1% progress ticks don't immediately pop back up. */
    private val userDismissedOngoingKeys = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        prefsRepository = OpticsPreferencesRepository(applicationContext)
        overlayController = LiquidOverlayController(this)
        LiveNotificationStore.listenerCallback = this
        serviceScope.launch {
            val initial = runCatching { prefsRepository.configFlow.first() }.getOrNull()
            if (initial != null) {
                LiveNotificationStore.updateConfig(initial)
                syncListenerHints(initial)
            }
            prefsRepository.configFlow.collect { config ->
                LiveNotificationStore.updateConfig(config)
                syncListenerHints(config)
                if (!config.enableSystemOverlay) {
                    overlayController?.detach()
                }
            }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        LiveNotificationStore.setListenerConnected(true)
        LiveNotificationStore.listenerCallback = this
        syncListenerHints(LiveNotificationStore.latestConfig.value)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        LiveNotificationStore.setListenerConnected(false)
        overlayController?.detach()
        serviceScope.launch {
            delay(750)
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

        val config = LiveNotificationStore.latestConfig.value
        if (!isSelfTest && sbn.packageName in config.blockedPackages) {
            return
        }

        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val messageLines = NotificationDropParser.extractMessageOrInboxLines(extras)
        if (!isSelfTest && isGroupSummary &&
            (LiveNotificationStore.hasRecentFromPackage(sbn.packageName, 6000L) || messageLines.isEmpty())
        ) {
            // Suppress redundant group summary banner ("3 new messages") if requested, without overriding the active drop
            if (config.enableSystemOverlay && config.replaceSystemHeadsUp && sbn.isClearable && !sbn.isOngoing) {
                sbn.key?.let { key ->
                    suppressedSystemKeys.add(key)
                    runCatching { cancelNotification(key) }
                }
            }
            return
        }

        val usesChronometer = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) &&
            notification.`when` > 0L
        val isChronometerCountDown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        } else {
            false
        }

        var title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val conversationTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        val bodyText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim()
        var message = bigText?.takeIf { it.isNotEmpty() }
            ?: bodyText?.takeIf { it.isNotEmpty() }
            ?: messageLines.lastOrNull()?.text.orEmpty()

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

        val progressInfo = NotificationDropParser.extractProgressInfo(
            packageName = sbn.packageName,
            extras = extras,
            title = title,
            message = message,
            subText = subText,
            infoText = infoText,
            isOngoing = sbn.isOngoing
        )

        if (config.suppressOngoing && sbn.isOngoing &&
            notification.category != Notification.CATEGORY_CALL &&
            notification.category != Notification.CATEGORY_TRANSPORT &&
            progressInfo == null &&
            !usesChronometer &&
            !NotificationDropParser.isDeliveryPackage(sbn.packageName) &&
            !NotificationDropParser.isInstallerOrDownloadPackage(sbn.packageName)
        ) {
            return
        }

        if (title.isEmpty() && message.isEmpty() && messageLines.isEmpty() && progressInfo == null) {
            return
        }

        // If user explicitly dismissed an ongoing notification, only re-surface it on completion
        val key = sbn.key
        if (key != null && key in userDismissedOngoingKeys) {
            val isCompleted = progressInfo != null && !progressInfo.indeterminate &&
                progressInfo.max > 0 && progressInfo.progress >= progressInfo.max
            if (!isCompleted) return
            userDismissedOngoingKeys.remove(key)
        }

        val appName = resolveAppName(sbn.packageName)
        val iconBitmap = resolveAppIcon(sbn)
        val bigPicture = extractBigPicture(extras, mediaController)
        val otpCode = NotificationDropParser.extractOtpCode(
            "$title $message ${messageLines.joinToString(" ") { it.text }}"
        )

        val testCategoryOverride = extras.getString(SystemBridgeHelper.EXTRA_TEST_CATEGORY)
        val isDelivery = NotificationDropParser.isDeliveryNotification(sbn.packageName, extras, title, message)
        val category = when {
            testCategoryOverride == "DELIVERY" -> DropCategory.DELIVERY
            testCategoryOverride == "OTP" -> DropCategory.OTP
            testCategoryOverride == "MEDIA" -> DropCategory.MEDIA
            notification.category == Notification.CATEGORY_CALL -> DropCategory.CALL
            notification.category == Notification.CATEGORY_TRANSPORT || mediaController != null -> DropCategory.MEDIA
            progressInfo != null && isDelivery -> DropCategory.DELIVERY
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
                semantic = NotificationDropParser.classifyActionSemantic(actionTitle, hasRemoteInput)
            )
        }
        val replyDropAction = dropActions.firstOrNull { it.isReply }
        val replyAction = replyDropAction?.let { rawActions.getOrNull(it.index) }
        val remoteInput = replyAction?.remoteInputs?.firstOrNull()

        val accentColor = NotificationDropParser.resolveLiveAccentColor(
            rawColor = notification.color,
            icon = iconBitmap,
            bigPicture = bigPicture,
            category = category
        )

        val isMediaPlaying = if (mediaController != null) {
            playbackPlaying
        } else {
            subText?.equals("Playing", ignoreCase = true) == true ||
                dropActions.any { it.title.equals("Pause", ignoreCase = true) }
        }

        val drop = DropNotification(
            id = key ?: "${sbn.packageName}_${sbn.id}",
            key = key,
            packageName = sbn.packageName,
            appName = appName,
            title = title.ifEmpty { conversationTitle ?: appName },
            text = message.ifEmpty { subText ?: "New notification" },
            subText = subText,
            conversationTitle = conversationTitle,
            timestampMillis = sbn.postTime,
            usesChronometer = usesChronometer,
            chronometerBaseMillis = notification.`when`,
            isChronometerCountDown = isChronometerCountDown,
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
            val shown = overlayController?.showOrUpdate() == true
            if (shown && config.replaceSystemHeadsUp && key != null && sbn.isClearable && !sbn.isOngoing) {
                // Cancel the duplicate stock notification banner immediately so ColorOS/Android doesn't
                // draw a native heads-up banner on top of DropGlass, while keeping LiveSystemActions alive.
                suppressedSystemKeys.add(key)
                runCatching { cancelNotification(key) }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val key = sbn?.key ?: return
        if (suppressedSystemKeys.remove(key)) {
            // Removed by DropGlass to suppress the duplicate stock heads-up banner; preserve active droplet & reply intents.
            return
        }
        userDismissedOngoingKeys.remove(key)
        LiveNotificationStore.clearActiveSystemDrop(key)
        overlayController?.hideIfEmpty()
    }

    override fun dismissFromSystem(key: String) {
        suppressedSystemKeys.remove(key)
        userDismissedOngoingKeys.add(key)
        runCatching { cancelNotification(key) }
        overlayController?.hideIfEmpty()
    }

    override fun snoozeFromSystem(key: String, durationMillis: Long) {
        suppressedSystemKeys.remove(key)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { snoozeNotification(key, durationMillis) }
        } else {
            runCatching { cancelNotification(key) }
        }
        overlayController?.hideIfEmpty()
    }

    override fun onOverlayHide() {
        overlayController?.hideIfEmpty()
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayController?.detach()
        overlayController = null
        if (LiveNotificationStore.listenerCallback === this) {
            LiveNotificationStore.listenerCallback = null
        }
        serviceScope.cancel()
    }

    private fun syncListenerHints(config: GlassOpticsConfig) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            LiveNotificationStore.isListenerConnected.value
        ) {
            val hints = if (config.enableSystemOverlay && config.replaceSystemHeadsUp) {
                HINT_HOST_DISABLE_NOTIFICATION_EFFECTS
            } else {
                0
            }
            runCatching { requestListenerHints(hints) }
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
}

object NotificationDropParser {

    fun extractMessageOrInboxLines(extras: Bundle): List<DropMessageLine> {
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
                    val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        msgBundle.getParcelable("sender_person", Person::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        msgBundle.getParcelable("sender_person") as? Person
                    }
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

    fun extractProgressInfo(
        packageName: String,
        extras: Bundle,
        title: String,
        message: String,
        subText: String? = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        infoText: String? = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString(),
        isOngoing: Boolean = false
    ): DropProgressInfo? {
        val customMilestones = extras
            .getStringArrayList(SystemBridgeHelper.EXTRA_DELIVERY_MILESTONES)
            ?.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
            ?.takeIf { it.isNotEmpty() }
        val customStep = extras.getInt(SystemBridgeHelper.EXTRA_DELIVERY_ACTIVE_STEP, -1)
        val customEta = extras.getString(SystemBridgeHelper.EXTRA_DELIVERY_ETA)

        // Android 16 ProgressStyle segment bundles if present
        val segmentBundles = readBundleListCompat(extras, "android.progressSegments")
        val segmentTotal = segmentBundles?.sumOf { it.getInt("length", 0).coerceAtLeast(0) } ?: 0
        val segmentColors = segmentBundles
            ?.mapNotNull { it.getInt("color", 0).takeIf { c -> c != 0 } }
            .orEmpty()

        var max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        var progress = extras.getInt(Notification.EXTRA_PROGRESS, 0)
        var indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)

        if (max <= 0 && segmentTotal > 0) {
            max = segmentTotal
        }

        val combinedText = listOfNotNull(title, message, subText, infoText).joinToString(" ")
        val isInstaller = isInstallerOrDownloadPackage(packageName)
        val hasCustomStep = customStep >= 0
        val parsedPercentFromText = Regex("""\b(\d{1,3})\s*%""").find(combinedText)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }

        val looksLikeActiveTransfer = isInstaller && isOngoing && Regex(
            """(?i)\b(installing|pending|waiting|verifying|updating|downloading|preparing)\b"""
        ).containsMatchIn(combinedText)

        if (max <= 0 && !indeterminate && !hasCustomStep) {
            if (parsedPercentFromText != null && (isInstaller || isOngoing)) {
                max = 100
                progress = parsedPercentFromText
            } else if (looksLikeActiveTransfer) {
                indeterminate = true
            } else {
                return null
            }
        }

        val effectiveMax = if (max > 0) max else 100
        val effectiveProgress = when {
            indeterminate -> 0
            max > 0 -> progress.coerceIn(0, max)
            hasCustomStep -> when (customStep) {
                0 -> 20
                1 -> 50
                2 -> 80
                else -> 100
            }
            else -> 0
        }

        val isDelivery = isDeliveryNotification(packageName, extras, title, message) ||
            hasCustomStep ||
            customMilestones != null

        val milestones = when {
            !isDelivery -> emptyList()
            customMilestones != null -> customMilestones
            else -> listOf("Ordered", "Picked Up", "En Route", "Delivered")
        }

        val activeIndex = when {
            milestones.isEmpty() -> -1
            hasCustomStep && !indeterminate -> customStep.coerceIn(0, milestones.lastIndex)
            indeterminate -> -1
            else -> inferDeliveryStepFromText(combinedText, milestones.size)
                ?: run {
                    val ratio = effectiveProgress.toFloat() / effectiveMax.toFloat()
                    (ratio * milestones.size).toInt().coerceIn(0, milestones.lastIndex)
                }
        }

        val etaCandidates = listOfNotNull(customEta, infoText, subText, message)
        val etaLabel = etaCandidates
            .mapNotNull { extractEtaSnippet(it) }
            .firstOrNull()

        val detailLabel = listOfNotNull(
            subText?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) },
            infoText?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) },
            extractSizeOrSpeedSnippet(combinedText),
            if (looksLikeActiveTransfer && message.isNotBlank()) message else null
        ).firstOrNull()

        return DropProgressInfo(
            progress = effectiveProgress,
            max = effectiveMax,
            indeterminate = indeterminate,
            milestoneLabels = milestones,
            activeMilestoneIndex = activeIndex,
            segmentColors = segmentColors,
            etaLabel = etaLabel,
            detailLabel = detailLabel,
            showPercentage = !indeterminate && effectiveMax > 0
        )
    }

    private fun inferDeliveryStepFromText(text: String, milestoneCount: Int): Int? {
        if (milestoneCount <= 0) return null
        val last = milestoneCount - 1
        return when {
            Regex("""(?i)\b(delivered|arrived at|dropped off|order complete)\b""").containsMatchIn(text) -> last
            Regex("""(?i)\b(on the way|en route|out for delivery|nearby|approaching|almost there|\d+\s*m away)\b""").containsMatchIn(text) ->
                (last - 1).coerceAtLeast(0)
            Regex("""(?i)\b(picked up|with driver|with courier|rider picked|collected|shipped|dispatched)\b""").containsMatchIn(text) ->
                1.coerceAtMost(last)
            Regex("""(?i)\b(preparing|confirmed|ordered|finding a driver|in the kitchen)\b""").containsMatchIn(text) -> 0
            else -> null
        }
    }

    private fun extractEtaSnippet(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty() || text.length > 64) return null
        val etaRegex = Regex(
            """(?i)(\d{1,3}\s*(?:seconds|second|secs|sec|s|minutes|minute|mins|min|m|hours|hour|hrs|hr|h)(?:\s+left)?\b|\bETA\s*\d{1,2}:\d{2}(?:\s*(?:am|pm))?|\b\d{1,2}:\d{2}\s*(?:am|pm)?\b|\b\d{2,4}\s*m\s*away\b|\bArrived\b|\bDelivered\b)"""
        )
        return etaRegex.find(text)?.value?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun extractSizeOrSpeedSnippet(raw: String): String? {
        val sizeRegex = Regex("""(?i)\d+(?:\.\d+)?\s*(?:KB|MB|GB)\s*/\s*\d+(?:\.\d+)?\s*(?:KB|MB|GB)""")
        return sizeRegex.find(raw)?.value?.trim()
    }

    fun isDeliveryNotification(
        packageName: String,
        extras: Bundle,
        title: String = "",
        message: String = ""
    ): Boolean {
        if (extras.getString(SystemBridgeHelper.EXTRA_TEST_CATEGORY) == "DELIVERY") return true
        if (isInstallerOrDownloadPackage(packageName)) return false
        val hasProgress = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 ||
            extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false) ||
            !readBundleListCompat(extras, "android.progressSegments").isNullOrEmpty()
        if (!hasProgress) return false
        if (isDeliveryPackage(packageName)) return true
        val combined = "$title $message"
        return Regex("""(?i)\b(courier|rider|driver|arriving|picked up|en route|out for delivery)\b""")
            .containsMatchIn(combined)
    }

    fun isDeliveryPackage(packageName: String): Boolean {
        val pkgLower = packageName.lowercase()
        return pkgLower.containsAny(
            "uber", "ubereats", "grab", "gojek", "doordash", "deliveroo", "foodpanda",
            "glovo", "instacart", "postmates", "swiggy", "zomato", "talabat", "careem",
            "delivery", "courier", "shopeefood", "lalamove", "ninjavan", "ninja.van",
            "poslaju", "j&t", "jnt", "fedex", "dhl", "ups", "amazon", "shopee", "lazada",
            "lyft", "bolt", "maxim", "indrive", "airasia"
        )
    }

    fun isInstallerOrDownloadPackage(packageName: String): Boolean {
        val pkgLower = packageName.lowercase()
        return pkgLower == "com.android.vending" ||
            pkgLower.startsWith("com.android.providers.downloads") ||
            pkgLower.contains("market") ||
            pkgLower.contains("packageinstaller") ||
            pkgLower.contains("appstore") ||
            pkgLower == "com.android.chrome" ||
            pkgLower == "org.mozilla.firefox"
    }

    fun resolveLiveAccentColor(
        rawColor: Int,
        icon: Bitmap?,
        bigPicture: Bitmap?,
        category: DropCategory
    ): Int {
        // For rich media / photo notifications, sample live art first; otherwise sample live app icon
        if (category == DropCategory.MEDIA && bigPicture != null) {
            extractVibrantColor(bigPicture)?.let { return it }
        }
        icon?.let { bmp ->
            extractVibrantColor(bmp)?.let { return it }
        }
        if (Color.alpha(rawColor) != 0 && rawColor != 0) {
            return normalizeLiveLuminance(rawColor)
        }
        if (bigPicture != null) {
            extractVibrantColor(bigPicture)?.let { return it }
        }
        return when (category) {
            DropCategory.CALL -> 0xFF7CD49A.toInt()
            DropCategory.MEDIA -> 0xFF7DD3C7.toInt()
            DropCategory.DELIVERY -> 0xFF7CD49A.toInt()
            DropCategory.OTP -> 0xFFE6BE7A.toInt()
            DropCategory.MESSAGE -> 0xFF8BBCE8.toInt()
            DropCategory.SYSTEM -> 0xFFDCE3EC.toInt()
        }
    }

    /** Chroma-weighted sampler pulling the true live brand/artwork hue cleanly into glass space. */
    fun extractVibrantColor(bitmap: Bitmap): Int? {
        val step = (minOf(bitmap.width, bitmap.height) / 18).coerceAtLeast(1)
        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var weightSum = 0.0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                if (Color.alpha(color) >= 180) {
                    val r = Color.red(color)
                    val g = Color.green(color)
                    val b = Color.blue(color)
                    val max = maxOf(r, g, b)
                    val min = minOf(r, g, b)
                    val saturation = if (max == 0) 0f else (max - min) / max.toFloat()
                    if (saturation >= 0.16f && max >= 36 && min <= 238) {
                        val w = (saturation * saturation * (max / 255f)).toDouble().coerceAtLeast(0.05)
                        red += r * w
                        green += g * w
                        blue += b * w
                        weightSum += w
                    }
                }
                x += step
            }
            y += step
        }
        if (weightSum < 0.25) return null
        val avg = Color.rgb(
            (red / weightSum).toInt().coerceIn(0, 255),
            (green / weightSum).toInt().coerceIn(0, 255),
            (blue / weightSum).toInt().coerceIn(0, 255)
        )
        return normalizeLiveLuminance(avg)
    }

    private fun normalizeLiveLuminance(argb: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(argb, hsv)
        return Color.HSVToColor(
            floatArrayOf(
                hsv[0],
                hsv[1].coerceIn(0.36f, 0.86f),
                hsv[2].coerceIn(0.68f, 0.96f)
            )
        )
    }

    fun extractOtpCode(content: String): String? {
        if (content.isBlank()) return null
        val prefixPattern = Regex("""(?i)(?:code|otp|pin|verification|passcode|token)[^0-9]{0,20}(\d{4,8})\b""")
        prefixPattern.find(content)?.groupValues?.getOrNull(1)?.let { return it }
        val suffixPattern = Regex("""(?i)\b(\d{4,8})\b[^.]{0,28}(?:code|otp|verification|expires|valid)""")
        return suffixPattern.find(content)?.groupValues?.getOrNull(1)
    }

    fun classifyActionSemantic(title: String, isReply: Boolean): ActionSemantic {
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

    private fun readBundleListCompat(extras: Bundle, key: String): List<Bundle>? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArrayList(key, Bundle::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArrayList(key)
            }
        }.getOrNull()
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it, ignoreCase = true) }
}
