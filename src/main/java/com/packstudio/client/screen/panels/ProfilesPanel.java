package com.packstudio.client.screen.panels;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.client.screen.AdaptiveLayout.Rect;
import com.packstudio.client.screen.components.GlassButton;
import com.packstudio.client.screen.components.GlassModalDialog;
import com.packstudio.compatibility.ProfileValidator;
import com.packstudio.compatibility.ProfileValidator.MissingPackNotice;
import com.packstudio.compatibility.ProfileValidator.ProfileHealth;
import com.packstudio.compat.MinecraftCompatProvider;
import com.packstudio.core.PackStudioConstants;
import com.packstudio.manager.ProfileManager;
import com.packstudio.manager.ProfileManager.ImportOutcome;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackProfile;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Full Profile Manager view supporting creation, renaming, duplication, deletion,
 * one-click switching, JSON export/import, and explicit missing-pack reporting.
 */
public final class ProfilesPanel {
    private record HitZone(Rect rect, Runnable action) {
    }

    private final StudioPanelHost host;
    private final List<GlassButton> headerButtons = new ArrayList<>();
    private final List<HitZone> cardHitZones = new ArrayList<>();
    private double scrollOffset = 0.0;
    private int totalContentHeight = 0;
    private String selectedProfileId = "";

    public ProfilesPanel(StudioPanelHost host) {
        this.host = host;
    }

    public void layoutHeaderButtons(Rect bounds) {
        headerButtons.clear();
        int pad = 8;
        int y = bounds.y() + pad;
        int right = bounds.right() - pad;

        GlassButton openFolderBtn = new GlassButton(
                right - 74,
                y,
                74,
                18,
                Component.literal("Folder"),
                GlassButton.Variant.SECONDARY,
                _ -> {
                    Path dir = host.bootstrap().profileManager().profilesDirectory();
                    try {
                        Files.createDirectories(dir);
                        MinecraftCompatProvider.get().openDirectory(dir);
                    } catch (Exception e) {
                        PackStudioConstants.LOGGER.warn("Could not open profiles folder {}", dir, e);
                    }
                }
        );

        GlassButton importBtn = new GlassButton(
                openFolderBtn.getX() - 78,
                y,
                74,
                18,
                Component.translatable("packstudio.profiles.import"),
                GlassButton.Variant.SECONDARY,
                _ -> openImportModal()
        );

        GlassButton createBtn = new GlassButton(
                importBtn.getX() - 96,
                y,
                92,
                18,
                Component.literal("+ " + I18n.get("packstudio.profiles.create")),
                GlassButton.Variant.PRIMARY,
                _ -> openCreateProfileModal()
        );

        headerButtons.add(createBtn);
        headerButtons.add(importBtn);
        headerButtons.add(openFolderBtn);
    }

