package com.packstudio.compat.impl;

import com.packstudio.compat.MinecraftCompat;
import java.io.IOException;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

/**
 * Compatibility implementation for the Minecraft 26.3 API family (baseline release).
 *
 * <p>Behaviour is identical to the original PackStudio 26.3 implementation: {@code Minecraft.gui} for
 * screen navigation and {@code Pack.openMetadata()} for {@code pack.mcmeta} / {@code pack.png}, which
 * only opens the pack directories that are actually needed.
 */
public final class MinecraftCompatImpl implements MinecraftCompat {
    private static final SystemToast.SystemToastId TOAST_ID = new SystemToast.SystemToastId(3800L);

    @Override
    public Screen currentScreen(Minecraft client) {
        return client.gui.screen();
    }

    @Override
    public void setScreen(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }

    @Override
    public InputStream openRootResource(Pack pack, String path) throws IOException {
        PackMetadataResources metadata = pack.openMetadata();
        if (metadata == null) {
            return null;
        }
        IoSupplier<InputStream> supplier = metadata.getRootResource(path);
        if (supplier == null) {
            metadata.close();
            return null;
        }
        return new MetadataInputStream(supplier.get(), metadata);
    }

    @Override
    public boolean showSystemToast(String title, String message) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null || client.gui.toastManager() == null) {
            return false;
        }
        SystemToast.addOrUpdate(
                client.gui.toastManager(),
                TOAST_ID,
                Component.literal(title),
                Component.literal(message)
        );
        return true;
    }

    /** Closes the owning {@link PackMetadataResources} together with the resource stream. */
    private static final class MetadataInputStream extends InputStream {
        private final InputStream delegate;
        private final PackMetadataResources owner;

        private MetadataInputStream(InputStream delegate, PackMetadataResources owner) {
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
