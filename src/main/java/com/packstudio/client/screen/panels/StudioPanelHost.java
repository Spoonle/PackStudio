package com.packstudio.client.screen.panels;

import com.packstudio.client.screen.components.GlassModalDialog;
import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.model.FilterMode;
import com.packstudio.model.NavigationTab;
import com.packstudio.model.PackEntry;
import com.packstudio.model.SortMode;
import com.packstudio.model.ViewMode;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;

/**
 * Clean contract exposed by {@code PackStudioScreen} to its child Minimalist panels.
 */
public interface StudioPanelHost {
    PackStudioBootstrap bootstrap();

    Font font();

    NavigationTab activeTab();

    void switchTab(NavigationTab tab);

    ViewMode viewMode();

    void setViewMode(ViewMode mode);

    FilterMode filterMode();

    void setFilterMode(FilterMode mode);

    SortMode sortMode();

    void setSortMode(SortMode mode);

    String searchQuery();

    List<PackEntry> visiblePacks();

    Optional<PackEntry> selectedPack();

    void selectPack(String packId);

    void rebuildAndRefreshState();

    void openModal(GlassModalDialog modal);

    void closeModal();

    void triggerApplyProfile(String profileId);
}
