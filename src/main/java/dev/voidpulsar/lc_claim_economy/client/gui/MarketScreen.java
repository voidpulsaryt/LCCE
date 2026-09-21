package dev.voidpulsar.lc_claim_economy.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.voidpulsar.lc_claim_economy.client.ClientMarket;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.EmptyMessageRow;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.LcScreenChrome;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.PillButton;
import dev.voidpulsar.lc_claim_economy.network.MarketBuyPayload;
import dev.voidpulsar.lc_claim_economy.network.MarketCancelPayload;
import dev.voidpulsar.lc_claim_economy.network.MarketListingDto;
import dev.voidpulsar.lc_claim_economy.network.MarketSellPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestMarketPayload;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * The {@code /lcce market} GUI: a status panel for the chunk you're currently standing in (list
 * it, cancel your own listing, or buy someone else's - exactly what {@code /lcce market
 * sell|cancel|buy} already do from the command line, since every one of those always targets
 * wherever you're standing when the action actually runs, GUI or not) plus a scrollable browse
 * list of every chunk currently for sale server-wide. Opened/refreshed purely by receiving a
 * {@link dev.voidpulsar.lc_claim_economy.network.SyncMarketPayload}, mirroring {@link
 * WarpListScreen}, so plain command-line market usage never pops this open uninvited.
 */
