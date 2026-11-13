package com.packstudio;

import static org.junit.jupiter.api.Assertions.*;

import com.packstudio.client.NotificationManager;
import com.packstudio.client.screen.AdaptiveLayout;
import com.packstudio.client.screen.components.GlassButton;
import com.packstudio.client.screen.components.GlassModalDialog;
import com.packstudio.client.screen.components.GlassNotificationOverlay;
import com.packstudio.client.screen.components.GlassTabButton;
import com.packstudio.model.NavigationTab;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

/**
 * Verifies Milestone 2 UI Foundation: Minimalist primitives, adaptive multi-scale layout,
 * animation toggle behavior, and modal/notification components.
 */
class UiFoundationMilestoneTest {

    @Test
    void adaptiveLayoutNeverOverlapsAcrossGuiScales() {
        int[][] viewports = {
                {380, 220}, // GUI Scale 4x / small window
                {427, 240}, // GUI Scale 3x on 1280x720
                {640, 360}, // GUI Scale 2x on 1280x720
                {960, 540}, // GUI Scale 2x on 1920x1080
                {1280, 720} // GUI Scale 1x
        };

        for (int[] vp : viewports) {
            for (NavigationTab tab : NavigationTab.values()) {
                AdaptiveLayout layout = AdaptiveLayout.compute(vp[0], vp[1], tab);
                assertTrue(layout.topBar().width() > 0 && layout.topBar().height() > 0);
                assertTrue(layout.sidebar().y() >= layout.topBar().bottom());
                assertTrue(layout.mainContent().x() >= layout.sidebar().right());
                assertTrue(layout.mainContent().bottom() <= layout.bottomBar().y());
                if (layout.showDetailsPanel()) {
                    assertTrue(layout.detailsPanel().x() >= layout.mainContent().right());
                    assertTrue(layout.detailsPanel().right() <= vp[0]);
                }
            }
        }
    }

    @Test
    void animationToggleControlsTransitions() {
        float instant = AnimationUtil.approach(0.0F, 1.0F, 0.35F, 0.5F, false);
        assertEquals(1.0F, instant, 0.0001F);

        float animated = AnimationUtil.approach(0.0F, 1.0F, 0.35F, 0.5F, true);
        assertTrue(animated > 0.0F && animated < 1.0F);
    }

    @Test
    void minimalistComponentsRenderAndHandleInputCleanly(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        Minecraft client = TestHarnessBridge.requireHeadlessClient(tempDir);
        PackStudioConfig cfg = new PackStudioConfig();
        GuiGraphicsExtractor graphics = TestHarnessBridge.createGraphics(640, 360, 2);

        GlassRenderUtil.drawBackdrop(graphics, 640, 360, cfg);
        GlassRenderUtil.drawGlassPanel(graphics, 12, 12, 200, 140, cfg, false);
        GlassRenderUtil.drawGlassCard(graphics, 20, 20, 180, 48, cfg, true, 0.5F);

        GlassButton primary = new GlassButton(24, 80, 100, 20, Component.literal("Apply"), GlassButton.Variant.PRIMARY, _ -> {});
        primary.extractRenderState(graphics, 30, 85, 0.2F);

        GlassTabButton tabBtn = new GlassTabButton(14, 110, 120, 22, NavigationTab.ALL_PACKS, true, () -> 12, _ -> {});
        tabBtn.extractRenderState(graphics, 20, 115, 0.2F);

        NotificationManager notifications = new NotificationManager();
        notifications.info("PackStudio", "Minimalist design ready");
        GlassNotificationOverlay.render(graphics, client.font, 640, cfg, notifications);

        AtomicReference<String> submitted = new AtomicReference<>();
        GlassModalDialog modal = GlassModalDialog.promptText(
                "Create Profile",
                "Enter a name for your new resource pack preset.",
                "",
                "PvP Preset",
                48,
                "Create",
                submitted::set,
                () -> {}
        );
        modal.layout(client.font, 640, 360);
        modal.charTyped(new CharacterEvent('P'));
        modal.charTyped(new CharacterEvent('v'));
        modal.charTyped(new CharacterEvent('P'));
        modal.render(graphics, client.font, 640, 360, 320, 180, 0.16F);
        modal.keyPressed(new KeyEvent(com.mojang.blaze3d.platform.InputConstants.KEY_RETURN, 0, 0));
        assertEquals("PvP", submitted.get());

        assertEquals("Faithful 64x", TextFormatUtil.cleanText("\u00A7bFaithful \u00A7f64x"));
    }
}
