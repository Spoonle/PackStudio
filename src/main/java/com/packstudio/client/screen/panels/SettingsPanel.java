package com.packstudio.client.screen.panels;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.client.PackStudioClient;
import com.packstudio.client.screen.AdaptiveLayout.Rect;
import com.packstudio.client.screen.components.GlassButton;
import com.packstudio.client.screen.components.GlassModalDialog;
import com.packstudio.compat.MinecraftCompatProvider;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.manager.ConfigManager;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.util.Mth;

/**
 * Functional persistent settings panel for PackStudio, including theme selection,
 * glass opacity controls, default view mode, smooth animation toggle, screen button toggles,
 * SDL3-compatible keyboard shortcut rebinding, and settings reset.
 */
public final class SettingsPanel {
    private enum ListeningKeyTarget {
        NONE,
        OPEN_STUDIO,
        QUICK_SWITCH
    }

    private record ControlZone(Rect rect, Runnable onClick) {
    }

    private final StudioPanelHost host;
    private final List<ControlZone> controlZones = new ArrayList<>();
    private ListeningKeyTarget listeningTarget = ListeningKeyTarget.NONE;
    private double scrollOffset = 0.0;
    private int totalContentHeight = 0;

    public SettingsPanel(StudioPanelHost host) {
        this.host = host;
    }

    public boolean isListeningForKey() {
        return listeningTarget != ListeningKeyTarget.NONE;
    }

