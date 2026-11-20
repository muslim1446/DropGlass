package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CameraPreset
import com.example.model.GlassOpticsConfig
import com.example.model.GlassStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.opticsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dropglass_optics_prefs"
)

class OpticsPreferencesRepository(private val context: Context) {

    private object Keys {
        val PRESET = stringPreferencesKey("camera_preset")
        val OFFSET_X = floatPreferencesKey("camera_offset_x_dp")
        val TOP_MARGIN = floatPreferencesKey("camera_top_margin_dp")
        val CAMERA_RADIUS = floatPreferencesKey("camera_radius_dp")
        val DROP_GAP = floatPreferencesKey("drop_gap_dp")
        val GLASS_STYLE = stringPreferencesKey("glass_style")
        val REFRACTION = floatPreferencesKey("refraction_intensity")
        val BLUR_RADIUS = floatPreferencesKey("blur_radius_dp")
        val SPECULAR_RIM = floatPreferencesKey("specular_rim_intensity")
        val VISCOSITY_SPEED = floatPreferencesKey("viscosity_speed")
        val AUTO_DISMISS_SEC = floatPreferencesKey("auto_dismiss_seconds")
        val ENABLE_OVERLAY = booleanPreferencesKey("enable_system_overlay")
        val REPLACE_HEADS_UP = booleanPreferencesKey("replace_system_heads_up")
        val SUPPRESS_ONGOING = booleanPreferencesKey("suppress_ongoing")
        val AVOID_BANNERS = booleanPreferencesKey("avoid_system_banners")
        val DYNAMIC_COLOR = booleanPreferencesKey("use_system_dynamic_color")
        val ADAPTIVE_CONTRAST_BLUR = booleanPreferencesKey("adaptive_contrast_blur")
        val BLOCKED_PACKAGES = stringSetPreferencesKey("blocked_packages")
    }

    val configFlow: Flow<GlassOpticsConfig> = context.opticsDataStore.data.map { prefs ->
        val presetName = prefs[Keys.PRESET]
        val styleName = prefs[Keys.GLASS_STYLE]
        GlassOpticsConfig(
            preset = CameraPreset.entries.firstOrNull { it.name == presetName }
                ?: CameraPreset.OPPO_CPH2773,
            cameraOffsetXdp = prefs[Keys.OFFSET_X] ?: 0f,
            cameraTopMarginDp = prefs[Keys.TOP_MARGIN] ?: 12f,
            cameraRadiusDp = prefs[Keys.CAMERA_RADIUS] ?: 11.5f,
            dropGapDp = prefs[Keys.DROP_GAP] ?: 7f,
            glassStyle = GlassStyle.entries.firstOrNull { it.name == styleName }
                ?: GlassStyle.CRYSTAL_CLEAR,
            refractionIntensity = prefs[Keys.REFRACTION] ?: 0.92f,
            blurRadiusDp = prefs[Keys.BLUR_RADIUS] ?: 28f,
            specularRimIntensity = prefs[Keys.SPECULAR_RIM] ?: 0.95f,
            viscositySpeed = prefs[Keys.VISCOSITY_SPEED] ?: 1.0f,
            autoDismissSeconds = prefs[Keys.AUTO_DISMISS_SEC] ?: 5.0f,
            enableSystemOverlay = prefs[Keys.ENABLE_OVERLAY] ?: true,
            replaceSystemHeadsUp = prefs[Keys.REPLACE_HEADS_UP] ?: true,
            suppressOngoing = prefs[Keys.SUPPRESS_ONGOING] ?: true,
            avoidSystemBanners = prefs[Keys.AVOID_BANNERS] ?: true,
            useSystemDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
            adaptiveContrastBlur = prefs[Keys.ADAPTIVE_CONTRAST_BLUR] ?: true,
            blockedPackages = prefs[Keys.BLOCKED_PACKAGES] ?: emptySet()
        )
    }

    suspend fun updateConfig(transform: (GlassOpticsConfig) -> GlassOpticsConfig) {
        context.opticsDataStore.edit { prefs ->
            val current = GlassOpticsConfig(
                preset = CameraPreset.entries.firstOrNull { it.name == prefs[Keys.PRESET] }
                    ?: CameraPreset.OPPO_CPH2773,
                cameraOffsetXdp = prefs[Keys.OFFSET_X] ?: 0f,
                cameraTopMarginDp = prefs[Keys.TOP_MARGIN] ?: 12f,
                cameraRadiusDp = prefs[Keys.CAMERA_RADIUS] ?: 11.5f,
                dropGapDp = prefs[Keys.DROP_GAP] ?: 7f,
                glassStyle = GlassStyle.entries.firstOrNull { it.name == prefs[Keys.GLASS_STYLE] }
                    ?: GlassStyle.CRYSTAL_CLEAR,
                refractionIntensity = prefs[Keys.REFRACTION] ?: 0.92f,
                blurRadiusDp = prefs[Keys.BLUR_RADIUS] ?: 28f,
                specularRimIntensity = prefs[Keys.SPECULAR_RIM] ?: 0.95f,
                viscositySpeed = prefs[Keys.VISCOSITY_SPEED] ?: 1.0f,
                autoDismissSeconds = prefs[Keys.AUTO_DISMISS_SEC] ?: 5.0f,
                enableSystemOverlay = prefs[Keys.ENABLE_OVERLAY] ?: true,
                replaceSystemHeadsUp = prefs[Keys.REPLACE_HEADS_UP] ?: true,
                suppressOngoing = prefs[Keys.SUPPRESS_ONGOING] ?: true,
                avoidSystemBanners = prefs[Keys.AVOID_BANNERS] ?: true,
                useSystemDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: true,
                adaptiveContrastBlur = prefs[Keys.ADAPTIVE_CONTRAST_BLUR] ?: true,
                blockedPackages = prefs[Keys.BLOCKED_PACKAGES] ?: emptySet()
            )
            val updated = transform(current)
            prefs[Keys.PRESET] = updated.preset.name
            prefs[Keys.OFFSET_X] = updated.cameraOffsetXdp
            prefs[Keys.TOP_MARGIN] = updated.cameraTopMarginDp
            prefs[Keys.CAMERA_RADIUS] = updated.cameraRadiusDp
            prefs[Keys.DROP_GAP] = updated.dropGapDp
            prefs[Keys.GLASS_STYLE] = updated.glassStyle.name
            prefs[Keys.REFRACTION] = updated.refractionIntensity
            prefs[Keys.BLUR_RADIUS] = updated.blurRadiusDp
            prefs[Keys.SPECULAR_RIM] = updated.specularRimIntensity
            prefs[Keys.VISCOSITY_SPEED] = updated.viscositySpeed
            prefs[Keys.AUTO_DISMISS_SEC] = updated.autoDismissSeconds
            prefs[Keys.ENABLE_OVERLAY] = updated.enableSystemOverlay
            prefs[Keys.REPLACE_HEADS_UP] = updated.replaceSystemHeadsUp
            prefs[Keys.SUPPRESS_ONGOING] = updated.suppressOngoing
            prefs[Keys.AVOID_BANNERS] = updated.avoidSystemBanners
            prefs[Keys.DYNAMIC_COLOR] = updated.useSystemDynamicColor
            prefs[Keys.ADAPTIVE_CONTRAST_BLUR] = updated.adaptiveContrastBlur
            prefs[Keys.BLOCKED_PACKAGES] = updated.blockedPackages
        }
    }
}
