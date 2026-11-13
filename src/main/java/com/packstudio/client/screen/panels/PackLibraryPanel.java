package com.packstudio.client.screen.panels;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.client.screen.AdaptiveLayout.Rect;
import com.packstudio.manager.PackIconCache;
import com.packstudio.manager.PackRepositoryManager;
import com.packstudio.compatibility.CompatibilityAnalyzer;
import com.packstudio.model.FilterMode;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.NavigationTab;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.model.ViewMode;
import com.packstudio.util.AnimationUtil;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Interactive Resource Pack Library supporting both Grid and List views, filter pills,
 * real icons, compatibility badges, favorites, enable/disable toggles, and priority reordering.
 */
public final class PackLibraryPanel {
    private record HitTarget(Rect rect, Runnable onClick) {
    }

    private final StudioPanelHost host;
    private final List<HitTarget> hitTargets = new ArrayList<>();
    private final Map<String, Float> cardHoverProgress = new HashMap<>();
    private double scrollOffset = 0.0;
    private int totalContentHeight = 0;

    public PackLibraryPanel(StudioPanelHost host) {
        this.host = host;
    }

    public void resetScroll() {
        this.scrollOffset = 0.0;
    }

    public void render(GuiGraphicsExtractor graphics, Rect bounds, int mouseX, int mouseY, float delta) {
        hitTargets.clear();
        PackStudioConfig cfg = host.bootstrap().configManager().config();
        LiquidThemePreset theme = cfg.theme();
        Font font = host.font();

        GlassRenderUtil.drawGlassPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), cfg, false);

        int pad = 8;
        int headerY = bounds.y() + pad;
        int innerX = bounds.x() + pad;
        int innerW = Math.max(80, bounds.width() - pad * 2);

        // Top inline filter strip (on All Packs tab) or Favorites header
        if (host.activeTab() == NavigationTab.ALL_PACKS) {
            int fx = innerX;
            for (FilterMode mode : FilterMode.values()) {
                String label = mode.displayName().getString();
                int pw = Math.max(34, font.width(label) + 12);
                if (fx + pw > bounds.right() - pad) {
                    break;
                }
                Rect pill = new Rect(fx, headerY, pw, 15);
                boolean active = host.filterMode() == mode;
                boolean hov = pill.contains(mouseX, mouseY);
                int bg = active
                        ? AnimationUtil.withAlpha(theme.accentSecondary(), 175)
                        : AnimationUtil.withAlpha(theme.elevatedRgb(), hov ? 165 : 105);
                int border = active
                        ? AnimationUtil.withAlpha(theme.accentPrimary(), 205)
                        : (hov ? 0x48FFFFFF : 0x22FFFFFF);
                GlassRenderUtil.drawRoundedFill(graphics, pill.x(), pill.y(), pill.width(), pill.height(), bg);
                GlassRenderUtil.drawRoundedBorder(graphics, pill.x(), pill.y(), pill.width(), pill.height(), border, border);
                graphics.centeredText(font, label, pill.x() + pill.width() / 2, pill.y() + 4, active ? 0xFFFFFFFF : theme.textMuted());

                FilterMode targetMode = mode;
                hitTargets.add(new HitTarget(pill, () -> {
                    playClick();
                    host.setFilterMode(targetMode);
                }));
                fx += pw + 4;
            }
        } else {
            graphics.text(font, "★ Favorite Resource Packs", innerX, headerY + 3, theme.accentPrimary(), false);
        }

        Rect viewport = new Rect(bounds.x() + 4, headerY + 20, Math.max(60, bounds.width() - 8), Math.max(40, bounds.bottom() - (headerY + 20) - 4));
        List<PackEntry> packs = host.visiblePacks();

        if (packs.isEmpty()) {
            String emptyMsg = host.activeTab() == NavigationTab.FAVORITES
                    ? "No favorite resource packs yet. Click ☆ on any pack to pin it here."
                    : "No resource packs match your current search or filter.";
            TextFormatUtil.drawWrappedLines(graphics, font, emptyMsg, innerX + 6, viewport.y() + 18, innerW - 12, 3, theme.textMuted());
            totalContentHeight = 0;
            return;
        }

        int maxScroll = Math.max(0, totalContentHeight - viewport.height());
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maxScroll);

        graphics.enableScissor(viewport.x(), viewport.y(), viewport.right(), viewport.bottom());

        if (host.viewMode() == ViewMode.GRID) {
            totalContentHeight = renderGrid(graphics, viewport, packs, mouseX, mouseY, delta, cfg);
        } else {
            totalContentHeight = renderList(graphics, viewport, packs, mouseX, mouseY, delta, cfg);
        }

        graphics.disableScissor();

        // Scrollbar
        int updatedMaxScroll = Math.max(0, totalContentHeight - viewport.height());
        if (updatedMaxScroll > 0) {
            int trackH = viewport.height() - 4;
            int thumbH = Math.max(18, Math.round(trackH * ((float) viewport.height() / (float) totalContentHeight)));
            int thumbY = viewport.y() + 2 + Math.round((trackH - thumbH) * (float) (scrollOffset / updatedMaxScroll));
            graphics.fill(bounds.right() - 4, viewport.y() + 2, bounds.right() - 2, viewport.bottom() - 2, 0x22FFFFFF);
            graphics.fill(bounds.right() - 4, thumbY, bounds.right() - 2, thumbY + thumbH, AnimationUtil.withAlpha(theme.accentPrimary(), 185));
        }
    }

    private int renderGrid(
            GuiGraphicsExtractor graphics,
            Rect viewport,
            List<PackEntry> packs,
            int mouseX,
            int mouseY,
            float delta,
            PackStudioConfig cfg
    ) {
        Font font = host.font();
        LiquidThemePreset theme = cfg.theme();
        PackIconCache icons = host.bootstrap().iconCache();
        PackRepositoryManager repo = host.bootstrap().packRepositoryManager();

        int gap = 6;
        int availW = viewport.width() - 8;
        int cols = availW >= 390 ? 3 : (availW >= 210 ? 2 : 1);
        int cardW = (availW - gap * (cols - 1)) / cols;
        int cardH = 66;

        int startX = viewport.x() + 4;
        int startY = viewport.y() + 2 - (int) Math.round(scrollOffset);

        for (int i = 0; i < packs.size(); i++) {
            PackEntry pack = packs.get(i);
            int col = i % cols;
            int row = i / cols;
            int cx = startX + col * (cardW + gap);
            int cy = startY + row * (cardH + gap);

            if (cy + cardH < viewport.y() - 4 || cy > viewport.bottom() + 4) {
                continue;
            }

            Rect cardRect = new Rect(cx, cy, cardW, cardH);
            boolean hovered = viewport.contains(mouseX, mouseY) && cardRect.contains(mouseX, mouseY);
            boolean selected = host.selectedPack().map(s -> s.id().equals(pack.id())).orElse(false);

            float prevHov = cardHoverProgress.getOrDefault(pack.id(), 0.0F);
            float hovAnim = AnimationUtil.approach(prevHov, hovered ? 1.0F : 0.0F, 0.42F, delta, cfg.animationsEnabled());
            cardHoverProgress.put(pack.id(), hovAnim);

            GlassRenderUtil.drawGlassCard(graphics, cx, cy, cardW, cardH, cfg, selected, hovAnim);

            // Pack icon (28x28)
            Identifier icon = icons.iconFor(pack.nativePack());
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, cx + 6, cy + 6, 0.0F, 0.0F, 28, 28, 28, 28);
            graphics.outline(cx + 5, cy + 5, 30, 30, 0x30FFFFFF);

            // Favorite Star top-right
            Rect starRect = new Rect(cx + cardW - 18, cy + 5, 13, 13);
            graphics.centeredText(
                    font,
                    pack.favorite() ? "★" : "☆",
                    starRect.x() + 6,
                    starRect.y() + 2,
                    pack.favorite() ? 0xFFFBBF24 : theme.textMuted()
            );

            // Pack Title + Priority/Source subtitle
            int textX = cx + 39;
            int textMaxW = Math.max(20, starRect.x() - 3 - textX);
            graphics.text(font, TextFormatUtil.ellipsize(font, pack.displayName(), textMaxW), textX, cy + 7, theme.textPrimary(), false);

            String sub = pack.enabled()
                    ? ("Priority #" + pack.priorityPosition() + " • " + pack.sourceKind().label())
                    : pack.sourceKind().label();
            graphics.text(font, TextFormatUtil.ellipsize(font, sub, cardW - 44), textX, cy + 19, theme.textMuted(), false);

            // Bottom strip inside card: Compatibility dot/pill on left + Priority Up/Down + Enable toggle on right
            int stripY = cy + cardH - 18;
            String stateText = pack.required() ? I18n.get("packstudio.details.required") : (pack.enabled() ? "Enabled" : "Enable");
            int toggleW = Math.max(40, font.width(stateText) + 10);
            Rect toggleRect = new Rect(cx + cardW - toggleW - 5, stripY, toggleW, 14);
            drawMiniActionPill(graphics, font, toggleRect, stateText, pack.enabled(), pack.canToggleEnable(), theme, mouseX, mouseY);

            int leftRightBound = toggleRect.x() - 4;
            // Reorder arrows if enabled & movable & card wide enough
            if (pack.canReorder() && toggleRect.x() - (cx + 52) >= 28) {
                Rect downRect = new Rect(toggleRect.x() - 15, stripY, 13, 14);
                Rect upRect = new Rect(downRect.x() - 15, stripY, 13, 14);
                drawMiniActionPill(graphics, font, upRect, "▲", false, pack.priorityPosition() > 1, theme, mouseX, mouseY);
                drawMiniActionPill(graphics, font, downRect, "▼", false, true, theme, mouseX, mouseY);
                leftRightBound = upRect.x() - 4;

                String pid = pack.id();
                if (pack.priorityPosition() > 1) {
                    hitTargets.add(new HitTarget(clipToViewport(upRect, viewport), () -> {
                        playClick();
                        host.selectPack(pid);
                        if (repo.movePackPriority(pid, -1)) host.rebuildAndRefreshState();
                    }));
                }
                hitTargets.add(new HitTarget(clipToViewport(downRect, viewport), () -> {
                    playClick();
                    host.selectPack(pid);
                    if (repo.movePackPriority(pid, +1)) host.rebuildAndRefreshState();
                }));
            }

            int compatColor = pack.compatibility().badgeColor();
            graphics.fill(cx + 6, stripY + 4, cx + 11, stripY + 9, compatColor);
            String compatShort = pack.compatibility().isCompatible()
                    ? CompatibilityAnalyzer.currentGameVersion() + " OK"
                    : pack.compatibility().displayName().getString();
            int availCompatW = Math.max(12, leftRightBound - (cx + 14));
            graphics.text(font, TextFormatUtil.ellipsize(font, compatShort, availCompatW), cx + 14, stripY + 3, compatColor, false);

            String pid = pack.id();
            hitTargets.add(new HitTarget(clipToViewport(starRect, viewport), () -> {
                playClick();
                cfg.toggleFavorite(pid);
                host.bootstrap().configManager().save();
                host.rebuildAndRefreshState();
            }));

            if (pack.canToggleEnable()) {
                hitTargets.add(new HitTarget(clipToViewport(toggleRect, viewport), () -> {
                    playClick();
                    host.selectPack(pid);
                    if (repo.togglePackEnabled(pid)) host.rebuildAndRefreshState();
                }));
            }

            // Card selection fallback (checked after inner buttons)
            hitTargets.add(new HitTarget(clipToViewport(cardRect, viewport), () -> {
                playClick();
                host.selectPack(pid);
            }));
        }

        int totalRows = (packs.size() + cols - 1) / cols;
        return totalRows * cardH + Math.max(0, totalRows - 1) * gap + 6;
    }

    private int renderList(
            GuiGraphicsExtractor graphics,
            Rect viewport,
            List<PackEntry> packs,
            int mouseX,
            int mouseY,
            float delta,
            PackStudioConfig cfg
    ) {
        Font font = host.font();
        LiquidThemePreset theme = cfg.theme();
        PackIconCache icons = host.bootstrap().iconCache();
        PackRepositoryManager repo = host.bootstrap().packRepositoryManager();

        int rowH = 36;
        int gap = 4;
        int rowX = viewport.x() + 4;
        int rowW = viewport.width() - 8;
        int startY = viewport.y() + 2 - (int) Math.round(scrollOffset);

        for (int i = 0; i < packs.size(); i++) {
            PackEntry pack = packs.get(i);
            int ry = startY + i * (rowH + gap);
            if (ry + rowH < viewport.y() - 4 || ry > viewport.bottom() + 4) {
                continue;
            }

            Rect rowRect = new Rect(rowX, ry, rowW, rowH);
            boolean hovered = viewport.contains(mouseX, mouseY) && rowRect.contains(mouseX, mouseY);
            boolean selected = host.selectedPack().map(s -> s.id().equals(pack.id())).orElse(false);

            float prevHov = cardHoverProgress.getOrDefault(pack.id(), 0.0F);
            float hovAnim = AnimationUtil.approach(prevHov, hovered ? 1.0F : 0.0F, 0.42F, delta, cfg.animationsEnabled());
            cardHoverProgress.put(pack.id(), hovAnim);

            GlassRenderUtil.drawGlassCard(graphics, rowX, ry, rowW, rowH, cfg, selected, hovAnim);

            // 26x26 Icon
            Identifier icon = icons.iconFor(pack.nativePack());
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, rowX + 5, ry + 5, 0.0F, 0.0F, 26, 26, 26, 26);

            // Right-side controls: Enable toggle | Up/Down | Favorite Star
            String stateText = pack.required() ? I18n.get("packstudio.details.required") : (pack.enabled() ? "Enabled" : "Enable");
            int toggleW = Math.max(44, font.width(stateText) + 10);
            Rect toggleRect = new Rect(rowRect.right() - toggleW - 6, ry + 11, toggleW, 14);
            drawMiniActionPill(graphics, font, toggleRect, stateText, pack.enabled(), pack.canToggleEnable(), theme, mouseX, mouseY);

            int rightLimit = toggleRect.x() - 4;
            String pid = pack.id();

            if (pack.canReorder() && rowW >= 220) {
                Rect downRect = new Rect(rightLimit - 14, ry + 11, 13, 14);
                Rect upRect = new Rect(downRect.x() - 15, ry + 11, 13, 14);
                drawMiniActionPill(graphics, font, upRect, "▲", false, pack.priorityPosition() > 1, theme, mouseX, mouseY);
                drawMiniActionPill(graphics, font, downRect, "▼", false, true, theme, mouseX, mouseY);
                rightLimit = upRect.x() - 4;

                if (pack.priorityPosition() > 1) {
                    hitTargets.add(new HitTarget(clipToViewport(upRect, viewport), () -> {
                        playClick();
                        host.selectPack(pid);
                        if (repo.movePackPriority(pid, -1)) host.rebuildAndRefreshState();
                    }));
                }
                hitTargets.add(new HitTarget(clipToViewport(downRect, viewport), () -> {
                    playClick();
                    host.selectPack(pid);
                    if (repo.movePackPriority(pid, +1)) host.rebuildAndRefreshState();
                }));
            }

            Rect starRect = new Rect(rightLimit - 14, ry + 11, 14, 14);
            graphics.centeredText(font, pack.favorite() ? "★" : "☆", starRect.x() + 7, starRect.y() + 3,
                    pack.favorite() ? 0xFFFBBF24 : theme.textMuted());
            rightLimit = starRect.x() - 6;

            // Compatibility badge if room
            if (rowW >= 260) {
                String compatText = pack.compatibility().displayName().getString();
                int cw = font.width(compatText) + 10;
                int cx = rightLimit - cw;
                GlassRenderUtil.drawBadgePill(graphics, cx, ry + 11, cw, 13, pack.compatibility().badgeColor());
                graphics.centeredText(font, compatText, cx + cw / 2, ry + 14, pack.compatibility().badgeColor());
                rightLimit = cx - 6;
            }

            // Title & Description on left
            int titleX = rowX + 36;
            int maxTextW = Math.max(30, rightLimit - titleX);
            String titlePrefix = pack.enabled() ? ("#" + pack.priorityPosition() + "  " + pack.displayName()) : pack.displayName();
            graphics.text(font, TextFormatUtil.ellipsize(font, titlePrefix, maxTextW), titleX, ry + 6, theme.textPrimary(), false);

            String sub = pack.description().isBlank() ? pack.sourceKind().label() : pack.description();
            graphics.text(font, TextFormatUtil.ellipsize(font, sub, maxTextW), titleX, ry + 19, theme.textMuted(), false);

            hitTargets.add(new HitTarget(clipToViewport(starRect, viewport), () -> {
                playClick();
                cfg.toggleFavorite(pid);
                host.bootstrap().configManager().save();
                host.rebuildAndRefreshState();
            }));
            if (pack.canToggleEnable()) {
                hitTargets.add(new HitTarget(clipToViewport(toggleRect, viewport), () -> {
                    playClick();
                    host.selectPack(pid);
                    if (repo.togglePackEnabled(pid)) host.rebuildAndRefreshState();
                }));
            }
            hitTargets.add(new HitTarget(clipToViewport(rowRect, viewport), () -> {
                playClick();
                host.selectPack(pid);
            }));
        }

        return packs.size() * rowH + Math.max(0, packs.size() - 1) * gap + 6;
    }

    private static void drawMiniActionPill(
            GuiGraphicsExtractor graphics,
            Font font,
            Rect rect,
            String text,
            boolean activeAccent,
            boolean enabled,
            LiquidThemePreset theme,
            int mouseX,
            int mouseY
    ) {
        boolean hov = enabled && rect.contains(mouseX, mouseY);
        int bg;
        int border;
        int fg;
        if (!enabled) {
            bg = 0x380E1420;
            border = 0x18FFFFFF;
            fg = 0xFF64748B;
        } else if (activeAccent) {
            bg = AnimationUtil.withAlpha(theme.accentSecondary(), hov ? 220 : 175);
            border = AnimationUtil.withAlpha(theme.accentPrimary(), 215);
            fg = 0xFFFFFFFF;
        } else {
            bg = AnimationUtil.withAlpha(theme.elevatedRgb(), hov ? 215 : 155);
            border = hov ? AnimationUtil.withAlpha(theme.accentPrimary(), 170) : 0x30FFFFFF;
            fg = theme.textPrimary();
        }
        GlassRenderUtil.drawRoundedFill(graphics, rect.x(), rect.y(), rect.width(), rect.height(), bg);
        GlassRenderUtil.drawRoundedBorder(graphics, rect.x(), rect.y(), rect.width(), rect.height(), border, border);
        graphics.centeredText(font, text, rect.x() + rect.width() / 2, rect.y() + 3, fg);
    }

    private static Rect clipToViewport(Rect r, Rect vp) {
        int x1 = Math.max(r.x(), vp.x());
        int y1 = Math.max(r.y(), vp.y());
        int x2 = Math.min(r.right(), vp.right());
        int y2 = Math.min(r.bottom(), vp.bottom());
        return new Rect(x1, y1, Math.max(0, x2 - x1), Math.max(0, y2 - y1));
    }

    public boolean mouseClicked(MouseButtonEvent event, Rect bounds) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        for (HitTarget target : hitTargets) {
            if (target.rect().width() > 0 && target.rect().height() > 0 && target.rect().contains(event.x(), event.y())) {
                target.onClick().run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY, Rect bounds) {
        if (!bounds.contains(mouseX, mouseY)) {
            return false;
        }
        scrollOffset = Math.max(0.0, scrollOffset - scrollY * 20.0);
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        List<PackEntry> packs = host.visiblePacks();
        if (packs.isEmpty()) {
            return false;
        }
        String currentId = host.selectedPack().map(PackEntry::id).orElse("");
        int idx = 0;
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).id().equals(currentId)) {
                idx = i;
                break;
            }
        }
        if (event.isDown()) {
            int next = Math.min(packs.size() - 1, idx + 1);
            host.selectPack(packs.get(next).id());
            return true;
        }
        if (event.isUp()) {
            int prev = Math.max(0, idx - 1);
            host.selectPack(packs.get(prev).id());
            return true;
        }
        if (event.isSelection() && host.selectedPack().isPresent()) {
            PackEntry sel = host.selectedPack().get();
            if (sel.canToggleEnable() && host.bootstrap().packRepositoryManager().togglePackEnabled(sel.id())) {
                host.rebuildAndRefreshState();
                return true;
            }
        }
        return false;
    }

    private static void playClick() {
        AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
    }
}
