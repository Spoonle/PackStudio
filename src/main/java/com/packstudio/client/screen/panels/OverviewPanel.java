package com.packstudio.client.screen.panels;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.client.screen.AdaptiveLayout.Rect;
import com.packstudio.compatibility.CompatibilityAnalyzer;
import com.packstudio.compatibility.CompatibilityAnalyzer.LibraryHealthReport;
import com.packstudio.compatibility.ProfileValidator;
import com.packstudio.manager.PackIconCache;
import com.packstudio.model.LiquidThemePreset;
import com.packstudio.model.NavigationTab;
import com.packstudio.model.PackEntry;
import com.packstudio.model.PackProfile;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.AnimationUtil;
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
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Overview dashboard presenting live resource pack statistics, one-click preset pills,
 * active priority stack preview, and honest compatibility diagnostics.
 */
public final class OverviewPanel {
    private record ClickZone(Rect bounds, Runnable action) {
    }

    private final StudioPanelHost host;
    private final List<ClickZone> clickZones = new ArrayList<>();
    private double scrollOffset = 0.0;
    private int contentHeight = 0;

    public OverviewPanel(StudioPanelHost host) {
        this.host = host;
    }

    public void render(GuiGraphicsExtractor graphics, Rect bounds, int mouseX, int mouseY, float delta) {
        clickZones.clear();
        PackStudioConfig cfg = host.bootstrap().configManager().config();
        LiquidThemePreset theme = cfg.theme();
        Font font = host.font();

        GlassRenderUtil.drawGlassPanel(graphics, bounds.x(), bounds.y(), bounds.width(), bounds.height(), cfg, false);

        List<PackEntry> allPacks = host.bootstrap().packRepositoryManager().allPacks();
        LibraryHealthReport health = CompatibilityAnalyzer.analyzeLibrary(allPacks);
        List<PackProfile> profiles = host.bootstrap().profileManager().profiles();
        Optional<PackProfile> activeProfile = host.bootstrap().profileManager().findById(cfg.activeProfileId());

        int pad = 10;
        int innerX = bounds.x() + pad;
        int innerW = Math.max(80, bounds.width() - pad * 2);

        int maxScroll = Math.max(0, contentHeight - (bounds.height() - pad * 2));
        scrollOffset = Mth.clamp(scrollOffset, 0.0, maxScroll);
        int cursorY = bounds.y() + pad - (int) Math.round(scrollOffset);
        int startY = cursorY;

        graphics.enableScissor(bounds.x() + 2, bounds.y() + 2, bounds.right() - 2, bounds.bottom() - 2);

        // 1. Header banner
        graphics.text(font, I18n.get("packstudio.overview.welcome"), innerX, cursorY, theme.textPrimary(), false);
        graphics.text(
                font,
                TextFormatUtil.ellipsize(font, I18n.get("packstudio.tagline"), innerW),
                innerX,
                cursorY + 12,
                theme.textMuted(),
                false
        );
        cursorY += 28;

        // 2. Four Liquid Glass metric cards (2x2 on narrow widths, 4x1 on spacious widths)
        int cols = innerW >= 340 ? 4 : 2;
        int cardGap = 6;
        int cardW = (innerW - cardGap * (cols - 1)) / cols;
        int cardH = 38;

        String activeProfileName = activeProfile.map(PackProfile::name).orElse(I18n.get("packstudio.overview.none_active"));
        String[][] metrics = {
                {I18n.get("packstudio.overview.total_packs"), Integer.toString(health.totalPacks())},
                {I18n.get("packstudio.overview.enabled_packs"), Integer.toString(health.enabledPacks())},
                {I18n.get("packstudio.overview.active_profile"), activeProfileName},
                {"Pack Format", health.runtimePackFormat()}
        };

        for (int i = 0; i < metrics.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int cx = innerX + col * (cardW + cardGap);
            int cy = cursorY + row * (cardH + cardGap);
            boolean hovered = bounds.contains(mouseX, mouseY) && mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH;
            GlassRenderUtil.drawGlassCard(graphics, cx, cy, cardW, cardH, cfg, i == 2 && activeProfile.isPresent(), hovered ? 0.6F : 0.0F);
            graphics.text(font, TextFormatUtil.ellipsize(font, metrics[i][0], cardW - 10), cx + 6, cy + 6, theme.textMuted(), false);
            graphics.text(font, TextFormatUtil.ellipsize(font, metrics[i][1], cardW - 10), cx + 6, cy + 20, theme.textPrimary(), false);

            NavigationTab targetTab = (i == 2) ? NavigationTab.PROFILES : NavigationTab.ALL_PACKS;
            clickZones.add(new ClickZone(new Rect(cx, cy, cardW, cardH), () -> host.switchTab(targetTab)));
        }
        int metricRows = (metrics.length + cols - 1) / cols;
        cursorY += metricRows * cardH + (metricRows - 1) * cardGap + 12;

        // 3. One-Click Quick Switch Profiles row
        graphics.text(font, I18n.get("packstudio.overview.quick_switch"), innerX, cursorY, theme.textPrimary(), false);
        cursorY += 13;

        if (profiles.isEmpty()) {
            GlassRenderUtil.drawGlassCard(graphics, innerX, cursorY, innerW, 30, cfg, false, 0.0F);
            graphics.text(
                    font,
                    TextFormatUtil.ellipsize(font, I18n.get("packstudio.profiles.empty") + " — Click to create one.", innerW - 16),
                    innerX + 8,
                    cursorY + 11,
                    theme.textMuted(),
                    false
            );
            clickZones.add(new ClickZone(new Rect(innerX, cursorY, innerW, 30), () -> host.switchTab(NavigationTab.PROFILES)));
            cursorY += 38;
        } else {
            int pillX = innerX;
            int pillRowH = 22;
            for (PackProfile profile : profiles) {
                boolean isActive = profile.id().equals(cfg.activeProfileId());
                String label = (isActive ? "● " : "○ ") + profile.name() + " (" + profile.enabledCount() + ")";
                int pillW = Math.min(innerW, Math.max(68, font.width(label) + 16));
                if (pillX + pillW > innerX + innerW && pillX > innerX) {
                    pillX = innerX;
                    cursorY += pillRowH + 5;
                }
                Rect pillRect = new Rect(pillX, cursorY, pillW, pillRowH);
                boolean hov = bounds.contains(mouseX, mouseY) && pillRect.contains(mouseX, mouseY);
                GlassRenderUtil.drawGlassCard(graphics, pillX, cursorY, pillW, pillRowH, cfg, isActive, hov ? 1.0F : 0.0F);
                graphics.centeredText(
                        font,
                        TextFormatUtil.ellipsize(font, label, pillW - 8),
                        pillX + pillW / 2,
                        cursorY + 7,
                        isActive ? theme.accentPrimary() : theme.textPrimary()
                );
                String pid = profile.id();
                clickZones.add(new ClickZone(pillRect, () -> host.triggerApplyProfile(pid)));
                pillX += pillW + 6;
            }
            cursorY += pillRowH + 12;
        }

        // 4. Active Priority Stack Preview (Top enabled packs)
        graphics.text(font, "Active Priority Order (Top Overrides Bottom)", innerX, cursorY, theme.textPrimary(), false);
        cursorY += 13;

        List<PackEntry> enabledOrdered = allPacks.stream()
                .filter(PackEntry::enabled)
                .sorted(java.util.Comparator.comparingInt(PackEntry::priorityPosition))
                .limit(5)
                .toList();

        PackIconCache icons = host.bootstrap().iconCache();
        for (PackEntry pack : enabledOrdered) {
            Rect rowRect = new Rect(innerX, cursorY, innerW, 24);
            boolean hov = bounds.contains(mouseX, mouseY) && rowRect.contains(mouseX, mouseY);
            boolean sel = host.selectedPack().map(p -> p.id().equals(pack.id())).orElse(false);
            GlassRenderUtil.drawGlassCard(graphics, rowRect.x(), rowRect.y(), rowRect.width(), rowRect.height(), cfg, sel, hov ? 0.8F : 0.0F);

            Identifier icon = icons.iconFor(pack.nativePack());
            graphics.blit(RenderPipelines.GUI_TEXTURED, icon, rowRect.x() + 4, rowRect.y() + 3, 0.0F, 0.0F, 18, 18, 18, 18);

            String rank = "#" + pack.priorityPosition();
            graphics.text(font, rank, rowRect.x() + 26, rowRect.y() + 8, theme.accentPrimary(), false);

            int titleX = rowRect.x() + 26 + font.width(rank) + 6;
            String badge = pack.compatibility().displayName().getString();
            int badgeW = font.width(badge) + 10;
            graphics.text(
                    font,
                    TextFormatUtil.ellipsize(font, pack.displayName(), Math.max(24, rowRect.right() - badgeW - 10 - titleX)),
                    titleX,
                    rowRect.y() + 8,
                    theme.textPrimary(),
                    false
            );

            int bx = rowRect.right() - badgeW - 5;
            GlassRenderUtil.drawBadgePill(graphics, bx, rowRect.y() + 6, badgeW, 12, pack.compatibility().badgeColor());
            graphics.centeredText(font, badge, bx + badgeW / 2, rowRect.y() + 8, pack.compatibility().badgeColor());

            String packId = pack.id();
            clickZones.add(new ClickZone(rowRect, () -> host.selectPack(packId)));
            cursorY += 27;
        }
        cursorY += 6;

        // 5. Smart Compatibility & Profile Integrity Diagnostics
        graphics.text(font, I18n.get("packstudio.overview.health_title"), innerX, cursorY, theme.textPrimary(), false);
        cursorY += 13;

        List<String> notices = new ArrayList<>(health.warnings());
        for (PackProfile profile : profiles) {
            var pHealth = ProfileValidator.evaluateAgainstLibrary(profile, allPacks);
            if (!pHealth.missingPacks().isEmpty()) {
                notices.add("Profile '" + profile.name() + "' references "
                        + pHealth.missingPacks().size() + " missing pack(s).");
            }
        }

        if (notices.isEmpty()) {
            GlassRenderUtil.drawGlassCard(graphics, innerX, cursorY, innerW, 24, cfg, false, 0.0F);
            graphics.fill(innerX + 2, cursorY + 4, innerX + 4, cursorY + 20, 0xFF34D399);
            graphics.text(
                    font,
                    TextFormatUtil.ellipsize(font, healthOkMessage(), innerW - 16),
                    innerX + 10,
                    cursorY + 8,
                    0xFF6EE7B7,
                    false
            );
            cursorY += 28;
        } else {
            for (String warning : notices) {
                GlassRenderUtil.drawGlassCard(graphics, innerX, cursorY, innerW, 22, cfg, false, 0.0F);
                graphics.fill(innerX + 2, cursorY + 4, innerX + 4, cursorY + 18, 0xFFFBBF24);
                graphics.text(
                        font,
                        TextFormatUtil.ellipsize(font, warning, innerW - 14),
                        innerX + 9,
                        cursorY + 7,
                        0xFFFDE68A,
                        false
                );
                cursorY += 25;
            }
        }

        contentHeight = (cursorY - startY);
        graphics.disableScissor();

        // Scrollbar indicator if needed
        if (maxScroll > 0) {
            int trackH = bounds.height() - 12;
            int thumbH = Math.max(18, Math.round(trackH * ((float) bounds.height() / (float) (contentHeight + 20))));
            int thumbY = bounds.y() + 6 + Math.round((trackH - thumbH) * (float) (scrollOffset / maxScroll));
            graphics.fill(bounds.right() - 4, bounds.y() + 6, bounds.right() - 2, bounds.bottom() - 6, 0x22FFFFFF);
            graphics.fill(bounds.right() - 4, thumbY, bounds.right() - 2, thumbY + thumbH, AnimationUtil.withAlpha(theme.accentPrimary(), 170));
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, Rect bounds) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        for (ClickZone zone : clickZones) {
            if (zone.bounds().contains(event.x(), event.y())) {
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

    /** Version-aware healthy-library message; falls back cleanly when a translation lacks the placeholder. */
    private static String healthOkMessage() {
        String message = I18n.get("packstudio.overview.health_ok", CompatibilityAnalyzer.currentGameVersion());
        return message.contains("%s")
                ? I18n.get("packstudio.overview.health_ok_versioned")
                : message;
    }
}
