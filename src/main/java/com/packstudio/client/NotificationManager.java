package com.packstudio.client;

import com.packstudio.core.PackStudioConstants;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Manages clean in-screen Liquid Glass notifications as well as native Minecraft {@link SystemToast}s
 * when PackStudio is not the active screen.
 */
public final class NotificationManager {
    private static final int MAX_VISIBLE = 3;
    private static final long DEFAULT_DURATION_MS = 4200L;
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(3800L);

    public enum Level {
        INFO(0xFF38BDF8),
        SUCCESS(0xFF34D399),
        WARNING(0xFFFBBF24),
        ERROR(0xFFF87171);

        private final int accentColor;

        Level(int accentColor) {
            this.accentColor = accentColor;
        }

        public int accentColor() {
            return accentColor;
        }
    }

    public record Notification(
            Level level,
            String title,
            String message,
            long createdAtMs,
            long durationMs
    ) {
        public float progress(long nowMs) {
            if (durationMs <= 0) {
                return 1.0F;
            }
            return Math.clamp((float) (nowMs - createdAtMs) / (float) durationMs, 0.0F, 1.0F);
        }

        public boolean isExpired(long nowMs) {
            return nowMs - createdAtMs >= durationMs;
        }
    }

    private final Deque<Notification> active = new ArrayDeque<>();

    public synchronized void push(Level level, String title, String message) {
        push(level, title, message, DEFAULT_DURATION_MS, false);
    }

    public synchronized void push(Level level, String title, String message, long durationMs, boolean alsoSystemToast) {
        long now = Util.getMillis();
        pruneExpired(now);
        while (active.size() >= MAX_VISIBLE) {
            active.pollFirst();
        }
        active.addLast(new Notification(
                level == null ? Level.INFO : level,
                title == null ? PackStudioConstants.MOD_NAME : title,
                message == null ? "" : message,
                now,
                Math.max(1200L, durationMs)
        ));

        if (alsoSystemToast) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gui != null && mc.gui.toastManager() != null) {
                SystemToast.addOrUpdate(
                        mc.gui.toastManager(),
                        TOAST_ID,
                        Component.literal(title == null ? PackStudioConstants.MOD_NAME : title),
                        Component.literal(message == null ? "" : message)
                );
            }
        }
    }

    public synchronized void info(String title, String message) {
        push(Level.INFO, title, message);
    }

    public synchronized void success(String title, String message, boolean systemToast) {
        push(Level.SUCCESS, title, message, DEFAULT_DURATION_MS, systemToast);
    }

    public synchronized void warning(String title, String message) {
        push(Level.WARNING, title, message, 5200L, false);
    }

    public synchronized void error(String title, String message, boolean systemToast) {
        push(Level.ERROR, title, message, 6000L, systemToast);
    }

    public synchronized List<Notification> activeNotifications() {
        pruneExpired(Util.getMillis());
        return List.copyOf(active);
    }

    public synchronized void clear() {
        active.clear();
    }

    private void pruneExpired(long nowMs) {
        active.removeIf(n -> n.isExpired(nowMs));
    }
}
