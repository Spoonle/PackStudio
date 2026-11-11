package com.packstudio.model;

import net.minecraft.network.chat.Component;

/**
 * Resource pack library layout mode.
 */
public enum ViewMode {
    GRID("packstudio.topbar.view.grid"),
    LIST("packstudio.topbar.view.list");

    private final String translationKey;

    ViewMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public ViewMode toggle() {
        return this == GRID ? LIST : GRID;
    }
}
