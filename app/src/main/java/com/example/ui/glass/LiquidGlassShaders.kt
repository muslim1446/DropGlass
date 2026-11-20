package com.example.ui.glass

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

object LiquidGlassShaders {

    /**
     * Real-time Pixel-Bending Liquid Glass Lensing RenderEffect Shader (AGSL / API 33+):
     * Wraps actual underlying Compose backdrop pixels (`uniform shader uContent`) to simulate:
     * 1. True SDF + 3D curved dome surface normals
     * 2. Lensing refraction displacement (pulling & stretching edge pixels inward)
     * 3. Chromatic RGB wavelength dispersion along curved bevels
     * 4. Thickness-coupled Gaussian light scattering + 16% saturation boost
     * 5. Tilt-driven 3D Fresnel specular rim lighting
     * 6. Real-time background luminance evaluation, Light/Dark glass polarity shifting, & ambient bleed
     */
    private const val LIQUID_GLASS_LENSING_AGSL = """
        uniform shader uContent;
        uniform float2 uSize;
        uniform float2 uCamCenter;
        uniform float uCamRadius;
        uniform float4 uDropRect; // center x, center y, half width, half height
        uniform float uDropCorner;
        uniform float uNeckBlend;
        uniform float uSpecular;
        uniform float uRefraction;
        uniform float4 uAccent;
        uniform int uStyleMode; // 0=Clear, 1=Smoked, 2=Prism, 3=Tinted
        uniform float uBlurRadiusPx;
        uniform float uThickness; // 0.18 (seed) .. 1.0 (full glass)
        uniform float2 uTilt; // normalized device tilt (-1..1)
        uniform float uScreenLumaHint;

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

        float3 sampleClamp(float2 coord) {
            float2 c = clamp(coord, float2(1.0), max(uSize - float2(1.0), float2(2.0)));
            return float3(uContent.eval(c).rgb);
        }

        float3 scatterGaussian(float2 uv, float radius) {
            float r1 = radius * 0.48;
            float r2 = radius;
            float3 acc = sampleClamp(uv) * 0.22;
            acc += sampleClamp(uv + float2(r1, 0.0)) * 0.11;
            acc += sampleClamp(uv + float2(-r1, 0.0)) * 0.11;
            acc += sampleClamp(uv + float2(0.0, r1)) * 0.11;
            acc += sampleClamp(uv + float2(0.0, -r1)) * 0.11;
            acc += sampleClamp(uv + float2(r2 * 0.707, r2 * 0.707)) * 0.085;
            acc += sampleClamp(uv + float2(-r2 * 0.707, r2 * 0.707)) * 0.085;
            acc += sampleClamp(uv + float2(r2 * 0.707, -r2 * 0.707)) * 0.085;
            acc += sampleClamp(uv + float2(-r2 * 0.707, -r2 * 0.707)) * 0.085;
            return acc;
        }

        float3 boostSaturation(float3 rgb, float boost) {
            float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
            return clamp(mix(float3(luma), rgb, boost), 0.0, 1.0);
        }

        half4 main(float2 fragCoord) {
            half4 passthrough = uContent.eval(clamp(fragCoord, float2(0.0), max(uSize - float2(1.0), float2(1.0))));
            float d = sceneSdf(fragCoord);

            // Soft optical drop shadow & ambient subsurface bleed just outside the meniscus
            if (d > 1.5) {
                float shadowDist = sceneSdf(fragCoord - float2(uTilt.x * 3.0, 5.0 + uTilt.y * 2.5));
                float shadowFalloff = exp(-max(shadowDist, 0.0) * 0.085) * (0.24 + 0.12 * uThickness);
                float bgL = dot(float3(passthrough.rgb), float3(0.2126, 0.7152, 0.0722));
                float3 bleedShadow = mix(float3(0.02, 0.03, 0.06), uAccent.rgb * 0.25, 0.22);
                float3 shadowed = mix(float3(passthrough.rgb), bleedShadow, shadowFalloff * mix(0.65, 1.0, bgL));
                return half4(shadowed, passthrough.a);
            }

            // Compute 2D gradient & virtual 3D curved dome surface normal
            float eps = 2.0;
            float2 grad = float2(
                sceneSdf(fragCoord + float2(eps, 0.0)) - sceneSdf(fragCoord - float2(eps, 0.0)),
                sceneSdf(fragCoord + float2(0.0, eps)) - sceneSdf(fragCoord - float2(0.0, eps))
            );
            float2 normal2d = normalize(grad + float2(0.0001));

            float bevelSpan = mix(16.0, 34.0, uThickness);
            float depth = clamp(-d / bevelSpan, 0.0, 1.0);
            float curveProfile = pow(1.0 - depth, 1.55);
            float3 N = normalize(float3(normal2d * (curveProfile * 0.92), sqrt(max(0.06, 1.0 - curveProfile * curveProfile * 0.85))));

            // Lensing refraction: pull & stretch pixels near curved edges inward
            float lensForce = (curveProfile * 22.0 + (1.0 - depth) * 6.0) * uRefraction;
            float2 uvBase = fragCoord - normal2d * lensForce;

            // Chromatic dispersion: bend R / G / B wavelengths separately near the curved bevel
            float dispStrength = curveProfile * (uStyleMode == 2 ? 7.5 : 3.6) * uRefraction;
            float2 uvR = uvBase + normal2d * dispStrength;
            float2 uvG = uvBase;
            float2 uvB = uvBase - normal2d * dispStrength;

            // Light scattering scaled by thickness (thin clear bead -> rich thick frosted glass)
            float scatterRadius = clamp(uBlurRadiusPx * (0.38 + 0.62 * uThickness), 3.0, 42.0);
            float3 scatR = scatterGaussian(uvR, scatterRadius);
            float3 scatG = scatterGaussian(uvG, scatterRadius);
            float3 scatB = scatterGaussian(uvB, scatterRadius);
            float3 diffused = float3(scatR.r, scatG.g, scatB.b);

            // Multi-pass style +16% saturation boost so scattered light looks vibrant, not gray
            float3 richDiffused = boostSaturation(diffused, 1.16);

            // Real-time background luminance evaluation + ambient light bleed from perimeter
            float3 localCenter = scatterGaussian(fragCoord, scatterRadius * 1.35);
            float localLuma = mix(dot(localCenter, float3(0.2126, 0.7152, 0.0722)), uScreenLumaHint, 0.25);
            float3 ambientBleed = boostSaturation(
                scatterGaussian(fragCoord + normal2d * 24.0, scatterRadius * 1.4),
                1.25
            );

            bool isLightGlass = (uStyleMode != 1) && (localLuma >= 0.56);

            float3 glassBody = richDiffused;
            if (uStyleMode == 1) {
                // Smoked Obsidian: deep privacy glass with lush subsurface tint bleed
                float3 smokedBase = mix(richDiffused * 0.28, float3(0.035, 0.042, 0.058), 0.72);
                glassBody = mix(smokedBase, ambientBleed * 0.22 + uAccent.rgb * 0.16, 0.24 + curveProfile * 0.22);
            } else if (isLightGlass) {
                // Light Glass Polarity: luminous translucent white crystal + ambient light bleed
                float frostMix = mix(0.42, 0.64, uThickness * 0.7 + (1.0 - curveProfile) * 0.3);
                float3 lightCrystal = mix(richDiffused, float3(0.97, 0.985, 1.0), frostMix);
                float accentBleedAmt = (uStyleMode == 3) ? 0.20 : 0.07;
                glassBody = mix(lightCrystal, mix(ambientBleed, uAccent.rgb, 0.45), accentBleedAmt);
            } else {
                // Dark Glass Polarity: deep refractive glass + vibrant subsurface color bleed
                float darkFrost = mix(0.22, 0.46, uThickness * 0.75);
                float3 darkCrystal = mix(richDiffused * 0.58, float3(0.055, 0.068, 0.095), darkFrost);
                float accentBleedAmt = (uStyleMode == 3) ? 0.34 : 0.16;
                glassBody = mix(darkCrystal, uAccent.rgb * 0.42 + ambientBleed * 0.28, accentBleedAmt * (0.55 + curveProfile * 0.45));
            }

            // Tilt-driven 3D Fresnel specular rim lighting + caustic bounce
            float3 V = normalize(float3(uTilt.x * 0.38, uTilt.y * 0.38, 1.0));
            float3 L = normalize(float3(-0.52 + uTilt.x * 0.85, -0.78 + uTilt.y * 0.75, 0.74));
            float3 H = normalize(L + V);
            float3 bounceL = normalize(float3(0.48 - uTilt.x * 0.5, 0.74 - uTilt.y * 0.5, 0.62));
            float3 H2 = normalize(bounceL + V);

            float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.7);
            float rimBand = exp(-abs(d) * 0.30);
            float keySpec = pow(max(dot(N, H), 0.0), 24.0) * rimBand;
            float causticBounce = pow(max(dot(N, H2), 0.0), 16.0) * rimBand * 0.52;

            // Spectral dispersion iridescence along curved meniscus rim
            float angle = atan(normal2d.y, normal2d.x) + uTilt.x * 1.1;
            float3 prismSpectrum = 0.54 + 0.46 * cos(angle * 2.0 + float3(0.0, 2.094, 4.188));
            float prismStrength = (uStyleMode == 2 ? 0.68 : 0.24) * uRefraction;
            float3 rimTint = mix(float3(0.98, 0.99, 1.0), mix(prismSpectrum, uAccent.rgb * 1.1, 0.38), prismStrength);

            float specIntensity = isLightGlass ? uSpecular * 0.82 : uSpecular * 1.05;
            float3 litGlass = glassBody;
            litGlass += rimTint * ((keySpec * 0.88 + causticBounce * 0.58 + fresnel * 0.18) * specIntensity);
            litGlass += prismSpectrum * (curveProfile * prismStrength * 0.14);

            // Smooth optical fusion with black selfie camera lens
            float camInner = 1.0 - smoothstep(uCamRadius - 2.6, uCamRadius - 0.3, length(fragCoord - uCamCenter));
            litGlass = mix(litGlass, float3(0.015, 0.018, 0.024), camInner * 0.96);

            float aa = 1.4;
            float mask = 1.0 - smoothstep(-aa, aa, d);
            float3 outRgb = mix(float3(passthrough.rgb), clamp(litGlass, 0.0, 1.0), mask);
            return half4(outRgb, passthrough.a);
        }
    """

