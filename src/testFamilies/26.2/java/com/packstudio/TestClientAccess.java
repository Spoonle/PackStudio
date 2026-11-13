package com.packstudio;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Version-neutral screen access for the shared JUnit suite, compiled per Minecraft API family.
 *
 * <p>This is the 26.2 variant: {@code Minecraft.gui} exposes the current screen.
 */
public final class TestClientAccess {
    private TestClientAccess() {
    }

    public static Screen screen(Minecraft client) {
        return client.gui.screen();
    }

    public static void setScreen(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }
}
