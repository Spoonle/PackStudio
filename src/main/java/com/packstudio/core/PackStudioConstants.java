package com.packstudio.core;

import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared constants, directory layout, and schema versions for PackStudio.
 */
public final class PackStudioConstants {
    public static final String MOD_ID = "packstudio";
    public static final String MOD_NAME = "PackStudio";
    public static final String TAGLINE = "Your resource packs. Your presets. Your world.";

    /**
     * Human-readable mod version, read from {@code fabric.mod.json}. Packaging adds the targeted
     * Minecraft version (for example {@code 1.0.0+mc26.2}), so the UI never claims a version the jar
     * was not built for.
     */
    public static final String MOD_VERSION = resolveModVersion();

    public static final int CONFIG_SCHEMA_VERSION = 1;
    public static final int PROFILE_SCHEMA_VERSION = 1;

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    private PackStudioConstants() {
    }

    private static String resolveModVersion() {
        return FabricLoader.getInstance()
                .getModContainer(MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("dev");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    public static Path configRoot() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    }

    public static Path configFile() {
        return configRoot().resolve("config.json");
    }

    public static Path profilesDirectory() {
        return configRoot().resolve("profiles");
    }

    public static Path exportsDirectory() {
        return configRoot().resolve("exports");
    }
}
