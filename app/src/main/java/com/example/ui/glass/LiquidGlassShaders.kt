package com.example.ui.glass

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

object LiquidGlassShaders {

    /**
     * Flowing dark obsidian optical silk backdrop with subtle caustic ribbons,
     * designed to showcase translucent Liquid Glass refraction in the Stage.
     */
    private const val SILK_BACKDROP_AGSL = """
        uniform float2 uResolution;
        uniform float uTime;
        uniform float4 uAccent;

        float hash(float2 p) {
            return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
        }

        half4 main(float2 fragCoord) {
            float2 uv = fragCoord / max(uResolution, float2(1.0));
            float t = uTime * 0.35;

            // Flowing optical caustic waves
            float wave1 = sin(uv.x * 3.4 + t + sin(uv.y * 2.7 - t * 0.7) * 1.15);
            float wave2 = cos(uv.y * 4.2 - t * 0.8 + cos(uv.x * 3.1 + t * 0.5) * 1.2);
            float ribbon = smoothstep(-0.35, 0.95, wave1 * 0.55 + wave2 * 0.45);

            // Deep obsidian void base
            float3 baseCol = float3(0.016, 0.024, 0.042);
            float3 deepSapphire = float3(0.035, 0.085, 0.175);
            float3 iceRibbon = mix(float3(0.18, 0.48, 0.78), uAccent.rgb, 0.48);

            // Upper selfie-lens optical halo
            float2 camUv = float2(0.5, 0.07);
            float camDist = length((uv - camUv) * float2(1.0, uResolution.y / max(uResolution.x, 1.0)));
            float camGlow = exp(-camDist * 5.2) * 0.24;

            float3 col = mix(baseCol, deepSapphire, ribbon * 0.65);
            float specularSilk = pow(max(0.0, ribbon), 3.6) * 0.32;
            col += iceRibbon * (specularSilk + camGlow);

            // Subtle micro-dither to prevent 8-bit gradient banding on AMOLED
            float grain = (hash(fragCoord) - 0.5) * 0.008;
            col += grain;

            return half4(clamp(col, 0.0, 1.0), 1.0);
        }
    """

