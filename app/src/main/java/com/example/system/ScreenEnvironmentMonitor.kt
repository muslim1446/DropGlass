package com.example.system

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.WindowManager
import androidx.compose.runtime.Immutable
import com.example.model.DropStage
import com.example.model.GlassOpticsConfig
import com.example.model.GlassStyle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.function.Consumer
import kotlin.math.abs

@Immutable
data class ScreenEnvironmentState(
    val isLightUiMode: Boolean = false,
    val isLightWallpaper: Boolean = false,
    val ambientLux: Float = 150f,
    val isCrossWindowBlurActive: Boolean = true,
    val isPowerSaveMode: Boolean = false
) {
    /** Estimated screen luminance (0 = dark, 1 = bright light screen) used to protect text legibility. */
    val estimatedScreenLuma: Float
        get() {
            val base = when {
                isLightUiMode && isLightWallpaper -> 0.90f
                isLightUiMode -> 0.82f
                isLightWallpaper -> 0.48f
                else -> 0.16f
            }
            val daylightBoost = if (ambientLux > 1800f) 0.14f else if (ambientLux > 600f) 0.06f else 0f
            return (base + daylightBoost).coerceIn(0f, 1f)
        }
}

@Immutable
data class DynamicBlurProfile(
    val effectiveBlurDp: Float,
    val blurDepthNormalized: Float,
    val effectiveScreenLuma: Float,
    val useCrossWindowBlur: Boolean,
    val isLightGlassMode: Boolean = false,
    val thicknessFactor: Float = 0.7f
)

object ScreenEnvironmentCalculator {

    fun thicknessForStage(stage: DropStage): Float = when (stage) {
        DropStage.CAMERA_SEED -> 0.18f
        DropStage.SMALL_DROP -> 0.34f
        DropStage.MEDIUM_PILL -> 0.62f
        DropStage.BIGGEST_GLASS -> 1.0f
    }

    fun isLightGlassPolarity(style: GlassStyle, screenLuma: Float): Boolean = when (style) {
        GlassStyle.OBSIDIAN_SMOKE -> false
        GlassStyle.CRYSTAL_CLEAR,
        GlassStyle.IRIDESCENT_PRISM,
        GlassStyle.COLOROS_ADAPTIVE -> screenLuma >= 0.56f
    }

    fun computeProfile(
        config: GlassOpticsConfig,
        env: ScreenEnvironmentState,
        screenLuminanceOverride: Float? = null,
        componentThickness: Float = 1.0f
    ): DynamicBlurProfile {
        val luma = (screenLuminanceOverride ?: env.estimatedScreenLuma).coerceIn(0f, 1f)
        val baseBlur = config.blurRadiusDp.coerceIn(4f, 56f)
        val clampedThickness = componentThickness.coerceIn(0.15f, 1.0f)

        // Selective light scattering: compact beads stay optically tight & refractive;
        // expanded popovers simulate thicker, denser glass scattering.
        val thicknessScale = 0.48f + 0.52f * clampedThickness

        val adaptiveBlur = if (config.adaptiveContrastBlur) {
            when {
                luma >= 0.65f -> (baseBlur * 1.28f * thicknessScale).coerceIn(10f, 56f)
                luma >= 0.35f -> (baseBlur * 1.08f * thicknessScale).coerceIn(8f, 52f)
                else -> (baseBlur * 0.85f * thicknessScale).coerceIn(6f, 48f)
            }
        } else {
            (baseBlur * thicknessScale).coerceIn(4f, 56f)
        }

        val effectiveLuma = if (config.adaptiveContrastBlur) {
            luma
        } else {
            luma * 0.45f
        }

        val lightGlass = isLightGlassPolarity(config.glassStyle, effectiveLuma)

        return DynamicBlurProfile(
            effectiveBlurDp = adaptiveBlur,
            blurDepthNormalized = (adaptiveBlur / 52f).coerceIn(0f, 1f),
            effectiveScreenLuma = effectiveLuma,
            useCrossWindowBlur = env.isCrossWindowBlurActive && !env.isPowerSaveMode,
            isLightGlassMode = lightGlass,
            thicknessFactor = clampedThickness
        )
    }
}

object ScreenEnvironmentMonitor {

