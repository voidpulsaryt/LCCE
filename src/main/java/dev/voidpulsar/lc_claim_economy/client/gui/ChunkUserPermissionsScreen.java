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
import dev.voidpulsar.lc_claim_economy.client.ClientChunkUserPermissions;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.EmptyMessageRow;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.LcScreenChrome;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.PillButton;
import dev.voidpulsar.lc_claim_economy.network.ChunkUserPermissionEntry;
import dev.voidpulsar.lc_claim_economy.network.RequestChunkUserPermsPayload;
import dev.voidpulsar.lc_claim_economy.network.SetChunkUserPermsPayload;
import dev.voidpulsar.lc_claim_economy.service.ChunkPermissionFlags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class ChunkUserPermissionsScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = LcScreenChrome.HEADER_HEIGHT;
    private static final int HEADER_BUTTON_SIZE = LcScreenChrome.HEADER_BUTTON_SIZE;
    private static final int CONTENT_PAD = LcScreenChrome.CONTENT_PAD;
    private static final int SCROLLBAR_WIDTH = LcScreenChrome.SCROLLBAR_WIDTH;
    private static final int ADD_ROW_HEIGHT = 20;
    private static final int ENTRY_HEIGHT = 22;
    private static final int HEADER_ROW_HEIGHT = 18;

    private final BaseScreen parent;
    private final String chunkKey;

    private SimpleButton backButton;
    private SimpleButton refreshButton;
    private TextBox addPlayerBox;
    private SimpleButton addPlayerButton;
    private EntryPanel entryPanel;
    private PanelScrollBar scrollBar;

    public ChunkUserPermissionsScreen(BaseScreen parent, String chunkKey) {
        this.parent = parent;
        this.chunkKey = chunkKey;
    }

    public static void refreshIfOpen() {
        ChunkUserPermissionsScreen screen = dev.ftb.mods.ftblibrary.util.client.ClientUtils.getCurrentGuiAs(ChunkUserPermissionsScreen.class);
        if (screen != null) {
            screen.rebuild();
        }
    }

    @Override
    public boolean onInit() {
        setWidth(LcScreenChrome.clamped(getScreen().getGuiScaledWidth(), 360));
        setHeight(LcScreenChrome.clamped(getScreen().getGuiScaledHeight(), 300));
        PacketDistributor.sendToServer(new RequestChunkUserPermsPayload(chunkKey));
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> parent.openGui());
        add(backButton);

        refreshButton = new SimpleButton(this, Component.translatable("gui.lc_claim_economy.chunk_user_perm.refresh"), Icons.REFRESH, (button, mouseButton) ->
                PacketDistributor.sendToServer(new RequestChunkUserPermsPayload(chunkKey)));
        add(refreshButton);

        addPlayerBox = new TextBox(this);
        addPlayerBox.ghostText = Component.translatable("gui.lc_claim_economy.chunk_user_perm.add_ghost").getString();
        addPlayerBox.charLimit = 48;
        add(addPlayerBox);

        addPlayerButton = new SimpleButton(this, Component.translatable("gui.add"), Icons.ACCEPT, (button, mouseButton) -> submitAddPlayer());
        add(addPlayerButton);

        entryPanel = new EntryPanel(this);
        entryPanel.setOnlyRenderWidgetsInside(true);
        entryPanel.setOnlyInteractWithWidgetsInside(true);
        add(entryPanel);

        scrollBar = new PanelScrollBar(this, entryPanel);
        add(scrollBar);
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        refreshButton.setPosAndSize(width - 5 - HEADER_BUTTON_SIZE, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        boolean canManage = ClientChunkUserPermissions.canManage() && chunkKey.equals(ClientChunkUserPermissions.activeChunkKey());

        int contentTop = HEADER_HEIGHT + 4;
        int listTop = contentTop;
        if (canManage) {
            int addWidth = width - CONTENT_PAD * 2 - 54;
            addPlayerBox.setPosAndSize(CONTENT_PAD, contentTop, addWidth, ADD_ROW_HEIGHT);
            addPlayerButton.setPosAndSize(CONTENT_PAD + addWidth + 4, contentTop + 1, 50, 18);
            listTop += ADD_ROW_HEIGHT + 4;
        } else {
            addPlayerBox.setPosAndSize(0, 0, 0, 0);
            addPlayerButton.setPosAndSize(0, 0, 0, 0);
        }

        int contentHeight = height - listTop - CONTENT_PAD;
        int contentWidth = width - CONTENT_PAD * 2 - SCROLLBAR_WIDTH - 2;
        entryPanel.setPosAndSize(CONTENT_PAD, listTop, contentWidth, contentHeight);
        entryPanel.alignWidgets();
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
        theme.drawString(
                graphics,
                Component.translatable("gui.lc_claim_economy.chunk_user_perm.title"),
                x + w / 2,
            y + 5,
                NordColors.SNOW_STORM_0,
                Theme.CENTERED
        );
        theme.drawString(
            graphics,
            Component.translatable("gui.lc_claim_economy.chunk_user_perm.chunk", chunkKey),
            x + w / 2,
            y + 15,
            NordColors.SNOW_STORM_1,
            Theme.CENTERED
        );
    }

    private void submitAddPlayer() {
        if (!(ClientChunkUserPermissions.canManage() && chunkKey.equals(ClientChunkUserPermissions.activeChunkKey())) || addPlayerBox == null) {
            return;
        }

        String value = addPlayerBox.getText().trim();
        if (value.isEmpty()) {
            return;
        }

        PacketDistributor.sendToServer(new SetChunkUserPermsPayload(
                chunkKey,
                value,
                ChunkPermissionFlags.BLOCK_EDIT | ChunkPermissionFlags.BLOCK_INTERACT
        ));
        addPlayerBox.setText("");
    }

    private void rebuild() {
        if (entryPanel != null) {
            entryPanel.refreshWidgets();
            entryPanel.alignWidgets();
        }
        alignWidgets();
    }

    private static final class EntryPanel extends Panel {
        EntryPanel(BaseScreen screen) {
            super(screen);
        }

        @Override
        public void addWidgets() {
            if (!ClientChunkUserPermissions.activeChunkKey().equals(((ChunkUserPermissionsScreen) getGui()).chunkKey)) {
                add(new EmptyMessageRow(this, Component.translatable("gui.lc_claim_economy.chunk_user_perm.loading").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                return;
            }

            if (!ClientChunkUserPermissions.canManage()) {
                add(new EmptyMessageRow(this, Component.translatable("gui.lc_claim_economy.chunk_user_perm.view_only").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
            }

            List<ChunkUserPermissionEntry> entries = ClientChunkUserPermissions.entries();
            if (entries.isEmpty()) {
                add(new EmptyMessageRow(this, Component.translatable("gui.lc_claim_economy.chunk_user_perm.empty").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                return;
            }

            add(new HeaderRow(this));

            for (ChunkUserPermissionEntry entry : entries) {
                add(new EntryRow(this, entry));
            }
        }

        @Override
        public void alignWidgets() {
            int y = 0;
            for (Widget widget : widgets) {
                widget.setPos(0, y);
                widget.setWidth(width);
                widget.setHeight(widget instanceof HeaderRow ? HEADER_ROW_HEIGHT : ENTRY_HEIGHT);
                if (widget instanceof EntryRow row) {
                    row.alignWidgets();
                }
                y += (widget instanceof HeaderRow ? HEADER_ROW_HEIGHT : ENTRY_HEIGHT) + 2;
            }
        }
    }

    private static final class HeaderRow extends Button {
        HeaderRow(Panel panel) {
            super(panel, Component.empty(), Color4I.empty());
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
            theme.drawString(graphics, Component.translatable("gui.lc_claim_economy.chunk_user_perm.player_col"), x + 6, y + 5, NordColors.SNOW_STORM_1, 0);

            int colX = x + w - 90;
            drawCol(theme, graphics, "B", colX, y);
            drawCol(theme, graphics, "I", colX + 18, y);
            drawCol(theme, graphics, "E", colX + 36, y);
            drawCol(theme, graphics, "P", colX + 54, y);
            drawCol(theme, graphics, "X", colX + 72, y);
        }

        private void drawCol(Theme theme, GuiGraphics graphics, String value, int x, int y) {
            theme.drawString(graphics, Component.literal(value), x + 8, y + 5, NordColors.SNOW_STORM_1, Theme.CENTERED);
        }
    }

    private static final class EntryRow extends Panel {
        private final ChunkUserPermissionEntry entry;

        EntryRow(Panel panel, ChunkUserPermissionEntry entry) {
            super(panel);
            this.entry = entry;
        }

        @Override
        public void addWidgets() {
            add(new ToggleFlagButton(this, entry, ChunkPermissionFlags.BLOCK_EDIT, "B"));
            add(new ToggleFlagButton(this, entry, ChunkPermissionFlags.BLOCK_INTERACT, "I"));
            add(new ToggleFlagButton(this, entry, ChunkPermissionFlags.ENTITY_INTERACT, "E"));
            add(new ToggleFlagButton(this, entry, ChunkPermissionFlags.PVP, "P"));
            if (!entry.allPlayers()) {
                add(new RemovePlayerButton(this, entry));
            }
        }

        @Override
        public void alignWidgets() {
            int x = width - 90;
            for (Widget widget : widgets) {
                widget.setPosAndSize(x, 2, 16, 16);
                x += 18;
            }
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_2.withAlpha(isMouseOver() ? 220 : 180).draw(graphics, x, y, w, h);
            Component name = entry.allPlayers()
                    ? Component.translatable("gui.lc_claim_economy.chunk_user_perm.all_players_label").withStyle(ChatFormatting.BOLD)
                    : Component.literal(entry.displayName());
            theme.drawString(graphics, name, x + 6, y + 4, NordColors.SNOW_STORM_0, 0);
            if (!entry.allPlayers()) {
                theme.drawString(graphics, Component.literal(entry.playerId().toString()), x + 6, y + 13, NordColors.SNOW_STORM_1.withAlpha(180), 0);
            } else {
                theme.drawString(graphics, Component.translatable("gui.lc_claim_economy.chunk_user_perm.all_players_hint"), x + 6, y + 13, NordColors.SNOW_STORM_1.withAlpha(180), 0);
            }
        }
    }

    private static final class ToggleFlagButton extends Button {
        private final ChunkUserPermissionEntry entry;
        private final int flag;

        ToggleFlagButton(Panel panel, ChunkUserPermissionEntry entry, int flag, String label) {
            super(panel, Component.literal(label), Color4I.empty());
            this.entry = entry;
            this.flag = flag;
        }

        @Override
        public void onClicked(MouseButton button) {
            ChunkUserPermissionsScreen screen = (ChunkUserPermissionsScreen) getGui();
            if (!ClientChunkUserPermissions.canManage()) {
                return;
            }
            int next = entry.flags();
            if ((next & flag) != 0) {
                next &= ~flag;
            } else {
                next |= flag;
            }
            String playerRef = entry.allPlayers() ? "*" : entry.playerId().toString();
            PacketDistributor.sendToServer(new SetChunkUserPermsPayload(screen.chunkKey, playerRef, next));
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            boolean active = (entry.flags() & flag) != 0;
            Color4I fill = active ? NordColors.FROST_2 : NordColors.POLAR_NIGHT_1;
            if (!ClientChunkUserPermissions.canManage()) {
                fill = fill.withAlpha(120);
            }
            fill.draw(graphics, x, y, w, h);
            NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            super.draw(graphics, theme, x, y, w, h);
            theme.drawString(graphics, getTitle(), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            String key = switch (flag) {
                case ChunkPermissionFlags.BLOCK_EDIT -> "gui.lc_claim_economy.chunk_user_perm.flag_block_edit";
                case ChunkPermissionFlags.BLOCK_INTERACT -> "gui.lc_claim_economy.chunk_user_perm.flag_block_interact";
                case ChunkPermissionFlags.ENTITY_INTERACT -> "gui.lc_claim_economy.chunk_user_perm.flag_entity_interact";
                case ChunkPermissionFlags.PVP -> "gui.lc_claim_economy.chunk_user_perm.flag_pvp";
                default -> "";
            };
            if (!key.isEmpty()) {
                list.add(Component.translatable(key));
            }
        }
    }

    private static final class RemovePlayerButton extends PillButton {
        private final ChunkUserPermissionEntry entry;

        RemovePlayerButton(Panel panel, ChunkUserPermissionEntry entry) {
            super(panel, Component.literal("X"), 210, 150);
            this.entry = entry;
        }

        @Override
        protected Color4I fillColor() {
            return NordColors.RED;
        }

        @Override
        public void onClicked(MouseButton button) {
            ChunkUserPermissionsScreen screen = (ChunkUserPermissionsScreen) getGui();
            if (!ClientChunkUserPermissions.canManage()) {
                return;
            }
            PacketDistributor.sendToServer(new SetChunkUserPermsPayload(screen.chunkKey, entry.playerId().toString(), 0));
        }

        @Override
        public void addMouseOverText(TooltipList list) {
            list.add(Component.translatable("gui.lc_claim_economy.chunk_user_perm.remove_player"));
        }
    }
}
