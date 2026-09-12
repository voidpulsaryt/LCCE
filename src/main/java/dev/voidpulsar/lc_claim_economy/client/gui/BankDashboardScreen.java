package dev.voidpulsar.lc_claim_economy.client.gui;

import dev.voidpulsar.lc_claim_economy.client.ClientClaimPrices;
import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import dev.voidpulsar.lc_claim_economy.network.RequestClaimPricesPayload;
import dev.voidpulsar.lc_claim_economy.util.MoneyMessageUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * A standalone bank/upkeep dashboard built entirely from vanilla
 * {@link Screen}/{@link GuiGraphics} widgets, with no dependency on
 * ftblibrary's UI toolkit (unlike {@link ClaimBreakdownScreen}). ftblibrary
 * is an optional dependency of this mod (see {@code ModCompat}) that an
 * OP&C-only install has no reason to also require, so a screen meant to
 * work on either backend can't be built on top of it.
 * <p>
 * Everything shown comes from {@link ClientClaimPrices}, which both the FTB
 * and OP&C server-side integrations populate identically via the same
 * {@code SyncClaimPricesPayload} - see {@code ClaimPriceSync}. That's what
 * lets this one screen work unmodified regardless of which claim mod is
 * actually installed.
 */
public final class BankDashboardScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_MIN_MARGIN = 20;
    private static final int HEADER_HEIGHT = 24;
    private static final int ROW_HEIGHT = 13;
    private static final int SECTION_GAP = 6;
    private static final int CONTENT_PAD = 10;
    private static final int BUTTON_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = BUTTON_HEIGHT + CONTENT_PAD * 2;

    // Hand-picked to sit alongside the Nord palette ClaimBreakdownScreen uses
    // on the FTB side (ftblibrary's NordColors), so the two screens feel like
    // the same mod even though this one can't depend on that class.
    // Fully opaque - this screen calls renderTransparentBackground() to blur
    // the world behind it, and any translucency here let that blurred world
    // bleed faintly through the panel, washing out the text on top of it.
    private static final int COLOR_PANEL_BG = 0xFF2E3440;
    private static final int COLOR_HEADER_BG = 0xFF3B4252;
    private static final int COLOR_DIVIDER = 0xFF434C5E;
    private static final int COLOR_TITLE = 0xFFECEFF4;
    private static final int COLOR_LABEL = 0xFFD8DEE9;
    private static final int COLOR_VALUE = 0xFFA3BE8C;
    private static final int COLOR_SECTION = 0xFF88C0D0;
    private static final int COLOR_MUTED = 0xFF6C7893;

    private int panelX;
    private int panelY;
    private int panelHeight;
    private final List<Row> rows = new ArrayList<>();

    public BankDashboardScreen() {
        super(Component.translatable("gui.lc_claim_economy.dashboard.title"));
    }

    @Override
    protected void init() {
        PacketDistributor.sendToServer(new RequestClaimPricesPayload());
        buildRows();

        panelHeight = Math.min(
                height - PANEL_MIN_MARGIN,
                HEADER_HEIGHT + CONTENT_PAD * 2 + rows.size() * ROW_HEIGHT + FOOTER_HEIGHT
        );
        panelX = (width - PANEL_WIDTH) / 2;
        panelY = (height - panelHeight) / 2;

        int buttonY = panelY + panelHeight - FOOTER_HEIGHT + CONTENT_PAD;
        int buttonWidth = 88;
        int gap = 8;
        int totalWidth = buttonWidth * 2 + gap;
        int buttonsX = panelX + (PANEL_WIDTH - totalWidth) / 2;

        addRenderableWidget(Button.builder(
                Component.translatable("gui.lc_claim_economy.dashboard.refresh"),
                button -> {
                    PacketDistributor.sendToServer(new RequestClaimPricesPayload());
                    rebuildWidgets();
                }
        ).bounds(buttonsX, buttonY, buttonWidth, BUTTON_HEIGHT).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose()
        ).bounds(buttonsX + buttonWidth + gap, buttonY, buttonWidth, BUTTON_HEIGHT).build());
    }

    private void buildRows() {
        rows.clear();

        if (!ClientClaimPrices.isSynced()) {
            rows.add(Row.text(Component.translatable("gui.lc_claim_economy.dashboard.loading")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            return;
        }

        rows.add(Row.section("gui.lc_claim_economy.dashboard.section_account"));
        rows.add(Row.value("gui.lc_claim_economy.dashboard.balance", ClientClaimPrices.currentBalanceText()));
        rows.add(Row.value("gui.lc_claim_economy.dashboard.claim_price", ClientClaimPrices.currentEffectiveClaimPrice()));
        rows.add(Row.value("gui.lc_claim_economy.dashboard.claimed_chunks",
                Component.literal(String.valueOf(ClientClaimPrices.claimedChunks()))));
        rows.add(Row.value("gui.lc_claim_economy.dashboard.free_chunks_left",
                Component.literal(String.valueOf(ClientClaimPrices.remainingFreeChunks()))));

        rows.add(Row.section("gui.lc_claim_economy.dashboard.section_upkeep"));
        rows.add(Row.value("gui.lc_claim_economy.dashboard.upkeep_period",
                Component.literal(periodLabel(ClientClaimPrices.upkeepPeriodMinutes()))));
        rows.add(Row.value("gui.lc_claim_economy.dashboard.forceload_price",
                MoneyMessageUtil.formatPrice(ClientClaimPrices.forceLoadUpkeepPrice())));

        rows.add(Row.section("gui.lc_claim_economy.dashboard.section_protection"));
        addProtectionRow("allow_mob_griefing", "message.lc_claim_economy.upkeep_detail.mob_grief");
        addProtectionRow("allow_explosions", "message.lc_claim_economy.upkeep_detail.explosions");
        addProtectionRow("allow_pvp", "message.lc_claim_economy.upkeep_detail.pvp");
        addProtectionRow("block_interact_mode", "gui.lc_claim_economy.claim_breakdown.block_interact");
        addProtectionRow("block_edit_mode", "gui.lc_claim_economy.claim_breakdown.block_edit");
        addProtectionRow("entity_interact_mode", "gui.lc_claim_economy.claim_breakdown.entity_interact");
        rows.add(Row.value(Component.translatable("gui.lc_claim_economy.claim_breakdown.land_group_size",
                ClientClaimPrices.landChunkGroupSize()), Component.empty()));

        rows.add(Row.section(activeBackendLabel()));
    }

    private void addProtectionRow(String propertyKey, String labelKey) {
        Long price = ClientClaimPrices.protectionPrice(propertyKey);
        if (price == null) {
            return;
        }
        rows.add(Row.value(Component.translatable(labelKey),
                Component.translatable("gui.lc_claim_economy.protection_price_active",
                        MoneyMessageUtil.formatPrice(price))));
    }

    private static String activeBackendLabel() {
        if (ModCompat.isFtbAvailable()) {
            return "gui.lc_claim_economy.dashboard.backend_ftb";
        }
        if (ModCompat.isOpcAvailable()) {
            return "gui.lc_claim_economy.dashboard.backend_opc";
        }
        return "gui.lc_claim_economy.dashboard.backend_none";
    }

    private static String periodLabel(int minutes) {
        if (minutes <= 0) {
            return "?";
        }
        if (minutes % 1440 == 0) {
            return (minutes / 1440) + "d";
        }
        if (minutes % 60 == 0) {
            return (minutes / 60) + "h";
        }
        return minutes + "m";
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);

        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + panelHeight, COLOR_PANEL_BG);
        graphics.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT, COLOR_HEADER_BG);
        graphics.fill(panelX, panelY + HEADER_HEIGHT, panelX + PANEL_WIDTH, panelY + HEADER_HEIGHT + 1, COLOR_DIVIDER);
        graphics.drawCenteredString(font, title, panelX + PANEL_WIDTH / 2, panelY + 8, COLOR_TITLE);

        int rowY = panelY + HEADER_HEIGHT + CONTENT_PAD;
        for (Row row : rows) {
            drawRow(graphics, row, panelX + CONTENT_PAD, rowY, PANEL_WIDTH - CONTENT_PAD * 2);
            rowY += ROW_HEIGHT;
        }

        int footerY = panelY + panelHeight - FOOTER_HEIGHT;
        graphics.fill(panelX, footerY, panelX + PANEL_WIDTH, footerY + 1, COLOR_DIVIDER);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawRow(GuiGraphics graphics, Row row, int x, int y, int width) {
        switch (row.type()) {
            case SECTION -> {
                graphics.drawString(font, row.label().copy().withStyle(ChatFormatting.BOLD), x, y + 2, COLOR_SECTION, false);
                graphics.fill(x, y + ROW_HEIGHT - 3, x + width, y + ROW_HEIGHT - 2, COLOR_DIVIDER);
            }
            case TEXT -> graphics.drawString(font, row.label(), x, y + 2, COLOR_MUTED, false);
            case VALUE -> {
                graphics.drawString(font, row.label(), x, y + 2, COLOR_LABEL, false);
                MutableComponent value = row.value();
                if (value != null && !value.getString().isEmpty()) {
                    int valueWidth = font.width(value);
                    graphics.drawString(font, value, x + width - valueWidth, y + 2, COLOR_VALUE, false);
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Re-pulls the latest {@link ClientClaimPrices} data into the row list, if this screen is currently open. */
    public void refresh() {
        rebuildWidgets();
    }

    private enum RowType {
        SECTION,
        TEXT,
        VALUE
    }

    private record Row(RowType type, MutableComponent label, MutableComponent value) {
        static Row section(String labelKey) {
            return new Row(RowType.SECTION, Component.translatable(labelKey), null);
        }

        static Row text(MutableComponent label) {
            return new Row(RowType.TEXT, label, null);
        }

        static Row value(String labelKey, Component value) {
            return value(Component.translatable(labelKey), value);
        }

        static Row value(Component label, Component value) {
            return new Row(RowType.VALUE, label.copy(), value.copy());
        }
    }
}
