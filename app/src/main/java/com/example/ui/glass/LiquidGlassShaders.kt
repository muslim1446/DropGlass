package com.example.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

object LiquidGlassShaders {

    /**
     * Optical Liquid Glass SDF Shader:
     * Renders true translucent liquid glass with live color refraction from uAccent,
     * crisp Fresnel edge reflection, chromatic prism dispersion, and smooth metaball
     * surface-tension fusion with the selfie camera lens.
     */
    private const val LIQUID_DROP_GLASS_AGSL = """
        uniform float2 uSize;
        uniform float2 uCamCenter;
        uniform float uCamRadius;
        uniform float4 uDropRect; // center x, center y, half width, half height
        uniform float uDropCorner;
        uniform float uNeckBlend;
        uniform float uSpecular;
        uniform float uRefraction;
        uniform float4 uAccent;
        uniform int uStyleMode; // 0=Crystal Clear, 1=Smoked Obsidian, 2=Iridescent Prism, 3=Live Color Adaptive

        float sdCircle(float2 p, float r) {
            return length(p) - r;
        }

        float sdRoundedBox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + r;
            return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
        }

        float smin(float a, float b, float k) {
            if (k <= 0.001) return min(a, b);
            float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
            return mix(b, a, h) - k * h * (1.0 - h);
        }

        float sceneSdf(float2 p) {
            float dCam = sdCircle(p - uCamCenter, uCamRadius);
            float dDrop = sdRoundedBox(p - uDropRect.xy, uDropRect.zw, uDropCorner);
            return smin(dCam, dDrop, uNeckBlend);
        }

        half4 main(float2 fragCoord) {
            float d = sceneSdf(fragCoord);
            float aa = 1.5;
            float mask = 1.0 - smoothstep(-aa, aa, d);
            if (mask <= 0.001) {
                return half4(0.0);
            }

            float eps = 2.0;
            float2 grad = float2(
                sceneSdf(fragCoord + float2(eps, 0.0)) - sceneSdf(fragCoord - float2(eps, 0.0)),
                sceneSdf(fragCoord + float2(0.0, eps)) - sceneSdf(fragCoord - float2(0.0, eps))
            );
            float2 normal2d = normalize(grad + float2(0.0001));

            // Distance inside glass meniscus
            float depth = clamp(-d / 26.0, 0.0, 1.0);
            float rimBand = exp(-abs(d) * 0.28);
            float innerBevel = smoothstep(0.0, 0.42, depth) * (1.0 - smoothstep(0.32, 0.98, depth));

            // Directional studio key light (top-left) + caustic bounce (bottom-right)
            float2 lightDir = normalize(float2(-0.58, -0.81));
            float fresnel = pow(1.0 - depth, 2.2);
            float topLeftSpec = pow(max(0.0, dot(normal2d, lightDir)), 3.0) * rimBand;
            float bottomBounce = pow(max(0.0, dot(normal2d, -lightDir)), 4.0) * rimBand * 0.55;

            // Subtle vertical refraction gradient across the water lens
            float domeNormY = clamp((fragCoord.y - (uDropRect.y - uDropRect.w)) / max(uDropRect.w * 2.0, 1.0), 0.0, 1.0);
            float domeSheen = pow(1.0 - domeNormY, 2.4) * 0.14 * uSpecular;

            // Base optical body color & genuine translucency per material style
            float3 liveTint = uAccent.rgb;
            float3 darkNeutral = float3(0.055, 0.062, 0.078);
            float3 bodyColor = darkNeutral;
            float bodyAlpha = 0.46;

            if (uStyleMode == 0) {
                // Crystal Clear: true optical transparency with subtle live accent refraction
                bodyColor = mix(darkNeutral, liveTint * 0.42 + float3(0.10, 0.12, 0.15), 0.24 + fresnel * 0.30);
                bodyAlpha = mix(0.42, 0.56, fresnel * 0.75 + depth * 0.25);
            } else if (uStyleMode == 1) {
                // Smoked Obsidian: high-contrast privacy glass with crisp accent edge
                bodyColor = mix(float3(0.028, 0.032, 0.042), liveTint * 0.28, 0.16 + fresnel * 0.20);
                bodyAlpha = mix(0.72, 0.84, fresnel);
            } else if (uStyleMode == 2) {
                // Iridescent Prism: spectral rim dispersion over translucent glass
                float angle = atan(normal2d.y, normal2d.x);
                float3 prism = 0.52 + 0.48 * cos(angle * 2.0 + float3(0.0, 2.1, 4.2));
                bodyColor = mix(darkNeutral + liveTint * 0.14, prism, innerBevel * 0.42 * uRefraction);
                bodyAlpha = mix(0.44, 0.60, fresnel);
            } else {
                // Live Color Adaptive: rich translucent tint matching the active notification color live
                float3 richTint = mix(darkNeutral, liveTint * 0.58, 0.52 + (1.0 - domeNormY) * 0.22);
                bodyColor = mix(richTint, liveTint * 0.75, fresnel * 0.32);
                bodyAlpha = mix(0.48, 0.64, fresnel * 0.8 + depth * 0.2);
            }

            // Chromatic bevel dispersion + live accent specular rim
            float3 dispersion = float3(0.65, 0.90, 1.0) * max(0.0, -normal2d.x) +
                                mix(float3(0.95, 0.78, 1.0), liveTint, 0.5) * max(0.0, normal2d.x);
            float3 rimColor = mix(float3(0.96, 0.98, 1.0), mix(dispersion, liveTint * 1.15, 0.42), 0.45 * uRefraction);

            // Smooth lens integration around selfie camera cutout
            float camInner = 1.0 - smoothstep(uCamRadius - 3.0, uCamRadius - 0.4, length(fragCoord - uCamCenter));

            float3 finalRgb = bodyColor + domeSheen;
            finalRgb += liveTint * (innerBevel * 0.24 * uRefraction);
            finalRgb += rimColor * ((topLeftSpec * 0.98 + bottomBounce * 0.65 + fresnel * 0.16) * uSpecular);
            finalRgb = mix(finalRgb, float3(0.015, 0.018, 0.024), camInner * 0.94);

            float finalAlpha = clamp(bodyAlpha + rimBand * 0.28 * uSpecular, 0.0, 0.94) * mask;
            return half4(finalRgb * finalAlpha, finalAlpha);
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createLiquidDropBrush(
        canvasWidth: Float,
        canvasHeight: Float,
        camCenterX: Float,
        camCenterY: Float,
        camRadius: Float,
        dropCenterX: Float,
        dropCenterY: Float,
        dropHalfW: Float,
        dropHalfH: Float,
        dropCorner: Float,
        neckBlend: Float,
        specular: Float,
        refraction: Float,
        accentColor: Color,
        styleMode: Int
    ): ShaderBrush {
        val shader = RuntimeShader(LIQUID_DROP_GLASS_AGSL).apply {
            setFloatUniform("uSize", canvasWidth.coerceAtLeast(1f), canvasHeight.coerceAtLeast(1f))
            setFloatUniform("uCamCenter", camCenterX, camCenterY)
            setFloatUniform("uCamRadius", camRadius.coerceAtLeast(1f))
            setFloatUniform(
                "uDropRect",
                dropCenterX,
                dropCenterY,
                dropHalfW.coerceAtLeast(1f),
                dropHalfH.coerceAtLeast(1f)
            )
            setFloatUniform("uDropCorner", dropCorner.coerceAtLeast(1f))
            setFloatUniform("uNeckBlend", neckBlend.coerceAtLeast(0f))
            setFloatUniform("uSpecular", specular)
            setFloatUniform("uRefraction", refraction)
            val argb = accentColor.toArgb()
            setFloatUniform(
                "uAccent",
                ((argb shr 16) and 0xFF) / 255f,
                ((argb shr 8) and 0xFF) / 255f,
                (argb and 0xFF) / 255f,
                1f
            )
            setIntUniform("uStyleMode", styleMode)
        }
        return ShaderBrush(shader)
    }
}
