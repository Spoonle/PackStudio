package com.packstudio.client.screen;

import com.packstudio.model.NavigationTab;
import net.minecraft.util.Mth;

/**
 * Responsive layout calculator for {@link PackStudioScreen} across different window sizes and GUI scales.
 */
public record AdaptiveLayout(
        int screenWidth,
        int screenHeight,
        int margin,
        int gap,
        Rect topBar,
        Rect sidebar,
        Rect mainContent,
        Rect detailsPanel,
        Rect bottomBar,
        boolean showDetailsPanel,
        boolean compactMode
) {
    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }
    }

    public static AdaptiveLayout compute(int screenWidth, int screenHeight, NavigationTab activeTab) {
        boolean compact = screenWidth < 520 || screenHeight < 290;
        int margin = compact ? 6 : 10;
        int gap = compact ? 5 : 8;

        int topHeight = compact ? 28 : 34;
        int bottomHeight = compact ? 26 : 32;

        Rect topBar = new Rect(margin, margin, Math.max(120, screenWidth - margin * 2), topHeight);
        Rect bottomBar = new Rect(
                margin,
                Math.max(topBar.bottom() + gap + 60, screenHeight - margin - bottomHeight),
                Math.max(120, screenWidth - margin * 2),
                bottomHeight
        );

        int bodyY = topBar.bottom() + gap;
        int bodyHeight = Math.max(60, bottomBar.y() - gap - bodyY);

        int sidebarWidth = compact
                ? Mth.clamp(screenWidth / 5, 84, 108)
                : Mth.clamp(screenWidth / 6, 116, 148);
        Rect sidebar = new Rect(margin, bodyY, sidebarWidth, bodyHeight);

        boolean tabUsesDetails = activeTab == NavigationTab.ALL_PACKS
                || activeTab == NavigationTab.FAVORITES
                || activeTab == NavigationTab.OVERVIEW;
        boolean canFitDetails = tabUsesDetails && (screenWidth - sidebar.right() - margin - gap * 2 >= 260);

        Rect mainContent;
        Rect detailsPanel;
        if (canFitDetails) {
            int remaining = screenWidth - sidebar.right() - gap - margin;
            int detailsWidth = compact
                    ? Mth.clamp((int) (remaining * 0.36F), 124, 165)
                    : Mth.clamp((int) (remaining * 0.34F), 165, 228);
            int mainWidth = Math.max(120, remaining - detailsWidth - gap);
            mainContent = new Rect(sidebar.right() + gap, bodyY, mainWidth, bodyHeight);
            detailsPanel = new Rect(mainContent.right() + gap, bodyY, detailsWidth, bodyHeight);
        } else {
            int mainWidth = Math.max(120, screenWidth - sidebar.right() - gap - margin);
            mainContent = new Rect(sidebar.right() + gap, bodyY, mainWidth, bodyHeight);
            detailsPanel = new Rect(0, 0, 0, 0);
        }

        return new AdaptiveLayout(
                screenWidth,
                screenHeight,
                margin,
                gap,
                topBar,
                sidebar,
                mainContent,
                detailsPanel,
                bottomBar,
                canFitDetails,
                compact
        );
    }
}
