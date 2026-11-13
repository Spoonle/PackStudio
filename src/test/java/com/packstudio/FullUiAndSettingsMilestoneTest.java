package com.packstudio;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.client.PackStudioClient;
import com.packstudio.client.screen.PackStudioScreen;
import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.model.FilterMode;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.NavigationTab;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.model.SortMode;
import com.packstudio.model.ViewMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies Milestone 5 full Minimalist UI screens, panels, keyboard navigation,
 * unsaved-changes guard, and persistent SDL3 Settings rebinding.
 */
class FullUiAndSettingsMilestoneTest {

    private static void createZip(Path zipPath, String desc) throws Exception {
        String mcmeta = "{\"pack\":{\"description\":\"" + desc + "\",\"min_format\":[75,0],\"max_format\":[75,0],\"author\":\"StudioDev\"}}";
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write(mcmeta.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    @Test
    void fullScreenRendersAllTabsViewsAndPersistsSettings(@TempDir Path tempDir) throws Exception {
        Minecraft client = TestHarnessBridge.requireHeadlessClient(tempDir);
        createZip(client.getResourcePackDirectory().resolve("Prism_HD.zip"), "High-clarity textures");
        createZip(client.getResourcePackDirectory().resolve("Competitive_PvP.zip"), "Clean low-fire HUD");

        PackStudioClient mod = new PackStudioClient();
        mod.onInitializeClient();

        PackStudioBootstrap bootstrap = PackStudioBootstrap.get();
        bootstrap.packRepositoryManager().refreshFromRepository(false);
        bootstrap.packRepositoryManager().inspectMetadataSync();
        bootstrap.profileManager().createFromCurrentSelection("Default Studio", bootstrap.packRepositoryManager());

        TitleScreen parent = new TitleScreen();
        TestHarnessBridge.resizeWindow(client, 1708, 960, 2);
        PackStudioScreen screen = new PackStudioScreen(parent);
        TestClientAccess.setScreen(client, screen);

        GuiGraphicsExtractor graphics = TestHarnessBridge.createGraphics(854, 480, 2);

        // Render ALL_PACKS in GRID & LIST modes across sort/filter combinations
        screen.extractRenderState(graphics, 260, 180, 0.16F);
        screen.setViewMode(ViewMode.LIST);
        screen.setSortMode(SortMode.ALPHABETICAL_ASC);
        screen.setFilterMode(FilterMode.ALL);
        screen.extractRenderState(graphics, 260, 180, 0.16F);

        // Keyboard navigation + Space toggle stages a pack change
        screen.selectPack("file/Prism_HD.zip");
        assertTrue(screen.keyPressed(new KeyEvent(InputConstants.KEY_SPACE, 0, 0)));
        assertTrue(bootstrap.packRepositoryManager().hasUnsavedChanges());

        // Escape with unsaved changes opens confirmation modal instead of losing work immediately
        assertTrue(screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0)));
        assertSame(screen, TestClientAccess.screen(client), "Screen must prompt before discarding unsaved changes");
        screen.closeModal();
        bootstrap.packRepositoryManager().discardStagedChanges();
        screen.rebuildAndRefreshState();

        // Render OVERVIEW, PROFILES, FAVORITES, SETTINGS tabs cleanly
        for (NavigationTab tab : NavigationTab.values()) {
            screen.switchTab(tab);
            screen.extractRenderState(graphics, 320, 200, 0.16F);
        }

        // Settings SDL3 keybind update & theme persistence
        PackStudioConfig cfg = bootstrap.configManager().config();
        cfg.setTheme(LiquidThemePreset.MIDNIGHT_INDIGO);
        cfg.setOpenStudioKey("key.keyboard.k");
        assertTrue(bootstrap.configManager().save());
        PackStudioClient.syncConfiguredKeyMappings(cfg);
        assertEquals("key.keyboard.k", PackStudioClient.openStudioMapping().saveString());

        // Escape when clean returns to parent screen
        assertTrue(screen.keyPressed(new KeyEvent(InputConstants.KEY_ESCAPE, 0, 0)));
        assertSame(parent, TestClientAccess.screen(client));
    }

    private static void assertSame(Object expected, Object actual) {
        if (expected != actual) {
            throw new AssertionError("Expected same instance <" + expected + "> but got <" + actual + ">");
        }
    }

    private static void assertSame(Object expected, Object actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " — Expected <" + expected + "> but got <" + actual + ">");
        }
    }
}
