package com.packstudio;

import static org.junit.jupiter.api.Assertions.*;

import com.packstudio.compatibility.CompatibilityAnalyzer;
import com.packstudio.manager.ConfigManager;
import com.packstudio.manager.PackIconCache;
import com.packstudio.manager.PackRepositoryManager;
import com.packstudio.model.FilterMode;
import com.packstudio.model.PackCompatibilityStatus;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackSourceKind;
import com.packstudio.model.SortMode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies Milestone 3 Resource Pack Library: real ZIP & folder discovery from PackRepository,
 * honest pack.mcmeta inspection, icon loading, search/filter/sort, priority reordering,
 * native reload application, and automatic rollback on reload failure.
 */
class ResourcePackLibraryMilestoneTest {

    private static byte[] makeTinyPng(int argb) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                img.setRGB(x, y, argb);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static void createZipPack(Path zipPath, String mcmetaJson, byte[] pngBytes) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write(mcmetaJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            if (pngBytes != null) {
                zos.putNextEntry(new ZipEntry("pack.png"));
                zos.write(pngBytes);
                zos.closeEntry();
            }
        }
    }

    @Test
    void discoversRealZipAndFolderPacksAndAppliesOrRollsBackReloads(@TempDir Path tempDir) throws Exception {
        Minecraft client = TestHarnessBridge.requireHeadlessClient(tempDir);
        Path packsDir = client.getResourcePackDirectory();

        // 1. Compatible 26.3 ZIP pack with author + icon
        createZipPack(
                packsDir.resolve("Faithful_64x.zip"),
                """
                {
                  "pack": {
                    "description": "Crisp 64x textures for 26.3",
                    "min_format": [75, 0],
                    "max_format": [75, 0],
                    "author": "Faithful Team"
                  }
                }
                """,
                makeTinyPng(0xFF38BDF8)
        );

        // 2. Folder pack without author field (must NOT fabricate author!)
        Path folderPack = packsDir.resolve("Dark_UI_Glass");
        Files.createDirectories(folderPack);
        Files.writeString(folderPack.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "Minimalist dark HUD",
                    "min_format": [75, 0],
                    "max_format": [75, 0]
                  }
                }
                """);

        // 3. Older pack format ZIP (pack_format 15 -> OLDER_FORMAT)
        createZipPack(
                packsDir.resolve("Legacy_PvP_1.20.zip"),
                """
                {
                  "pack": {
                    "description": "Short swords for PvP",
                    "pack_format": 15
                  }
                }
                """,
                null
        );

        // 4. Future pack format ZIP ([99, 0] -> NEWER_FORMAT)
        createZipPack(
                packsDir.resolve("Experimental_Future.zip"),
                """
                {
                  "pack": {
                    "description": "Future pack format test",
                    "min_format": [99, 0],
                    "max_format": [99, 0]
                  }
                }
                """,
                null
        );

        ConfigManager configManager = new ConfigManager(() -> tempDir.resolve("config/packstudio/config.json"));
        configManager.load();
        PackIconCache iconCache = new PackIconCache();
        PackRepositoryManager repoManager = new PackRepositoryManager(configManager, iconCache);

        repoManager.refreshFromRepository(false);
        repoManager.inspectMetadataSync();

        List<PackEntry> all = repoManager.allPacks();
        // Expect vanilla + fabric + high_contrast + 4 user packs = 7 packs
        assertEquals(7, all.size());

        PackEntry faithful = repoManager.findPack("file/Faithful_64x.zip").orElseThrow();
        assertEquals(PackSourceKind.ZIP_ARCHIVE, faithful.sourceKind());
        assertEquals("Faithful Team", faithful.author().orElseThrow());
        assertEquals("75.0", faithful.packFormatLabel().orElseThrow());
        assertEquals(PackCompatibilityStatus.COMPATIBLE, faithful.compatibility());

        PackEntry darkUi = repoManager.findPack("file/Dark_UI_Glass").orElseThrow();
        assertEquals(PackSourceKind.FOLDER, darkUi.sourceKind());
        assertTrue(darkUi.author().isEmpty(), "Missing author must never be fabricated");

        PackEntry legacyPvp = repoManager.findPack("file/Legacy_PvP_1.20.zip").orElseThrow();
        assertEquals(PackCompatibilityStatus.OLDER_FORMAT, legacyPvp.compatibility());
        assertEquals("15", legacyPvp.packFormatLabel().orElseThrow());

        PackEntry futurePack = repoManager.findPack("file/Experimental_Future.zip").orElseThrow();
        assertEquals(PackCompatibilityStatus.NEWER_FORMAT, futurePack.compatibility());

        // Preload icons & verify custom texture vs fallback icon
        iconCache.preloadNowOnClientThread(client.getResourcePackRepository().getAvailablePacks());
        Identifier faithfulIcon = iconCache.iconFor(faithful.nativePack());
        assertNotEquals(PackIconCache.FALLBACK_ICON, faithfulIcon);
        assertEquals(PackIconCache.FALLBACK_ICON, iconCache.iconFor(legacyPvp.nativePack()));

        // Enable packs & reorder priority (#1 highest priority -> #2 -> pinned base packs at bottom)
        assertTrue(repoManager.setPackEnabled("file/Faithful_64x.zip", true));
        assertTrue(repoManager.setPackEnabled("file/Dark_UI_Glass", true));
        assertTrue(repoManager.hasUnsavedChanges());
        assertEquals("file/Dark_UI_Glass", repoManager.stagedEnabledTopDownIds().get(0));
        assertEquals("file/Faithful_64x.zip", repoManager.stagedEnabledTopDownIds().get(1));

        // Move Faithful_64x up above Dark_UI_Glass
        assertTrue(repoManager.movePackPriority("file/Faithful_64x.zip", -1));
        assertEquals("file/Faithful_64x.zip", repoManager.stagedEnabledTopDownIds().get(0));
        assertEquals("file/Dark_UI_Glass", repoManager.stagedEnabledTopDownIds().get(1));
        // Cannot move Dark_UI_Glass below pinned base packs
        assertFalse(repoManager.movePackPriority("file/Dark_UI_Glass", +1));

        // Search & Filter checks
        assertEquals(1, repoManager.queryPacks("faithful", FilterMode.ALL, SortMode.ALPHABETICAL_ASC).size());
        assertEquals(4, repoManager.queryPacks("", FilterMode.ENABLED, SortMode.PRIORITY).size());
        assertEquals(2, repoManager.queryPacks("", FilterMode.INCOMPATIBLE, SortMode.PRIORITY).size());

        // Apply staged changes -> native PackRepository stores bottom-to-top order (Faithful_64x last = highest priority)
        assertTrue(repoManager.applyStagedChanges(null).join());
        assertFalse(repoManager.hasUnsavedChanges());
        List<String> committedNative = List.copyOf(client.getResourcePackRepository().getSelectedIds());
        assertEquals("file/Faithful_64x.zip", committedNative.getLast());
        assertEquals("file/Dark_UI_Glass", committedNative.get(committedNative.size() - 2));

        // Simulate reload failure -> must roll back cleanly to committed configuration
        repoManager.setPackEnabled("file/Legacy_PvP_1.20.zip", true);
        TestHarnessBridge.setFailNextReload(client, true);
        assertFalse(repoManager.applyStagedChanges(null).join(), "Failed reload must report false");
        assertEquals(
                committedNative,
                List.copyOf(client.getResourcePackRepository().getSelectedIds()),
                "Previous pack configuration must be restored on reload failure"
        );

        var report = CompatibilityAnalyzer.analyzeLibrary(repoManager.allPacks());
        assertEquals("75.0", report.runtimePackFormat());
        assertEquals(0, report.incompatibleEnabledCount());
    }
}