    /**
     * System Overlay Window Liquid Glass SDF Shader (AGSL / API 33+):
     * Pairs with WindowManager SurfaceFlinger `FLAG_BLUR_BEHIND` when floating over other apps,
     * providing 3D SDF dome normals, tilt-driven Fresnel rim shimmer, chromatic edge dispersion,
     * thickness-scaled scatter, light/dark polarity adaptation, and ambient bleed drop shadow.
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
        uniform int uStyleMode; // 0=Clear, 1=Smoked, 2=Prism, 3=Tinted
        uniform float uBlurDepth; // normalized 0..1 from dynamic blur profile
        uniform float uScreenLuma; // estimated screen luminance 0=dark .. 1=bright white screen
        uniform int uLiveBlurActive; // 1 when hardware cross-window blur is active, 0 fallback
        uniform float uThickness; // 0.18 .. 1.0
        uniform float2 uTilt; // normalized device tilt (-1..1)

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

            // Soft ambient subsurface shadow cast beneath the droplet
            if (d > 1.5) {
                float shadowDist = sceneSdf(fragCoord - float2(uTilt.x * 3.0, 5.5 + uTilt.y * 2.5));
                float shadowAlpha = exp(-max(shadowDist, 0.0) * 0.11) * (0.20 + 0.10 * uThickness) * (1.0 - smoothstep(12.0, 26.0, d));
                if (shadowAlpha <= 0.003) return half4(0.0);
                float3 shadowColor = mix(float3(0.01, 0.015, 0.025), uAccent.rgb * 0.18, 0.25);
                return half4(shadowColor * shadowAlpha, shadowAlpha);
            }

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

            float bevelSpan = mix(16.0, 32.0, uThickness);
            float depth = clamp(-d / bevelSpan, 0.0, 1.0);
            float curveProfile = pow(1.0 - depth, 1.5);
            float3 N = normalize(float3(normal2d * (curveProfile * 0.92), sqrt(max(0.06, 1.0 - curveProfile * curveProfile * 0.85))));

            float3 V = normalize(float3(uTilt.x * 0.38, uTilt.y * 0.38, 1.0));
            float3 L = normalize(float3(-0.52 + uTilt.x * 0.85, -0.78 + uTilt.y * 0.75, 0.74));
            float3 H = normalize(L + V);
            float3 bounceL = normalize(float3(0.48 - uTilt.x * 0.5, 0.74 - uTilt.y * 0.5, 0.62));
            float3 H2 = normalize(bounceL + V);

            float fresnel = pow(1.0 - max(dot(N, V), 0.0), 2.6);
            float rimBand = exp(-abs(d) * 0.28);
            float keySpec = pow(max(dot(N, H), 0.0), 22.0) * rimBand;
            float bottomBounce = pow(max(dot(N, H2), 0.0), 14.0) * rimBand * 0.52;

            float domeNormY = clamp((fragCoord.y - (uDropRect.y - uDropRect.w)) / max(uDropRect.w * 2.0, 1.0), 0.0, 1.0);
            float domeSheen = pow(1.0 - domeNormY, 2.3) * 0.12 * uSpecular;

            float blurUnavailable = uLiveBlurActive == 0 ? 1.0 : 0.0;
            bool isLightGlass = (uStyleMode != 1) && (uScreenLuma >= 0.56);

            // Micro-scattering diffusion grain modulated by thickness & blur depth
            float grain = (fract(sin(dot(fragCoord, float2(12.9898, 78.233))) * 43758.5453) - 0.5) *
                          0.020 * (0.4 + 0.6 * uThickness) * uBlurDepth * (1.0 - fresnel * 0.55);

            float3 liveTint = uAccent.rgb;
            float3 bodyColor;
            float bodyAlpha;

            if (uStyleMode == 1) {
                // Smoked Obsidian: guaranteed dark privacy core
                bodyColor = mix(float3(0.032, 0.038, 0.052), liveTint * 0.30, 0.18 + fresnel * 0.22);
                bodyAlpha = mix(0.76, 0.90, max(fresnel * 0.55, uScreenLuma * 0.45 + blurUnavailable * 0.2));
            } else if (isLightGlass) {
                // Light Glass Polarity: luminous translucent frosted crystal for bright screens
                float3 lightCrystal = mix(float3(0.92, 0.945, 0.98), liveTint * 0.52 + float3(0.55), (uStyleMode == 3 ? 0.32 : 0.14));
                bodyColor = mix(lightCrystal, float3(0.985, 0.99, 1.0), (1.0 - curveProfile) * 0.35);
                float baseLightA = mix(0.62, 0.80, uThickness * 0.45 + (1.0 - depth) * 0.25);
                bodyAlpha = max(baseLightA, 0.72 + blurUnavailable * 0.16);
            } else {
                // Dark Glass Polarity: translucent refractive crystal for dark/colorful screens
                float3 darkNeutral = float3(0.050, 0.060, 0.080);
                float tintFactor = (uStyleMode == 3) ? 0.42 : 0.20;
                bodyColor = mix(darkNeutral, liveTint * 0.56 + float3(0.06, 0.08, 0.11), tintFactor + curveProfile * 0.24);
                bodyAlpha = mix(0.48, 0.68, max(fresnel * 0.7, uThickness * 0.35)) + blurUnavailable * 0.18;
            }

            // Chromatic wavelength dispersion along curved bevel
            float angle = atan(normal2d.y, normal2d.x) + uTilt.x * 1.1;
            float3 prism = 0.54 + 0.46 * cos(angle * 2.0 + float3(0.0, 2.094, 4.188));
            float prismMix = (uStyleMode == 2 ? 0.68 : 0.26) * uRefraction;
            float3 rimColor = mix(float3(0.97, 0.99, 1.0), mix(prism, liveTint * 1.15, 0.40), prismMix);

            float camInner = 1.0 - smoothstep(uCamRadius - 3.0, uCamRadius - 0.4, length(fragCoord - uCamCenter));

            float3 finalRgb = bodyColor + (isLightGlass ? domeSheen * 0.45 : domeSheen) + grain;
            finalRgb += mix(liveTint, prism, prismMix) * (curveProfile * 0.22 * uRefraction);
            finalRgb += rimColor * ((keySpec * 0.94 + bottomBounce * 0.60 + fresnel * 0.18) * uSpecular);
            finalRgb = mix(finalRgb, float3(0.015, 0.018, 0.024), camInner * 0.95);

            float finalAlpha = clamp(bodyAlpha + rimBand * 0.24 * uSpecular, 0.0, 0.94) * mask;
            return half4(finalRgb * finalAlpha, finalAlpha);
        }
    """

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun createLiquidGlassRenderEffect(
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
        styleMode: Int,
        blurRadiusPx: Float,
        thickness: Float,
        tiltX: Float,
        tiltY: Float,
        screenLumaHint: Float
    ): RenderEffect? = runCatching {
        val shader = RuntimeShader(LIQUID_GLASS_LENSING_AGSL).apply {
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
            setFloatUniform("uBlurRadiusPx", blurRadiusPx.coerceIn(2f, 48f))
            setFloatUniform("uThickness", thickness.coerceIn(0.15f, 1f))
            setFloatUniform("uTilt", tiltX.coerceIn(-1f, 1f), tiltY.coerceIn(-1f, 1f))
            setFloatUniform("uScreenLumaHint", screenLumaHint.coerceIn(0f, 1f))
        }
        RenderEffect.createRuntimeShaderEffect(shader, "uContent")
    }.getOrNull()

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
        styleMode: Int,
        blurDepth: Float = 0.5f,
        screenLuma: Float = 0.25f,
        liveBlurActive: Boolean = true,
        thickness: Float = 0.7f,
        tiltX: Float = 0f,
        tiltY: Float = 0f
    ): ShaderBrush? = runCatching {
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
            setFloatUniform("uBlurDepth", blurDepth.coerceIn(0f, 1f))
            setFloatUniform("uScreenLuma", screenLuma.coerceIn(0f, 1f))
            setIntUniform("uLiveBlurActive", if (liveBlurActive) 1 else 0)
            setFloatUniform("uThickness", thickness.coerceIn(0.15f, 1f))
            setFloatUniform("uTilt", tiltX.coerceIn(-1f, 1f), tiltY.coerceIn(-1f, 1f))
        }
        ShaderBrush(shader)
    }.getOrNull()
}
