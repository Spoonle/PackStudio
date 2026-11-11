package com.packstudio.manager;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import com.packstudio.core.PackStudioConstants;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.util.Util;

/**
 * Asynchronous pack icon loader and GPU texture cache backed by Minecraft's {@link TextureManager}.
 * Decodes PNG bytes on {@link Util#backgroundExecutor()} and registers {@link DynamicTexture}s strictly
 * on the Minecraft client thread.
 */
public final class PackIconCache {
    public static final Identifier FALLBACK_ICON =
            PackStudioConstants.id("textures/gui/default_pack_icon.png");

    private final Map<String, Identifier> iconByPackId = new ConcurrentHashMap<>();
    private final Set<String> loadingIds = ConcurrentHashMap.newKeySet();
    private volatile boolean fallbackRegistered = false;

    public Identifier iconFor(Pack pack) {
        ensureFallbackTextureRegistered();
        if (pack == null) {
            return FALLBACK_ICON;
        }
        String packId = pack.getId();
        Identifier existing = iconByPackId.get(packId);
        if (existing != null) {
            return existing;
        }

        if (loadingIds.add(packId)) {
            Minecraft client = Minecraft.getInstance();
            CompletableFuture.supplyAsync(() -> decodePackPng(pack), Util.backgroundExecutor())
                    .thenAcceptAsync(nativeImage -> {
                        try {
                            if (nativeImage != null && client != null && client.getTextureManager() != null) {
                                Identifier texId = iconIdentifier(packId);
                                client.getTextureManager().register(texId, new DynamicTexture(texId::toString, nativeImage));
                                iconByPackId.put(packId, texId);
                            } else {
                                if (nativeImage != null) {
                                    nativeImage.close();
                                }
                                iconByPackId.put(packId, FALLBACK_ICON);
                            }
                        } finally {
                            loadingIds.remove(packId);
                        }
                    }, client != null ? client : Runnable::run);
        }
        return FALLBACK_ICON;
    }

    /**
     * Synchronously preloads icons during tests or controlled refresh passes on the client thread.
     */
    public void preloadNowOnClientThread(Iterable<Pack> packs) {
        ensureFallbackTextureRegistered();
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }
        TextureManager tm = client.getTextureManager();
        for (Pack pack : packs) {
            if (pack == null || iconByPackId.containsKey(pack.getId())) {
                continue;
            }
            NativeImage img = decodePackPng(pack);
            if (img != null) {
                Identifier texId = iconIdentifier(pack.getId());
                tm.register(texId, new DynamicTexture(texId::toString, img));
                iconByPackId.put(pack.getId(), texId);
            } else {
                iconByPackId.put(pack.getId(), FALLBACK_ICON);
            }
        }
    }

    public void invalidateAll() {
        iconByPackId.clear();
        loadingIds.clear();
    }

    private void ensureFallbackTextureRegistered() {
        if (fallbackRegistered) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getTextureManager() == null) {
            return;
        }
        synchronized (this) {
            if (fallbackRegistered) {
                return;
            }
            try (InputStream stream = PackIconCache.class.getClassLoader()
                    .getResourceAsStream("assets/packstudio/textures/gui/default_pack_icon.png")) {
                if (stream != null) {
                    NativeImage img = NativeImage.read(stream);
                    client.getTextureManager().register(FALLBACK_ICON, new DynamicTexture(FALLBACK_ICON::toString, img));
                }
            } catch (Exception e) {
                PackStudioConstants.LOGGER.debug("Could not pre-register fallback pack icon", e);
            }
            fallbackRegistered = true;
        }
    }

    private static NativeImage decodePackPng(Pack pack) {
        try (PackMetadataResources meta = pack.openMetadata()) {
            IoSupplier<InputStream> supplier = meta.getRootResource("pack.png");
            if (supplier == null) {
                return null;
            }
            try (InputStream in = supplier.get()) {
                return NativeImage.read(in);
            }
        } catch (Exception e) {
            PackStudioConstants.LOGGER.debug("Pack '{}' has no readable pack.png: {}", pack.getId(), e.getMessage());
            return null;
        }
    }

    private static Identifier iconIdentifier(String packId) {
        StringBuilder sanitized = new StringBuilder(packId.length());
        String lower = packId.toLowerCase(java.util.Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.') {
                sanitized.append(c);
            } else {
                sanitized.append('_');
            }
        }
        String hash = Hashing.sha256().hashString(packId, StandardCharsets.UTF_8).toString().substring(0, 12);
        return PackStudioConstants.id("pack_icon/" + sanitized + "_" + hash);
    }
}
