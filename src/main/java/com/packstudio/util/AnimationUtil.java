package com.packstudio.util;

import net.minecraft.util.Mth;

/**
 * Lightweight frame-rate-independent interpolation helper that honors the user's animation toggle.
 */
public final class AnimationUtil {
    private AnimationUtil() {
    }

    public static float approach(float current, float target, float speed, float deltaTicks, boolean animationsEnabled) {
        if (!animationsEnabled || Math.abs(target - current) < 0.002F) {
            return target;
        }
        float clampedDelta = Mth.clamp(deltaTicks, 0.01F, 2.5F);
        float factor = 1.0F - (float) Math.exp(-speed * clampedDelta);
        return Mth.lerp(Mth.clamp(factor, 0.0F, 1.0F), current, target);
    }

    public static int blendColors(int colorFrom, int colorTo, float t) {
        float clamped = Mth.clamp(t, 0.0F, 1.0F);
        int a1 = (colorFrom >>> 24) & 0xFF;
        int r1 = (colorFrom >>> 16) & 0xFF;
        int g1 = (colorFrom >>> 8) & 0xFF;
        int b1 = colorFrom & 0xFF;

        int a2 = (colorTo >>> 24) & 0xFF;
        int r2 = (colorTo >>> 16) & 0xFF;
        int g2 = (colorTo >>> 8) & 0xFF;
        int b2 = colorTo & 0xFF;

        int a = Math.round(Mth.lerp(clamped, a1, a2));
        int r = Math.round(Mth.lerp(clamped, r1, r2));
        int g = Math.round(Mth.lerp(clamped, g1, g2));
        int b = Math.round(Mth.lerp(clamped, b1, b2));
        return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    public static int withAlpha(int rgbOrArgb, int alpha) {
        return ((Mth.clamp(alpha, 0, 255) & 0xFF) << 24) | (rgbOrArgb & 0x00FFFFFF);
    }

    public static int scaleAlpha(int argb, float factor) {
        int baseAlpha = (argb >>> 24) & 0xFF;
        int scaled = Mth.clamp(Math.round(baseAlpha * factor), 0, 255);
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }
}
