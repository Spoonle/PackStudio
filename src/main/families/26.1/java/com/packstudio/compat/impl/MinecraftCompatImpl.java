package com.packstudio.compat.impl;

import com.packstudio.compat.MinecraftCompat;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Compatibility implementation for the Minecraft 26.1 API family (26.1, 26.1.1, 26.1.2).
 *
 * <p>Differences from the 26.2+ family: the client screen lives directly on {@code Minecraft} and
 * resource-pack root files are read from {@code Pack.open()} → {@code PackResources} (the
 * {@code PackMetadataResources} type used by 26.3 does not exist yet).
 */
public final class MinecraftCompatImpl implements MinecraftCompat {
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(3800L);

    @Override
    public Screen currentScreen(Minecraft client) {
        return client.screen;
    }

    @Override
    public void setScreen(Minecraft client, Screen screen) {
        client.setScreen(screen);
    }

    @Override
    public InputStream openRootResource(Pack pack, String path) throws IOException {
        PackResources resources = pack.open();
        if (resources == null) {
            return null;
        }
        IoSupplier<InputStream> supplier = resources.getRootResource(path);
        if (supplier == null) {
            resources.close();
            return null;
        }
        return new ClosingInputStream(supplier.get(), resources);
    }

    /**
     * The GUI holder that exposes {@code toastManager()} in 26.2+ does not exist in 26.1, where the
     * toast manager is reached from the client itself. Both shapes are probed reflectively so this
     * class keeps compiling on every 26.1 patch release; when neither is available the caller still
     * shows the in-screen Liquid Glass notification.
     */
    @Override
    public boolean showSystemToast(String title, String message) {
        Minecraft client = Minecraft.getInstance();
        ToastManager toasts = resolveToastManager(client);
        if (toasts == null) {
            return false;
        }
        SystemToast.addOrUpdate(toasts, TOAST_ID, Component.literal(title), Component.literal(message));
        return true;
    }

    private static ToastManager resolveToastManager(Minecraft client) {
        if (client == null) {
            return null;
        }
        for (String name : new String[] {"getToastManager", "toastManager"}) {
            try {
                Method method = client.getClass().getMethod(name);
                if (ToastManager.class.isAssignableFrom(method.getReturnType())) {
                    return (ToastManager) method.invoke(client);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // Try the next shape.
            }
        }
        try {
            Object gui = client.getClass().getField("gui").get(client);
            Method method = gui.getClass().getMethod("toastManager");
            return (ToastManager) method.invoke(gui);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Closes the owning {@link PackResources} together with the resource stream, mirroring the
     * try-with-resources lifetime that 26.3's {@code PackMetadataResources} provides.
     */
    private static final class ClosingInputStream extends InputStream {
        private final InputStream delegate;
        private final PackResources owner;

        private ClosingInputStream(InputStream delegate, PackResources owner) {
            this.delegate = delegate;
            this.owner = owner;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                owner.close();
            }
        }
    }
}
