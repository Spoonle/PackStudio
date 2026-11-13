package com.packstudio.client.screen;

import com.packstudio.client.screen.AdaptiveLayout.Rect;
import com.packstudio.client.screen.components.GlassButton;
import com.packstudio.client.screen.components.GlassModalDialog;
import com.packstudio.client.screen.components.GlassNotificationOverlay;
import com.packstudio.client.screen.components.GlassSearchField;
import com.packstudio.compat.MinecraftCompatProvider;
import com.packstudio.client.screen.components.GlassTabButton;
import com.packstudio.client.screen.panels.OverviewPanel;
import com.packstudio.client.screen.panels.PackDetailsPanel;
import com.packstudio.client.screen.panels.PackLibraryPanel;
import com.packstudio.client.screen.panels.ProfilesPanel;
import com.packstudio.client.screen.panels.SettingsPanel;
import com.packstudio.client.screen.panels.StudioPanelHost;
import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.manager.PackRepositoryManager;
import com.packstudio.model.FilterMode;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.NavigationTab;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackProfile;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.model.SortMode;
import com.packstudio.model.ViewMode;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Primary Liquid Glass workspace screen for PackStudio.
 */
public final class PackStudioScreen extends Screen implements StudioPanelHost {
    private final Screen parentScreen;
    private final PackStudioBootstrap bootstrap;

    private NavigationTab activeTab = NavigationTab.ALL_PACKS;
    private ViewMode viewMode;
    private FilterMode filterMode = FilterMode.ALL;
    private SortMode sortMode;
    private String searchQuery = "";
    private String selectedPackId = "";

    private AdaptiveLayout layout;
    private GlassSearchField searchBox;
    private GlassButton sortButton;
    private GlassButton viewToggleButton;
    private GlassButton applyButton;
    private GlassButton discardButton;
    private GlassButton resetButton;
    private final List<GlassTabButton> tabButtons = new ArrayList<>();

    private final OverviewPanel overviewPanel;
    private final PackLibraryPanel packLibraryPanel;
    private final PackDetailsPanel packDetailsPanel;
    private final ProfilesPanel profilesPanel;
    private final SettingsPanel settingsPanel;

    private GlassModalDialog activeModal;
    private List<PackEntry> cachedVisiblePacks = List.of();

    public PackStudioScreen(Screen parentScreen) {
        super(Component.translatable("packstudio.title"));
        this.parentScreen = parentScreen;
        this.bootstrap = PackStudioBootstrap.get();

        PackStudioConfig cfg = this.bootstrap.configManager().config();
        this.viewMode = cfg.defaultViewMode();
        this.sortMode = cfg.defaultSortMode();

        this.overviewPanel = new OverviewPanel(this);
        this.packLibraryPanel = new PackLibraryPanel(this);
        this.packDetailsPanel = new PackDetailsPanel(this);
        this.profilesPanel = new ProfilesPanel(this);
        this.settingsPanel = new SettingsPanel(this);
    }

    @Override
    protected void init() {
        bootstrap.packRepositoryManager().refreshFromRepository(true);
        bootstrap.profileManager().loadAll();
        recomputeVisiblePacks();
        buildScreenWidgets();
    }

