package com.packstudio.compat;

import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.packs.repository.Pack;

/**
 * The complete set of Minecraft client APIs that differ between stable 26.X releases.
 *
 * <p>Everything else PackStudio uses (screen events, {@code GuiGraphicsExtractor} drawing,
 * {@code Button}/{@code EditBox} widgets, {@code PackRepository}, key mappings, i18n) is stable across
 * 26.1 – 26.3 and is therefore referenced directly by the shared sources.
 *
 * <p>Exactly one implementation from {@code com.packstudio.compat.impl} is compiled into each release
 * jar (see {@code minecraft_family} in {@code gradle.properties}), so no version can silently use
 * another version's API at runtime.
 */
public interface MinecraftCompat {

    /**
     * The screen currently displayed by the client.
     *
     * <p>26.1 exposes this as {@code Minecraft.screen}; 26.2 and later as {@code Minecraft.gui.screen()}.
     */
    Screen currentScreen(Minecraft client);

    /**
     * Replaces the current screen.
     *
     * <p>26.1: {@code Minecraft.setScreen(…)}; 26.2+: {@code Minecraft.gui.setScreen(…)}.
     */
    void setScreen(Minecraft client, Screen screen);

    /**
     * Opens a root-level file ({@code pack.mcmeta}, {@code pack.png}) from a resource pack.
     *
     * <p>26.1/26.2 read these through {@code Pack.open()} → {@code PackResources.getRootResource(…)};
     * 26.3 through {@code Pack.openMetadata()} → {@code PackMetadataResources.getRootResource(…)},
     * which also only opens the pack directories that are needed.
     *
     * @return a stream the caller must close, or {@code null} when the pack has no such file
     */
    InputStream openRootResource(Pack pack, String path) throws IOException;

    /**
     * Shows a native Minecraft system toast (used for one-click profile switching outside the studio).
     *
     * @return {@code true} when the toast was handed to the client
     */
    boolean showSystemToast(String title, String message);
}
