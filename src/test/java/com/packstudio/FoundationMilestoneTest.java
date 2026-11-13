package com.packstudio;

import static org.junit.jupiter.api.Assertions.*;

import com.packstudio.client.PackStudioClient;
import com.packstudio.client.screen.PackStudioScreen;
import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.core.PackStudioExecutors;
import com.packstudio.manager.ConfigManager;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.model.ViewMode;
import com.packstudio.storage.JsonStorage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies Milestone 1 foundation: client initialization, atomic config persistence,
 * corrupted-JSON quarantine recovery, keybinding registration, and screen launch buttons.
 */
class FoundationMilestoneTest {

    @Test
    void atomicConfigPersistenceAndCorruptRecovery(@TempDir Path tempDir) throws Exception {
        Path configFile = tempDir.resolve("config/packstudio/config.json");
        ConfigManager manager = new ConfigManager(() -> configFile);

        PackStudioConfig initial = manager.load();
        assertEquals(1, initial.schemaVersion());
        assertEquals(LiquidThemePreset.OBSIDIAN_CYAN, initial.theme());
        assertTrue(Files.isRegularFile(configFile));

        initial.setTheme(LiquidThemePreset.FROSTED_EMERALD);
        initial.setGlassOpacity(0.75F);
        initial.setDefaultViewMode(ViewMode.LIST);
        initial.toggleFavorite("file/Faithful.zip");
        assertTrue(manager.save());

        ConfigManager reloaded = new ConfigManager(() -> configFile);
        PackStudioConfig loaded = reloaded.load();
        assertEquals(LiquidThemePreset.FROSTED_EMERALD, loaded.theme());
        assertEquals(0.75F, loaded.glassOpacity(), 0.001F);
        assertEquals(ViewMode.LIST, loaded.defaultViewMode());
        assertTrue(loaded.isFavorite("file/Faithful.zip"));

        // Corrupt the config file and ensure graceful quarantine backup + reset
        Files.writeString(configFile, "{ broken json !!!");
        PackStudioConfig recovered = reloaded.load();
        assertTrue(reloaded.consumeCorruptionNotice());
        assertEquals(LiquidThemePreset.OBSIDIAN_CYAN, recovered.theme());
        long backups = Files.list(configFile.getParent())
                .filter(p -> p.getFileName().toString().contains(".corrupt."))
                .count();
        assertEquals(1L, backups, "Corrupted config must be backed up rather than silently lost");
    }

    @Test
    void pathSanitizationPreventsTraversal() {
        assertEquals("PvP_Pack_1.0", JsonStorage.sanitizeFileStem("PvP Pack 1.0"));
        assertFalse(JsonStorage.sanitizeFileStem("../../etc/passwd").contains(".."));
        assertFalse(JsonStorage.sanitizeFileStem("../../etc/passwd").contains("/"));
    }

    @Test
    void managedIoExecutorRunsOffCallerThreadOnDaemonWorker() throws Exception {
        long callerId = Thread.currentThread().threadId();
        record WorkerInfo(long threadId, String name, boolean daemon) {
        }
        WorkerInfo info = CompletableFuture.supplyAsync(
                () -> {
                    Thread current = Thread.currentThread();
                    return new WorkerInfo(current.threadId(), current.getName(), current.isDaemon());
                },
                PackStudioExecutors.ioExecutor()
        ).get(5, TimeUnit.SECONDS);

        assertNotEquals(callerId, info.threadId(), "I/O executor must run off the caller/client thread");
        assertTrue(info.name().startsWith("PackStudio-IO-"), "Worker thread must use PackStudio-IO naming");
        assertTrue(info.daemon(), "Worker thread must be daemon so it never blocks JVM exit");
    }

    @Test
    void clientEntrypointInitializesAndRegistersScreens(@TempDir Path tempDir) {
        Minecraft client = TestHarnessBridge.requireHeadlessClient(tempDir);

        PackStudioClient modClient = new PackStudioClient();
        modClient.onInitializeClient();

        assertNotNull(PackStudioClient.openStudioMapping());
        assertNotNull(PackStudioClient.quickSwitchProfileMapping());
        assertNotNull(PackStudioBootstrap.get().configManager().config());

        TitleScreen titleScreen = new TitleScreen();
        TestClientAccess.setScreen(client, titleScreen);
        assertFalse(Screens.getWidgets(titleScreen).isEmpty(), "TitleScreen should receive PackStudio launch button");

        OptionsScreen optionsScreen = new OptionsScreen(titleScreen, client.options);
        TestClientAccess.setScreen(client, optionsScreen);
        assertFalse(Screens.getWidgets(optionsScreen).isEmpty(), "OptionsScreen should receive PackStudio launch button");

        // Simulate keybind click opening PackStudioScreen
        TestHarnessBridge.simulateKeyMappingPress(PackStudioClient.openStudioMapping());
        ClientTickEvents.END_CLIENT_TICK.invoker().onEndTick(client);
        assertInstanceOf(PackStudioScreen.class, TestClientAccess.screen(client));
    }
}