    private void buildScreenWidgets() {
        this.clearWidgets();
        this.tabButtons.clear();

        this.layout = AdaptiveLayout.compute(this.width, this.height, this.activeTab);

        // 1. Top Bar Widgets
        Rect top = layout.topBar();
        int btnH = layout.compactMode() ? 18 : 20;
        int btnY = top.y() + (top.height() - btnH) / 2;
        int rightCursor = top.right() - 6;

        // Close button
        GlassButton closeBtn = addRenderableWidget(new GlassButton(
                rightCursor - 20, btnY, 20, btnH,
                Component.literal("✕"),
                GlassButton.Variant.SUBTLE,
                _ -> requestCloseScreen()
        ));
        rightCursor = closeBtn.getX() - 4;

        // Folder button
        if (!layout.compactMode()) {
            GlassButton folderBtn = addRenderableWidget(new GlassButton(
                    rightCursor - 46, btnY, 46, btnH,
                    Component.translatable("packstudio.topbar.open_folder"),
                    GlassButton.Variant.SECONDARY,
                    _ -> openResourcePacksFolder()
            ));
            rightCursor = folderBtn.getX() - 4;
        }

        // Refresh button
        GlassButton refreshBtn = addRenderableWidget(new GlassButton(
                rightCursor - 22, btnY, 22, btnH,
                Component.literal("↻"),
                GlassButton.Variant.SECONDARY,
                _ -> {
                    bootstrap.iconCache().invalidateAll();
                    bootstrap.packRepositoryManager().refreshFromRepository(true);
                    bootstrap.profileManager().loadAll();
                    rebuildAndRefreshState();
                    bootstrap.notifications().info(PackStudioConstants.MOD_NAME, "Refreshed resource packs and presets.");
                }
        ));
        rightCursor = refreshBtn.getX() - 4;

        // Grid / List View Toggle
        int viewW = layout.compactMode() ? 42 : 52;
        this.viewToggleButton = addRenderableWidget(new GlassButton(
                rightCursor - viewW, btnY, viewW, btnH,
                viewMode.displayName(),
                GlassButton.Variant.SECONDARY,
                _ -> setViewMode(viewMode.toggle())
        ));
        rightCursor = viewToggleButton.getX() - 4;

        // Sort Cycle Button
        int sortW = layout.compactMode() ? 64 : 92;
        this.sortButton = addRenderableWidget(new GlassButton(
                rightCursor - sortW, btnY, sortW, btnH,
                Component.literal("Sort: " + sortMode.displayName().getString()),
                GlassButton.Variant.SECONDARY,
                _ -> setSortMode(sortMode.next(1))
        ));
        rightCursor = sortButton.getX() - 6;

        // Search Field (Liquid Glass native EditBox subclass)
        int brandReserve = layout.compactMode() ? 78 : 108;
        int searchX = top.x() + brandReserve;
        int searchW = Math.max(64, rightCursor - searchX);
        this.searchBox = new GlassSearchField(this.font, searchX, btnY, searchW, btnH, Component.translatable("packstudio.topbar.search"));
        this.searchBox.setMaxLength(80);
        this.searchBox.setHint(Component.translatable("packstudio.topbar.search"));
        this.searchBox.setValue(this.searchQuery);
        this.searchBox.setResponder(val -> {
            this.searchQuery = val == null ? "" : val;
            recomputeVisiblePacks();
            if (activeTab != NavigationTab.ALL_PACKS && activeTab != NavigationTab.FAVORITES && !this.searchQuery.isBlank()) {
                this.activeTab = NavigationTab.ALL_PACKS;
                buildScreenWidgets();
            }
        });
        addRenderableWidget(this.searchBox);

        // 2. Sidebar Navigation Tabs
        Rect side = layout.sidebar();
        int navY = side.y() + 8;
        int navH = layout.compactMode() ? 20 : 23;
        int navGap = layout.compactMode() ? 3 : 4;

        for (NavigationTab tab : NavigationTab.values()) {
            var badge = switch (tab) {
                case ALL_PACKS -> (java.util.function.IntSupplier) () -> bootstrap.packRepositoryManager().allPacks().size();
                case PROFILES -> (java.util.function.IntSupplier) () -> bootstrap.profileManager().profiles().size();
                case FAVORITES -> (java.util.function.IntSupplier) () -> bootstrap.configManager().config().favoritePackIds().size();
                default -> null;
            };
            GlassTabButton tabBtn = new GlassTabButton(
                    side.x() + 6,
                    navY,
                    side.width() - 12,
                    navH,
                    tab,
                    tab == this.activeTab,
                    badge,
                    this::switchTab
            );
            this.tabButtons.add(tabBtn);
            addRenderableWidget(tabBtn);
            navY += navH + navGap;
        }

        // 3. Bottom Action Bar Buttons (Apply Changes | Discard Changes | Reset)
        Rect bot = layout.bottomBar();
        int bBtnH = layout.compactMode() ? 18 : 20;
        int bBtnY = bot.y() + (bot.height() - bBtnH) / 2;
        int bRight = bot.right() - 8;

        int applyW = layout.compactMode() ? 78 : 98;
        int discardW = layout.compactMode() ? 78 : 96;
        int resetW = layout.compactMode() ? 52 : 66;

        this.applyButton = addRenderableWidget(new GlassButton(
                bRight - applyW, bBtnY, applyW, bBtnH,
                Component.translatable("packstudio.action.apply"),
                GlassButton.Variant.PRIMARY,
                _ -> applyPendingChanges()
        ));

        this.discardButton = addRenderableWidget(new GlassButton(
                applyButton.getX() - discardW - 6, bBtnY, discardW, bBtnH,
                Component.translatable("packstudio.action.discard"),
                GlassButton.Variant.SECONDARY,
                _ -> {
                    bootstrap.packRepositoryManager().discardStagedChanges();
                    rebuildAndRefreshState();
                    bootstrap.notifications().info(PackStudioConstants.MOD_NAME, "Discarded unsaved pack changes.");
                }
        ));

        this.resetButton = addRenderableWidget(new GlassButton(
                discardButton.getX() - resetW - 6, bBtnY, resetW, bBtnH,
                Component.translatable("packstudio.action.reset"),
                GlassButton.Variant.DANGER,
                _ -> confirmResetToDefaultPacks()
        ));

        // 4. Child panel sub-layouts
        if (layout.showDetailsPanel()) {
            packDetailsPanel.layoutButtons(layout.detailsPanel());
        }
        if (activeTab == NavigationTab.PROFILES) {
            profilesPanel.layoutHeaderButtons(layout.mainContent());
        }
        if (activeModal != null) {
            activeModal.layout(this.font, this.width, this.height);
        }

        syncActionButtons();
    }

