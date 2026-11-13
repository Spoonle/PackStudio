package com.packstudio.client.screen.components;

import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Native {@link EditBox} subclass styled as a rounded Liquid Glass search bar while preserving
 * Minecraft's full SDL3 text input, selection, clipboard, and cursor handling.
 *
 * <p>The glass shell is painted by {@link #renderShell} from the screen's background pass rather than
 * from a widget render hook, so the field keeps vanilla text, selection, and cursor rendering on every
 * 26.X release without depending on which widget hook that release exposes.
 */
public final class GlassSearchField extends EditBox {
    private final Font textFont;
    private final int shellX;
    private final int shellY;
    private final int shellWidth;
    private final int shellHeight;

    public GlassSearchField(Font font, int x, int y, int width, int height, Component message) {
        super(font, x + 18, y + (height - 8) / 2, Math.max(24, width - 24), 10, message);
        this.textFont = font;
        this.shellX = x;
        this.shellY = y;
        this.shellWidth = width;
        this.shellHeight = height;
        this.setBordered(false);
    }

    private boolean containsPoint(double mouseX, double mouseY) {
        return this.visible
                && mouseX >= shellX
                && mouseX < shellX + shellWidth
                && mouseY >= shellY
                && mouseY < shellY + shellHeight;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.visible || !this.active) {
            return false;
        }
        if (containsPoint(event.x(), event.y())) {
            this.setFocused(true);
            super.mouseClicked(event, doubleClick);
            return true;
        }
        this.setFocused(false);
        return false;
    }

    /**
     * Paints the Liquid Glass shell and search glyph behind the field's own text.
     *
     * <p>Must be called before the widget pass so vanilla {@link EditBox} text, selection highlighting,
     * and the blinking cursor render on top of the shell.
     */
    public void renderShell(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }
        PackStudioConfig cfg = PackStudioBootstrap.get().configManager().config();
        LiquidThemePreset theme = cfg.theme();

        boolean hovered = containsPoint(mouseX, mouseY);
        boolean focused = isFocused();

        int bg = AnimationUtil.withAlpha(
                AnimationUtil.blendColors(theme.panelRgb(), 0x050811, 0.45F),
                focused ? 228 : 198
        );
        int border = focused
                ? AnimationUtil.withAlpha(theme.accentPrimary(), 220)
                : (hovered ? 0x50FFFFFF : 0x2AFFFFFF);

        GlassRenderUtil.drawRoundedFill(graphics, shellX, shellY, shellWidth, shellHeight, bg);
        GlassRenderUtil.drawRoundedBorder(graphics, shellX, shellY, shellWidth, shellHeight, border, border);

        int iconY = shellY + (shellHeight - 8) / 2;
        graphics.text(textFont, "⌕", shellX + 6, iconY, focused ? theme.accentPrimary() : theme.textMuted(), false);
    }
}