    public void render(GuiGraphicsExtractor graphics, Rect bounds, int mouseX, int mouseY, float delta) {
        cardHitZones.clear();
        PackStudioConfig cfg = host.bootstrap().configManager().config();
        LiquidThemePreset theme = cfg.theme();
        Font font = host.font();

        GlassRenderUtil.drawGlassPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), cfg, false);

        int pad = 8;
        if (bounds.width() >= 360) {
            graphics.text(font, I18n.get("packstudio.profiles.title"), bounds.x() + pad + 2, bounds.y() + pad + 5, theme.textPrimary(), false);
        }

        for (GlassButton btn : headerButtons) {
            btn.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        Rect viewport = new Rect(bounds.x() + 6, bounds.y() + 32, Math.max(80, bounds.width() - 12), Math.max(40, bounds.bottom() - (bounds.y() + 32) - 6));
        List<PackProfile> profiles = host.bootstrap().profileManager().profiles();

        if (profiles.isEmpty()) {
            GlassRenderUtil.drawGlassCard(graphics, viewport.x() + 4, viewport.y() + 10, viewport.width() - 8, 58, cfg, false, 0.0F);
            graphics.text(font, I18n.get("packstudio.profiles.empty"), viewport.x() + 14, viewport.y() + 22, theme.textPrimary(), false);
            graphics.text(
                    font,
                    TextFormatUtil.ellipsize(font, "Click '+ Create Profile' above to save your current resource pack order as a reusable preset.", viewport.width() - 28),
                    viewport.x() + 14,
                    viewport.y() + 36,
                    theme.textMuted(),
                    false
            );
            totalContentHeight = 0;
            return;
        }

        if (selectedProfileId.isEmpty() || host.bootstrap().profileManager().findById(selectedProfileId).isEmpty()) {
            selectedProfileId = cfg.activeProfileId().isEmpty() ? profiles.getFirst().id() : cfg.activeProfileId();
        }

        int maxScroll = Math.max(0, totalContentHeight - viewport.height());
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maxScroll);

        List<PackEntry> allPacks = host.bootstrap().packRepositoryManager().allPacks();
        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());

        int cursorY = viewport.y() + 2 - (int) Math.round(scrollOffset);
        int startY = cursorY;
        int cardX = viewport.x() + 2;
        int cardW = viewport.width() - 6;

        for (PackProfile profile : profiles) {
            ProfileHealth health = ProfileValidator.evaluateAgainstLibrary(profile, allPacks);
            boolean hasMissing = !health.missingPacks().isEmpty();
            int cardH = hasMissing ? 72 : 56;

            if (cursorY + cardH >= viewport.y() - 4 && cursorY <= viewport.bottom() + 4) {
                Rect cardRect = new Rect(cardX, cursorY, cardW, cardH);
                boolean isActive = profile.id().equals(cfg.activeProfileId());
                boolean isSelected = profile.id().equals(selectedProfileId);
                boolean hov = viewport.contains(mouseX, mouseY) && cardRect.contains(mouseX, mouseY);

                GlassRenderUtil.drawGlassCard(graphics, cardX, cursorY, cardW, cardH, cfg, isActive || isSelected, hov ? 0.65F : 0.0F);

                // Profile Title + Active Badge
                int titleX = cardX + 8;
                int titleY = cursorY + 7;
                graphics.text(font, TextFormatUtil.ellipsize(font, profile.name(), Math.max(40, cardW - 140)), titleX, titleY, theme.textPrimary(), false);

                if (isActive) {
                    String activeTag = I18n.get("packstudio.profiles.active_badge");
                    int bw = font.width(activeTag) + 10;
                    int bx = titleX + font.width(TextFormatUtil.ellipsize(font, profile.name(), Math.max(40, cardW - 140))) + 8;
                    if (bx + bw < cardRect.right() - 8) {
                        GlassRenderUtil.drawBadgePill(graphics, bx, titleY - 2, bw, 12, theme.accentPrimary());
                        graphics.centeredText(font, activeTag, bx + bw / 2, titleY, theme.accentPrimary());
                    }
                }

                // Subtitle: pack count + modified timestamp
                String metaLine = I18n.get("packstudio.profiles.packs_count", profile.enabledCount())
                        + "  •  Modified " + TextFormatUtil.formatTimestamp(profile.modifiedAt());
                graphics.text(font, TextFormatUtil.ellipsize(font, metaLine, cardW - 16), titleX, cursorY + 20, theme.textMuted(), false);

                // Missing pack warning line (never silent substitution!)
                if (hasMissing) {
                    String missingNames = health.missingPacks().stream()
                            .map(MissingPackNotice::label)
                            .limit(2)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("");
                    String warnLine = "⚠ " + I18n.get("packstudio.profiles.missing_warning", health.missingPacks().size())
                            + ": " + missingNames;
                    graphics.text(font, TextFormatUtil.ellipsize(font, warnLine, cardW - 16), titleX, cursorY + 33, 0xFFFBBF24, false);
                }

                // Action row at bottom of card: Apply | Save Current | Rename | Duplicate | Export | Delete
                int btnY = cursorY + cardH - 19;
                int bx = cardX + 8;
                String pid = profile.id();

                bx = addInlineActionButton(graphics, font, viewport, bx, btnY,
                        I18n.get("packstudio.profiles.apply"), true, false, theme, mouseX, mouseY,
                        () -> host.triggerApplyProfile(pid));

                bx = addInlineActionButton(graphics, font, viewport, bx, btnY,
                        "Update", false, false, theme, mouseX, mouseY,
                        () -> {
                            if (host.bootstrap().profileManager().updateProfileFromCurrentSelection(pid, host.bootstrap().packRepositoryManager())) {
                                host.bootstrap().notifications().success(PackStudioConstants.MOD_NAME,
                                        "Updated preset '" + profile.name() + "' from current selection.", false);
                                host.rebuildAndRefreshState();
                            }
                        });

                bx = addInlineActionButton(graphics, font, viewport, bx, btnY,
                        I18n.get("packstudio.profiles.rename"), false, false, theme, mouseX, mouseY,
                        () -> openRenameModal(profile));

                bx = addInlineActionButton(graphics, font, viewport, bx, btnY,
                        I18n.get("packstudio.profiles.duplicate"), false, false, theme, mouseX, mouseY,
                        () -> {
                            host.bootstrap().profileManager().duplicateProfile(pid);
                            host.rebuildAndRefreshState();
                        });

                bx = addInlineActionButton(graphics, font, viewport, bx, btnY,
                        I18n.get("packstudio.profiles.export"), false, false, theme, mouseX, mouseY,
                        () -> exportProfile(profile));

                if (bx + 44 <= cardRect.right() - 4) {
                    addInlineActionButton(graphics, font, viewport, bx, btnY,
                            I18n.get("packstudio.profiles.delete"), false, true, theme, mouseX, mouseY,
                            () -> openDeleteConfirmModal(profile));
                }

                cardHitZones.add(new HitZone(clip(cardRect, viewport), () -> this.selectedProfileId = pid));
            }
            cursorY += cardH + 6;
        }

        totalContentHeight = (cursorY - startY);
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

    private int addInlineActionButton(
            GuiGraphicsExtractor graphics,
            Font font,
            Rect viewport,
            int x,
            int y,
            String label,
            boolean primary,
            boolean danger,
            LiquidThemePreset theme,
            int mouseX,
            int mouseY,
            Runnable onClick
    ) {
        int w = Math.max(36, font.width(label) + 10);
        if (x + w > viewport.right() - 6) {
            return x;
        }
        Rect r = new Rect(x, y, w, 14);
        boolean hov = viewport.contains(mouseX, mouseY) && r.contains(mouseX, mouseY);
        int bg;
        int border;
        int fg;
        if (primary) {
            bg = AnimationUtil.withAlpha(theme.accentSecondary(), hov ? 225 : 185);
            border = AnimationUtil.withAlpha(theme.accentPrimary(), 215);
            fg = 0xFFFFFFFF;
        } else if (danger) {
            bg = hov ? 0xCC7F1D1D : 0x8845141B;
            border = 0x99F87171;
            fg = 0xFFFECACA;
        } else {
            bg = AnimationUtil.withAlpha(theme.elevatedRgb(), hov ? 215 : 155);
            border = hov ? AnimationUtil.withAlpha(theme.accentPrimary(), 165) : 0x2EFFFFFF;
            fg = theme.textPrimary();
        }
        GlassRenderUtil.drawRoundedFill(graphics, r.x(), r.y(), r.width(), r.height(), bg);
        GlassRenderUtil.drawRoundedBorder(graphics, r.x(), r.y(), r.width(), r.height(), border, border);
        graphics.centeredText(font, label, r.x() + r.width() / 2, r.y() + 3, fg);

        cardHitZones.add(new HitZone(clip(r, viewport), () -> {
            AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
            onClick.run();
        }));
        return x + w + 5;
    }

    private void openCreateProfileModal() {
        host.openModal(GlassModalDialog.promptText(
                I18n.get("packstudio.profiles.create"),
                "Saves your current staged pack selection and priority order.",
                "",
                "e.g. PvP, Vanilla+, Builder...",
                64,
                "Create",
                rawName -> {
                    PackProfile created = host.bootstrap().profileManager()
                            .createFromCurrentSelection(rawName, host.bootstrap().packRepositoryManager());
                    this.selectedProfileId = created.id();
                    host.closeModal();
                    host.bootstrap().notifications().success(
                            PackStudioConstants.MOD_NAME,
                            "Created profile '" + created.name() + "'.",
                            false
                    );
                    host.rebuildAndRefreshState();
                },
                host::closeModal
        ));
    }

    private void openRenameModal(PackProfile profile) {
        host.openModal(GlassModalDialog.promptText(
                I18n.get("packstudio.profiles.rename"),
                "Enter a unique name for '" + profile.name() + "':",
                profile.name(),
                profile.name(),
                64,
                "Rename",
                newName -> {
                    Optional<String> err = host.bootstrap().profileManager().renameProfile(profile.id(), newName);
                    if (err.isPresent()) {
                        host.bootstrap().notifications().error(PackStudioConstants.MOD_NAME, err.get(), false);
                    } else {
                        host.closeModal();
                        host.rebuildAndRefreshState();
                    }
                },
                host::closeModal
        ));
    }

    private void openDeleteConfirmModal(PackProfile profile) {
        host.openModal(GlassModalDialog.confirm(
                I18n.get("packstudio.profiles.delete") + " '" + profile.name() + "'?",
                "This removes the preset JSON from config/packstudio/profiles/. Your resource packs are untouched.",
                I18n.get("packstudio.profiles.delete"),
                GlassButton.Variant.DANGER,
                () -> {
                    host.bootstrap().profileManager().deleteProfile(profile.id());
                    host.closeModal();
                    host.rebuildAndRefreshState();
                },
                host::closeModal
        ));
    }

    private void exportProfile(PackProfile profile) {
        ProfileManager pm = host.bootstrap().profileManager();
        Optional<Path> exportedFile = pm.exportProfileToFile(profile.id());
        pm.exportProfileToJson(profile.id()).ifPresent(json -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(json);
            }
        });
        if (exportedFile.isPresent()) {
            host.bootstrap().notifications().success(
                    PackStudioConstants.MOD_NAME,
                    I18n.get("packstudio.profiles.exported", exportedFile.get().getFileName().toString()) + " (also copied to clipboard)",
                    false
            );
        }
    }

    private void openImportModal() {
        Minecraft mc = Minecraft.getInstance();
        String clip = (mc != null && mc.keyboardHandler != null) ? mc.keyboardHandler.getClipboard() : "";
        String prefill = (clip != null && clip.trim().startsWith("{")) ? "" : "";
        host.openModal(GlassModalDialog.promptText(
                I18n.get("packstudio.profiles.import"),
                "Enter a JSON filename inside config/packstudio/exports/ (or leave blank to import JSON from clipboard):",
                prefill,
                "preset-file.json or blank for clipboard",
                512,
                "Import",
                input -> {
                    ProfileManager pm = host.bootstrap().profileManager();
                    ImportOutcome outcome;
                    String trimmed = input == null ? "" : input.trim();
                    if (trimmed.isEmpty()) {
                        String clipboardJson = (mc != null && mc.keyboardHandler != null) ? mc.keyboardHandler.getClipboard() : "";
                        outcome = pm.importProfileFromJson(clipboardJson);
                    } else if (trimmed.startsWith("{")) {
                        outcome = pm.importProfileFromJson(trimmed);
                    } else {
                        Path candidate = Path.of(trimmed);
                        if (!candidate.isAbsolute()) {
                            candidate = pm.exportsDirectory().resolve(trimmed).normalize();
                        }
                        outcome = pm.importProfileFromPath(candidate);
                    }

                    if (!outcome.success()) {
                        host.bootstrap().notifications().error(
                                PackStudioConstants.MOD_NAME,
                                I18n.get("packstudio.error.import_invalid", outcome.errorMessage()),
                                false
                        );
                    } else {
                        this.selectedProfileId = outcome.profile().id();
                        host.closeModal();
                        host.bootstrap().notifications().success(
                                PackStudioConstants.MOD_NAME,
                                I18n.get("packstudio.profiles.imported", outcome.profile().name()),
                                false
                        );
                        host.rebuildAndRefreshState();
                    }
                },
                host::closeModal
        ));
    }

    private static Rect clip(Rect r, Rect vp) {
        int x1 = Math.max(r.x(), vp.x());
        int y1 = Math.max(r.y(), vp.y());
        int x2 = Math.min(r.right(), vp.right());
        int y2 = Math.min(r.bottom(), vp.bottom());
        return new Rect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, Rect bounds) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        for (GlassButton btn : headerButtons) {
            if (btn.mouseClicked(event, doubleClick)) {
                return true;
            }
        }
        for (HitZone zone : cardHitZones) {
            if (zone.rect().width() > 0 && zone.rect().height() > 0 && zone.rect().contains(event.x(), event.y())) {
                zone.action().run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, Rect bounds) {
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        scrollOffset = Math.max(0.0, scrollOffset - scrollY * 18.0);
        return true;
    }
}
