package com.packstudio.compat;

import com.packstudio.core.PackStudioConstants;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Locates the single {@link MinecraftCompat} implementation compiled into this jar.
 *
 * <p>The implementation class name is fixed, so a jar built for one Minecraft version can never fall
 * back to another version's API: if the class is missing the jar is defective and fails loudly at
 * startup instead of behaving unpredictably.
 */
public final class MinecraftCompatProvider {
    private static final String IMPLEMENTATION = "com.packstudio.compat.impl.MinecraftCompatImpl";

    private static volatile MinecraftCompat instance;

    private MinecraftCompatProvider() {
    }

    public static MinecraftCompat get() {
        MinecraftCompat local = instance;
        if (local != null) {
            return local;
        }
        synchronized (MinecraftCompatProvider.class) {
            if (instance == null) {
                instance = load();
            }
            return instance;
        }
    }

    private static MinecraftCompat load() {
        String minecraftVersion = FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        try {
            Class<?> type = Class.forName(IMPLEMENTATION);
            return (MinecraftCompat) type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            throw new IllegalStateException(
                    "PackStudio " + PackStudioConstants.MOD_VERSION
                            + " was not built correctly for Minecraft " + minecraftVersion
                            + " (missing " + IMPLEMENTATION + ')',
                    e
            );
        }
    }
}
