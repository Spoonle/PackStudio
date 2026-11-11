package com.packstudio.client.screen.components;

import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.NavigationTab;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;

/**
 * Sidebar navigation button with active Liquid Glass pill highlight and optional item-count badge.
 */
public final class GlassTabButton extends Button {
    private final NavigationTab tab;
    private final IntSupplier badgeSupplier;
    private boolean activeTab;
    private float hoverAnim = 0.0F;

    public GlassTabButton(
            int x,
            int y,
            int width,
            int height,
            NavigationTab tab,
            boolean activeTab,
            IntSupplier badgeSupplier,
            Consumer<NavigationTab> onSelect
    ) {
        super(x, y, width, height, tab.displayName(), _ -> onSelect.accept(tab));
        this.tab = tab;
        this.activeTab = activeTab;
        this.badgeSupplier = badgeSupplier;
    }

    public void setActiveTab(boolean activeTab) {
        this.activeTab = activeTab;
    }

    public NavigationTab tab() {
        return tab;
    }

    @Override
    public void onPress() {
        if (this.active && this.visible) {
            AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
            super.onPress();
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        PackStudioConfig cfg = PackStudioBootstrap.get().configManager().config();
        LiquidThemePreset theme = cfg.theme();

        float target = (activeTab || isHoveredOrFocused()) ? 1.0F : 0.0F;
        this.hoverAnim = AnimationUtil.approach(this.hoverAnim, target, 0.45F, delta, cfg.animationsEnabled());

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        if (activeTab) {
            int fillTop = AnimationUtil.withAlpha(AnimationUtil.blendColors(theme.elevatedRgb(), theme.accentSecondary(), 0.34F), 215);
            int fillBot = AnimationUtil.withAlpha(theme.elevatedRgb(), 220);
            GlassRenderUtil.drawRoundedGradient(graphics, x, y, w, h, fillTop, fillBot);
            GlassRenderUtil.drawRoundedBorder(
                    graphics,
                    x,
                    y,
                    w,
                    h,
                    AnimationUtil.withAlpha(theme.accentPrimary(), 165),
                    AnimationUtil.withAlpha(theme.accentPrimary(), 210)
            );
            // Active vertical accent bar
            graphics.fill(x + 2, y + 4, x + 4, y + h - 4, theme.accentPrimary());
        } else if (hoverAnim > 0.02F) {
            int hoverFill = AnimationUtil.withAlpha(theme.elevatedRgb(), Math.round(135 * hoverAnim));
            GlassRenderUtil.drawRoundedFill(graphics, x, y, w, h, hoverFill);
            GlassRenderUtil.drawRoundedBorder(graphics, x, y, w, h, AnimationUtil.withAlpha(0xFFFFFF, Math.round(45 * hoverAnim)), AnimationUtil.withAlpha(0xFFFFFF, Math.round(60 * hoverAnim)));
        }

        Font font = Minecraft.getInstance().font;
        int badgeCount = badgeSupplier == null ? -1 : badgeSupplier.getAsInt();
        String badgeText = badgeCount >= 0 ? Integer.toString(badgeCount) : null;
        int badgeReserve = badgeText != null ? font.width(badgeText) + 12 : 0;

        int textColor = activeTab
                ? theme.textPrimary()
                : AnimationUtil.blendColors(theme.textMuted(), theme.textPrimary(), hoverAnim);

        int textY = y + (h - 8) / 2;
        graphics.text(font, tab.glyph(), x + 8, textY, activeTab ? theme.accentPrimary() : textColor, false);

        String title = TextFormatUtil.ellipsize(font, getMessage().getString(), Math.max(16, w - 24 - badgeReserve));
        graphics.text(font, title, x + 20, textY, textColor, false);

        if (badgeText != null && w >= 88) {
            int pillW = Math.max(14, font.width(badgeText) + 8);
            int pillX = x + w - pillW - 5;
            int pillY = y + (h - 12) / 2;
            GlassRenderUtil.drawBadgePill(graphics, pillX, pillY, pillW, 12, activeTab ? theme.accentPrimary() : 0xFF94A3B8);
            graphics.centeredText(font, badgeText, pillX + pillW / 2, pillY + 2, activeTab ? theme.textPrimary() : theme.textMuted());
        }
    }
}
