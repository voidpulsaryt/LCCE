package dev.voidpulsar.lc_claim_economy.client;

import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

public final class EditConfigScreenUiHelper {
    public static final int TITLE_Y = 2;
    public static final int TITLE_HEIGHT = 14;
    public static final int NOTE_LINE1_Y = 18;
    public static final int NOTE_LINE2_Y = 28;
    public static final int NOTE_LINE_HEIGHT = 9;
    /** Extra height added to the default 20px FTB top panel. */
    public static final int TOP_PANEL_EXTRA_HEIGHT = NOTE_LINE2_Y + NOTE_LINE_HEIGHT - 20;

    private EditConfigScreenUiHelper() {
    }

    public static boolean isFtbChunksPropertiesTitle(Component title) {
        String text = title.getString().toLowerCase();
        return text.contains("ftb chunks")
                || text.contains("chunk")
                || text.contains("team properties");
    }

    public static void drawProtectionPricesNote(GuiGraphics graphics, Theme theme, int panelX, int panelY, int panelWidth) {
        int noteX = panelX + 6;
        int maxWidth = panelWidth - 12;

        Component line1 = Component.translatable("gui.lc_claim_economy.protection_prices_note_line1")
                .withStyle(ChatFormatting.GRAY);
        Component line2 = Component.translatable(
                "gui.lc_claim_economy.protection_prices_note_line2",
                SafeguardPriceDisplay.upkeepPeriodLabel(),
                SafeguardPriceDisplay.landChunkGroupSize()
        ).withStyle(ChatFormatting.GRAY);

        drawFittedString(graphics, theme, line1, noteX, panelY + NOTE_LINE1_Y, maxWidth);
        drawFittedString(graphics, theme, line2, noteX, panelY + NOTE_LINE2_Y, maxWidth);
    }

    /** Formats a config entry's raw value text into a {@link Component}, given the already-fetched value. */
    @FunctionalInterface
    public interface ValueTextFormatter {
        Component format(ConfigValue<?> config, Object value);
    }

    /**
     * Builds the full protection-config value-column line: the base value text (recolored to the
     * protection's active/inactive color when applicable), an active/inactive price suffix, and a
     * pending-change suffix - all three appended onto one line since FTB Library only renders a
     * single value string per config row. Shared by {@code TeamSettingsEntryButtonMixin}'s
     * value-column redraw and its {@code getValueStr} override, so both stay in sync.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Component buildFormattedLine(
            ConfigValue<?> config,
            Object value,
            ValueTextFormatter formatter
    ) {
        String propertyKey = propertyKey(config);

        Long basePrice = SafeguardPriceDisplay.pricePerChunkForConfigId(propertyKey);
        long price = basePrice != null ? SafeguardPriceDisplay.effectiveProtectionPrice(basePrice) : 0L;
        boolean hasPrice = basePrice != null && basePrice > 0;
        boolean hasPending = ClientQueuedChanges.hasPendingProperty(propertyKey);

        if (!hasPrice && !hasPending) {
            return formatter.format(config, value);
        }

        Component valueText = formatter.format(config, value);
        TextColor protectionColor = SafeguardPriceDisplay.protectionAllowBooleanColor(propertyKey, value);
        int valueRgb = protectionColor != null
                ? protectionColor.getValue()
                : ((ConfigValue) config).getColor(value).rgba() & 0xFFFFFF;
        MutableComponent styledValue = valueText.copy().withStyle(style ->
                style.getColor() == null
                        ? style.withColor(protectionColor != null ? protectionColor : TextColor.fromRgb(valueRgb))
                        : style);
        MutableComponent line = Component.empty().append(styledValue);

        if (hasPrice) {
            line.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
            if (SafeguardPriceDisplay.isLandProtectionPropertyKey(propertyKey)) {
                int groupSize = SafeguardPriceDisplay.landChunkGroupSize();
                if (SafeguardPriceDisplay.isActiveBillableSetting(propertyKey, value)) {
                    line.append(Component.translatable(
                            "gui.lc_claim_economy.protection_price_active_per_n_chunks",
                            SafeguardPriceDisplay.formatPricePerChunk(price),
                            groupSize
                    ).withStyle(ChatFormatting.GREEN));
                } else {
                    line.append(Component.translatable(
                            "gui.lc_claim_economy.protection_price_inactive_per_n_chunks",
                            SafeguardPriceDisplay.formatPricePerChunk(price),
                            groupSize
                    ).withStyle(ChatFormatting.GRAY));
                }
            } else if (SafeguardPriceDisplay.isActiveBillableSetting(propertyKey, value)) {
                line.append(Component.translatable(
                        "gui.lc_claim_economy.protection_price_active",
                        SafeguardPriceDisplay.formatPricePerChunk(price)
                ).withStyle(ChatFormatting.GREEN));
            } else {
                line.append(Component.translatable(
                        "gui.lc_claim_economy.protection_price_inactive",
                        SafeguardPriceDisplay.formatPricePerChunk(price)
                ).withStyle(ChatFormatting.GRAY));
            }
        }

        if (hasPending) {
            Object pendingValue = ClientQueuedChanges.getDisplayValue(propertyKey, value);
            if (!java.util.Objects.equals(pendingValue, value)) {
                Component pendingText = formatter.format(config, pendingValue);
                line.append(Component.literal(" ").append(
                        Component.translatable("gui.lc_claim_economy.pending_value", pendingText)
                                .withStyle(ChatFormatting.GOLD)
                ));
            } else {
                line.append(Component.literal(" ").append(
                        Component.translatable("gui.lc_claim_economy.pending")
                                .withStyle(ChatFormatting.GOLD)
                ));
            }
        }

        return line;
    }

    public static String propertyKey(ConfigValue<?> config) {
        return SafeguardPriceDisplay.protectionPropertyKey(config.id, config.getPath());
    }

    private static void drawFittedString(
            GuiGraphics graphics,
            Theme theme,
            Component text,
            int x,
            int y,
            int maxWidth
    ) {
        Component draw = theme.getStringWidth(text) <= maxWidth
                ? text
                : Component.literal(theme.trimStringToWidth(text.getString(), maxWidth - 6) + "...")
                .withStyle(text.getStyle());
        theme.drawString(graphics, draw, x, y, Color4I.rgb(0xAAAAAA), 0);
    }
}