    private void syncActionButtons() {
        PackRepositoryManager repo = bootstrap.packRepositoryManager();
        boolean dirty = repo.hasUnsavedChanges();
        boolean busy = repo.isApplyingReload();
        if (applyButton != null) {
            applyButton.active = dirty && !busy;
            applyButton.setMessage(Component.translatable(busy ? "packstudio.action.applying" : "packstudio.action.apply"));
        }
        if (discardButton != null) {
            discardButton.active = dirty && !busy;
        }
        if (resetButton != null) {
            resetButton.active = !busy;
        }
        if (viewToggleButton != null) {
            viewToggleButton.setMessage(viewMode.displayName());
        }
        if (sortButton != null) {
            sortButton.setMessage(Component.literal("Sort: " + sortMode.displayName().getString()));
        }
        for (GlassTabButton tb : tabButtons) {
            tb.setActiveTab(tb.tab() == this.activeTab);
        }
    }

    private void recomputeVisiblePacks() {
        FilterMode effectiveFilter = (activeTab == NavigationTab.FAVORITES) ? FilterMode.FAVORITES : this.filterMode;
        this.cachedVisiblePacks = bootstrap.packRepositoryManager().queryPacks(this.searchQuery, effectiveFilter, this.sortMode);

        if (selectedPackId.isEmpty() || bootstrap.packRepositoryManager().findPack(selectedPackId).isEmpty()) {
            if (!cachedVisiblePacks.isEmpty()) {
                selectedPackId = cachedVisiblePacks.getFirst().id();
            } else {
                var all = bootstrap.packRepositoryManager().allPacks();
                selectedPackId = all.isEmpty() ? "" : all.getFirst().id();
            }
        }
    }

    private void applyPendingChanges() {
        syncActionButtons();
        bootstrap.packRepositoryManager().applyStagedChanges(() -> {
            recomputeVisiblePacks();
            syncActionButtons();
            if (layout != null && layout.showDetailsPanel()) {
                packDetailsPanel.layoutButtons(layout.detailsPanel());
            }
        }).thenAccept(ok -> {
            if (ok) {
                bootstrap.notifications().success(
                        PackStudioConstants.MOD_NAME,
                        "Applied resource packs and reloaded client assets.",
                        false
                );
            } else {
                bootstrap.notifications().error(
                        PackStudioConstants.MOD_NAME,
                        I18n.get("packstudio.error.reload_failed"),
                        false
                );
            }
        });
    }

