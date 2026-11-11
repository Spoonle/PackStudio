package com.packstudio.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * Text formatting, pixel-safe truncation, wrapped block rendering, and human-friendly timestamps.
 */
public final class TextFormatUtil {
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT).withZone(ZoneId.systemDefault());

    private TextFormatUtil() {
    }

    public static String cleanText(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String stripped = ChatFormatting.stripFormatting(raw);
        return stripped == null ? "" : stripped.replace("\r\n", "\n").trim();
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 4) {
            return "";
        }
        String clean = cleanText(text).replace('\n', ' ');
        if (font.width(clean) <= maxWidth) {
            return clean;
        }
        String dots = "...";
        int dotsWidth = font.width(dots);
        if (maxWidth <= dotsWidth + 2) {
            return font.plainSubstrByWidth(clean, maxWidth);
        }
        return font.plainSubstrByWidth(clean, maxWidth - dotsWidth).stripTrailing() + dots;
    }

    public static int drawWrappedLines(
            GuiGraphicsExtractor graphics,
            Font font,
            String text,
            int x,
            int y,
            int maxWidth,
            int maxLines,
            int color
    ) {
        if (text == null || text.isBlank() || maxWidth <= 12 || maxLines <= 0) {
            return 0;
        }
        List<FormattedText> lines = font.getSplitter().splitLines(
                Component.literal(cleanText(text)),
                maxWidth,
                Style.EMPTY
        );
        int drawn = Math.min(lines.size(), maxLines);
        int lineStep = font.lineHeight + 2;
        for (int i = 0; i < drawn; i++) {
            FormattedText line = lines.get(i);
            if (i == drawn - 1 && lines.size() > maxLines) {
                line = FormattedText.composite(
                        font.substrByWidth(line, Math.max(8, maxWidth - font.width("..."))),
                        FormattedText.of("...")
                );
            }
            FormattedCharSequence seq = Language.getInstance().getVisualOrder(line);
            graphics.text(font, seq, x, y + i * lineStep, color, false);
        }
        return drawn * lineStep;
    }

    public static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0L) {
            return "Unknown";
        }
        return DATE_FMT.format(Instant.ofEpochMilli(epochMillis));
    }
}