    /**
     * Optical Liquid Glass SDF Shader:
     * Computes smooth-union metaball fusion between the selfie camera seed circle
     * and the expanding rounded droplet/pill, plus Fresnel specular rim lighting
     * and prismatic bevel dispersion.
     */
    private const val LIQUID_DROP_GLASS_AGSL = """
        uniform float2 uSize;
        uniform float2 uCamCenter;
        uniform float uCamRadius;
        uniform float4 uDropRect; // x, y, halfW, halfH (center x, center y, half width, half height)
        uniform float uDropCorner;
        uniform float uNeckBlend;
        uniform float uSpecular;
        uniform float uRefraction;
        uniform float4 uAccent;
        uniform int uStyleMode; // 0=Crystal, 1=Obsidian, 2=Prism, 3=Adaptive

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
            float aa = 1.6;
            float mask = 1.0 - smoothstep(-aa, aa, d);
            if (mask <= 0.001) {
                return half4(0.0);
            }

            // Finite-difference surface normal for optical lens curvature
            float eps = 2.0;
            float2 grad = float2(
                sceneSdf(fragCoord + float2(eps, 0.0)) - sceneSdf(fragCoord - float2(eps, 0.0)),
                sceneSdf(fragCoord + float2(0.0, eps)) - sceneSdf(fragCoord - float2(0.0, eps))
            );
            float2 normal2d = normalize(grad + float2(0.0001));

            // Distance inward from liquid meniscus boundary
            float depth = clamp(-d / 28.0, 0.0, 1.0);
            float rimBand = exp(-abs(d) * 0.24);
            float innerBevel = smoothstep(0.0, 0.45, depth) * (1.0 - smoothstep(0.35, 1.0, depth));

            // Directional studio light from top-left (135 deg) + caustic bounce bottom-right
            float2 lightDir = normalize(float2(-0.62, -0.78));
            float fresnel = pow(1.0 - depth, 2.3);
            float topLeftSpec = pow(max(0.0, dot(normal2d, lightDir)), 3.2) * rimBand;
            float bottomBounce = pow(max(0.0, dot(normal2d, -lightDir)), 4.5) * rimBand * 0.55;

            // Glass body tint per style mode
            float3 bodyColor = float3(0.06, 0.09, 0.14);
            float bodyAlpha = 0.72;
            if (uStyleMode == 0) { // Crystal Clear
                bodyColor = mix(float3(0.08, 0.13, 0.20), float3(0.22, 0.34, 0.48), fresnel * 0.45);
                bodyAlpha = mix(0.62, 0.80, fresnel);
            } else if (uStyleMode == 1) { // Obsidian Smoke
                bodyColor = mix(float3(0.025, 0.035, 0.055), float3(0.09, 0.13, 0.20), fresnel * 0.4);
                bodyAlpha = mix(0.82, 0.92, fresnel);
            } else if (uStyleMode == 2) { // Iridescent Prism
                float angle = atan(normal2d.y, normal2d.x);
                float3 prism = 0.5 + 0.5 * cos(angle * 2.0 + float3(0.0, 2.1, 4.2));
                bodyColor = mix(float3(0.06, 0.09, 0.15), prism, innerBevel * 0.45 * uRefraction);
                bodyAlpha = mix(0.68, 0.84, fresnel);
            } else { // Adaptive
                bodyColor = mix(float3(0.05, 0.08, 0.12), uAccent.rgb * 0.42, 0.38 + fresnel * 0.25);
                bodyAlpha = mix(0.70, 0.86, fresnel);
            }

            // Internal convex dome sheen (upper hemisphere liquid reflection)
            float domeNormY = clamp((fragCoord.y - (uDropRect.y - uDropRect.w)) / max(uDropRect.w * 2.0, 1.0), 0.0, 1.0);
            float domeSheen = pow(1.0 - domeNormY, 2.6) * 0.16 * uSpecular;

            // Chromatic edge dispersion along curved glass wall
            float3 dispersion = float3(0.45, 0.84, 1.0) * max(0.0, -normal2d.x) +
                                float3(0.85, 0.62, 1.0) * max(0.0, normal2d.x);
            float3 rimColor = mix(float3(0.92, 0.97, 1.0), dispersion, 0.32 * uRefraction);

            // Cutout lens interior stays pure camera-dark so the punch hole integrates cleanly
            float camInner = 1.0 - smoothstep(uCamRadius - 3.0, uCamRadius - 0.5, length(fragCoord - uCamCenter));

            float3 finalRgb = bodyColor + domeSheen;
            finalRgb += uAccent.rgb * (innerBevel * 0.22 * uRefraction);
            finalRgb += rimColor * ((topLeftSpec * 0.95 + bottomBounce * 0.6 + fresnel * 0.18) * uSpecular);
            finalRgb = mix(finalRgb, float3(0.015, 0.02, 0.03), camInner * 0.92);

            float finalAlpha = clamp(bodyAlpha + rimBand * 0.24 * uSpecular, 0.0, 0.96) * mask;
            return half4(finalRgb * finalAlpha, finalAlpha);
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createSilkBackdropBrush(
        widthPx: Float,
        heightPx: Float,
        timeSeconds: Float,
        accentColor: Color
    ): ShaderBrush {
        val shader = RuntimeShader(SILK_BACKDROP_AGSL).apply {
            setFloatUniform("uResolution", widthPx.coerceAtLeast(1f), heightPx.coerceAtLeast(1f))
            setFloatUniform("uTime", timeSeconds)
            val argb = accentColor.toArgb()
            setFloatUniform(
                "uAccent",
                ((argb shr 16) and 0xFF) / 255f,
                ((argb shr 8) and 0xFF) / 255f,
                (argb and 0xFF) / 255f,
                1f
            )
        }
        return ShaderBrush(shader)
    }

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
