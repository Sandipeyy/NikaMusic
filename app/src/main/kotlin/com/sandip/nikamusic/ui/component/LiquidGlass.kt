// NikaMusic's liquid glass for the floating bars, a built-in glass for the pipeline the bars already use for a look's
// glass (ModuleChromeGlass): what is behind the bar is blurred a little, then this shader bends it at the rim like thick
// glass, splits it into faint colour fringes, lights it, and puts a lens over the lit tab that follows the pill.
//   Android 13+  : this AGSL shader
//   Android 12/L : the frosted glass, lighter and more see-through (FrostedGlass.kt)
//   older        : the frosted fallback, unchanged
// "Disable blur" in the settings always falls back to the frosted look; a look module's own glass still wins.

package com.sandip.nikamusic.ui.component

import android.os.Build
import androidx.annotation.RequiresApi
import com.sandip.nikamusic.modules.skin.ActiveGlass

internal object LiquidGlass {
    private const val BlurDp = 5f
    // the shader bends the picture by up to ~16dp, so it reads that much of the app past the bar
    private const val MarginDp = 26f
    private const val Tint = 0.42f

    private var cached: ActiveGlass? = null

    /** The one shared glass: every bar builds its own effect from it. Only call on Android 13+. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun get(): ActiveGlass = cached ?: ActiveGlass(
        moduleId = "nika.liquid",
        shader = createAgslShader(Agsl),
        blurDp = BlurDp,
        marginDp = MarginDp,
        uniforms = mapOf("tint" to Tint),
        drawsPill = true,
    ).also { cached = it }
}

// AGSL. Coordinates are the bar's layer pixels; the bar is the rounded box inset by `margin`, the rest is the bleed the glass reads.
// pill = the lit tab's rectangle (left, top, right, bottom) in the same pixels, all zero on a bar without one.
private const val Agsl = """
uniform shader content;
uniform float2 size;
uniform float margin;
uniform float radius;
uniform float density;
uniform float quality;
uniform float kind;
uniform float tint;
uniform float4 pill;
uniform float pillShown;
uniform float pillLift;
layout(color) uniform half4 chrome;
layout(color) uniform half4 accent;

float sdBox(float2 p, float2 b, float r) {
    float2 q = abs(p) - b + float2(r);
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

float2 sdGrad(float2 p, float2 b, float r) {
    float e = 1.0;
    float2 g = float2(
        sdBox(p + float2(e, 0.0), b, r) - sdBox(p - float2(e, 0.0), b, r),
        sdBox(p + float2(0.0, e), b, r) - sdBox(p - float2(0.0, e), b, r));
    return normalize(g + float2(0.00001));
}

// glass bends each colour a touch differently; on lower quality one plain read
half3 readGlass(float2 pos, float2 dir, float amount) {
    if (quality > 0.5) {
        half r = content.eval(pos + dir * amount).r;
        half g = content.eval(pos).g;
        half b = content.eval(pos - dir * amount).b;
        return half3(r, g, b);
    }
    return content.eval(pos).rgb;
}

half4 main(float2 fc) {
    float2 c = size * 0.5;
    float2 b = size * 0.5 - float2(margin);
    float2 p = fc - c;
    float d = sdBox(p, b, radius);
    if (d > 1.0) return half4(0.0);

    float inside = -d;
    float bevel = 15.0 * density;
    float t = clamp(1.0 - inside / bevel, 0.0, 1.0);
    float2 n = sdGrad(p, b, radius);

    // thick glass: toward the rim the picture is pulled in from further inside, so edges look rounded and magnified
    float2 pos = fc - n * (t * t * 16.0 * density);
    pos = c + (pos - c) * float2(0.99, 0.95);
    half3 col = readGlass(pos, n, 1.6 * density * t);
    col = mix(col, chrome.rgb, half(tint + kind * 0.0));

    // the lens over the lit tab: follows the pill, swells a little when a finger lifts it
    float2 pc = float2((pill.x + pill.z) * 0.5, (pill.y + pill.w) * 0.5);
    float grow = pillLift * 3.0 * density;
    float2 ph = float2(max((pill.z - pill.x) * 0.5, 1.0), max((pill.w - pill.y) * 0.5, 1.0)) + float2(grow);
    if (pillShown > 0.001) {
        float2 lp = fc - pc;
        float dl = sdBox(lp, ph, ph.y);
        if (dl < 2.0) {
            float lin = -dl;
            float lt = clamp(1.0 - lin / (11.0 * density), 0.0, 1.0);
            float2 ln = sdGrad(lp, ph, ph.y);
            float zoom = 0.88 - 0.06 * pillLift;
            float2 lpos = pc + lp * zoom - ln * (lt * lt * 12.0 * density);
            half3 lcol = readGlass(lpos, ln, 2.2 * density * lt);
            lcol = mix(lcol, chrome.rgb, half(tint * 0.5));
            lcol += half3(0.10 + 0.06 * pillLift);
            float lrim = 1.0 - smoothstep(0.0, 2.5 * density, lin);
            float lface = clamp(dot(ln, normalize(float2(-0.6, -0.8))), 0.0, 1.0);
            lcol += half3(lrim * (0.25 + 0.75 * pow(lface, 3.0)) * 0.55);
            float mask = clamp(0.5 - dl, 0.0, 1.0) * pillShown;
            col = mix(col, lcol, half(mask));
        }
    }

    // light on the bar: a bright thin rim, strongest where it faces the top left, a soft inner glow, and a sheen that drifts with the lens
    float rim = 1.0 - smoothstep(0.0, 2.2 * density, inside);
    float face = clamp(dot(n, normalize(float2(-0.5, -0.85))), 0.0, 1.0);
    float glow = (1.0 - smoothstep(0.0, 9.0 * density, inside)) * 0.05;
    float drift = (p.x - (pc.x - c.x)) / (size.x * 0.28 + 1.0) + p.y / (size.x * 0.5 + 1.0);
    float sheen = exp(-drift * drift) * 0.05 * (0.4 + 0.6 * pillShown);
    col += half3(rim * (0.12 + 0.55 * pow(face, 4.0)) + glow + sheen);

    float breath = exp(-pow((p.x - (pc.x - c.x)) / (size.x * 0.18 + 1.0), 2.0)) * 0.06 * pillShown;
    col = mix(col, accent.rgb, half(breath));

    float cover = clamp(0.5 - d, 0.0, 1.0);
    return half4(col * half(cover), half(cover));
}
"""