public class MarketScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = LcScreenChrome.HEADER_HEIGHT;
    private static final int HEADER_BUTTON_SIZE = LcScreenChrome.HEADER_BUTTON_SIZE;
    private static final int CONTENT_PAD = LcScreenChrome.CONTENT_PAD;
    private static final int SCROLLBAR_WIDTH = LcScreenChrome.SCROLLBAR_WIDTH;
    private static final int STATUS_PANEL_HEIGHT = 40;
    private static final int ENTRY_HEIGHT = 22;
    private static final int SECTION_HEADER_HEIGHT = 14;

    private SimpleButton closeButton;
    private SimpleButton refreshButton;
    private StatusPanel statusPanel;
    private ListingsPanel listingsPanel;
    private PanelScrollBar scrollBar;

    public static void openOrRefresh() {
        MarketScreen screen = ClientUtils.getCurrentGuiAs(MarketScreen.class);
        if (screen != null) {
            screen.rebuild();
        } else {
            new MarketScreen().openGui();
        }
    }

    @Override
    public boolean onInit() {
        setWidth(LcScreenChrome.clamped(getScreen().getGuiScaledWidth(), 360));
        setHeight(LcScreenChrome.clamped(getScreen().getGuiScaledHeight(), 340));
        return true;
    }

    @Override
    public void addWidgets() {
        closeButton = new SimpleButton(this, Component.translatable("gui.lc_claim_economy.market.close"), Icons.CANCEL,
                (button, mouseButton) -> Minecraft.getInstance().setScreen(null));
        add(closeButton);

        refreshButton = new SimpleButton(this, Component.translatable("gui.lc_claim_economy.market.refresh"), Icons.REFRESH,
                (button, mouseButton) -> PacketDistributor.sendToServer(new RequestMarketPayload()));
        add(refreshButton);

        statusPanel = new StatusPanel(this);
        add(statusPanel);

        listingsPanel = new ListingsPanel(this);
        listingsPanel.setOnlyRenderWidgetsInside(true);
        listingsPanel.setOnlyInteractWithWidgetsInside(true);
        add(listingsPanel);

        scrollBar = new PanelScrollBar(this, listingsPanel);
        add(scrollBar);
    }

    @Override
    public void alignWidgets() {
        closeButton.setPosAndSize(width - 5 - HEADER_BUTTON_SIZE, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        refreshButton.setPosAndSize(width - 7 - HEADER_BUTTON_SIZE * 2, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        int statusTop = HEADER_HEIGHT + 4;
        statusPanel.setPosAndSize(CONTENT_PAD, statusTop, width - CONTENT_PAD * 2, STATUS_PANEL_HEIGHT);
        statusPanel.alignWidgets();

        int listTop = statusTop + STATUS_PANEL_HEIGHT + 6;
        int contentHeight = height - listTop - CONTENT_PAD;
        int contentWidth = width - CONTENT_PAD * 2 - SCROLLBAR_WIDTH - 2;
        listingsPanel.setPosAndSize(CONTENT_PAD, listTop, contentWidth, contentHeight);
        listingsPanel.alignWidgets();
        scrollBar.setPosAndSize(CONTENT_PAD + contentWidth + 2, listTop, SCROLLBAR_WIDTH, contentHeight);
    }

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawBackground(graphics, theme, x, y, w, h);
        LcScreenChrome.drawContentBackground(graphics, x, y, w, h);
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(graphics, Component.translatable("gui.lc_claim_economy.market.title"), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
        theme.drawString(
                graphics,
                Component.translatable("gui.lc_claim_economy.market.subtitle", ClientMarket.listings().size()),
                x + w / 2,
                y + 15,
                NordColors.SNOW_STORM_1,
                Theme.CENTERED
        );
    }

    private void rebuild() {
        statusPanel.refreshWidgets();
        statusPanel.alignWidgets();
        listingsPanel.refreshWidgets();
        listingsPanel.alignWidgets();
        alignWidgets();
    }

    /** The "act on the chunk you're standing in" row - shape depends entirely on that chunk's current claim/listing state. */
    private static final class StatusPanel extends Panel {
        private TextBox priceBox;

        StatusPanel(BaseScreen screen) {
            super(screen);
        }

        @Override
        public void addWidgets() {
            if (!ClientMarket.currentChunkIsClaim()) {
                return;
            }
            if (ClientMarket.currentChunkIsOwnClaim() && !ClientMarket.currentChunkListed()) {
                priceBox = new TextBox(this);
                priceBox.ghostText = Component.translatable("gui.lc_claim_economy.market.price_ghost").getString();
                priceBox.charLimit = 12;
                add(priceBox);
                add(new SellButton(this, () -> submitSell()));
            } else if (ClientMarket.currentChunkIsOwnClaim()) {
                add(new CancelListingButton(this));
            } else if (ClientMarket.currentChunkListed()) {
                add(new BuyButton(this));
            }
        }

        @Override
        public void alignWidgets() {
            if (priceBox != null) {
                int buttonWidth = 60;
                int boxWidth = width - buttonWidth - 4;
                priceBox.setPosAndSize(0, 20, boxWidth, 18);
                for (Widget widget : widgets) {
                    if (widget != priceBox) {
                        widget.setPosAndSize(boxWidth + 4, 20, buttonWidth, 18);
                    }
                }
                return;
            }
            for (Widget widget : widgets) {
                widget.setPosAndSize(0, 20, 100, 18);
            }
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_1.withAlpha(180).draw(graphics, x, y, w, h);
            NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
            theme.drawString(graphics, statusLine(), x + 4, y + 4, NordColors.SNOW_STORM_0, 0);
        }

        private Component statusLine() {
            if (!ClientMarket.currentChunkIsClaim()) {
                return Component.translatable("gui.lc_claim_economy.market.status_unclaimed").withStyle(ChatFormatting.GRAY);
            }
            if (ClientMarket.currentChunkIsOwnClaim() && ClientMarket.currentChunkListed()) {
                return Component.translatable("gui.lc_claim_economy.market.status_own_listed",
                        priceText(ClientMarket.currentChunkListingPriceCopper())).withStyle(ChatFormatting.GOLD);
            }
            if (ClientMarket.currentChunkIsOwnClaim()) {
                return Component.translatable("gui.lc_claim_economy.market.status_own_unlisted").withStyle(ChatFormatting.AQUA);
            }
            if (ClientMarket.currentChunkListed()) {
                return Component.translatable("gui.lc_claim_economy.market.status_for_sale",
                        priceText(ClientMarket.currentChunkListingPriceCopper())).withStyle(ChatFormatting.GREEN);
            }
            return Component.translatable("gui.lc_claim_economy.market.status_not_for_sale").withStyle(ChatFormatting.GRAY);
        }

        private void submitSell() {
            if (priceBox == null) {
                return;
            }
            long priceCopper = parseCopper(priceBox.getText());
            if (priceCopper <= 0) {
                return;
            }
            PacketDistributor.sendToServer(new MarketSellPayload(priceCopper));
            priceBox.setText("");
        }

        private static long parseCopper(String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException notANumber) {
                return 0L;
            }
        }
    }

    private static final class ListingsPanel extends Panel {
        ListingsPanel(BaseScreen screen) {
            super(screen);
        }

        @Override
        public void addWidgets() {
            add(new SectionHeaderRow(this, Component.translatable("gui.lc_claim_economy.market.section_browse")));
            List<MarketListingDto> listings = ClientMarket.listings();
            if (listings.isEmpty()) {
                add(new EmptyMessageRow(this, Component.translatable("gui.lc_claim_economy.market.browse_empty").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                return;
            }
            for (MarketListingDto listing : listings) {
                add(new ListingRow(this, listing));
            }
        }

        @Override
        public void alignWidgets() {
            int y = 0;
            for (Widget widget : widgets) {
                int rowHeight = widget instanceof SectionHeaderRow ? SECTION_HEADER_HEIGHT : ENTRY_HEIGHT;
                widget.setPos(0, y);
                widget.setWidth(width);
                widget.setHeight(rowHeight);
                y += rowHeight + 2;
            }
        }
    }

    private static final class SectionHeaderRow extends Button {
        SectionHeaderRow(Panel panel, Component title) {
            super(panel, title, Color4I.empty());
        }

        @Override
        public void onClicked(MouseButton button) {
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_1.draw(graphics, x, y, w, h);
            NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            super.draw(graphics, theme, x, y, w, h);
            theme.drawString(graphics, getTitle(), x + 6, y + 3, NordColors.SNOW_STORM_1, 0);
        }
    }

    private static final class ListingRow extends Button {
        private final MarketListingDto listing;

        ListingRow(Panel panel, MarketListingDto listing) {
            super(panel, Component.empty(), Color4I.empty());
            this.listing = listing;
        }

        @Override
        public void onClicked(MouseButton button) {
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            Color4I fill = listing.listedByViewersTeam() ? NordColors.FROST_3 : NordColors.POLAR_NIGHT_2;
            fill.withAlpha(isMouseOver() ? 220 : 170).draw(graphics, x, y, w, h);
            theme.drawString(graphics, priceText(listing.priceCopper()), x + 6, y + 3, NordColors.SNOW_STORM_0, 0);
            Component location = Component.translatable(
                    "gui.lc_claim_economy.market.browse_line_detail",
                    listing.x(), listing.z(), listing.dimensionDisplay(), listing.sellerName()
            );
            theme.drawString(graphics, location, x + 6, y + 12, NordColors.SNOW_STORM_1.withAlpha(200), 0);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            if (listing.listedByViewersTeam()) {
                list.add(Component.translatable("gui.lc_claim_economy.market.browse_line_own_hint"));
            } else {
                list.add(Component.translatable("gui.lc_claim_economy.market.browse_line_hint"));
            }
        }
    }

    private static final class SellButton extends PillButton {
        private final Runnable action;

        SellButton(Panel panel, Runnable action) {
            super(panel, Component.translatable("gui.lc_claim_economy.market.sell"), 220, 170);
            this.action = action;
        }

        @Override
        protected Color4I fillColor() {
            return NordColors.FROST_2;
        }

        @Override
        public void onClicked(MouseButton button) {
            action.run();
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.translatable("gui.lc_claim_economy.market.sell_hint"));
        }
    }

    private static final class CancelListingButton extends PillButton {
        CancelListingButton(Panel panel) {
            super(panel, Component.translatable("gui.lc_claim_economy.market.cancel"), 210, 150);
        }

        @Override
        protected Color4I fillColor() {
            return NordColors.RED;
        }

        @Override
        public void onClicked(MouseButton button) {
            PacketDistributor.sendToServer(new MarketCancelPayload());
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.translatable("gui.lc_claim_economy.market.cancel_hint"));
        }
    }

    private static final class BuyButton extends PillButton {
        BuyButton(Panel panel) {
            super(panel, Component.translatable("gui.lc_claim_economy.market.buy"), 220, 170);
        }

        @Override
        protected Color4I fillColor() {
            return NordColors.GREEN;
        }

        @Override
        public void onClicked(MouseButton button) {
            PacketDistributor.sendToServer(new MarketBuyPayload());
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.translatable("gui.lc_claim_economy.market.buy_hint"));
        }
    }

    private static Component priceText(long copper) {
        return CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(copper));
    }
}