    private void confirmResetToDefaultPacks() {
        openModal(GlassModalDialog.confirm(
                "Reset Resource Pack Selection?",
                "Stages only required base packs (Default + required mod packs). Click 'Apply Changes' afterwards to reload.",
                I18n.get("packstudio.action.reset"),
                GlassButton.Variant.DANGER,
                () -> {
                    bootstrap.packRepositoryManager().stageResetToDefaults();
                    closeModal();
                    rebuildAndRefreshState();
                },
                this::closeModal
        ));
    }

    private void openResourcePacksFolder() {
        if (this.minecraft == null) {
            return;
        }
        Path dir = this.minecraft.getResourcePackDirectory();
        if (dir != null) {
            try {
                Files.createDirectories(dir);
                MinecraftCompatProvider.get().openDirectory(dir);
            } catch (Exception e) {
                PackStudioConstants.LOGGER.warn("Failed to open resourcepacks directory {}", dir, e);
            }
        }
    }

    private void requestCloseScreen() {
        if (bootstrap.packRepositoryManager().hasUnsavedChanges()) {
            openModal(GlassModalDialog.confirm(
                    "Discard Unsaved Pack Changes?",
                    "You have staged pack changes that haven't been applied yet. Close without applying?",
                    "Discard & Close",
                    GlassButton.Variant.DANGER,
                    () -> {
                        bootstrap.packRepositoryManager().discardStagedChanges();
                        closeModal();
                        onClose();
                    },
                    this::closeModal
            ));
            return;
        }
        onClose();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        PackStudioConfig cfg = bootstrap.configManager().config();
        GlassRenderUtil.drawBackdrop(graphics, this.width, this.height, cfg);

        if (layout == null) {
            layout = AdaptiveLayout.compute(this.width, this.height, this.activeTab);
        }
        LiquidThemePreset theme = cfg.theme();

        // 1. Chrome Glass Containers (Top Bar, Sidebar, Bottom Action Bar)
        GlassRenderUtil.drawGlassPanel(graphics, layout.topBar().x(), layout.topBar().y(), layout.topBar().width(), layout.topBar().height(), cfg, true);
        GlassRenderUtil.drawGlassPanel(graphics, layout.sidebar().x(), layout.sidebar().y(), layout.sidebar().width(), layout.sidebar().height(), cfg, false);
        GlassRenderUtil.drawGlassPanel(graphics, layout.bottomBar().x(), layout.bottomBar().y(), layout.bottomBar().width(), layout.bottomBar().height(), cfg, true);

        // Top Bar Brand Mark
        int bx = layout.topBar().x() + 8;
        int by = layout.topBar().y() + (layout.topBar().height() - 14) / 2;
        GlassRenderUtil.drawRoundedFill(graphics, bx, by, 14, 14, AnimationUtil.withAlpha(theme.accentSecondary(), 210));
        GlassRenderUtil.drawRoundedBorder(graphics, bx, by, 14, 14, theme.accentPrimary(), 0xCCFFFFFF);
        graphics.centeredText(this.font, "◈", bx + 7, by + 3, 0xFFFFFFFF);
        graphics.text(this.font, PackStudioConstants.MOD_NAME, bx + 19, by + 3, theme.textPrimary(), false);

        // Sidebar Active Preset Pill at bottom
        if (layout.sidebar().height() >= 165) {
            int pillH = 34;
            int px = layout.sidebar().x() + 6;
            int py = layout.sidebar().bottom() - pillH - 6;
            int pw = layout.sidebar().width() - 12;
            GlassRenderUtil.drawGlassCard(graphics, px, py, pw, pillH, cfg, false, 0.0F);
            graphics.text(this.font, "Active Preset", px + 6, py + 5, theme.textMuted(), false);
            String activeName = bootstrap.profileManager()
                    .findById(cfg.activeProfileId())
                    .map(PackProfile::name)
                    .orElse(I18n.get("packstudio.overview.none_active"));
            graphics.text(this.font, TextFormatUtil.ellipsize(this.font, activeName, pw - 12), px + 6, py + 18, theme.accentPrimary(), false);
        }

        // 2. Active Center Panel + Optional Right Details Panel
        int effectiveMouseX = activeModal != null ? -1000 : mouseX;
        int effectiveMouseY = activeModal != null ? -1000 : mouseY;

        // Glass search shell, painted before the widget pass so vanilla EditBox text/selection/cursor
        // stay on top. Keeps the field independent of per-version widget render hooks.
        if (searchBox != null) {
            searchBox.renderShell(graphics, effectiveMouseX, effectiveMouseY);
        }

        switch (activeTab) {
            case OVERVIEW -> overviewPanel.render(graphics, layout.mainContent(), effectiveMouseX, effectiveMouseY, delta);
            case ALL_PACKS, FAVORITES -> packLibraryPanel.render(graphics, layout.mainContent(), effectiveMouseX, effectiveMouseY, delta);
            case PROFILES -> profilesPanel.render(graphics, layout.mainContent(), effectiveMouseX, effectiveMouseY, delta);
            case SETTINGS -> settingsPanel.render(graphics, layout.mainContent(), effectiveMouseX, effectiveMouseY, delta);
        }

        if (layout.showDetailsPanel()) {
            packDetailsPanel.render(graphics, layout.detailsPanel(), effectiveMouseX, effectiveMouseY, delta);
        }

        // 3. Bottom Action Bar Status Text
        boolean dirty = bootstrap.packRepositoryManager().hasUnsavedChanges();
        boolean reloading = bootstrap.packRepositoryManager().isApplyingReload();
        int statusDotCol = reloading ? 0xFF38BDF8 : (dirty ? 0xFFFBBF24 : 0xFF34D399);
        String statusMsg = reloading
                ? I18n.get("packstudio.action.applying")
                : (dirty ? I18n.get("packstudio.action.unsaved") : "Pack selection synchronized");
        int sx = layout.bottomBar().x() + 10;
        int sy = layout.bottomBar().y() + (layout.bottomBar().height() - 8) / 2;
        graphics.fill(sx, sy + 2, sx + 5, sy + 7, statusDotCol);
        int maxStatusW = Math.max(30, (resetButton != null ? resetButton.getX() : layout.bottomBar().right()) - (sx + 10) - 8);
        graphics.text(this.font, TextFormatUtil.ellipsize(this.font, statusMsg, maxStatusW), sx + 9, sy, dirty ? 0xFFFDE68A : theme.textMuted(), false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int effectiveMouseX = activeModal != null ? -1000 : mouseX;
        int effectiveMouseY = activeModal != null ? -1000 : mouseY;

        // Calls extractBackground(...) once and then renders all registered top/sidebar/bottom widgets
        super.extractRenderState(graphics, effectiveMouseX, effectiveMouseY, delta);

        // Modal Dialog & Non-blocking Liquid Glass Notifications on top
        PackStudioConfig cfg = bootstrap.configManager().config();
        if (activeModal != null) {
            activeModal.render(graphics, this.font, this.width, this.height, mouseX, mouseY, delta);
        }
        GlassNotificationOverlay.render(graphics, this.font, this.width, cfg, bootstrap.notifications());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (activeModal != null) {
            return activeModal.mouseClicked(event, doubleClick);
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (layout == null) {
            return false;
        }
        if (layout.showDetailsPanel() && packDetailsPanel.mouseClicked(event, doubleClick, layout.detailsPanel())) {
            return true;
        }
        return switch (activeTab) {
            case OVERVIEW -> overviewPanel.mouseClicked(event, layout.mainContent());
            case ALL_PACKS, FAVORITES -> packLibraryPanel.mouseClicked(event, layout.mainContent());
            case PROFILES -> profilesPanel.mouseClicked(event, doubleClick, layout.mainContent());
            case SETTINGS -> settingsPanel.mouseClicked(event, layout.mainContent());
        };
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (activeModal != null || layout == null) {
            return false;
        }
        return switch (activeTab) {
            case OVERVIEW -> overviewPanel.mouseScrolled(mouseX, mouseY, scrollY, layout.mainContent());
            case ALL_PACKS, FAVORITES -> packLibraryPanel.mouseScrolled(mouseX, mouseY, scrollY, layout.mainContent());
            case PROFILES -> profilesPanel.mouseScrolled(mouseX, mouseY, scrollY, layout.mainContent());
            case SETTINGS -> settingsPanel.mouseScrolled(mouseX, mouseY, scrollY, layout.mainContent());
        };
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (activeModal != null) {
            return activeModal.keyPressed(event);
        }
        if (activeTab == NavigationTab.SETTINGS && settingsPanel.isListeningForKey()) {
            return settingsPanel.keyPressed(event);
        }
        if (event.isEscape()) {
            requestCloseScreen();
            return true;
        }
        if (searchBox != null && searchBox.isFocused() && searchBox.keyPressed(event)) {
            return true;
        }
        if ((activeTab == NavigationTab.ALL_PACKS || activeTab == NavigationTab.FAVORITES)
                && (searchBox == null || !searchBox.isFocused())
                && packLibraryPanel.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (activeModal != null) {
            return activeModal.charTyped(event);
        }
        return super.charTyped(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            MinecraftCompatProvider.get().setScreen(this.minecraft, this.parentScreen);
        }
    }

    public Screen parentScreen() {
        return parentScreen;
    }

    // === StudioPanelHost Implementation ===

    @Override
    public PackStudioBootstrap bootstrap() {
        return bootstrap;
    }

    @Override
    public Font font() {
        return this.font;
    }

    @Override
    public NavigationTab activeTab() {
        return activeTab;
    }

    @Override
    public void switchTab(NavigationTab tab) {
        if (tab == null || tab == this.activeTab) {
            return;
        }
        this.activeTab = tab;
        this.packLibraryPanel.resetScroll();
        recomputeVisiblePacks();
        buildScreenWidgets();
    }

    @Override
    public ViewMode viewMode() {
        return viewMode;
    }

    @Override
    public void setViewMode(ViewMode mode) {
        if (mode != null) {
            this.viewMode = mode;
            syncActionButtons();
        }
    }

    @Override
    public FilterMode filterMode() {
        return filterMode;
    }

    @Override
    public void setFilterMode(FilterMode mode) {
        if (mode != null) {
            this.filterMode = mode;
            this.packLibraryPanel.resetScroll();
            recomputeVisiblePacks();
            if (layout != null && layout.showDetailsPanel()) {
                packDetailsPanel.layoutButtons(layout.detailsPanel());
            }
        }
    }

    @Override
    public SortMode sortMode() {
        return sortMode;
    }

    @Override
    public void setSortMode(SortMode mode) {
        if (mode != null) {
            this.sortMode = mode;
            recomputeVisiblePacks();
            syncActionButtons();
        }
    }

    @Override
    public String searchQuery() {
        return searchQuery;
    }

    @Override
    public List<PackEntry> visiblePacks() {
        return cachedVisiblePacks;
    }

    @Override
    public Optional<PackEntry> selectedPack() {
        return bootstrap.packRepositoryManager().findPack(selectedPackId);
    }

    @Override
    public void selectPack(String packId) {
        this.selectedPackId = packId == null ? "" : packId;
        if (layout != null && layout.showDetailsPanel()) {
            packDetailsPanel.layoutButtons(layout.detailsPanel());
        }
    }

    @Override
    public void rebuildAndRefreshState() {
        recomputeVisiblePacks();
        syncActionButtons();
        if (layout != null && layout.showDetailsPanel()) {
            packDetailsPanel.layoutButtons(layout.detailsPanel());
        }
    }

    @Override
    public void openModal(GlassModalDialog modal) {
        this.activeModal = modal;
        if (this.activeModal != null) {
            this.activeModal.layout(this.font, this.width, this.height);
        }
    }

    @Override
    public void closeModal() {
        this.activeModal = null;
    }

    @Override
    public void triggerApplyProfile(String profileId) {
        bootstrap.profileApplier().applyProfile(profileId, false, this::rebuildAndRefreshState);
        syncActionButtons();
    }
}
