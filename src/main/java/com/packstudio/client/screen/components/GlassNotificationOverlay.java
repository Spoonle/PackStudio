package com.packstudio.client.screen.components;

import com.packstudio.client.NotificationManager;
import com.packstudio.client.NotificationManager.Notification;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Util;

/**
 * Renders non-blocking Minimalist notification cards in the top-right corner of PackStudio.
 */
public final class GlassNotificationOverlay {
    private GlassNotificationOverlay() {
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Font font,
            int screenWidth,
            PackStudioConfig config,
            NotificationManager manager
    ) {
        List<Notification> items = manager.activeNotifications();
        if (items.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        int cardWidth = Math.min(240, Math.max(150, screenWidth / 3));
        int x = screenWidth - cardWidth - 14;
        int screenHeight = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledHeight();
        int y = Math.max(46, screenHeight - 82 - (items.size() - 1) * 39);

        for (Notification n : items) {
            float progress = n.progress(now);
            GlassRenderUtil.drawGlassPanel(graphics, x, y, cardWidth, 34, config, true);

            // Left status bar + subtle bottom progress line
            int accent = n.level().accentColor();
            graphics.fill(x + 2, y + 4, x + 4, y + 30, accent);
            int barWidth = Math.round((cardWidth - 8) * (1.0F - progress));
            if (barWidth > 0) {
                graphics.fill(x + 4, y + 31, x + 4 + barWidth, y + 32, AnimationUtil.withAlpha(accent, 180));
            }

            graphics.text(
                    font,
                    TextFormatUtil.ellipsize(font, n.title(), cardWidth - 14),
                    x + 9,
                    y + 5,
                    config.theme().textPrimary(),
                    false
            );
            graphics.text(
                    font,
                    TextFormatUtil.ellipsize(font, n.message(), cardWidth - 14),
                    x + 9,
                    y + 17,
                    config.theme().textMuted(),
                    false
            );
            y += 39;
        }
    }
}
