package dev.voidpulsar.lc_claim_economy.client.gui;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.Icons;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.NordButton;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.PanelScrollBar;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftblibrary.ui.TextBox;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.Widget;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import dev.voidpulsar.lc_claim_economy.client.ClientConflictState;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.LcScreenChrome;
import dev.voidpulsar.lc_claim_economy.client.ConflictIcons;
import dev.voidpulsar.lc_claim_economy.network.ConflictStateRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.ToggleConflictPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictEntryStatus;
import dev.voidpulsar.lc_claim_economy.network.ConflictTeamEntry;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

public class ConflictScreen extends BaseScreen {
    private static final int HEADER_BUTTON_SIZE = LcScreenChrome.HEADER_BUTTON_SIZE;
    private static final int HEADER_HEIGHT = LcScreenChrome.HEADER_HEIGHT;
    private static final int ROW_HEIGHT = 22;
    private static final int SECTION_HEADER_HEIGHT = 20;
    private static final int EMPTY_ROW_HEIGHT = 14;
    private static final int ROW_GAP = 2;
    private static final int ROW_PAD = 6;
    private static final int ROW_BUTTON_GAP = 4;
    private static final int FILTER_BOX_HEIGHT = 16;
    private static final int FILTER_BOX_MIN_WIDTH = 96;
    private static final int FILTER_BOX_MAX_WIDTH = 160;
    private static final int SECTION_HEADER_GAP = 2;
    private static final int SECTION_GAP = 6;
    private static final int SCROLLBAR_WIDTH = LcScreenChrome.SCROLLBAR_WIDTH;
    private static final int CONTENT_PAD = LcScreenChrome.CONTENT_PAD;
    private static final int MIN_SECTION_LIST_HEIGHT = 28;

    private final MyTeamScreen parent;
    private SimpleButton backButton;
    private SimpleButton infoButton;
    private WarSection incomingSection;
    private WarSection outgoingSection;
    private WarSection declareSection;

    public ConflictScreen(MyTeamScreen parent) {
        this.parent = parent;
    }

    public static void refreshIfOpen() {
        ConflictScreen screen = ClientUtils.getCurrentGuiAs(ConflictScreen.class);
        if (screen != null) {
            screen.rebuildList();
        }
    }

    @Override
    public boolean onInit() {
        setWidth(getScreen().getGuiScaledWidth() * 3 / 5);
        setHeight(getScreen().getGuiScaledHeight() * 3 / 5);
        PacketDistributor.sendToServer(new ConflictStateRequestPayload());
        return true;
    }

    @Override
    public void addWidgets() {
        backButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> parent.openGui());
        add(backButton);
        infoButton = new SimpleButton(this, Component.empty(), Icons.INFO, (button, mouseButton) -> {}) {
            @Override
            public void addMouseOverText(TooltipList list) {
                addWarCostTooltip(list);
            }

            @Override
            public void playClickSound() {
            }
        };
        add(infoButton);

