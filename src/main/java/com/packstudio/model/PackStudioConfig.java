package com.packstudio.model;

import com.packstudio.core.PackStudioConstants;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.util.Mth;

/**
 * Persistent user configuration for PackStudio, versioned via {@code schemaVersion}.
 */
public final class PackStudioConfig {
    private int schemaVersion = PackStudioConstants.CONFIG_SCHEMA_VERSION;
    private LiquidThemePreset theme = LiquidThemePreset.OBSIDIAN_CYAN;
    private float glassOpacity = 0.82F;
    private ViewMode defaultViewMode = ViewMode.GRID;
    private SortMode defaultSortMode = SortMode.PRIORITY;
    private boolean animationsEnabled = true;
    private boolean showTitleScreenButton = true;
    private boolean showOptionsButton = true;
    private String openStudioKey = "key.keyboard.p";
    private String quickSwitchProfileKey = "key.keyboard.o";
    private String activeProfileId = "";
    private Set<String> favoritePackIds = new LinkedHashSet<>();

    public PackStudioConfig() {
    }

    /**
     * Normalizes deserialized fields so missing or out-of-range JSON values never cause runtime errors.
     */
    public void sanitize() {
        if (schemaVersion <= 0) {
            schemaVersion = PackStudioConstants.CONFIG_SCHEMA_VERSION;
        }
        if (theme == null) {
            theme = LiquidThemePreset.OBSIDIAN_CYAN;
        }
        if (Float.isNaN(glassOpacity) || Float.isInfinite(glassOpacity)) {
            glassOpacity = 0.82F;
        }
        glassOpacity = Mth.clamp(glassOpacity, 0.45F, 0.96F);
        if (defaultViewMode == null) {
            defaultViewMode = ViewMode.GRID;
        }
        if (defaultSortMode == null) {
            defaultSortMode = SortMode.PRIORITY;
        }
        if (openStudioKey == null || openStudioKey.isBlank()) {
            openStudioKey = "key.keyboard.p";
        }
        if (quickSwitchProfileKey == null || quickSwitchProfileKey.isBlank()) {
            quickSwitchProfileKey = "key.keyboard.o";
        }
        if (activeProfileId == null) {
            activeProfileId = "";
        }
        if (favoritePackIds == null) {
            favoritePackIds = new LinkedHashSet<>();
        } else {
            favoritePackIds.removeIf(id -> id == null || id.isBlank());
        }
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public LiquidThemePreset theme() {
        return theme;
    }

    public void setTheme(LiquidThemePreset theme) {
        this.theme = theme == null ? LiquidThemePreset.OBSIDIAN_CYAN : theme;
    }

    public float glassOpacity() {
        return glassOpacity;
    }

    public void setGlassOpacity(float glassOpacity) {
        this.glassOpacity = Mth.clamp(glassOpacity, 0.45F, 0.96F);
    }

    public ViewMode defaultViewMode() {
        return defaultViewMode;
    }

    public void setDefaultViewMode(ViewMode defaultViewMode) {
        this.defaultViewMode = defaultViewMode == null ? ViewMode.GRID : defaultViewMode;
    }

    public SortMode defaultSortMode() {
        return defaultSortMode;
    }

    public void setDefaultSortMode(SortMode defaultSortMode) {
        this.defaultSortMode = defaultSortMode == null ? SortMode.PRIORITY : defaultSortMode;
    }

    public boolean animationsEnabled() {
        return animationsEnabled;
    }

    public void setAnimationsEnabled(boolean animationsEnabled) {
        this.animationsEnabled = animationsEnabled;
    }

    public boolean showTitleScreenButton() {
        return showTitleScreenButton;
    }

    public void setShowTitleScreenButton(boolean showTitleScreenButton) {
        this.showTitleScreenButton = showTitleScreenButton;
    }

    public boolean showOptionsButton() {
        return showOptionsButton;
    }

    public void setShowOptionsButton(boolean showOptionsButton) {
        this.showOptionsButton = showOptionsButton;
    }

    public String openStudioKey() {
        return openStudioKey;
    }

    public void setOpenStudioKey(String openStudioKey) {
        this.openStudioKey = (openStudioKey == null || openStudioKey.isBlank()) ? "key.keyboard.p" : openStudioKey;
    }

    public String quickSwitchProfileKey() {
        return quickSwitchProfileKey;
    }

    public void setQuickSwitchProfileKey(String quickSwitchProfileKey) {
        this.quickSwitchProfileKey = (quickSwitchProfileKey == null || quickSwitchProfileKey.isBlank()) ? "key.keyboard.o" : quickSwitchProfileKey;
    }

    public String activeProfileId() {
        return activeProfileId;
    }

    public void setActiveProfileId(String activeProfileId) {
        this.activeProfileId = activeProfileId == null ? "" : activeProfileId;
    }

    public Set<String> favoritePackIds() {
        return Set.copyOf(favoritePackIds);
    }

    public boolean isFavorite(String packId) {
        return packId != null && favoritePackIds.contains(packId);
    }

    public boolean toggleFavorite(String packId) {
        if (packId == null || packId.isBlank()) {
            return false;
        }
        if (favoritePackIds.contains(packId)) {
            favoritePackIds.remove(packId);
            return false;
        }
        favoritePackIds.add(packId);
        return true;
    }
}
