package com.packstudio;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.junit.jupiter.api.Assumptions;

/**
 * Reflection bridge for headless Minecraft 26.3 test simulation.
 * Keeps all test sources strictly compliant with official Mojang 26.3 signatures so
 * {@code ./gradlew compileTestJava} succeeds against standard Fabric Loom artifacts.
 */
final class TestHarnessBridge {
    private TestHarnessBridge() {
    }

    static Minecraft requireHeadlessClient(Path tempGameDir) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                Assumptions.assumeTrue(false, "Headless client instance not running");
                return null;
            }
            Method reset = Minecraft.class.getMethod("resetForGameDir", Path.class);
            reset.invoke(client, tempGameDir);
            return client;
        } catch (ReflectiveOperationException | IllegalStateException | ExceptionInInitializerError | NoClassDefFoundError e) {
            Assumptions.assumeTrue(false, "Skipping headless client integration test outside simulation harness: " + e);
            return null;
        }
    }

    static GuiGraphicsExtractor createGraphics(int guiWidth, int guiHeight, int scale) {
        try {
            Constructor<GuiGraphicsExtractor> ctor = GuiGraphicsExtractor.class.getConstructor(int.class, int.class, int.class);
            return ctor.newInstance(guiWidth, guiHeight, scale);
        } catch (ReflectiveOperationException e) {
            Assumptions.assumeTrue(false, "Headless GuiGraphicsExtractor surface constructor unavailable");
            return null;
        }
    }

    static void resizeWindow(Minecraft client, int pixelWidth, int pixelHeight, int guiScale) {
        try {
            Method m = client.getWindow().getClass().getMethod("packstudio$setDimensions", int.class, int.class, int.class);
            m.invoke(client.getWindow(), pixelWidth, pixelHeight, guiScale);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to resize simulated window", e);
        }
    }

    static void simulateKeyMappingPress(KeyMapping mapping) {
        try {
            Method m = mapping.getClass().getMethod("packstudio$simulatePress");
            m.invoke(mapping);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to simulate key press", e);
        }
    }

    static void setFailNextReload(Minecraft client, boolean fail) {
        try {
            Field f = client.getClass().getField("failNextReload");
            f.setBoolean(client, fail);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to toggle reload failure simulation", e);
        }
    }
}
