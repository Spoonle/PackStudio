package com.packstudio.model;

import net.minecraft.network.chat.Component;

/**
 * Primary navigation sections in the PackStudio sidebar.
 */
public enum NavigationTab {
    OVERVIEW("packstudio.nav.overview", "◈"),
    ALL_PACKS("packstudio.nav.all_packs", "❖"),
    PROFILES("packstudio.nav.profiles", "▣"),
    FAVORITES("packstudio.nav.favorites", "★"),
    SETTINGS("packstudio.nav.settings", "⚙");

    private final String translationKey;
    private final String glyph;

    NavigationTab(String translationKey, String glyph) {
        this.translationKey = translationKey;
        this.glyph = glyph;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public String glyph() {
        return glyph;
    }
}
