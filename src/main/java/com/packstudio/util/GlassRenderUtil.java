package com.packstudio.util;

import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.PackStudioConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * Native Minecraft {@link GuiGraphicsExtractor} rendering primitives for the Minimalist design system.
 * Uses zero custom shaders for predictable performance and 100% compatibility with Sodium.
 */
public final class GlassRenderUtil {
    private GlassRenderUtil() {
    }

    public static void drawBackdrop(GuiGraphicsExtractor graphics, int width, int height, PackStudioConfig config) {
        LiquidThemePreset theme = config.theme();
        int alpha = Mth.clamp(Math.round(config.glassOpacity() * 242.0F), 150, 248);
        int top = AnimationUtil.withAlpha(theme.backdropTop(), alpha);
        int bottom = AnimationUtil.withAlpha(theme.backdropBottom(), Math.min(252, alpha + 8));
        graphics.fillGradient(0, 0, width, height, top, bottom);

        // Subtle ambient top horizon wash (single pass, no heavy glow)
        int wash = AnimationUtil.withAlpha(theme.accentPrimary(), 14);
        graphics.fillGradient(0, 0, width, Math.min(height, 72), wash, 0x00000000);
    }

    public static void drawGlassPanel(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            PackStudioConfig config,
            boolean elevated
    ) {
        if (width <= 2 || height <= 2) {
            return;
        }
        LiquidThemePreset theme = config.theme();
        int baseAlpha = Mth.clamp(Math.round(config.glassOpacity() * (elevated ? 236.0F : 212.0F)), 115, 246);
        int rgb = elevated ? theme.elevatedRgb() : theme.panelRgb();

        // Soft 2-px ambient drop shadow
        drawRoundedFill(graphics, x + 1, y + 2, width, height, 0x36000000);
        drawRoundedFill(graphics, x + 2, y + 3, width, height, 0x1B000000);

        // Translucent frosted glass body with subtle vertical sheen
        int topFill = AnimationUtil.withAlpha(AnimationUtil.blendColors(rgb, 0xFFFFFF, 0.045F), baseAlpha);
        int botFill = AnimationUtil.withAlpha(rgb, Math.min(250, baseAlpha + 8));
        drawRoundedGradient(graphics, x, y, width, height, topFill, botFill);

        // Crisp 1px glass rim + specular top edge
        drawRoundedBorder(graphics, x, y, width, height, 0x26FFFFFF, 0x42FFFFFF);
    }

    public static void drawGlassCard(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            PackStudioConfig config,
            boolean selected,
            float hoverProgress
    ) {
        if (width <= 2 || height <= 2) {
            return;
        }
        LiquidThemePreset theme = config.theme();
        int alpha = Mth.clamp(Math.round(config.glassOpacity() * 218.0F), 120, 245);
        int baseRgb = AnimationUtil.blendColors(theme.panelRgb(), theme.elevatedRgb(), 0.55F + 0.35F * hoverProgress);
        if (selected) {
            baseRgb = AnimationUtil.blendColors(baseRgb, theme.accentSecondary(), 0.22F);
        }

        drawRoundedFill(graphics, x + 1, y + 1, width, height, 0x28000000);

        int top = AnimationUtil.withAlpha(AnimationUtil.blendColors(baseRgb, 0xFFFFFF, 0.05F + 0.04F * hoverProgress), alpha);
        int bottom = AnimationUtil.withAlpha(baseRgb, Math.min(248, alpha + 6));
        drawRoundedGradient(graphics, x, y, width, height, top, bottom);

        int borderCol = selected
                ? AnimationUtil.withAlpha(theme.accentPrimary(), 215)
                : AnimationUtil.blendColors(0x24FFFFFF, AnimationUtil.withAlpha(theme.accentPrimary(), 135), hoverProgress);
        int topRim = selected
                ? AnimationUtil.withAlpha(theme.accentPrimary(), 240)
                : AnimationUtil.blendColors(0x3AFFFFFF, AnimationUtil.withAlpha(theme.accentPrimary(), 170), hoverProgress);
        drawRoundedBorder(graphics, x, y, width, height, borderCol, topRim);
    }

    public static void drawBadgePill(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int accentArgb
    ) {
        if (width <= 2 || height <= 2) {
            return;
        }
        int bg = AnimationUtil.withAlpha(accentArgb, 42);
        int border = AnimationUtil.withAlpha(accentArgb, 138);
        drawRoundedFill(graphics, x, y, width, height, bg);
        drawRoundedBorder(graphics, x, y, width, height, border, border);
    }

    /**
     * Draws a bevel-rounded rectangle (2px corner radius) using 3 horizontal bands for crisp rendering at any GUI scale.
     */
    public static void drawRoundedFill(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        if (width <= 4 || height <= 4) {
            graphics.fill(x, y, x + width, y + height, color);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + 1, color);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, color);
        graphics.fill(x, y + 2, x + width, y + height - 2, color);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, color);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
    }

    public static void drawRoundedGradient(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int colorTop,
            int colorBottom
    ) {
        if (width <= 4 || height <= 4) {
            graphics.fillGradient(x, y, x + width, y + height, colorTop, colorBottom);
            return;
        }
        graphics.fill(x + 2, y, x + width - 2, y + 1, colorTop);
        graphics.fill(x + 1, y + 1, x + width - 1, y + 2, colorTop);
        graphics.fillGradient(x, y + 2, x + width, y + height - 2, colorTop, colorBottom);
        graphics.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, colorBottom);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, colorBottom);
    }

    public static void drawRoundedBorder(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            int borderColor,
            int topSpecularColor
    ) {
        if (width <= 4 || height <= 4) {
            graphics.outline(x, y, width, height, borderColor);
            return;
        }
        // Top specular rim + corner pixels
        graphics.fill(x + 2, y, x + width - 2, y + 1, topSpecularColor);
        graphics.fill(x + 1, y + 1, x + 2, y + 2, topSpecularColor);
        graphics.fill(x + width - 2, y + 1, x + width - 1, y + 2, topSpecularColor);

        // Side & bottom fine borders
        graphics.fill(x, y + 2, x + 1, y + height - 2, borderColor);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, borderColor);
        graphics.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor);
        graphics.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, borderColor);
    }
}
