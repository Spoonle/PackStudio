package com.packstudio.client.screen.panels;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.client.screen.AdaptiveLayout.Rect;
import com.packstudio.client.screen.components.GlassButton;
import com.packstudio.manager.PackRepositoryManager;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Right-hand inspector displaying authentic pack metadata and priority/favorite actions.
 */
public final class PackDetailsPanel {
    private final StudioPanelHost host;
    private final List<GlassButton> actionButtons = new ArrayList<>();
    private String boundPackId = "";

    public PackDetailsPanel(StudioPanelHost host) {
        this.host = host;
    }

    public void layoutButtons(Rect bounds) {
        actionButtons.clear();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        Optional<PackEntry> opt = host.selectedPack();
        if (opt.isEmpty()) {
            boundPackId = "";
            return;
        }
        PackEntry entry = opt.get();
        boundPackId = entry.id();

        int pad = 8;
        int innerX = bounds.x() + pad;
        int innerW = Math.max(60, bounds.width() - pad * 2);
        int bottomY = bounds.bottom() - pad;

        PackRepositoryManager repo = host.bootstrap().packRepositoryManager();
        PackStudioConfig cfg = host.bootstrap().configManager().config();

        // Row 2 (bottom): Move Up | Move Down | Favorite
        int row2Y = bottomY - 18;
        int smallGap = 4;
        int thirdW = (innerW - smallGap * 2) / 3;

        GlassButton moveUpBtn = new GlassButton(
                innerX,
                row2Y,
                thirdW,
                18,
                Component.literal("▲ Up"),
                GlassButton.Variant.SECONDARY,
                _ -> {
                    if (repo.movePackPriority(entry.id(), -1)) {
                        host.rebuildAndRefreshState();
                    }
                }
        );
        moveUpBtn.active = entry.canReorder() && entry.priorityPosition() > 1;

        GlassButton moveDownBtn = new GlassButton(
                innerX + thirdW + smallGap,
                row2Y,
                thirdW,
                18,
                Component.literal("▼ Dn"),
                GlassButton.Variant.SECONDARY,
                _ -> {
                    if (repo.movePackPriority(entry.id(), +1)) {
                        host.rebuildAndRefreshState();
                    }
                }
        );
        moveDownBtn.active = entry.canReorder();

        boolean fav = entry.favorite();
        GlassButton favBtn = new GlassButton(
                innerX + (thirdW + smallGap) * 2,
                row2Y,
                innerW - (thirdW + smallGap) * 2,
                18,
                Component.literal(fav ? "★ Fav" : "☆ Fav"),
                GlassButton.Variant.SUBTLE,
                _ -> {
                    cfg.toggleFavorite(entry.id());
                    host.bootstrap().configManager().save();
                    host.rebuildAndRefreshState();
                }
        ).setSelectedState(fav);

        // Row 1 (above Row 2): Enable / Disable / Required
        int row1Y = row2Y - 22;
        String toggleLabel = entry.required()
                ? I18n.get("packstudio.details.required")
                : (entry.enabled() ? "Disable Pack" : "Enable Pack");
        GlassButton toggleBtn = new GlassButton(
                innerX,
                row1Y,
                innerW,
                19,
                Component.literal(toggleLabel),
                entry.enabled() ? GlassButton.Variant.SECONDARY : GlassButton.Variant.PRIMARY,
                _ -> {
                    if (repo.togglePackEnabled(entry.id())) {
                        host.rebuildAndRefreshState();
                    }
                }
        );
        toggleBtn.active = entry.canToggleEnable();

        actionButtons.add(toggleBtn);
        actionButtons.add(moveUpBtn);
        actionButtons.add(moveDownBtn);
        actionButtons.add(favBtn);
    }

