package com.packstudio.client.screen.components;

import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Custom Liquid Glass button supporting Default, Primary Accent, Danger, and Subtle Pill variants.
 */
public final class GlassButton extends Button {
    public enum Variant {
        SECONDARY,
        PRIMARY,
        DANGER,
        SUBTLE
    }

    private final Variant variant;
    private boolean selectedState = false;
    private float hoverAnim = 0.0F;

    public GlassButton(int x, int y, int width, int height, Component message, Variant variant, OnPress onPress) {
        super(x, y, width, height, message, onPress, Button.DEFAULT_NARRATION);
        this.variant = variant == null ? Variant.SECONDARY : variant;
    }

    public GlassButton setSelectedState(boolean selectedState) {
        this.selectedState = selectedState;
        return this;
    }

    public boolean isSelectedState() {
        return selectedState;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        PackStudioConfig cfg = PackStudioBootstrap.get().configManager().config();
        LiquidThemePreset theme = cfg.theme();

        float target = (this.active && this.isHoveredOrFocused()) ? 1.0F : 0.0F;
        this.hoverAnim = AnimationUtil.approach(this.hoverAnim, target, 0.45F, delta, cfg.animationsEnabled());

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        int topFill;
        int botFill;
        int borderCol;
        int topSpec;
        int textCol;

        if (!this.active) {
            topFill = 0x55121824;
            botFill = 0x550E131D;
            borderCol = 0x1AFFFFFF;
            topSpec = 0x22FFFFFF;
            textCol = 0xFF64748B;
        } else {
            switch (variant) {
                case PRIMARY -> {
                    int c1 = AnimationUtil.blendColors(theme.accentSecondary(), theme.accentPrimary(), 0.35F + 0.40F * hoverAnim);
                    int c2 = AnimationUtil.blendColors(theme.accentSecondary(), theme.panelRgb(), 0.35F - 0.15F * hoverAnim);
                    topFill = AnimationUtil.withAlpha(c1, 228);
                    botFill = AnimationUtil.withAlpha(c2, 236);
                    borderCol = AnimationUtil.withAlpha(theme.accentPrimary(), 210);
                    topSpec = 0xAAFFFFFF;
                    textCol = 0xFFFFFFFF;
                }
                case DANGER -> {
                    int base = AnimationUtil.blendColors(0x521820, 0x992430, hoverAnim);
                    topFill = AnimationUtil.withAlpha(base, 215);
                    botFill = AnimationUtil.withAlpha(0x3A1016, 225);
                    borderCol = AnimationUtil.blendColors(0x66F87171, 0xCCF87171, hoverAnim);
                    topSpec = borderCol;
                    textCol = 0xFFFECACA;
                }
                case SUBTLE -> {
                    int alpha = selectedState ? 185 : Math.round(95 + 75 * hoverAnim);
                    int rgb = selectedState
                            ? AnimationUtil.blendColors(theme.elevatedRgb(), theme.accentSecondary(), 0.32F)
                            : AnimationUtil.blendColors(theme.panelRgb(), theme.elevatedRgb(), hoverAnim);
                    topFill = AnimationUtil.withAlpha(rgb, alpha);
                    botFill = AnimationUtil.withAlpha(rgb, alpha);
                    borderCol = selectedState
                            ? AnimationUtil.withAlpha(theme.accentPrimary(), 195)
                            : AnimationUtil.blendColors(0x1EFFFFFF, 0x48FFFFFF, hoverAnim);
                    topSpec = borderCol;
                    textCol = selectedState ? theme.textPrimary() : AnimationUtil.blendColors(theme.textMuted(), theme.textPrimary(), hoverAnim);
                }
                default -> {
                    int rgb = selectedState
                            ? AnimationUtil.blendColors(theme.elevatedRgb(), theme.accentSecondary(), 0.28F)
                            : AnimationUtil.blendColors(theme.panelRgb(), theme.elevatedRgb(), 0.45F + 0.55F * hoverAnim);
                    topFill = AnimationUtil.withAlpha(AnimationUtil.blendColors(rgb, 0xFFFFFF, 0.05F), 210);
                    botFill = AnimationUtil.withAlpha(rgb, 222);
                    borderCol = selectedState
                            ? AnimationUtil.withAlpha(theme.accentPrimary(), 200)
                            : AnimationUtil.blendColors(0x2EFFFFFF, AnimationUtil.withAlpha(theme.accentPrimary(), 155), hoverAnim);
                    topSpec = selectedState ? borderCol : 0x52FFFFFF;
                    textCol = theme.textPrimary();
                }
            }
        }

        GlassRenderUtil.drawRoundedGradient(graphics, x, y, w, h, topFill, botFill);
        GlassRenderUtil.drawRoundedBorder(graphics, x, y, w, h, borderCol, topSpec);

        Font font = Minecraft.getInstance().font;
        String label = TextFormatUtil.ellipsize(font, getMessage().getString(), Math.max(8, w - 8));
        graphics.centeredText(font, label, x + w / 2, y + (h - 8) / 2, textCol);
    }
}
