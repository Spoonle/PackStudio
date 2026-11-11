package com.packstudio.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.client.screen.PackStudioScreen;
import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.model.PackStudioConfig;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;

/**
 * Client-side Fabric entrypoint for PackStudio on Minecraft 26.3.
 * Registers SDL3-compatible {@link KeyMapping}s and non-intrusive screen launch buttons.
 */
public final class PackStudioClient implements ClientModInitializer {
    private static KeyMapping openStudioMapping;
    private static KeyMapping quickSwitchProfileMapping;
    private static boolean initialized = false;

    @Override
    public void onInitializeClient() {
        PackStudioBootstrap bootstrap = PackStudioBootstrap.get();
        bootstrap.initialize();

        if (!initialized) {
            registerKeyMappings(bootstrap.configManager().config());
            registerTickHandlers();
            registerScreenEntryButtons(bootstrap);
            initialized = true;
        }
    }

    private static void registerKeyMappings(PackStudioConfig config) {
        KeyMapping.Category category = KeyMapping.Category.register(PackStudioConstants.id("general"));

        InputConstants.Key openKey = InputConstants.getKey(config.openStudioKey());
        int openCode = openKey == InputConstants.UNKNOWN ? InputConstants.KEY_P : openKey.getValue();
        openStudioMapping = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.packstudio.open", InputConstants.Type.KEYBOARD, openCode, category)
        );

        InputConstants.Key quickKey = InputConstants.getKey(config.quickSwitchProfileKey());
        int quickCode = quickKey == InputConstants.UNKNOWN ? InputConstants.KEY_O : quickKey.getValue();
        quickSwitchProfileMapping = KeyMappingHelper.registerKeyMapping(
                new KeyMapping("key.packstudio.quick_switch", InputConstants.Type.KEYBOARD, quickCode, category)
        );
    }

    private static void registerTickHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (openStudioMapping != null) {
                while (openStudioMapping.consumeClick()) {
                    openPackStudio(client, client.gui.screen());
                }
            }
            if (quickSwitchProfileMapping != null) {
                while (quickSwitchProfileMapping.consumeClick()) {
                    onQuickSwitchRequested(client);
                }
            }
        });
    }

    private static void registerScreenEntryButtons(PackStudioBootstrap bootstrap) {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, _) -> {
            PackStudioConfig cfg = bootstrap.configManager().config();
            if (screen instanceof TitleScreen && cfg.showTitleScreenButton()) {
                attachLaunchButton(client, screen, scaledWidth - 82, 6, 76, 20);
            } else if ((screen instanceof OptionsScreen || screen instanceof PackSelectionScreen) && cfg.showOptionsButton()) {
                attachLaunchButton(client, screen, scaledWidth - 84, 6, 78, 20);
            }
        });
    }

    private static void attachLaunchButton(Minecraft client, Screen screen, int x, int y, int width, int height) {
        List<AbstractWidget> widgets = Screens.getWidgets(screen);
        Button button = Button.builder(
                        Component.translatable("packstudio.button.open"),
                        _ -> openPackStudio(client, screen)
                )
                .pos(Math.max(4, x), Math.max(4, y))
                .size(width, height)
                .tooltip(Tooltip.create(Component.translatable("packstudio.button.open.tooltip")))
                .build();
        widgets.add(button);
    }

    public static void openPackStudio(Minecraft client, Screen parent) {
        if (client == null || client.gui == null) {
            return;
        }
        if (client.gui.screen() instanceof PackStudioScreen) {
            return;
        }
        client.gui.setScreen(new PackStudioScreen(parent));
    }

    private static void onQuickSwitchRequested(Minecraft client) {
        if (client != null) {
            boolean outsideStudio = !(client.gui.screen() instanceof PackStudioScreen);
            PackStudioBootstrap.get().profileApplier().cycleNextProfile(outsideStudio);
        }
    }

    public static KeyMapping openStudioMapping() {
        return openStudioMapping;
    }

    public static KeyMapping quickSwitchProfileMapping() {
        return quickSwitchProfileMapping;
    }

    public static void syncConfiguredKeyMappings(PackStudioConfig config) {
        if (openStudioMapping != null) {
            openStudioMapping.setKey(InputConstants.getKey(config.openStudioKey()));
        }
        if (quickSwitchProfileMapping != null) {
            quickSwitchProfileMapping.setKey(InputConstants.getKey(config.quickSwitchProfileKey()));
        }
        KeyMapping.resetMapping();
    }
}
