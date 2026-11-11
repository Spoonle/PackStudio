package com.packstudio.manager;

import com.packstudio.core.PackStudioConstants;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.storage.JsonStorage;
import com.packstudio.storage.JsonStorage.JsonStorageException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Manages persistent configuration in {@code config/packstudio/config.json} with schema validation
 * and graceful corrupted-file recovery.
 */
public final class ConfigManager {
    private final Supplier<Path> configFileSupplier;
    private PackStudioConfig config = new PackStudioConfig();
    private boolean recoveredFromCorruption = false;

    public ConfigManager() {
        this(PackStudioConstants::configFile);
    }

    public ConfigManager(Supplier<Path> configFileSupplier) {
        this.configFileSupplier = configFileSupplier;
    }

    public synchronized PackStudioConfig load() {
        this.recoveredFromCorruption = false;
        Path path = configFileSupplier.get();
        if (!Files.exists(path)) {
            this.config = new PackStudioConfig();
            save();
            return this.config;
        }

        try {
            PackStudioConfig loaded = JsonStorage.readFile(path, PackStudioConfig.class);
            loaded.sanitize();
            this.config = loaded;
        } catch (JsonStorageException e) {
            PackStudioConstants.LOGGER.error("Corrupted PackStudio config detected at {}; backing up and resetting.", path, e);
            JsonStorage.backupCorruptedFile(path);
            this.config = new PackStudioConfig();
            this.recoveredFromCorruption = true;
            save();
        }
        return this.config;
    }

    public synchronized boolean save() {
        Path path = configFileSupplier.get();
        try {
            config.sanitize();
            JsonStorage.writeAtomic(path, config);
            return true;
        } catch (JsonStorageException e) {
            PackStudioConstants.LOGGER.error("Failed to save PackStudio configuration to {}", path, e);
            return false;
        }
    }

    public synchronized PackStudioConfig resetToDefaults() {
        boolean keepFavorites = false;
        var preservedActiveProfile = config.activeProfileId();
        var favorites = config.favoritePackIds();
        this.config = new PackStudioConfig();
        this.config.setActiveProfileId(preservedActiveProfile);
        if (!keepFavorites) {
            for (String fav : favorites) {
                this.config.toggleFavorite(fav);
            }
        }
        save();
        return this.config;
    }

    public synchronized PackStudioConfig config() {
        return config;
    }

    public synchronized boolean consumeCorruptionNotice() {
        boolean flag = recoveredFromCorruption;
        recoveredFromCorruption = false;
        return flag;
    }
}