    public void render(GuiGraphicsExtractor graphics, Rect bounds, int mouseX, int mouseY, float delta) {
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        PackStudioConfig cfg = host.bootstrap().configManager().config();
        LiquidThemePreset theme = cfg.theme();
        Font font = host.font();

        GlassRenderUtil.drawGlassPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), cfg, false);

        Optional<PackEntry> opt = host.selectedPack();
        String currentId = opt.map(PackEntry::id).orElse("");
        if (!currentId.equals(boundPackId)) {
            layoutButtons(bounds);
        }

        int pad = 8;
        int innerX = bounds.x() + pad;
        int innerW = Math.max(60, bounds.width() - pad * 2);

        graphics.enableScissor(bounds.x() + 2, bounds.y() + 2, bounds.right() - 2, bounds.bottom() - 2);

        graphics.text(font, I18n.get("packstudio.details.title"), innerX, bounds.y() + pad, theme.accentPrimary(), false);

        if (opt.isEmpty()) {
            TextFormatUtil.drawWrappedLines(
                    graphics,
                    font,
                    I18n.get("packstudio.details.empty"),
                    innerX,
                    bounds.y() + 26,
                    innerW,
                    4,
                    theme.textMuted()
            );
            graphics.disableScissor();
            return;
        }

        PackEntry pack = opt.get();
        int y = bounds.y() + pad + 14;

        // Icon + Pack Name + Source Kind
        Identifier icon = host.bootstrap().iconCache().iconFor(pack.nativePack());
        graphics.blit(RenderPipelines.GUI_TEXTURED, icon, innerX, y, 0.0F, 0.0F, 32, 32, 32, 32);
        graphics.outline(innerX - 1, y - 1, 34, 34, 0x38FFFFFF);

        int titleX = innerX + 38;
        int titleMaxW = Math.max(24, bounds.right() - pad - titleX);
        graphics.text(font, TextFormatUtil.ellipsize(font, pack.displayName(), titleMaxW), titleX, y + 3, theme.textPrimary(), false);
        graphics.text(font, TextFormatUtil.ellipsize(font, pack.sourceKind().label(), titleMaxW), titleX, y + 15, theme.textMuted(), false);
        y += 38;

        // Compatibility badge pill
        String compatLabel = pack.compatibility().displayName().getString();
        int pillW = Math.min(innerW, font.width(compatLabel) + 12);
        GlassRenderUtil.drawBadgePill(graphics, innerX, y, pillW, 13, pack.compatibility().badgeColor());
        graphics.centeredText(font, compatLabel, innerX + pillW / 2, y + 3, pack.compatibility().badgeColor());
        y += 18;

        // Real Description
        String desc = pack.description().isBlank() ? "No description provided." : pack.description();
        int descHeight = TextFormatUtil.drawWrappedLines(graphics, font, desc, innerX, y, innerW, 3, theme.textMuted());
        y += Math.max(12, descHeight) + 6;

        // Divider
        graphics.fill(innerX, y, innerX + innerW, y + 1, 0x22FFFFFF);
        y += 6;

        int maxMetaBottom = bounds.bottom() - 52;
        y = drawMetaRow(graphics, font, innerX, y, innerW, maxMetaBottom,
                I18n.get("packstudio.details.author"),
                pack.author().orElse(I18n.get("packstudio.details.author_unknown")),
                theme);
        y = drawMetaRow(graphics, font, innerX, y, innerW, maxMetaBottom,
                I18n.get("packstudio.details.format"),
                pack.packFormatLabel().orElse(I18n.get("packstudio.details.format_unknown")),
                theme);
        String stateText = pack.required()
                ? I18n.get("packstudio.details.required")
                : (pack.enabled() ? I18n.get("packstudio.details.enabled") : I18n.get("packstudio.details.disabled"));
        y = drawMetaRow(graphics, font, innerX, y, innerW, maxMetaBottom,
                I18n.get("packstudio.details.status"),
                stateText,
                theme);
        String prioText = pack.enabled()
                ? ("#" + pack.priorityPosition() + (pack.priorityPosition() == 1 ? " (Top)" : ""))
                : "—";
        drawMetaRow(graphics, font, innerX, y, innerW, maxMetaBottom,
                I18n.get("packstudio.details.priority"),
                prioText,
                theme);

        for (GlassButton btn : actionButtons) {
            btn.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        graphics.disableScissor();
    }

    private static int drawMetaRow(
            GuiGraphicsExtractor graphics,
            Font font,
            int x,
            int y,
            int width,
            int maxY,
            String key,
            String value,
            LiquidThemePreset theme
    ) {
        if (y + 10 > maxY) {
            return y;
        }
        String keyLabel = key + ":";
        graphics.text(font, keyLabel, x, y, theme.textMuted(), false);
        int valMaxW = Math.max(20, width - font.width(keyLabel) - 6);
        String valTrunc = TextFormatUtil.ellipsize(font, value, valMaxW);
        graphics.text(font, valTrunc, x + width - font.width(valTrunc), y, theme.textPrimary(), false);
        return y + 12;
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick, Rect bounds) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        for (GlassButton btn : actionButtons) {
            if (btn.mouseClicked(event, doubleClick)) {
                return true;
            }
        }
        return false;
    }
}