    fun snapshot(
        context: Context,
        windowManager: WindowManager? = context.getSystemService(WindowManager::class.java),
        ambientLux: Float = 150f
    ): ScreenEnvironmentState {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isLightUi = nightModeFlags == Configuration.UI_MODE_NIGHT_NO

        val isLightWall = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching {
                val wm = WallpaperManager.getInstance(context)
                val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && colors != null) {
                    (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
                } else {
                    false
                }
            }.getOrDefault(false)
        } else {
            false
        }

        val powerSave = runCatching {
            context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        }.getOrDefault(false)

        val crossBlur = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager != null) {
            runCatching { windowManager.isCrossWindowBlurEnabled }.getOrDefault(false)
        } else {
            false
        }

        return ScreenEnvironmentState(
            isLightUiMode = isLightUi,
            isLightWallpaper = isLightWall,
            ambientLux = ambientLux,
            isCrossWindowBlurActive = crossBlur && !powerSave,
            isPowerSaveMode = powerSave
        )
    }

    fun observe(
        context: Context,
        windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    ): Flow<ScreenEnvironmentState> = callbackFlow {
        var latestLux = 150f

        fun emitCurrent() {
            trySend(snapshot(context, windowManager, latestLux))
        }

        emitCurrent()

        val mainHandler = Handler(Looper.getMainLooper())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                emitCurrent()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        }

        var wallListener: WallpaperManager.OnColorsChangedListener? = null
        val wallMgr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            runCatching { WallpaperManager.getInstance(context) }.getOrNull()
        } else {
            null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wallMgr != null) {
            wallListener = WallpaperManager.OnColorsChangedListener { _, _ ->
                emitCurrent()
            }
            runCatching { wallMgr.addOnColorsChangedListener(wallListener, mainHandler) }
        }

        var blurConsumer: Consumer<Boolean>? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager != null) {
            blurConsumer = Consumer { _ -> emitCurrent() }
            runCatching { windowManager.addCrossWindowBlurEnabledListener(blurConsumer) }
        }

        val sensorMgr = context.getSystemService(SensorManager::class.java)
        val lightSensor = sensorMgr?.getDefaultSensor(Sensor.TYPE_LIGHT)
        var sensorListener: SensorEventListener? = null
        if (sensorMgr != null && lightSensor != null) {
            sensorListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val lux = event?.values?.firstOrNull() ?: return
                    if (abs(lux - latestLux) > 250f) {
                        latestLux = lux
                        emitCurrent()
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            runCatching {
                sensorMgr.registerListener(sensorListener, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && wallMgr != null && wallListener != null) {
                runCatching { wallMgr.removeOnColorsChangedListener(wallListener) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager != null && blurConsumer != null) {
                runCatching { windowManager.removeCrossWindowBlurEnabledListener(blurConsumer) }
            }
            if (sensorMgr != null && sensorListener != null) {
                runCatching { sensorMgr.unregisterListener(sensorListener) }
            }
        }
    }

    /**
     * Streams smooth normalized device tilt (x, y in -1..1) from the gravity/accelerometer sensor
     * so Liquid Glass Fresnel specular highlights dynamically shift and shimmer with physical tilt.
     */
    fun observeTilt(context: Context): Flow<Pair<Float, Float>> = callbackFlow {
        trySend(0f to 0f)
        val sensorMgr = context.getSystemService(SensorManager::class.java)
        val tiltSensor = sensorMgr?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorMgr?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        var filteredX = 0f
        var filteredY = 0f
        var lastEmitMs = 0L
        var listener: SensorEventListener? = null

        if (sensorMgr != null && tiltSensor != null) {
            listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val values = event?.values ?: return
                    if (values.size < 2) return
                    val normX = (-values[0] / 9.81f).coerceIn(-1f, 1f)
                    val normY = ((values[1] - 4.5f) / 9.81f).coerceIn(-1f, 1f)
                    filteredX += (normX - filteredX) * 0.16f
                    filteredY += (normY - filteredY) * 0.16f
                    val now = android.os.SystemClock.uptimeMillis()
                    if (now - lastEmitMs >= 32L) {
                        lastEmitMs = now
                        trySend(filteredX to filteredY)
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            runCatching {
                sensorMgr.registerListener(listener, tiltSensor, SensorManager.SENSOR_DELAY_UI)
            }
        }

        awaitClose {
            if (sensorMgr != null && listener != null) {
                runCatching { sensorMgr.unregisterListener(listener) }
            }
        }
    }
}