        incomingSection = new WarSection(
                () -> Component.translatable("gui.lc_claim_economy.war.incoming_heading"),
                ClientConflictState::incoming,
                SectionMode.INCOMING,
                Component.translatable("gui.lc_claim_economy.war.empty_incoming").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
        outgoingSection = new WarSection(
                () -> Component.translatable("gui.lc_claim_economy.war.outgoing_heading"),
                ClientConflictState::outgoing,
                SectionMode.OUTGOING,
                Component.translatable("gui.lc_claim_economy.war.empty_outgoing").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
        declareSection = new WarSection(
                ConflictScreen::declareHeading,
                ClientConflictState::availableTargets,
                SectionMode.DECLARE,
                Component.translatable("gui.lc_claim_economy.war.empty_targets").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );

        incomingSection.addWidgets();
        outgoingSection.addWidgets();
        declareSection.addWidgets();
    }

    @Override
    public void alignWidgets() {
        backButton.setPosAndSize(5, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        infoButton.setPosAndSize(5 + HEADER_BUTTON_SIZE + 4, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        int contentTop = HEADER_HEIGHT + 6;
        int contentHeight = height - contentTop - CONTENT_PAD;
        int sectionWidth = width - CONTENT_PAD * 2;
        int sectionHeight = (contentHeight - SECTION_GAP * 2) / 3;

        int y = contentTop;
        incomingSection.setBounds(CONTENT_PAD, y, sectionWidth, sectionHeight);
        y += sectionHeight + SECTION_GAP;
        outgoingSection.setBounds(CONTENT_PAD, y, sectionWidth, sectionHeight);
        y += sectionHeight + SECTION_GAP;
        declareSection.setBounds(CONTENT_PAD, y, sectionWidth, contentHeight - (y - contentTop));
    }

    private void rebuildList() {
        if (incomingSection != null) {
            incomingSection.refreshList();
        }
        if (outgoingSection != null) {
            outgoingSection.refreshList();
        }
        if (declareSection != null) {
            declareSection.refreshList();
        }
        alignWidgets();
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
                Component.translatable("gui.lc_claim_economy.war.title"),
                x + w / 2,
                y + 7,
                NordColors.SNOW_STORM_0,
                Theme.CENTERED
        );
    }

    private static Component declareHeading() {
        Component base = Component.translatable("gui.lc_claim_economy.war.declare_heading");
        if (ClientConflictState.warDeclarationWindowOpen()) {
            return base;
        }
        return Component.empty()
                .append(base)
                .append(Component.literal(" - "))
                .append(Component.translatable(
                        "gui.lc_claim_economy.war.declare_heading_closed_suffix",
                        ClientConflictState.warDeclarationWindowDescription()
                ).withStyle(ChatFormatting.RED));
    }

    private enum SectionMode {
        INCOMING,
        OUTGOING,
        DECLARE
    }

    private static void addWarCostTooltip(TooltipList list) {
        list.add(Component.translatable("gui.lc_claim_economy.war.cost_tooltip.title").withStyle(ChatFormatting.GOLD));
        list.blankLine();
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.cost_tooltip.base",
                CurrencyTextFormat.formatPrice(ClientConflictState.baseUpkeepCopper())
        ));
        if (ClientConflictState.incomingWarCopper() > 0) {
            list.add(Component.translatable(
                    "gui.lc_claim_economy.war.cost_tooltip.incoming",
                    CurrencyTextFormat.formatPrice(ClientConflictState.incomingWarCopper())
            ));
        }
        if (ClientConflictState.outgoingWarCopper() > 0) {
            list.add(Component.translatable(
                    "gui.lc_claim_economy.war.cost_tooltip.outgoing",
                    CurrencyTextFormat.formatPrice(ClientConflictState.outgoingWarCopper())
            ));
        }
        list.blankLine();
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.cost_tooltip.total",
                CurrencyTextFormat.formatPrice(
                        ClientConflictState.baseUpkeepCopper() + ClientConflictState.totalWarCopper()
                ),
                SafeguardPriceDisplay.upkeepPeriodLabel()
        ).withStyle(ChatFormatting.AQUA));
        list.blankLine();
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.cost_tooltip.multiplier",
                formatMultiplier(ClientConflictState.warCostMultiplier())
        ).withStyle(ChatFormatting.GRAY));
    }

    private static String formatMultiplier(double multiplier) {
        if (Math.rint(multiplier) == multiplier) {
            return String.valueOf((long) multiplier);
        }
        return String.valueOf(multiplier);
    }

    private static Component formatEntryCost(long copper) {
        if (copper <= 0) {
            return CurrencyTextFormat.formatPrice(copper);
        }
        return Component.translatable(
                "gui.lc_claim_economy.war.entry_cost",
                CurrencyTextFormat.formatPrice(copper),
                SafeguardPriceDisplay.upkeepPeriodLabel()
        ).withStyle(ChatFormatting.GOLD);
    }

    private static void addEntryTooltip(TooltipList list, ConflictTeamEntry entry, SectionMode mode) {
        Component period = SafeguardPriceDisplay.upkeepPeriodLabel();

        if (entry.status() == ConflictEntryStatus.PENDING_DECLARE && mode == SectionMode.DECLARE) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_declare")
                    .withStyle(ChatFormatting.GOLD));
            addEntryCostDetails(list, entry, period);
            if (entry.opponentPendingDeclareOnViewer()) {
                list.blankLine();
                list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.opponent_pending_declare")
                        .withStyle(ChatFormatting.YELLOW));
            }
            if (ClientConflictState.canManageWar()) {
                list.blankLine();
                list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.click_to_cancel")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        if (entry.status() == ConflictEntryStatus.PENDING_DECLARE && mode == SectionMode.INCOMING) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_incoming")
                    .withStyle(ChatFormatting.GOLD));
            addEntryCostDetails(list, entry, period);
            return;
        }

        if (entry.status() == ConflictEntryStatus.PENDING_END && mode == SectionMode.OUTGOING) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_end")
                    .withStyle(ChatFormatting.GOLD));
            addEntryCostDetails(list, entry, period);
            addWarVulnerabilityTooltip(list, entry);
            if (ClientConflictState.canManageWar()) {
                list.blankLine();
                list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.click_to_cancel")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        addEntryCostDetails(list, entry, period);
        if (mode == SectionMode.DECLARE && entry.opponentPendingDeclareOnViewer()) {
            list.blankLine();
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.opponent_pending_declare")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (mode == SectionMode.DECLARE && !ClientConflictState.warDeclarationWindowOpen()) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.war.entry_tooltip.declare_window_closed",
                    ClientConflictState.warDeclarationWindowDescription()
            ).withStyle(ChatFormatting.RED));
        }
        if (mode == SectionMode.OUTGOING) {
            addWarVulnerabilityTooltip(list, entry);
        }
    }

    private static void addWarVulnerabilityTooltip(TooltipList list, ConflictTeamEntry entry) {
        if (!entry.hasWarVulnerability()) {
            return;
        }
        list.blankLine();
        list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.vulnerabilities_heading")
                .withStyle(ChatFormatting.GOLD));
        if (!entry.blockEditProtected()) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.missing_block_edit")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (!entry.explosionProtected()) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.missing_explosions")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (!entry.pvpProtected()) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.missing_pvp")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void addEntryCostDetails(TooltipList list, ConflictTeamEntry entry, Component period) {
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.entry_tooltip.base",
                CurrencyTextFormat.formatPrice(entry.targetBaseUpkeepCopper()),
                period
        ));
        if (entry.warCostCopper() <= 0) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.no_claims_yet")
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.entry_tooltip.cost",
                CurrencyTextFormat.formatPrice(entry.warCostCopper()),
                period
        ).withStyle(ChatFormatting.GOLD));
    }

    private static List<ConflictTeamEntry> filterEntries(List<ConflictTeamEntry> entries, String query) {
        if (entries.isEmpty()) {
            return List.of();
        }

        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ConflictTeamEntry> matches = new ArrayList<>();
        for (ConflictTeamEntry entry : entries) {
            if (normalized.isEmpty() || entry.displayName().toLowerCase(Locale.ROOT).contains(normalized)) {
                matches.add(entry);
            }
        }

        matches.sort(Comparator
                .comparingInt((ConflictTeamEntry entry) -> searchRank(entry.displayName(), normalized))
                .thenComparing(entry -> entry.displayName(), String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private static int searchRank(String name, String query) {
        if (query.isEmpty()) {
            return 0;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals(query)) {
            return 0;
        }
        if (lower.startsWith(query)) {
            return 1;
        }
        return 2;
    }

    private final class WarSection {
        private final Supplier<Component> titleSupplier;
        private final Supplier<List<ConflictTeamEntry>> sourceEntries;
        private final SectionMode mode;
        private final Component emptyMessage;

        private SectionHeaderBar headerBar;
        private WarEntryListPanel listPanel;
        private PanelScrollBar scrollBar;

        private WarSection(
                Supplier<Component> titleSupplier,
                Supplier<List<ConflictTeamEntry>> sourceEntries,
                SectionMode mode,
                Component emptyMessage
        ) {
            this.titleSupplier = titleSupplier;
            this.sourceEntries = sourceEntries;
            this.mode = mode;
            this.emptyMessage = emptyMessage;
        }

        void addWidgets() {
            headerBar = new SectionHeaderBar(ConflictScreen.this, titleSupplier, this::refreshList);
            listPanel = new WarEntryListPanel(ConflictScreen.this, this::filteredEntries, mode, emptyMessage);
            scrollBar = new WarScrollBar(ConflictScreen.this, listPanel);

            ConflictScreen.this.add(headerBar);
            ConflictScreen.this.add(listPanel);
            ConflictScreen.this.add(scrollBar);
        }

        void setBounds(int x, int y, int width, int height) {
            headerBar.setPosAndSize(x, y, width, SECTION_HEADER_HEIGHT);
            headerBar.alignWidgets();
            int listTop = y + SECTION_HEADER_HEIGHT + SECTION_HEADER_GAP;
            int listHeight = Math.max(MIN_SECTION_LIST_HEIGHT, height - SECTION_HEADER_HEIGHT - SECTION_HEADER_GAP);
            int listWidth = Math.max(0, width - SCROLLBAR_WIDTH - 2);
            listPanel.setPosAndSize(x, listTop, listWidth, listHeight);
            listPanel.alignWidgets();
            scrollBar.setPosAndSize(x + width - SCROLLBAR_WIDTH, listTop, SCROLLBAR_WIDTH, listHeight);
        }

        void refreshList() {
            if (listPanel != null) {
                listPanel.setScrollY(0);
                listPanel.refreshWidgets();
                listPanel.alignWidgets();
            }
        }

        private String filterQuery() {
            return headerBar == null ? "" : headerBar.filterQuery();
        }

        private List<ConflictTeamEntry> filteredEntries() {
            return filterEntries(sourceEntries.get(), filterQuery());
        }
    }

    private static class WarScrollBar extends PanelScrollBar {
        WarScrollBar(BaseScreen screen, Panel panel) {
            super(screen, panel);
        }

        @Override
        public boolean shouldDraw() {
            return super.shouldDraw() && getMaxValue() > getMinValue();
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            if (!shouldDraw()) {
                return;
            }
            super.drawBackground(graphics, theme, x, y, w, h);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            if (!shouldDraw()) {
                return;
            }
            super.draw(graphics, theme, x, y, w, h);
        }
    }

    private static class SectionHeaderBar extends Panel {
        private final Supplier<Component> titleSupplier;
        private final Runnable onFilterChanged;
        private TextBox filterBox;

        SectionHeaderBar(BaseScreen screen, Supplier<Component> titleSupplier, Runnable onFilterChanged) {
            super(screen);
            this.titleSupplier = titleSupplier;
            this.onFilterChanged = onFilterChanged;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        String filterQuery() {
            return filterBox == null ? "" : filterBox.getText();
        }

        @Override
        public void addWidgets() {
            filterBox = new TextBox(this) {
                @Override
                public void onTextChanged() {
                    onFilterChanged.run();
                }
            };
            filterBox.ghostText = Component.translatable("gui.lc_claim_economy.war.filter_ghost").getString();
            filterBox.charLimit = 48;
            add(filterBox);
        }

        @Override
        public void alignWidgets() {
            if (filterBox == null) {
                return;
            }
            int filterWidth = Math.min(FILTER_BOX_MAX_WIDTH, Math.max(FILTER_BOX_MIN_WIDTH, width / 3));
            int filterX = Math.max(width - filterWidth - 2, 0);
            filterBox.setPosAndSize(filterX, (height - FILTER_BOX_HEIGHT) / 2, filterWidth, FILTER_BOX_HEIGHT);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_1.draw(graphics, x, y + h - 1, w, 1);

            if (filterBox != null && filterBox.width > 0) {
                int bx = x + filterBox.getX();
                int by = y + filterBox.getY();
                NordColors.POLAR_NIGHT_0.draw(graphics, bx, by, filterBox.width, filterBox.height);
                NordColors.POLAR_NIGHT_3.draw(graphics, bx, by + filterBox.height - 1, filterBox.width, 1);
            }

            int titleMaxWidth = filterBox == null || filterBox.width <= 0
                    ? w - 4
                    : Math.max(0, filterBox.getX() - 6);
            Component heading = titleSupplier.get().copy().withStyle(ChatFormatting.BOLD);
            if (titleMaxWidth > 0 && theme.getStringWidth(heading) > titleMaxWidth) {
                heading = Component.literal(theme.trimStringToWidth(heading.getString(), titleMaxWidth - 4) + "...");
            }
            if (titleMaxWidth > 0) {
                theme.drawString(graphics, heading, x + 2, y + 6, NordColors.FROST_2, 0);
            }

            super.draw(graphics, theme, x, y, w, h);
        }
    }

    private static class WarEntryListPanel extends Panel {
        private final Supplier<List<ConflictTeamEntry>> entriesSupplier;
        private final SectionMode mode;
        private final Component emptyMessage;

        WarEntryListPanel(BaseScreen screen, Supplier<List<ConflictTeamEntry>> entriesSupplier, SectionMode mode, Component emptyMessage) {
            super(screen);
            this.entriesSupplier = entriesSupplier;
            this.mode = mode;
            this.emptyMessage = emptyMessage;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        @Override
        public void addWidgets() {
            if (mode == SectionMode.DECLARE && !ClientConflictState.canManageWar()) {
                add(new EmptyMessage(this, Component.translatable("gui.lc_claim_economy.war.view_only").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                return;
            }

            List<ConflictTeamEntry> entries = entriesSupplier.get();
            if (entries.isEmpty()) {
                Component message = emptyMessage;
                if (mode == SectionMode.DECLARE && !ClientConflictState.availableTargets().isEmpty()) {
                    message = Component.translatable("gui.lc_claim_economy.war.search_empty").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                }
                add(new EmptyMessage(this, message));
                return;
            }

            for (ConflictTeamEntry entry : entries) {
                add(createRow(entry));
            }
        }

        private WarEntryRow createRow(ConflictTeamEntry entry) {
            return switch (mode) {
                case INCOMING -> new WarEntryRow(this, entry, mode, null, null, true, false);
                case OUTGOING -> new WarEntryRow(
                        this,
                        entry,
                        mode,
                        entry.isPending() ? null : Component.translatable("gui.lc_claim_economy.war.end_war"),
                        entry.teamId(),
                        false,
                        false
                );
                case DECLARE -> new WarEntryRow(
                        this,
                        entry,
                        mode,
                        entry.isPending() ? null : Component.translatable("gui.lc_claim_economy.war.declare"),
                        entry.teamId(),
                        false,
                        true
                );
            };
        }

        @Override
        public void alignWidgets() {
            int y = 0;
            for (Widget widget : widgets) {
                widget.setPos(0, y);
                widget.setWidth(width);
                if (widget instanceof EmptyMessage) {
                    widget.setHeight(EMPTY_ROW_HEIGHT);
                    y += EMPTY_ROW_HEIGHT;
                } else if (widget instanceof WarEntryRow entryRow) {
                    widget.setHeight(ROW_HEIGHT);
                    entryRow.alignWidgets();
                    y += ROW_HEIGHT + ROW_GAP;
                }
            }
        }
    }

    private static class EmptyMessage extends Button {
        EmptyMessage(Panel panel, Component label) {
            super(panel, label, Color4I.empty());
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            theme.drawString(graphics, getTitle(), x + 10, y + 3, NordColors.SNOW_STORM_2, 0);
        }

        @Override
        public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
        }
    }

    private static class WarEntryRow extends Panel {
        private static final int INFO_BUTTON_SIZE = 16;

        private final ConflictTeamEntry entry;
        private final SectionMode sectionMode;
        private final Component actionLabel;
        private final UUID actionTarget;
        private final boolean infoOnly;
        private final boolean declareAction;
        private int costX;
        private int costWidth;

        WarEntryRow(
                Panel panel,
                ConflictTeamEntry entry,
                SectionMode sectionMode,
                Component actionLabel,
                UUID actionTarget,
                boolean infoOnly,
                boolean declareAction
        ) {
            super(panel);
            this.entry = entry;
            this.sectionMode = sectionMode;
            this.actionLabel = actionLabel;
            this.actionTarget = actionTarget;
            this.infoOnly = infoOnly;
            this.declareAction = declareAction;
        }

        private void sendToggleWar() {
            UUID target = actionTarget != null ? actionTarget : entry.teamId();
            PacketDistributor.sendToServer(new ToggleConflictPayload(target));
        }

        /** Business rule (not layout): an outgoing-war opponent missing a protection is shown as vulnerable. */
        private boolean isVulnerable() {
            return sectionMode == SectionMode.OUTGOING && entry.hasWarVulnerability();
        }

        @Override
        public void addWidgets() {
            if (entry.isPending() && ClientConflictState.canManageWar() && sectionMode != SectionMode.INCOMING) {
                add(new Button(this, Component.empty(), Color4I.empty()) {
                    @Override
                    public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
                        sendToggleWar();
                    }

                    @Override
                    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                    }
                });
            }

            if (!infoOnly && !entry.isPending() && actionLabel != null && ClientConflictState.canManageWar()) {
                boolean declareWindowClosed = declareAction && !ClientConflictState.warDeclarationWindowOpen();
                add(new NordButton(
                        this,
                        actionLabel,
                        declareAction
                                ? (declareWindowClosed ? ConflictIcons.SWORD.withTint(NordColors.POLAR_NIGHT_3) : ConflictIcons.SWORD)
                                : Icons.CANCEL.withTint(NordColors.SNOW_STORM_1)
                ) {
                    @Override
                    public void onClicked(dev.ftb.mods.ftblibrary.ui.input.MouseButton button) {
                        sendToggleWar();
                    }

                    @Override
                    public void addMouseOverText(TooltipList list) {
                        if (declareWindowClosed) {
                            list.add(Component.translatable(
                                    "gui.lc_claim_economy.war.entry_tooltip.declare_window_closed",
                                    ClientConflictState.warDeclarationWindowDescription()
                            ).withStyle(ChatFormatting.RED));
                        }
                    }
                });
            }

            add(new SimpleButton(this, Component.empty(), Icons.INFO, (button, mouseButton) -> {}) {
                @Override
                public void addMouseOverText(TooltipList list) {
                    addEntryTooltip(list, entry, sectionMode);
                }

                @Override
                public void playClickSound() {
                }
            });
        }

        @Override
        public void alignWidgets() {
            if (width <= 0 || widgets.isEmpty()) {
                return;
            }

            Theme theme = getGui().getTheme();
            Component costLabel = entry.isPending()
                    ? Component.translatable("gui.lc_claim_economy.pending").withStyle(ChatFormatting.GOLD)
                    : formatEntryCost(entry.warCostCopper());
            costWidth = theme.getStringWidth(costLabel) + 10;

            int right = width - ROW_PAD;
            Widget info = widgets.getLast();
            info.setPosAndSize(right - INFO_BUTTON_SIZE, (height - INFO_BUTTON_SIZE) / 2, INFO_BUTTON_SIZE, INFO_BUTTON_SIZE);
            right -= INFO_BUTTON_SIZE + ROW_BUTTON_GAP;

            int left = ROW_PAD;
            if (widgets.size() > 1) {
                Widget leading = widgets.getFirst();
                if (entry.isPending()) {
                    leading.setPosAndSize(ROW_PAD, 0, right - ROW_PAD, height);
                } else if (actionLabel != null) {
                    int actionWidth = Math.min(88, Math.max(68, theme.getStringWidth(actionLabel) + 24));
                    leading.setPosAndSize(right - actionWidth, (height - 18) / 2, actionWidth, 18);
                    right -= actionWidth + ROW_BUTTON_GAP;
                }
            }

            costX = Math.max(left + 40, right - costWidth);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            if (costWidth <= 0) {
                alignWidgets();
            }

            Color4I background = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            boolean vulnerable = isVulnerable();
            if (vulnerable && !isMouseOver()) {
                background = NordColors.POLAR_NIGHT_1;
            }
            background.withAlpha(isMouseOver() ? 220 : (vulnerable ? 200 : 180)).draw(graphics, x + 1, y, w - 2, h);
            if (vulnerable) {
                NordColors.YELLOW.withAlpha(isMouseOver() ? 200 : 140).draw(graphics, x + 1, y, 2, h);
            } else if (isMouseOver()) {
                (declareAction ? NordColors.RED : NordColors.FROST_1).withAlpha(160).draw(graphics, x + 1, y, 2, h);
            }

            Component costLabel = entry.isPending()
                    ? Component.translatable("gui.lc_claim_economy.pending").withStyle(ChatFormatting.GOLD)
                    : formatEntryCost(entry.warCostCopper());
            int nameMaxWidth = Math.max(0, costX - ROW_PAD - ROW_BUTTON_GAP);
            Component name = Component.literal(entry.displayName()).withStyle(ChatFormatting.WHITE);
            Color4I nameColor = vulnerable ? NordColors.YELLOW : NordColors.SNOW_STORM_0;
            if (nameMaxWidth > 0 && theme.getStringWidth(name) > nameMaxWidth) {
                name = Component.literal(theme.trimStringToWidth(name.getString(), nameMaxWidth - 4) + "...");
            }

            if (nameMaxWidth > 0) {
                theme.drawString(graphics, name, x + ROW_PAD, y + 7, nameColor, 0);
            }

            NordColors.POLAR_NIGHT_0.withAlpha(200).draw(graphics, x + costX, y + 4, costWidth, h - 8);
            theme.drawString(graphics, costLabel, x + costX + 5, y + 7, NordColors.SNOW_STORM_0, 0);
        }
    }
}
