package com.packstudio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Version-neutral screen access for the shared JUnit suite, compiled per Minecraft API family.
 *
 * <p>26.1 keeps the current screen directly on {@code Minecraft}; the 26.2 and 26.3 variants use
 * {@code Minecraft.gui}.
 */
public final class TestClientAccess {
    private TestClientAccess() {
    }

    public static Screen screen(Minecraft client) {
        return client.screen;
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }
}
