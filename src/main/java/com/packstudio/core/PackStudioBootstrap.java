package com.packstudio.core;

import com.packstudio.client.NotificationManager;
import com.packstudio.manager.ConfigManager;
import com.packstudio.manager.PackIconCache;
import com.packstudio.manager.PackRepositoryManager;
import com.packstudio.manager.ProfileApplier;
import com.packstudio.manager.ProfileManager;
import net.minecraft.client.resources.language.I18n;

/**
 * Central client service locator and lifecycle coordinator for PackStudio.
 */
public final class PackStudioBootstrap {
    private static PackStudioBootstrap instance;

    private final ConfigManager configManager;
    private final NotificationManager notificationManager;
    private final PackIconCache iconCache;
    private final PackRepositoryManager packRepositoryManager;
    private final ProfileManager profileManager;
    private final ProfileApplier profileApplier;

    private PackStudioBootstrap() {
        this.configManager = new ConfigManager();
        this.notificationManager = new NotificationManager();
        this.iconCache = new PackIconCache();
        this.packRepositoryManager = new PackRepositoryManager(this.configManager, this.iconCache);
        this.profileManager = new ProfileManager(this.configManager);
        this.profileApplier = new ProfileApplier(
                this.profileManager,
                this.packRepositoryManager,
                this.configManager,
                this.notificationManager
        );
    }

    public static synchronized PackStudioBootstrap get() {
        if (instance == null) {
            instance = new PackStudioBootstrap();
        }
        return instance;
    }

    public synchronized void initialize() {
        configManager.load();
        profileManager.loadAll();

        if (configManager.consumeCorruptionNotice()) {
            notificationManager.warning(
                    PackStudioConstants.MOD_NAME,
                    I18n.get("packstudio.error.config_corrupted")
            );
        }
        for (var corrupt : profileManager.corruptedNotices()) {
            notificationManager.warning(
                    PackStudioConstants.MOD_NAME,
                    I18n.get("packstudio.error.profile_corrupted", corrupt.fileName())
            );
        }

        PackStudioConstants.LOGGER.info(
                "{} initialized (config schema v{}, {} profiles loaded).",
                PackStudioConstants.MOD_NAME,
                configManager.config().schemaVersion(),
                profileManager.profiles().size()
        );
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public NotificationManager notifications() {
        return notificationManager;
    }

    public PackIconCache iconCache() {
        return iconCache;
    }

    public PackRepositoryManager packRepositoryManager() {
        return packRepositoryManager;
    }

    public ProfileManager profileManager() {
        return profileManager;
    }

    public ProfileApplier profileApplier() {
        return profileApplier;
    }
}
