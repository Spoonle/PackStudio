package com.packstudio.client.screen.components;

import com.mojang.blaze3d.platform.InputConstants;
import com.packstudio.core.PackStudioBootstrap;
import com.packstudio.model.PackStudioConfig;
import com.packstudio.util.GlassRenderUtil;
import com.packstudio.util.TextFormatUtil;
import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Lightweight modal dialog for confirmations and text entry (Create/Rename Profile, Import JSON).
 */
public final class GlassModalDialog {
    private final String title;
    private final String subtitle;
    private final boolean hasInput;
    private final String confirmLabel;
    private final GlassButton.Variant confirmVariant;
    private final Consumer<String> onConfirm;
    private final Runnable onCancel;

    private EditBox inputBox;
    private GlassButton confirmButton;
    private GlassButton cancelButton;
    private int boxX;
    private int boxY;
    private int boxWidth;
    private int boxHeight;
    private String initialValue = "";
    private String placeholder = "";
    private int maxInputLength = 96;

    private GlassModalDialog(
            String title,
            String subtitle,
            boolean hasInput,
            String confirmLabel,
            GlassButton.Variant confirmVariant,
            Consumer<String> onConfirm,
            Runnable onCancel
    ) {
        this.title = title;
        this.subtitle = subtitle;
        this.hasInput = hasInput;
        this.confirmLabel = confirmLabel;
        this.confirmVariant = confirmVariant;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    public static GlassModalDialog confirm(
            String title,
            String message,
            String confirmLabel,
            GlassButton.Variant variant,
            Runnable onConfirm,
            Runnable onCancel
    ) {
        return new GlassModalDialog(title, message, false, confirmLabel, variant, _ -> onConfirm.run(), onCancel);
    }

    public static GlassModalDialog promptText(
            String title,
            String subtitle,
            String initialValue,
            String placeholder,
            int maxLength,
            String confirmLabel,
            Consumer<String> onConfirm,
            Runnable onCancel
    ) {
        GlassModalDialog dialog = new GlassModalDialog(
                title,
                subtitle,
                true,
                confirmLabel,
                GlassButton.Variant.PRIMARY,
                onConfirm,
                onCancel
        );
        dialog.initialValue = initialValue == null ? "" : initialValue;
        dialog.placeholder = placeholder == null ? "" : placeholder;
        dialog.maxInputLength = Math.max(16, maxLength);
        return dialog;
    }

    public void layout(Font font, int screenWidth, int screenHeight) {
        this.boxWidth = Math.min(300, Math.max(200, screenWidth - 40));
        this.boxHeight = hasInput ? 118 : 98;
        this.boxX = (screenWidth - boxWidth) / 2;
        this.boxY = (screenHeight - boxHeight) / 2;

        if (hasInput) {
            String prev = inputBox != null ? inputBox.getValue() : initialValue;
            this.inputBox = new EditBox(font, boxX + 14, boxY + 52, boxWidth - 28, 20, Component.literal(title));
            this.inputBox.setMaxLength(maxInputLength);
            this.inputBox.setHint(Component.literal(placeholder));
            this.inputBox.setValue(prev);
            this.inputBox.setFocused(true);
        }

        int btnW = (boxWidth - 36) / 2;
        int btnY = boxY + boxHeight - 28;
        this.cancelButton = new GlassButton(
                boxX + 14,
                btnY,
                btnW,
                20,
                CommonComponents.GUI_CANCEL,
                GlassButton.Variant.SECONDARY,
                _ -> onCancel.run()
        );
        this.confirmButton = new GlassButton(
                boxX + 22 + btnW,
                btnY,
                btnW,
                20,
                Component.literal(confirmLabel),
                confirmVariant,
                _ -> submit()
        );
    }

    private void submit() {
        String val = inputBox != null ? inputBox.getValue() : "";
        onConfirm.accept(val);
    }

    public void render(GuiGraphicsExtractor graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY, float delta) {
        PackStudioConfig cfg = PackStudioBootstrap.get().configManager().config();
        graphics.fill(0, 0, screenWidth, screenHeight, 0xA803060C);
        GlassRenderUtil.drawGlassPanel(graphics, boxX, boxY, boxWidth, boxHeight, cfg, true);

        graphics.text(font, TextFormatUtil.ellipsize(font, title, boxWidth - 28), boxX + 14, boxY + 12, cfg.theme().textPrimary(), false);
        TextFormatUtil.drawWrappedLines(graphics, font, subtitle, boxX + 14, boxY + 26, boxWidth - 28, 2, cfg.theme().textMuted());

        if (inputBox != null) {
            inputBox.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        if (cancelButton != null) {
            cancelButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }
        if (confirmButton != null) {
            confirmButton.extractRenderState(graphics, mouseX, mouseY, delta);
        }
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return true;
        }
        if (inputBox != null && inputBox.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (cancelButton != null && cancelButton.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (confirmButton != null && confirmButton.mouseClicked(event, doubleClick)) {
            return true;
        }
        return true; // Modal consumes background clicks
    }

    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape()) {
            onCancel.run();
            return true;
        }
        if (event.isConfirmation()) {
            submit();
            return true;
        }
        return inputBox != null && inputBox.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return inputBox != null && inputBox.charTyped(event);
    }

    public void setInputText(String text) {
        if (inputBox != null) {
            inputBox.setValue(text);
        }
    }
}
