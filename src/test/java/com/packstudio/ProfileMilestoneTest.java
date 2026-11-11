package com.packstudio;

import static org.junit.jupiter.api.Assertions.*;

import com.packstudio.client.NotificationManager;
import com.packstudio.compatibility.ProfileValidator;
import com.packstudio.manager.ConfigManager;
import com.packstudio.manager.PackIconCache;
import com.packstudio.manager.PackRepositoryManager;
import com.packstudio.manager.ProfileApplier;
import com.packstudio.manager.ProfileManager;
import com.packstudio.model.PackProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies Milestone 4 Profile Manager & One-Click Switching: schema-versioned storage,
 * CRUD, duplicate/rename/import/export, missing-pack reporting without silent replacement,
 * absolute-path rejection, corrupt profile quarantine, and reload-failure rollback.
 */
class ProfileMilestoneTest {

    private static void writeZipPack(Path zipPath, String desc) throws Exception {
        String mcmeta = "{\"pack\":{\"description\":\"" + desc + "\",\"min_format\":[75,0],\"max_format\":[75,0]}}";
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write(mcmeta.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    @Test
    void profileCrudImportExportMissingPackAndTransactionalSwitching(@TempDir Path tempDir) throws Exception {
        Minecraft client = TestHarnessBridge.requireHeadlessClient(tempDir);
        Path packsDir = client.getResourcePackDirectory();

        writeZipPack(packsDir.resolve("PvP_Overlay.zip"), "Low fire and clean crosshair");
        writeZipPack(packsDir.resolve("Builder_Plus.zip"), "Connected stone and bricks");
        writeZipPack(packsDir.resolve("Sky_Atmosphere.zip"), "Custom skybox");

        Path configFile = tempDir.resolve("config/packstudio/config.json");
        Path profilesDir = tempDir.resolve("config/packstudio/profiles");
        Path exportsDir = tempDir.resolve("config/packstudio/exports");

        ConfigManager configManager = new ConfigManager(() -> configFile);
        configManager.load();
        PackRepositoryManager repoManager = new PackRepositoryManager(configManager, new PackIconCache());
        repoManager.refreshFromRepository(false);
        NotificationManager notifications = new NotificationManager();
        ProfileManager profileManager = new ProfileManager(() -> profilesDir, () -> exportsDir, configManager);
        ProfileApplier applier = new ProfileApplier(profileManager, repoManager, configManager, notifications);

        profileManager.loadAll();
        assertTrue(profileManager.profiles().isEmpty());

        // 1. Create "PvP" preset with [PvP_Overlay, Sky_Atmosphere]
        repoManager.setPackEnabled("file/Sky_Atmosphere.zip", true);
        repoManager.setPackEnabled("file/PvP_Overlay.zip", true);
        repoManager.applyStagedChanges(null).join();

        PackProfile pvp = profileManager.createFromCurrentSelection("PvP", repoManager);
        assertEquals(1, pvp.schemaVersion());
        assertEquals("PvP", pvp.name());
        assertEquals("file/PvP_Overlay.zip", pvp.enabledPackIdsOrdered().get(0));
        assertEquals("file/Sky_Atmosphere.zip", pvp.enabledPackIdsOrdered().get(1));

        // 2. Create "Builder" preset with [Builder_Plus]
        repoManager.stageResetToDefaults();
        repoManager.setPackEnabled("file/Builder_Plus.zip", true);
        repoManager.applyStagedChanges(null).join();
        PackProfile builder = profileManager.createFromCurrentSelection("Builder", repoManager);

        // 3. Rename & duplicate checks (including duplicate-name guard)
        assertTrue(profileManager.renameProfile(builder.id(), "PvP").isPresent(), "Must reject duplicate name");
        assertTrue(profileManager.renameProfile(builder.id(), "Builder Studio").isEmpty());
        PackProfile copy = profileManager.duplicateProfile(pvp.id()).orElseThrow();
        assertEquals("PvP (Copy)", copy.name());
        assertTrue(profileManager.deleteProfile(copy.id()));

        // 4. Export & re-import (duplicate ID/name disambiguated cleanly)
        Path exportedFile = profileManager.exportProfileToFile(pvp.id()).orElseThrow();
        assertTrue(Files.isRegularFile(exportedFile));
        ProfileManager.ImportOutcome imported = profileManager.importProfileFromPath(exportedFile);
        assertTrue(imported.success());
        assertTrue(imported.renamedDueToDuplicate());
        assertNotEquals(pvp.id(), imported.profile().id());
        profileManager.deleteProfile(imported.profile().id());

        // 5. Reject malformed JSON & absolute-path pack identities
        assertFalse(profileManager.importProfileFromJson("{not valid json").success());
        String badAbsoluteJson = """
                {
                  "schemaVersion": 1,
                  "id": "bad-abs",
                  "name": "Bad Path Profile",
                  "createdAt": 1000,
                  "modifiedAt": 1000,
                  "packs": [{"packId": "/Users/alice/minecraft/resourcepacks/Pack.zip", "displayNameHint": "Pack", "enabled": true}]
                }
                """;
        assertFalse(profileManager.importProfileFromJson(badAbsoluteJson).success(), "Absolute paths must be rejected");

        // 6. Corrupt profile file quarantine on reload
        Files.writeString(profilesDir.resolve("broken_profile.json"), "<<<corrupted>>>");
        profileManager.loadAll();
        assertEquals(1, profileManager.corruptedNotices().size());
        assertEquals(2, profileManager.profiles().size());

        // 7. Missing pack handling: delete Sky_Atmosphere.zip from disk, then apply PvP profile
        Files.delete(packsDir.resolve("Sky_Atmosphere.zip"));
        ProfileApplier.SwitchOutcome outcome = applier.applyProfile(pvp.id(), true, null).join();
        assertTrue(outcome.success());
        assertEquals(pvp.id(), configManager.config().activeProfileId());
        assertEquals(1, outcome.missingPacks().size(), "Deleted pack must be reported as missing");
        assertEquals("file/Sky_Atmosphere.zip", outcome.missingPacks().getFirst().packId());
        // Remaining valid pack PvP_Overlay.zip must be applied without silent substitution!
        assertEquals("file/PvP_Overlay.zip", List.copyOf(client.getResourcePackRepository().getSelectedIds()).getLast());

        var health = ProfileValidator.evaluateAgainstLibrary(pvp, repoManager.allPacks());
        assertEquals(1, health.missingPacks().size());

        // 8. Reload failure during one-click switch must preserve activeProfileId & prior packs
        TestHarnessBridge.setFailNextReload(client, true);
        ProfileApplier.SwitchOutcome failedSwitch = applier.applyProfile(builder.id(), false, null).join();
        assertFalse(failedSwitch.success());
        assertEquals(pvp.id(), configManager.config().activeProfileId(), "Active profile must not change on failed reload");
        assertEquals("file/PvP_Overlay.zip", List.copyOf(client.getResourcePackRepository().getSelectedIds()).getLast());
    }
}
