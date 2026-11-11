package com.packstudio.model;

import net.minecraft.network.chat.Component;

/**
 * Filter predicates available in the PackStudio library view.
 */
public enum FilterMode {
    ALL("packstudio.filter.all"),
    ENABLED("packstudio.filter.enabled"),
    DISABLED("packstudio.filter.disabled"),
    FAVORITES("packstudio.filter.favorites"),
    INCOMPATIBLE("packstudio.filter.incompatible");

    private final String translationKey;

    FilterMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }
}
