package com.packstudio.model;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackCompatibility;

/**
 * Truthful compatibility state mapped directly from Minecraft's native {@link PackCompatibility}.
 */
public enum PackCompatibilityStatus {
    COMPATIBLE("packstudio.compat.compatible", 0xFF34D399, true),
    OLDER_FORMAT("packstudio.compat.too_old", 0xFFFBBF24, false),
    NEWER_FORMAT("packstudio.compat.too_new", 0xFFF87171, false);

    private final String translationKey;
    private final int badgeColor;
    private final boolean compatible;

    PackCompatibilityStatus(String translationKey, int badgeColor, boolean compatible) {
        this.translationKey = translationKey;
        this.badgeColor = badgeColor;
        this.compatible = compatible;
    }

    public static PackCompatibilityStatus fromMinecraft(PackCompatibility nativeCompat) {
        if (nativeCompat == null || nativeCompat.isCompatible()) {
            return COMPATIBLE;
        }
        return switch (nativeCompat) {
            case TOO_OLD -> OLDER_FORMAT;
            case TOO_NEW -> NEWER_FORMAT;
            default -> COMPATIBLE;
        };
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public int badgeColor() {
        return badgeColor;
    }

    public boolean isCompatible() {
        return compatible;
    }
}
