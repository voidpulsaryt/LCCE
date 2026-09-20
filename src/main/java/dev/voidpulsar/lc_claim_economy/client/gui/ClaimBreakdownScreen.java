package dev.voidpulsar.lc_claim_economy.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.LcScreenChrome;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPricing;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;

/**
 * A dedicated popup showing the full price picture: current claim price and
 * balance, a bulk-claim projection table, and the full periodic upkeep
 * breakdown (force-loading, build protections, land protections).
 * <p>
 * Everything shown here comes from data the server already syncs to the
 * client via {@code PricingBroadcastPayload} ({@link ClientPricingCache}), so
 * opening this screen requires no extra network round trip.
 */
public class ClaimBreakdownScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = LcScreenChrome.HEADER_HEIGHT;
    private static final int HEADER_BUTTON_SIZE = LcScreenChrome.HEADER_BUTTON_SIZE;
    private static final int CONTENT_PAD = LcScreenChrome.CONTENT_PAD;
    private static final int SCROLLBAR_WIDTH = LcScreenChrome.SCROLLBAR_WIDTH;
    private static final int ROW_HEIGHT = 16;
    private static final int SECTION_HEADER_HEIGHT = 18;
    private static final int LINK_ROW_HEIGHT = 22;

    private static final int[] PROJECTION_SIZES = {5, 10, 25, 50};

    private final BaseScreen parent;
    private SimpleButton backButton;
    private ContentPanel contentPanel;
    private PanelScrollBar scrollBar;

    public ClaimBreakdownScreen(BaseScreen parent) {
        this.parent = parent;
    }

    @Override
    public boolean onInit() {
        setWidth(LcScreenChrome.clamped(getScreen().getGuiScaledWidth(), 340));
        setHeight(LcScreenChrome.clamped(getScreen().getGuiScaledHeight(), 280));
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(
                this,
                Component.translatable("gui.back"),
                Icons.BACK,
                (button, mouseButton) -> parent.openGui()
        );
        add(backButton);

        contentPanel = new ContentPanel(this);
        contentPanel.setOnlyRenderWidgetsInside(true);
        contentPanel.setOnlyInteractWithWidgetsInside(true);
        add(contentPanel);

        scrollBar = new PanelScrollBar(this, contentPanel);
        add(scrollBar);
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        int contentTop = HEADER_HEIGHT + 4;
        int contentHeight = height - contentTop - CONTENT_PAD;
        int contentWidth = width - CONTENT_PAD * 2 - SCROLLBAR_WIDTH - 2;

        contentPanel.setPosAndSize(CONTENT_PAD, contentTop, contentWidth, contentHeight);
        contentPanel.alignWidgets();
        scrollBar.setPosAndSize(CONTENT_PAD + contentWidth + 2, contentTop, SCROLLBAR_WIDTH, contentHeight);
    }

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawBackground(graphics, theme, x, y, w, h);
        LcScreenChrome.drawContentBackground(graphics, x, y, w, h);
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(
                graphics,
                Component.translatable("gui.lc_claim_economy.claim_breakdown.title"),
                x + w / 2,
                y + 7,
                NordColors.SNOW_STORM_0,
                Theme.CENTERED
        );
    }

    private interface Row {
        int rowHeight();
    }

    private static final class ContentPanel extends Panel {
        private final List<Row> rows = new ArrayList<>();

        ContentPanel(BaseScreen screen) {
            super(screen);
        }

        @Override
        public void addWidgets() {
            widgets.clear();
            rows.clear();

            if (!ClientPricingCache.isSynced()) {
                addTextRow("gui.lc_claim_economy.claim_breakdown.not_synced");
                return;
            }

            addSectionHeader("gui.lc_claim_economy.claim_breakdown.section_claim");
            addValueRow("gui.lc_claim_economy.claim_breakdown.claim_price", ClientPricingCache.currentEffectiveClaimPrice());
            addValueRow("gui.lc_claim_economy.claim_breakdown.free_chunks_left",
                    Component.literal(String.valueOf(ClientPricingCache.remainingFreeChunks())));
            addValueRow("gui.lc_claim_economy.claim_breakdown.claimed_chunks",
                    Component.literal(String.valueOf(ClientPricingCache.claimedChunks())));
            addValueRow("gui.lc_claim_economy.claim_breakdown.balance", ClientPricingCache.currentBalanceText());

            addSectionHeader("gui.lc_claim_economy.claim_breakdown.section_bulk");
            for (int size : PROJECTION_SIZES) {
                long cost = ClientPricingCache.projectedBulkClaimCopper(size);
                addValueRow(
                        Component.translatable("gui.lc_claim_economy.claim_breakdown.bulk_claim_n", size),
                        CurrencyTextFormat.formatPrice(cost)
                );
            }

            addSectionHeader("gui.lc_claim_economy.claim_breakdown.section_upkeep");
            addValueRow(Component.translatable("gui.lc_claim_economy.claim_breakdown.upkeep_period"),
                    Component.literal(periodLabel(ClientPricingCache.upkeepPeriodMinutes())));
            addValueRow("gui.lc_claim_economy.claim_breakdown.forceload_price",
                    CurrencyTextFormat.formatPrice(ClientPricingCache.forceLoadUpkeepPrice()));

            addSectionHeader("gui.lc_claim_economy.claim_breakdown.section_build_protection");
            addProtectionRow("allow_mob_griefing", "message.lc_claim_economy.upkeep_detail.mob_grief");
            addProtectionRow("allow_explosions", "message.lc_claim_economy.upkeep_detail.explosions");
            addProtectionRow("allow_pvp", "message.lc_claim_economy.upkeep_detail.pvp");
            addProtectionRow("block_interact_mode", "gui.lc_claim_economy.claim_breakdown.block_interact");
            addProtectionRow("block_edit_mode", "gui.lc_claim_economy.claim_breakdown.block_edit");
            addProtectionRow("entity_interact_mode", "gui.lc_claim_economy.claim_breakdown.entity_interact");

            addSectionHeader("gui.lc_claim_economy.claim_breakdown.section_land_protection");
            addValueRow(
                    Component.translatable("gui.lc_claim_economy.claim_breakdown.land_group_size",
                            SafeguardPricing.landChunkGroupSize()),
                    Component.empty()
            );
            addProtectionRow("block_interact_mode", "gui.lc_claim_economy.claim_breakdown.block_interact");
            addProtectionRow("block_edit_mode", "gui.lc_claim_economy.claim_breakdown.block_edit");

            addRow(new UpkeepDetailsLinkRow(this));
        }

        private void addSectionHeader(String key) {
            addRow(new SectionHeaderRow(this, Component.translatable(key)));
        }

        private void addTextRow(String key) {
            addRow(new TextRow(this, Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        }

        private void addValueRow(String labelKey, Component value) {
            addValueRow(Component.translatable(labelKey), value);
        }

        private void addValueRow(Component label, Component value) {
            addRow(new ValueRow(this, label, value));
        }

        private void addProtectionRow(String propertyKey, String labelKey) {
            Long price = ClientPricingCache.protectionPrice(propertyKey);
            if (price == null) {
                price = ClientPricingCache.defaultProtectionPrice(propertyKey);
            }
            if (price == null) {
                return;
            }
            addValueRow(Component.translatable(labelKey),
                    Component.translatable("gui.lc_claim_economy.protection_price_active",
                            CurrencyTextFormat.formatPrice(price)));
        }

        private void addRow(Row row) {
            rows.add(row);
            add((Widget) row);
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
        public void alignWidgets() {
            int y = 0;
            for (Row row : rows) {
                Widget widget = (Widget) row;
                widget.setPos(0, y);
                widget.setWidth(width);
                int h = row.rowHeight();
                widget.setHeight(h);
                y += h;
            }
        }
    }

    private static class SectionHeaderRow extends Button implements Row {
        private final Component label;

        SectionHeaderRow(Panel panel, Component label) {
            super(panel, label, Color4I.empty());
            this.label = label;
        }

        @Override
        public int rowHeight() {
            return SECTION_HEADER_HEIGHT;
        }

        @Override
        public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_1.draw(graphics, x, y + h - 2, w, 1);
            theme.drawString(graphics, label.copy().withStyle(ChatFormatting.BOLD), x + 2, y + 5, NordColors.FROST_2, 0);
        }
    }

    private static class ValueRow extends Button implements Row {
        private final Component label;
        private final Component value;

        ValueRow(Panel panel, Component label, Component value) {
            super(panel, label, Color4I.empty());
            this.label = label;
            this.value = value;
        }

        @Override
        public int rowHeight() {
            return ROW_HEIGHT;
        }

        @Override
        public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            theme.drawString(graphics, label, x + 6, y + 3, NordColors.SNOW_STORM_2, 0);
            if (value != null && !value.getString().isEmpty()) {
                int valueX = x + w - 6 - theme.getStringWidth(value);
                theme.drawString(graphics, value, valueX, y + 3, NordColors.SNOW_STORM_0, 0);
            }
        }
    }

    private static class TextRow extends Button implements Row {
        private final Component text;

        TextRow(Panel panel, Component text) {
            super(panel, text, Color4I.empty());
            this.text = text;
        }

        @Override
        public int rowHeight() {
            return ROW_HEIGHT;
        }

        @Override
        public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            theme.drawString(graphics, text, x + 6, y + 3, NordColors.SNOW_STORM_2, 0);
        }
    }

    private static class UpkeepDetailsLinkRow extends Button implements Row {
        private static final Component LINK = Component.translatable("message.lc_claim_economy.upkeep_see_more")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lcce upkeep_details"))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.lc_claim_economy.upkeep_see_more_hover")
                                        .withStyle(ChatFormatting.GRAY)
                        )));

        UpkeepDetailsLinkRow(Panel panel) {
            super(panel, LINK, Color4I.empty());
        }

        @Override
        public int rowHeight() {
            return LINK_ROW_HEIGHT;
        }

        @Override
        public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.connection.sendCommand("lc_claim_economy upkeep_details");
            }
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_1.draw(graphics, x, y, w, 1);
            theme.drawString(graphics, LINK, x + w / 2, y + 6, NordColors.SNOW_STORM_0, Theme.CENTERED);
        }
    }
}
