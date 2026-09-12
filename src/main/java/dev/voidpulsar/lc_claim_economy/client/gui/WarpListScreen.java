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
import dev.voidpulsar.lc_claim_economy.client.ClientWarps;
import dev.voidpulsar.lc_claim_economy.network.RequestWarpsPayload;
import dev.voidpulsar.lc_claim_economy.network.WarpCreatePayload;
import dev.voidpulsar.lc_claim_economy.network.WarpDeletePayload;
import dev.voidpulsar.lc_claim_economy.network.WarpDto;
import dev.voidpulsar.lc_claim_economy.network.WarpSetPublicPayload;
import dev.voidpulsar.lc_claim_economy.network.WarpTeleportOtherPayload;
import dev.voidpulsar.lc_claim_economy.network.WarpTeleportOwnPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The {@code /lcce warp} GUI: your own warps (rename/move by re-using {@code
 * set}, toggle public, delete, teleport) and every public warp other players
 * have shared. Opened/refreshed purely by receiving a {@link
 * dev.voidpulsar.lc_claim_economy.network.SyncWarpsPayload} - see {@link
 * #openOrRefresh()} - so it only ever pops open in response to the bare
 * {@code /lcce warp} command or an action taken from inside the GUI itself,
 * never from plain command-line warp usage.
 */
public class WarpListScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = 22;
    private static final int HEADER_BUTTON_SIZE = 16;
    private static final int CONTENT_PAD = 8;
    private static final int SCROLLBAR_WIDTH = 8;
    private static final int CREATE_ROW_HEIGHT = 20;
    private static final int ENTRY_HEIGHT = 22;
    private static final int SECTION_HEADER_HEIGHT = 14;

    private SimpleButton closeButton;
    private SimpleButton refreshButton;
    private TextBox createNameBox;
    private SimpleButton createButton;
    private EntryPanel entryPanel;
    private PanelScrollBar scrollBar;

    public static void openOrRefresh() {
        WarpListScreen screen = ClientUtils.getCurrentGuiAs(WarpListScreen.class);
        if (screen != null) {
            screen.rebuild();
        } else {
            new WarpListScreen().openGui();
        }
    }

    @Override
    public boolean onInit() {
        setWidth(Math.min(getScreen().getGuiScaledWidth() - 20, 360));
        setHeight(Math.min(getScreen().getGuiScaledHeight() - 20, 320));
        return true;
    }

    @Override
    public void addWidgets() {
        closeButton = new SimpleButton(this, Component.translatable("gui.lc_claim_economy.warp.close"), Icons.CANCEL,
                (button, mouseButton) -> Minecraft.getInstance().setScreen(null));
        add(closeButton);

        refreshButton = new SimpleButton(this, Component.translatable("gui.lc_claim_economy.warp.refresh"), Icons.REFRESH,
                (button, mouseButton) -> PacketDistributor.sendToServer(new RequestWarpsPayload()));
        add(refreshButton);

        createNameBox = new TextBox(this);
        createNameBox.ghostText = Component.translatable("gui.lc_claim_economy.warp.create_ghost").getString();
        createNameBox.charLimit = 32;
        add(createNameBox);

        createButton = new SimpleButton(this, Component.translatable("gui.lc_claim_economy.warp.create"), Icons.ACCEPT,
                (button, mouseButton) -> submitCreate());
        add(createButton);

        entryPanel = new EntryPanel(this);
        entryPanel.setOnlyRenderWidgetsInside(true);
        entryPanel.setOnlyInteractWithWidgetsInside(true);
        add(entryPanel);

        scrollBar = new PanelScrollBar(this, entryPanel);
        add(scrollBar);
    }

    @Override
    public void alignWidgets() {
        closeButton.setPosAndSize(width - 5 - HEADER_BUTTON_SIZE, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        refreshButton.setPosAndSize(width - 7 - HEADER_BUTTON_SIZE * 2, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        int contentTop = HEADER_HEIGHT + 4;
        int createWidth = width - CONTENT_PAD * 2 - 64;
        createNameBox.setPosAndSize(CONTENT_PAD, contentTop, createWidth, CREATE_ROW_HEIGHT);
        createButton.setPosAndSize(CONTENT_PAD + createWidth + 4, contentTop + 1, 60, 18);

        int listTop = contentTop + CREATE_ROW_HEIGHT + 4;
        int contentHeight = height - listTop - CONTENT_PAD;
        int contentWidth = width - CONTENT_PAD * 2 - SCROLLBAR_WIDTH - 2;
        entryPanel.setPosAndSize(CONTENT_PAD, listTop, contentWidth, contentHeight);
        entryPanel.alignWidgets();
        scrollBar.setPosAndSize(CONTENT_PAD + contentWidth + 2, listTop, SCROLLBAR_WIDTH, contentHeight);
    }

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawBackground(graphics, theme, x, y, w, h);
        NordColors.POLAR_NIGHT_0.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, h - HEADER_HEIGHT - 6);
        NordColors.POLAR_NIGHT_2.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, 1);
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        theme.drawString(graphics, Component.translatable("gui.lc_claim_economy.warp.title"), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
        theme.drawString(
                graphics,
                Component.translatable(
                        "gui.lc_claim_economy.warp.subtitle",
                        ClientWarps.ownWarps().size(),
                        ClientWarps.maxWarps(),
                        ClientWarps.createCostCopper()
                ),
                x + w / 2,
                y + 15,
                NordColors.SNOW_STORM_1,
                Theme.CENTERED
        );
    }

    private void submitCreate() {
        String value = createNameBox.getText().trim();
        if (value.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new WarpCreatePayload(value));
        createNameBox.setText("");
    }

    private void rebuild() {
        entryPanel.refreshWidgets();
        entryPanel.alignWidgets();
        alignWidgets();
    }

    private static final class EntryPanel extends Panel {
        EntryPanel(BaseScreen screen) {
            super(screen);
        }

        @Override
        public void addWidgets() {
            if (!ClientWarps.enabled()) {
                add(new MessageRow(this, Component.translatable("gui.lc_claim_economy.warp.disabled").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                return;
            }

            add(new SectionHeaderRow(this, Component.translatable("gui.lc_claim_economy.warp.section_own")));
            if (ClientWarps.ownWarps().isEmpty()) {
                add(new MessageRow(this, Component.translatable("gui.lc_claim_economy.warp.none_own").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            } else {
                for (WarpDto warp : ClientWarps.ownWarps()) {
                    add(new OwnWarpRow(this, warp));
                }
            }

            add(new SectionHeaderRow(this, Component.translatable("gui.lc_claim_economy.warp.section_public")));
            if (ClientWarps.publicWarps().isEmpty()) {
                add(new MessageRow(this, Component.translatable("gui.lc_claim_economy.warp.none_public").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            } else {
                for (WarpDto warp : ClientWarps.publicWarps()) {
                    add(new PublicWarpRow(this, warp));
                }
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
                if (widget instanceof OwnWarpRow row) {
                    row.alignWidgets();
                } else if (widget instanceof PublicWarpRow row) {
                    row.alignWidgets();
                }
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

    private static final class MessageRow extends Button {
        MessageRow(Panel panel, Component title) {
            super(panel, title, Color4I.empty());
        }

        @Override
        public void onClicked(MouseButton button) {
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            theme.drawString(graphics, getTitle(), x + 6, y + 6, NordColors.SNOW_STORM_2, 0);
        }
    }

    private static final class OwnWarpRow extends Panel {
        private final WarpDto warp;

        OwnWarpRow(Panel panel, WarpDto warp) {
            super(panel);
            this.warp = warp;
        }

        @Override
        public void addWidgets() {
            add(new TpButton(this, () -> PacketDistributor.sendToServer(new WarpTeleportOwnPayload(warp.name()))));
            add(new TogglePublicButton(this, warp));
            add(new DeleteButton(this, warp));
        }

        @Override
        public void alignWidgets() {
            int buttonWidth = 34;
            int x = width - buttonWidth * 3 - 4;
            for (Widget widget : widgets) {
                widget.setPosAndSize(x, 2, buttonWidth, 18);
                x += buttonWidth + 2;
            }
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_2.withAlpha(isMouseOver() ? 220 : 180).draw(graphics, x, y, w, h);
            theme.drawString(graphics, Component.literal(warp.name()), x + 6, y + 3, NordColors.SNOW_STORM_0, 0);
            Component status = warp.isPublic()
                    ? Component.translatable("gui.lc_claim_economy.warp.status_public").withStyle(ChatFormatting.GREEN)
                    : Component.translatable("gui.lc_claim_economy.warp.status_private").withStyle(ChatFormatting.GRAY);
            Component detail = status.copy().append(Component.literal(" - " + warp.dimensionDisplay()));
            if (!warp.aliases().isEmpty()) {
                detail = detail.copy().append(Component.translatable("gui.lc_claim_economy.warp.aliases_suffix", String.join(", ", warp.aliases())));
            }
            theme.drawString(graphics, detail, x + 6, y + 12, NordColors.SNOW_STORM_1.withAlpha(200), 0);
        }
    }

    private static final class PublicWarpRow extends Panel {
        private final WarpDto warp;
        private TpButton tpButton;

        PublicWarpRow(Panel panel, WarpDto warp) {
            super(panel);
            this.warp = warp;
        }

        @Override
        public void addWidgets() {
            tpButton = new TpButton(this, () -> PacketDistributor.sendToServer(new WarpTeleportOtherPayload(warp.ownerId(), warp.name())));
            add(tpButton);
        }

        @Override
        public void alignWidgets() {
            tpButton.setPosAndSize(width - 36, 2, 34, 18);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_2.withAlpha(isMouseOver() ? 220 : 180).draw(graphics, x, y, w, h);
            theme.drawString(graphics, Component.literal(warp.name()), x + 6, y + 3, NordColors.SNOW_STORM_0, 0);
            Component detail = Component.translatable("gui.lc_claim_economy.warp.owned_by", warp.ownerName(), warp.dimensionDisplay());
            if (!warp.aliases().isEmpty()) {
                detail = detail.copy().append(Component.translatable("gui.lc_claim_economy.warp.aliases_suffix", String.join(", ", warp.aliases())));
            }
            theme.drawString(graphics, detail, x + 6, y + 12, NordColors.SNOW_STORM_1.withAlpha(200), 0);
        }
    }

    private static final class TpButton extends Button {
        private final Runnable action;

        TpButton(Panel panel, Runnable action) {
            super(panel, Component.translatable("gui.lc_claim_economy.warp.tp"), Color4I.empty());
            this.action = action;
        }

        @Override
        public void onClicked(MouseButton button) {
            action.run();
            Minecraft.getInstance().setScreen(null);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.FROST_2.withAlpha(isMouseOver() ? 220 : 170).draw(graphics, x, y, w, h);
            NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            super.draw(graphics, theme, x, y, w, h);
            theme.drawString(graphics, getTitle(), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.translatable("gui.lc_claim_economy.warp.tp_hint"));
        }
    }

    private static final class TogglePublicButton extends Button {
        private final WarpDto warp;

        TogglePublicButton(Panel panel, WarpDto warp) {
            super(panel, Component.translatable(warp.isPublic() ? "gui.lc_claim_economy.warp.make_private" : "gui.lc_claim_economy.warp.make_public"), Color4I.empty());
            this.warp = warp;
        }

        @Override
        public void onClicked(MouseButton button) {
            PacketDistributor.sendToServer(new WarpSetPublicPayload(warp.name(), !warp.isPublic()));
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            Color4I fill = warp.isPublic() ? NordColors.FROST_1 : NordColors.POLAR_NIGHT_1;
            fill.withAlpha(isMouseOver() ? 220 : 170).draw(graphics, x, y, w, h);
            NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            super.draw(graphics, theme, x, y, w, h);
            theme.drawString(graphics, getTitle(), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.translatable(warp.isPublic() ? "gui.lc_claim_economy.warp.make_private_hint" : "gui.lc_claim_economy.warp.make_public_hint"));
        }
    }

    private static final class DeleteButton extends Button {
        private final WarpDto warp;

        DeleteButton(Panel panel, WarpDto warp) {
            super(panel, Component.literal("X"), Color4I.empty());
            this.warp = warp;
        }

        @Override
        public void onClicked(MouseButton button) {
            PacketDistributor.sendToServer(new WarpDeletePayload(warp.name()));
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.RED.withAlpha(isMouseOver() ? 210 : 150).draw(graphics, x, y, w, h);
            NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            super.draw(graphics, theme, x, y, w, h);
            theme.drawString(graphics, getTitle(), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.translatable("gui.lc_claim_economy.warp.delete_hint"));
        }
    }
}
