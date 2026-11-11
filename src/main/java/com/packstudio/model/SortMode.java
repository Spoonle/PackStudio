package com.packstudio.model;

import net.minecraft.network.chat.Component;

/**
 * Sorting options for the resource pack library.
 */
public enum SortMode {
    PRIORITY("packstudio.sort.priority"),
    ALPHABETICAL_ASC("packstudio.sort.alpha_asc"),
    ALPHABETICAL_DESC("packstudio.sort.alpha_desc"),
    ENABLED_FIRST("packstudio.sort.enabled_first"),
    COMPATIBILITY("packstudio.sort.compatibility");

    private final String translationKey;

    SortMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public SortMode next(int delta) {
        SortMode[] values = values();
        int idx = Math.floorMod(this.ordinal() + delta, values.length);
        return values[idx];
    }
}
