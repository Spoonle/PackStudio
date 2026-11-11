package com.packstudio.model;

import java.util.Optional;
import net.minecraft.server.packs.repository.Pack;

/**
 * Immutable view model of a real Minecraft resource pack discovered in {@code PackRepository},
 * enriched with honest metadata read from {@code pack.mcmeta}.
 */
public record PackEntry(
        String id,
        String displayName,
        String description,
        Optional<String> author,
        Optional<String> packFormatLabel,
        PackCompatibilityStatus compatibility,
        PackSourceKind sourceKind,
        boolean required,
        boolean fixedPosition,
        boolean enabled,
        int priorityPosition,
        boolean favorite,
        Pack nativePack
) {
    public boolean canToggleEnable() {
        return !required;
    }

    public boolean canReorder() {
        return enabled && !fixedPosition;
    }
}