    public void render(GuiGraphicsExtractor graphics, Rect bounds, int mouseX, int mouseY, float delta) {
        controlZones.clear();
        ConfigManager cm = host.bootstrap().configManager();
        PackStudioConfig cfg = cm.config();
        LiquidThemePreset theme = cfg.theme();
        Font font = host.font();

        GlassRenderUtil.drawGlassPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), cfg, false);

        int pad = 10;
        graphics.text(font, I18n.get("packstudio.settings.title"), bounds.x() + pad, bounds.y() + pad, theme.textPrimary(), false);

        Rect viewport = new Rect(bounds.x() + 6, bounds.y() + 26, Math.max(80, bounds.width() - 12), Math.max(40, bounds.bottom() - (bounds.y() + 26) - 6));
        int maxScroll = Math.max(0, totalContentHeight - viewport.height());
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maxScroll);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());

        int rowX = viewport.x() + 4;
        int rowW = viewport.width() - 10;
        int cursorY = viewport.y() + 2 - (int) Math.round(scrollOffset);
        int startY = cursorY;

        // 1. Minimalist Theme
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.theme"),
                "Select dark translucent surface palette and accent tint.",
                cfg.theme().label(),
                false, false, cfg, mouseX, mouseY,
                () -> {
                    cfg.setTheme(cfg.theme().next());
                    cm.save();
                    host.rebuildAndRefreshState();
                }
        );

        // 2. Panel Opacity (- / % / +)
        cursorY = drawOpacityRow(graphics, font, viewport, rowX, cursorY, rowW, cfg, cm, mouseX, mouseY);

        // 3. Default Library View (Grid / List)
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.default_view"),
                "Initial view mode when opening PackStudio.",
                cfg.defaultViewMode().displayName().getString(),
                false, false, cfg, mouseX, mouseY,
                () -> {
                    cfg.setDefaultViewMode(cfg.defaultViewMode().toggle());
                    host.setViewMode(cfg.defaultViewMode());
                    cm.save();
                    host.rebuildAndRefreshState();
                }
        );

        // 4. Smooth UI Animations
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.animations"),
                "Subtle hover and panel transitions (zero blur shaders).",
                cfg.animationsEnabled() ? "Enabled" : "Disabled",
                cfg.animationsEnabled(), false, cfg, mouseX, mouseY,
                () -> {
                    cfg.setAnimationsEnabled(!cfg.animationsEnabled());
                    cm.save();
                }
        );

        // 5. Title Screen Launch Button
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.title_button"),
                "Show quick PackStudio button on Minecraft's Title Screen.",
                cfg.showTitleScreenButton() ? "Shown" : "Hidden",
                cfg.showTitleScreenButton(), false, cfg, mouseX, mouseY,
                () -> {
                    cfg.setShowTitleScreenButton(!cfg.showTitleScreenButton());
                    cm.save();
                }
        );

        // 6. Options & Pack Screen Launch Button
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.options_button"),
                "Show PackStudio button on Options and vanilla Resource Packs screens.",
                cfg.showOptionsButton() ? "Shown" : "Hidden",
                cfg.showOptionsButton(), false, cfg, mouseX, mouseY,
                () -> {
                    cfg.setShowOptionsButton(!cfg.showOptionsButton());
                    cm.save();
                }
        );

        // 7. Open PackStudio Keyboard Shortcut (SDL3 InputConstants)
        String openKeyLabel = listeningTarget == ListeningKeyTarget.OPEN_STUDIO
                ? "> Press Key (ESC Cancel) <"
                : formatKeyDisplay(cfg.openStudioKey());
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.keybind_open"),
                "Global client shortcut to open PackStudio.",
                openKeyLabel,
                listeningTarget == ListeningKeyTarget.OPEN_STUDIO, false, cfg, mouseX, mouseY,
                () -> listeningTarget = (listeningTarget == ListeningKeyTarget.OPEN_STUDIO)
                        ? ListeningKeyTarget.NONE
                        : ListeningKeyTarget.OPEN_STUDIO
        );

        // 8. Quick Switch Profile Shortcut
        String quickKeyLabel = listeningTarget == ListeningKeyTarget.QUICK_SWITCH
                ? "> Press Key (ESC Cancel) <"
                : formatKeyDisplay(cfg.quickSwitchProfileKey());
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.keybind_quick"),
                "Cycles to your next saved resource pack preset.",
                quickKeyLabel,
                listeningTarget == ListeningKeyTarget.QUICK_SWITCH, false, cfg, mouseX, mouseY,
                () -> listeningTarget = (listeningTarget == ListeningKeyTarget.QUICK_SWITCH)
                        ? ListeningKeyTarget.NONE
                        : ListeningKeyTarget.QUICK_SWITCH
        );

        // 9. Reset Settings to Defaults
        cursorY = drawSettingRow(
                graphics, font, viewport, rowX, cursorY, rowW,
                I18n.get("packstudio.settings.reset"),
                "Restores default Minimalist appearance and keybindings (keeps profiles & favorites).",
                "Reset Defaults",
                false, true, cfg, mouseX, mouseY,
                this::confirmResetSettings
        );

        totalContentHeight = (cursorY - startY) + 4;
        graphics.disableScissor();

        int updatedMax = Math.max(0, totalContentHeight - viewport.height());
        if (updatedMax > 0) {
            int trackH = viewport.height() - 4;
            int thumbH = Math.max(18, Math.round(trackH * ((float) viewport.height() / (float) totalContentHeight)));
            int thumbY = viewport.y() + 2 + Math.round((trackH - thumbH) * (float) (scrollOffset / updatedMax));
            graphics.fill(bounds.right() - 4, viewport.y() + 2, bounds.right() - 2, viewport.bottom() - 2, 0x22FFFFFF);
            graphics.fill(bounds.right() - 4, thumbY, bounds.right() - 2, thumbY + thumbH, AnimationUtil.withAlpha(theme.accentPrimary(), 185));
        }
    }

    private int drawSettingRow(
            GuiGraphicsExtractor graphics,
            Font font,
            Rect viewport,
            int x,
            int y,
            int width,
            String title,
            String description,
            String buttonLabel,
            boolean highlightBtn,
            boolean dangerBtn,
            PackStudioConfig cfg,
            int mouseX,
            int mouseY,
            Runnable onClick
    ) {
        int rowH = 34;
        if (y + rowH >= viewport.y() - 4 && y <= viewport.bottom() + 4) {
            LiquidThemePreset theme = cfg.theme();
            GlassRenderUtil.drawGlassCard(graphics, x, y, width, rowH, cfg, false, 0.0F);

            int btnW = Math.min(Math.max(76, font.width(buttonLabel) + 14), Math.max(76, width / 2 - 8));
            Rect btnRect = new Rect(x + width - btnW - 7, y + 8, btnW, 18);
            boolean hov = viewport.contains(mouseX, mouseY) && btnRect.contains(mouseX, mouseY);

            int bg;
            int border;
            int fg;
            if (dangerBtn) {
                bg = hov ? 0xCC7F1D1D : 0x8845141B;
                border = 0xAAF87171;
                fg = 0xFFFECACA;
            } else if (highlightBtn) {
                bg = AnimationUtil.withAlpha(theme.accentSecondary(), hov ? 225 : 180);
                border = AnimationUtil.withAlpha(theme.accentPrimary(), 215);
                fg = 0xFFFFFFFF;
            } else {
                bg = AnimationUtil.withAlpha(theme.elevatedRgb(), hov ? 220 : 165);
                border = hov ? AnimationUtil.withAlpha(theme.accentPrimary(), 180) : 0x35FFFFFF;
                fg = theme.textPrimary();
            }

            GlassRenderUtil.drawRoundedFill(graphics, btnRect.x(), btnRect.y(), btnRect.width(), btnRect.height(), bg);
            GlassRenderUtil.drawRoundedBorder(graphics, btnRect.x(), btnRect.y(), btnRect.width(), btnRect.height(), border, border);
            graphics.centeredText(
                    font,
                    TextFormatUtil.ellipsize(font, buttonLabel, btnW - 8),
                    btnRect.x() + btnW / 2,
                    btnRect.y() + 5,
                    fg
            );

            int textMaxW = Math.max(40, btnRect.x() - (x + 8) - 8);
            graphics.text(font, TextFormatUtil.ellipsize(font, title, textMaxW), x + 8, y + 6, theme.textPrimary(), false);
            graphics.text(font, TextFormatUtil.ellipsize(font, description, textMaxW), x + 8, y + 19, theme.textMuted(), false);

            controlZones.add(new ControlZone(clip(btnRect, viewport), () -> {
                AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
                onClick.run();
            }));
        }
        return y + rowH + 5;
    }

    private int drawOpacityRow(
            GuiGraphicsExtractor graphics,
            Font font,
            Rect viewport,
            int x,
            int y,
            int width,
            PackStudioConfig cfg,
            ConfigManager cm,
            int mouseX,
            int mouseY
    ) {
        int rowH = 34;
        if (y + rowH >= viewport.y() - 4 && y <= viewport.bottom() + 4) {
            LiquidThemePreset theme = cfg.theme();
            GlassRenderUtil.drawGlassCard(graphics, x, y, width, rowH, cfg, false, 0.0F);

            int plusX = x + width - 25;
            int barW = 54;
            int barX = plusX - barW - 4;
            int minusX = barX - 22;

            Rect minusRect = new Rect(minusX, y + 8, 18, 18);
            Rect plusRect = new Rect(plusX, y + 8, 18, 18);
            Rect barRect = new Rect(barX, y + 8, barW, 18);

            drawSmallBtn(graphics, font, minusRect, "−", theme, viewport.contains(mouseX, mouseY) && minusRect.contains(mouseX, mouseY));
            drawSmallBtn(graphics, font, plusRect, "+", theme, viewport.contains(mouseX, mouseY) && plusRect.contains(mouseX, mouseY));

            GlassRenderUtil.drawRoundedFill(graphics, barRect.x(), barRect.y(), barRect.width(), barRect.height(), 0x770A101C);
            float norm = (cfg.glassOpacity() - 0.45F) / (0.96F - 0.45F);
            int fillW = Math.max(4, Math.round((barRect.width() - 4) * Mth.clamp(norm, 0.0F, 1.0F)));
            graphics.fill(barRect.x() + 2, barRect.y() + 2, barRect.x() + 2 + fillW, barRect.bottom() - 2,
                    AnimationUtil.withAlpha(theme.accentSecondary(), 200));
            GlassRenderUtil.drawRoundedBorder(graphics, barRect.x(), barRect.y(), barRect.width(), barRect.height(), 0x30FFFFFF, 0x40FFFFFF);
            String pct = Math.round(cfg.glassOpacity() * 100.0F) + "%";
            graphics.centeredText(font, pct, barRect.x() + barW / 2, barRect.y() + 5, 0xFFFFFFFF);

            int textMaxW = Math.max(40, minusX - (x + 8) - 8);
            graphics.text(font, TextFormatUtil.ellipsize(font, I18n.get("packstudio.settings.opacity"), textMaxW), x + 8, y + 6, theme.textPrimary(), false);
            graphics.text(font, TextFormatUtil.ellipsize(font, "Adjust Minimalist surface translucency (45%–96%).", textMaxW), x + 8, y + 19, theme.textMuted(), false);

            controlZones.add(new ControlZone(clip(minusRect, viewport), () -> {
                AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
                cfg.setGlassOpacity(cfg.glassOpacity() - 0.05F);
                cm.save();
            }));
            controlZones.add(new ControlZone(clip(plusRect, viewport), () -> {
                AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
                cfg.setGlassOpacity(cfg.glassOpacity() + 0.05F);
                cm.save();
            }));
        }
        return y + rowH + 5;
    }

    private static void drawSmallBtn(GuiGraphicsExtractor graphics, Font font, Rect r, String label, LiquidThemePreset theme, boolean hov) {
        int bg = AnimationUtil.withAlpha(theme.elevatedRgb(), hov ? 225 : 165);
        int border = hov ? AnimationUtil.withAlpha(theme.accentPrimary(), 180) : 0x35FFFFFF;
        GlassRenderUtil.drawRoundedFill(graphics, r.x(), r.y(), r.width(), r.height(), bg);
        GlassRenderUtil.drawRoundedBorder(graphics, r.x(), r.y(), r.width(), r.height(), border, border);
        graphics.centeredText(font, label, r.x() + r.width() / 2, r.y() + 5, theme.textPrimary());
    }

    private void confirmResetSettings() {
        host.openModal(GlassModalDialog.confirm(
                I18n.get("packstudio.settings.reset"),
                "Reset theme, opacity, view mode, animations, and shortcuts back to defaults?",
                "Reset",
                GlassButton.Variant.DANGER,
                () -> {
                    PackStudioConfig reset = host.bootstrap().configManager().resetToDefaults();
                    PackStudioClient.syncConfiguredKeyMappings(reset);
                    host.setViewMode(reset.defaultViewMode());
                    host.closeModal();
                    host.bootstrap().notifications().info(
                            PackStudioConstants.MOD_NAME,
                            I18n.get("packstudio.settings.reset_done")
                    );
                    host.rebuildAndRefreshState();
                },
                host::closeModal
        ));
    }

    private static String formatKeyDisplay(String translationName) {
        InputConstants.Key key = InputConstants.getKey(translationName);
        if (key == null || key == InputConstants.UNKNOWN) {
            return "Unbound";
        }
        return key.getDisplayName().getString();
    }

    private static Rect clip(Rect r, Rect vp) {
        int x1 = Math.max(r.x(), vp.x());
        int y1 = Math.max(r.y(), vp.y());
        int x2 = Math.min(r.right(), vp.right());
        int y2 = Math.min(r.bottom(), vp.bottom());
        return new Rect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    public boolean mouseClicked(MouseButtonEvent event, Rect bounds) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            listeningTarget = ListeningKeyTarget.NONE;
            return false;
        }
        for (ControlZone zone : controlZones) {
            if (zone.rect().width() > 0 && zone.rect().height() > 0 && zone.rect().contains(event.x(), event.y())) {
                zone.onClick().run();
                return true;
            }
        }
        listeningTarget = ListeningKeyTarget.NONE;
        return false;
    }

    public boolean keyPressed(KeyEvent event) {
        if (listeningTarget == ListeningKeyTarget.NONE) {
            return false;
        }
        if (event.isEscape()) {
            listeningTarget = ListeningKeyTarget.NONE;
            return true;
        }
        InputConstants.Key captured = MinecraftCompatProvider.get()
                .keyboardInputType()
                .getOrCreate(event.key());
        if (captured != null && captured != InputConstants.UNKNOWN) {
            PackStudioConfig cfg = host.bootstrap().configManager().config();
            if (listeningTarget == ListeningKeyTarget.OPEN_STUDIO) {
                cfg.setOpenStudioKey(captured.getName());
            } else if (listeningTarget == ListeningKeyTarget.QUICK_SWITCH) {
                cfg.setQuickSwitchProfileKey(captured.getName());
            }
            host.bootstrap().configManager().save();
            PackStudioClient.syncConfiguredKeyMappings(cfg);
        }
        listeningTarget = ListeningKeyTarget.NONE;
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, Rect bounds) {
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        scrollOffset = Math.max(0.0, scrollOffset - scrollY * 18.0);
        return true;
    }
}
