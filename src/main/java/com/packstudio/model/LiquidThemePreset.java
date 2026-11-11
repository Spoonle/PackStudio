package com.packstudio.model;

/**
 * Curated dark Liquid Glass color themes with controlled accent palettes and high-contrast text.
 */
public enum LiquidThemePreset {
    OBSIDIAN_CYAN(
            "Obsidian Cyan",
            0xFF070B14,
            0xFF0D1526,
            0x0F1828,
            0x162238,
            0xFF38BDF8,
            0xFF0284C7,
            0xFFF1F5F9,
            0xFF94A3B8
    ),
    MIDNIGHT_INDIGO(
            "Midnight Indigo",
            0xFF080915,
            0xFF12142B,
            0x12152A,
            0x1B2040,
            0xFF818CF8,
            0xFF4F46E5,
            0xFFF3F4F6,
            0xFF9CA3AF
    ),
    FROSTED_EMERALD(
            "Frosted Emerald",
            0xFF060E0D,
            0xFF0B1C1A,
            0x0E1F1D,
            0x152E2B,
            0xFF34D399,
            0xFF059669,
            0xFFECFDF5,
            0xFF94B8AE
    );

    private final String label;
    private final int backdropTop;
    private final int backdropBottom;
    private final int panelRgb;
    private final int elevatedRgb;
    private final int accentPrimary;
    private final int accentSecondary;
    private final int textPrimary;
    private final int textMuted;

    LiquidThemePreset(
            String label,
            int backdropTop,
            int backdropBottom,
            int panelRgb,
            int elevatedRgb,
            int accentPrimary,
            int accentSecondary,
            int textPrimary,
            int textMuted
    ) {
        this.label = label;
        this.backdropTop = backdropTop;
        this.backdropBottom = backdropBottom;
        this.panelRgb = panelRgb;
        this.elevatedRgb = elevatedRgb;
        this.accentPrimary = accentPrimary;
        this.accentSecondary = accentSecondary;
        this.textPrimary = textPrimary;
        this.textMuted = textMuted;
    }

    public String label() {
        return label;
    }

    public int backdropTop() {
        return backdropTop;
    }

    public int backdropBottom() {
        return backdropBottom;
    }

    public int panelRgb() {
        return panelRgb;
    }

    public int elevatedRgb() {
        return elevatedRgb;
    }

    public int accentPrimary() {
        return accentPrimary;
    }

    public int accentSecondary() {
        return accentSecondary;
    }

    public int textPrimary() {
        return textPrimary;
    }

    public int textMuted() {
        return textMuted;
    }

    public LiquidThemePreset next() {
        LiquidThemePreset[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }
}
