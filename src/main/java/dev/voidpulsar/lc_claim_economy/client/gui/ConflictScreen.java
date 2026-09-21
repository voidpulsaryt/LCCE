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
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import dev.ftb.mods.ftblibrary.util.TooltipList;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import dev.voidpulsar.lc_claim_economy.client.ClientConflictState;
import dev.voidpulsar.lc_claim_economy.client.ConflictIcons;
import dev.voidpulsar.lc_claim_economy.client.gui.widget.LcScreenChrome;
import dev.voidpulsar.lc_claim_economy.network.ConflictEntryStatus;
import dev.voidpulsar.lc_claim_economy.network.ConflictStateRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictTeamEntry;
import dev.voidpulsar.lc_claim_economy.network.ToggleConflictPayload;
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

/**
 * The team-hub sub-panel for managing standing conflicts: three stacked,
 * independently-searchable lists (conflicts opened against us, conflicts we
 * opened, and teams still eligible for a declaration), each rendered by a
 * {@link StandingList}. Buttons never touch local state directly - every
 * accept/decline/declare click fires a {@link ToggleConflictPayload} and the
 * lists simply wait for the resulting
 * {@link dev.voidpulsar.lc_claim_economy.network.ConflictStateBroadcastPayload}
 * to trigger {@link #refreshIfOpen()}.
 */
public class ConflictScreen extends BaseScreen {
    private static final int GROUP_GAP = 6;
    private static final int LIST_HEADER_H = 20;
    private static final int LIST_HEADER_GAP = 2;
    private static final int LIST_MIN_H = 28;
    private static final int ENTRY_H = 22;
    private static final int ENTRY_GAP = 2;
    private static final int ENTRY_PAD = 6;
    private static final int ENTRY_BTN_GAP = 4;
    private static final int EMPTY_LABEL_H = 14;
    private static final int SEARCH_H = 16;
    private static final int SEARCH_MIN_W = 96;
    private static final int SEARCH_MAX_W = 160;
    private static final int TOPBAR_H = LcScreenChrome.HEADER_HEIGHT;
    private static final int TOPBAR_BTN = LcScreenChrome.HEADER_BUTTON_SIZE;
    private static final int OUTER_PAD = LcScreenChrome.CONTENT_PAD;
    private static final int SB_WIDTH = LcScreenChrome.SCROLLBAR_WIDTH;

    private final MyTeamScreen hubScreen;
    private final StandingList[] standingLists = new StandingList[RelationKind.values().length];
    private SimpleButton backNavButton;
    private SimpleButton upkeepInfoButton;

    public ConflictScreen(MyTeamScreen parentScreen) {
        this.hubScreen = parentScreen;
    }

    public static void refreshIfOpen() {
        ConflictScreen active = ClientUtils.getCurrentGuiAs(ConflictScreen.class);
        if (active != null) {
            active.reloadStandingLists();
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
        backNavButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> hubScreen.openGui());
        add(backNavButton);

        upkeepInfoButton = new SimpleButton(this, Component.empty(), Icons.INFO, (button, mouseButton) -> {}) {
            @Override
            public void addMouseOverText(TooltipList list) {
                writeUpkeepCostTooltip(list);
            }

            @Override
            public void playClickSound() {
            }
        };
        add(upkeepInfoButton);

        standingLists[RelationKind.INCOMING.ordinal()] = new StandingList(
                () -> Component.translatable("gui.lc_claim_economy.war.incoming_heading"),
                ClientConflictState::incoming,
                RelationKind.INCOMING,
                Component.translatable("gui.lc_claim_economy.war.empty_incoming").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
        standingLists[RelationKind.OUTGOING.ordinal()] = new StandingList(
                () -> Component.translatable("gui.lc_claim_economy.war.outgoing_heading"),
                ClientConflictState::outgoing,
                RelationKind.OUTGOING,
                Component.translatable("gui.lc_claim_economy.war.empty_outgoing").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
        standingLists[RelationKind.DECLARE.ordinal()] = new StandingList(
                ConflictScreen::declareHeadingText,
                ClientConflictState::availableTargets,
                RelationKind.DECLARE,
                Component.translatable("gui.lc_claim_economy.war.empty_targets").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );

        for (StandingList list : standingLists) {
            list.addWidgets();
        }
    }

    @Override
    public void alignWidgets() {
        backNavButton.setPosAndSize(5, 5, TOPBAR_BTN, TOPBAR_BTN);
        upkeepInfoButton.setPosAndSize(5 + TOPBAR_BTN + 4, 5, TOPBAR_BTN, TOPBAR_BTN);

        int contentTop = TOPBAR_H + 6;
        int contentHeight = height - contentTop - OUTER_PAD;
        int columnWidth = width - OUTER_PAD * 2;
        int evenRowHeight = (contentHeight - GROUP_GAP * 2) / 3;
        int lastIndex = standingLists.length - 1;

        int cursor = contentTop;
        for (int i = 0; i < standingLists.length; i++) {
            int rowHeight = i == lastIndex ? contentHeight - (cursor - contentTop) : evenRowHeight;
            standingLists[i].setBounds(OUTER_PAD, cursor, columnWidth, rowHeight);
            cursor += rowHeight + GROUP_GAP;
        }
    }

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawBackground(graphics, theme, x, y, w, h);
        LcScreenChrome.drawContentBackground(graphics, x, y, w, h);
    }

    @Override
    public void drawForeground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.drawForeground(graphics, theme, x, y, w, h);
        Component title = Component.translatable("gui.lc_claim_economy.war.title");
        theme.drawString(graphics, title, x + w / 2, y + 7, NordColors.SNOW_STORM_0, Theme.CENTERED);
    }

    /** Pulls fresh rows into every list from the client-side cache and re-runs layout; invoked after a broadcast lands. */
    private void reloadStandingLists() {
        for (StandingList list : standingLists) {
            list.refreshList();
        }
        alignWidgets();
    }

    private enum RelationKind {
        INCOMING,
        OUTGOING,
        DECLARE
    }

    private static Component declareHeadingText() {
        if (!ClientConflictState.warDeclarationWindowOpen()) {
            return Component.empty()
                    .append(Component.translatable("gui.lc_claim_economy.war.declare_heading"))
                    .append(Component.literal(" - "))
                    .append(Component.translatable(
                            "gui.lc_claim_economy.war.declare_heading_closed_suffix",
                            ClientConflictState.warDeclarationWindowDescription()
                    ).withStyle(ChatFormatting.RED));
        }
        return Component.translatable("gui.lc_claim_economy.war.declare_heading");
    }

    private static void writeUpkeepCostTooltip(TooltipList list) {
        long baseCopper = ClientConflictState.baseUpkeepCopper();

        list.add(Component.translatable("gui.lc_claim_economy.war.cost_tooltip.title").withStyle(ChatFormatting.GOLD));
        list.blankLine();
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.cost_tooltip.base",
                CurrencyTextFormat.formatPrice(baseCopper)
        ));
        appendIfPositive(list, ClientConflictState.incomingWarCopper(), "gui.lc_claim_economy.war.cost_tooltip.incoming");
        appendIfPositive(list, ClientConflictState.outgoingWarCopper(), "gui.lc_claim_economy.war.cost_tooltip.outgoing");
        list.blankLine();
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.cost_tooltip.total",
                CurrencyTextFormat.formatPrice(baseCopper + ClientConflictState.totalWarCopper()),
                SafeguardPriceDisplay.upkeepPeriodLabel()
        ).withStyle(ChatFormatting.AQUA));
        list.blankLine();
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.cost_tooltip.multiplier",
                describeMultiplier(ClientConflictState.warCostMultiplier())
        ).withStyle(ChatFormatting.GRAY));
    }

    private static void appendIfPositive(TooltipList list, long copper, String translationKey) {
        if (copper > 0) {
            list.add(Component.translatable(translationKey, CurrencyTextFormat.formatPrice(copper)));
        }
    }

    /** A whole-number multiplier prints without a trailing ".0"; this is display-only text, never parsed back. */
    private static String describeMultiplier(double multiplier) {
        return Math.rint(multiplier) == multiplier ? String.valueOf((long) multiplier) : String.valueOf(multiplier);
    }

    private static Component formatEntryCostLabel(long copper) {
        if (copper > 0) {
            return Component.translatable(
                    "gui.lc_claim_economy.war.entry_cost",
                    CurrencyTextFormat.formatPrice(copper),
                    SafeguardPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD);
        }
        return CurrencyTextFormat.formatPrice(copper);
    }

    private static void writeEntryTooltip(TooltipList list, ConflictTeamEntry entry, RelationKind kind) {
        Component periodLabel = SafeguardPriceDisplay.upkeepPeriodLabel();
        switch (kind) {
            case DECLARE -> writeDeclareTooltip(list, entry, periodLabel);
            case INCOMING -> writeIncomingTooltip(list, entry, periodLabel);
            case OUTGOING -> writeOutgoingTooltip(list, entry, periodLabel);
        }
    }

    private static void writeDeclareTooltip(TooltipList list, ConflictTeamEntry entry, Component periodLabel) {
        if (entry.status() == ConflictEntryStatus.PENDING_DECLARE) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_declare")
                    .withStyle(ChatFormatting.GOLD));
            writeCostLines(list, entry, periodLabel);
            writeOpponentPendingNote(list, entry);
            if (ClientConflictState.canManageWar()) {
                list.blankLine();
                list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.click_to_cancel")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        writeCostLines(list, entry, periodLabel);
        writeOpponentPendingNote(list, entry);
        if (!ClientConflictState.warDeclarationWindowOpen()) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.war.entry_tooltip.declare_window_closed",
                    ClientConflictState.warDeclarationWindowDescription()
            ).withStyle(ChatFormatting.RED));
        }
    }

    private static void writeIncomingTooltip(TooltipList list, ConflictTeamEntry entry, Component periodLabel) {
        if (entry.status() == ConflictEntryStatus.PENDING_DECLARE) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_incoming")
                    .withStyle(ChatFormatting.GOLD));
        }
        writeCostLines(list, entry, periodLabel);
    }

    private static void writeOutgoingTooltip(TooltipList list, ConflictTeamEntry entry, Component periodLabel) {
        if (entry.status() == ConflictEntryStatus.PENDING_END) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_end")
                    .withStyle(ChatFormatting.GOLD));
            writeCostLines(list, entry, periodLabel);
            writeVulnerabilityLines(list, entry);
            if (ClientConflictState.canManageWar()) {
                list.blankLine();
                list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.click_to_cancel")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        writeCostLines(list, entry, periodLabel);
        writeVulnerabilityLines(list, entry);
    }

    private static void writeOpponentPendingNote(TooltipList list, ConflictTeamEntry entry) {
        if (entry.opponentPendingDeclareOnViewer()) {
            list.blankLine();
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.opponent_pending_declare")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void writeVulnerabilityLines(TooltipList list, ConflictTeamEntry entry) {
        if (!entry.hasWarVulnerability()) {
            return;
        }
        list.blankLine();
        list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.vulnerabilities_heading")
                .withStyle(ChatFormatting.GOLD));

        record Gap(boolean guarded, String translationKey) {
        }
        List<Gap> gaps = List.of(
                new Gap(entry.blockEditProtected(), "gui.lc_claim_economy.war.entry_tooltip.missing_block_edit"),
                new Gap(entry.explosionProtected(), "gui.lc_claim_economy.war.entry_tooltip.missing_explosions"),
                new Gap(entry.pvpProtected(), "gui.lc_claim_economy.war.entry_tooltip.missing_pvp")
        );
        for (Gap gap : gaps) {
            if (!gap.guarded()) {
                list.add(Component.translatable(gap.translationKey()).withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    private static void writeCostLines(TooltipList list, ConflictTeamEntry entry, Component period) {
        list.add(Component.translatable(
                "gui.lc_claim_economy.war.entry_tooltip.base",
                CurrencyTextFormat.formatPrice(entry.targetBaseUpkeepCopper()),
                period
        ));
        if (entry.warCostCopper() > 0) {
            list.add(Component.translatable(
                    "gui.lc_claim_economy.war.entry_tooltip.cost",
                    CurrencyTextFormat.formatPrice(entry.warCostCopper()),
                    period
            ).withStyle(ChatFormatting.GOLD));
            return;
        }
        list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.no_claims_yet")
                .withStyle(ChatFormatting.GRAY));
    }

    /** Case-insensitive substring filter; exact and prefix matches sort ahead of plain substring hits. */
    private static List<ConflictTeamEntry> applyNameFilter(List<ConflictTeamEntry> entries, String query) {
        if (entries.isEmpty()) {
            return List.of();
        }

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ConflictTeamEntry> exact = new ArrayList<>();
        List<ConflictTeamEntry> prefixed = new ArrayList<>();
        List<ConflictTeamEntry> contains = new ArrayList<>();

        for (ConflictTeamEntry candidate : entries) {
            String lowerName = candidate.displayName().toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || lowerName.contains(needle)) {
                switch (rankMatch(lowerName, needle)) {
                    case 0 -> exact.add(candidate);
                    case 1 -> prefixed.add(candidate);
                    default -> contains.add(candidate);
                }
            }
        }

        Comparator<ConflictTeamEntry> byName = Comparator.comparing(ConflictTeamEntry::displayName, String.CASE_INSENSITIVE_ORDER);
        exact.sort(byName);
        prefixed.sort(byName);
        contains.sort(byName);

        List<ConflictTeamEntry> merged = new ArrayList<>(exact.size() + prefixed.size() + contains.size());
        merged.addAll(exact);
        merged.addAll(prefixed);
        merged.addAll(contains);
        return merged;
    }

    private static int rankMatch(String lowerName, String query) {
        if (query.isEmpty() || lowerName.equals(query)) {
            return 0;
        }
        return lowerName.startsWith(query) ? 1 : 2;
    }

    private static Component trimToWidth(Theme theme, Component text, int maxWidth) {
        if (maxWidth <= 0 || theme.getStringWidth(text) <= maxWidth) {
            return text;
        }
        return Component.literal(theme.trimStringToWidth(text.getString(), maxWidth - 4) + "...");
    }

    /** One of the three stacked lists - a header/filter bar, its filtered rows, and its own scrollbar - laid out as a unit. */
    private final class StandingList {
        private final Supplier<Component> headingSupplier;
        private final Supplier<List<ConflictTeamEntry>> entriesSource;
        private final RelationKind kind;
        private final Component emptyText;

        private ListHeaderBar headerBar;
        private EntryRowsPanel rowsPanel;
        private PanelScrollBar scrollBar;

        private StandingList(
                Supplier<Component> headingSupplier,
                Supplier<List<ConflictTeamEntry>> entriesSource,
                RelationKind kind,
                Component emptyText
        ) {
            this.headingSupplier = headingSupplier;
            this.entriesSource = entriesSource;
            this.kind = kind;
            this.emptyText = emptyText;
        }

        void addWidgets() {
            headerBar = new ListHeaderBar(ConflictScreen.this, headingSupplier, this::refreshList);
            rowsPanel = new EntryRowsPanel(ConflictScreen.this, this::filteredEntries, kind, emptyText);
            scrollBar = new AutoHideScrollBar(ConflictScreen.this, rowsPanel);

            ConflictScreen.this.add(headerBar);
            ConflictScreen.this.add(rowsPanel);
            ConflictScreen.this.add(scrollBar);
        }

        void setBounds(int x, int y, int listWidth, int listHeight) {
            headerBar.setPosAndSize(x, y, listWidth, LIST_HEADER_H);
            headerBar.alignWidgets();
            int rowsTop = y + LIST_HEADER_H + LIST_HEADER_GAP;
            int rowsHeight = Math.max(LIST_MIN_H, listHeight - LIST_HEADER_H - LIST_HEADER_GAP);
            int rowsWidth = Math.max(0, listWidth - SB_WIDTH - 2);
            rowsPanel.setPosAndSize(x, rowsTop, rowsWidth, rowsHeight);
            rowsPanel.alignWidgets();
            scrollBar.setPosAndSize(x + listWidth - SB_WIDTH, rowsTop, SB_WIDTH, rowsHeight);
        }

        void refreshList() {
            if (rowsPanel != null) {
                rowsPanel.setScrollY(0);
                rowsPanel.refreshWidgets();
                rowsPanel.alignWidgets();
            }
        }

        private String currentQuery() {
            return headerBar == null ? "" : headerBar.currentQuery();
        }

        private List<ConflictTeamEntry> filteredEntries() {
            return applyNameFilter(entriesSource.get(), currentQuery());
        }
    }

    /** A {@link PanelScrollBar} that disappears entirely rather than drawing a full-length track once there's nothing to scroll. */
    private static class AutoHideScrollBar extends PanelScrollBar {
        AutoHideScrollBar(BaseScreen screen, Panel panel) {
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

    private static class ListHeaderBar extends Panel {
        private final Supplier<Component> headingSupplier;
        private final Runnable onFilterChanged;
        private TextBox searchBox;

        ListHeaderBar(BaseScreen screen, Supplier<Component> headingSupplier, Runnable onFilterChanged) {
            super(screen);
            this.headingSupplier = headingSupplier;
            this.onFilterChanged = onFilterChanged;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        String currentQuery() {
            return searchBox == null ? "" : searchBox.getText();
        }

        @Override
        public void addWidgets() {
            searchBox = new TextBox(this) {
                @Override
                public void onTextChanged() {
                    onFilterChanged.run();
                }
            };
            searchBox.ghostText = Component.translatable("gui.lc_claim_economy.war.filter_ghost").getString();
            searchBox.charLimit = 48;
            add(searchBox);
        }

        @Override
        public void alignWidgets() {
            if (searchBox == null) {
                return;
            }
            int boxWidth = Math.min(SEARCH_MAX_W, Math.max(SEARCH_MIN_W, width / 3));
            int boxX = Math.max(width - boxWidth - 2, 0);
            searchBox.setPosAndSize(boxX, (height - SEARCH_H) / 2, boxWidth, SEARCH_H);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_1.draw(graphics, x, y + h - 1, w, 1);

            if (searchBox != null && searchBox.width > 0) {
                int boxX = x + searchBox.getX();
                int boxY = y + searchBox.getY();
                NordColors.POLAR_NIGHT_0.draw(graphics, boxX, boxY, searchBox.width, searchBox.height);
                NordColors.POLAR_NIGHT_3.draw(graphics, boxX, boxY + searchBox.height - 1, searchBox.width, 1);
            }

            int titleMaxWidth = searchBox == null || searchBox.width <= 0
                    ? w - 4
                    : Math.max(0, searchBox.getX() - 6);
            if (titleMaxWidth > 0) {
                Component heading = trimToWidth(theme, headingSupplier.get().copy().withStyle(ChatFormatting.BOLD), titleMaxWidth);
                theme.drawString(graphics, heading, x + 2, y + 6, NordColors.FROST_2, 0);
            }

            super.draw(graphics, theme, x, y, w, h);
        }
    }

    private static class EntryRowsPanel extends Panel {
        private final Supplier<List<ConflictTeamEntry>> entriesSource;
        private final RelationKind kind;
        private final Component emptyText;

        EntryRowsPanel(BaseScreen screen, Supplier<List<ConflictTeamEntry>> entriesSource, RelationKind kind, Component emptyText) {
            super(screen);
            this.entriesSource = entriesSource;
            this.kind = kind;
            this.emptyText = emptyText;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        @Override
        public void addWidgets() {
            if (kind == RelationKind.DECLARE && !ClientConflictState.canManageWar()) {
                add(new EmptyStateLabel(this, Component.translatable("gui.lc_claim_economy.war.view_only").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                return;
            }

            List<ConflictTeamEntry> entries = entriesSource.get();
            if (entries.isEmpty()) {
                boolean searchNarrowedToEmpty = kind == RelationKind.DECLARE && !ClientConflictState.availableTargets().isEmpty();
                Component message = searchNarrowedToEmpty
                        ? Component.translatable("gui.lc_claim_economy.war.search_empty").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
                        : emptyText;
                add(new EmptyStateLabel(this, message));
                return;
            }

            for (ConflictTeamEntry entry : entries) {
                add(buildRow(entry));
            }
        }

        private record RowSpec(Component actionLabel, UUID targetId, boolean suppressAction, boolean declareAction) {
        }

        private StandingRow buildRow(ConflictTeamEntry entry) {
            RowSpec spec = switch (kind) {
                case INCOMING -> new RowSpec(null, null, true, false);
                case OUTGOING -> new RowSpec(
                        entry.isPending() ? null : Component.translatable("gui.lc_claim_economy.war.end_war"),
                        entry.teamId(),
                        false,
                        false
                );
                case DECLARE -> new RowSpec(
                        entry.isPending() ? null : Component.translatable("gui.lc_claim_economy.war.declare"),
                        entry.teamId(),
                        false,
                        true
                );
            };
            return new StandingRow(this, entry, kind, spec.actionLabel(), spec.targetId(), spec.suppressAction(), spec.declareAction());
        }

        @Override
        public void alignWidgets() {
            int y = 0;
            for (Widget widget : widgets) {
                widget.setPos(0, y);
                widget.setWidth(width);
                y += switch (widget) {
                    case EmptyStateLabel label -> {
                        label.setHeight(EMPTY_LABEL_H);
                        yield EMPTY_LABEL_H;
                    }
                    case StandingRow row -> {
                        row.setHeight(ENTRY_H);
                        row.alignWidgets();
                        yield ENTRY_H + ENTRY_GAP;
                    }
                    default -> 0;
                };
            }
        }
    }

    /** Italic placeholder label, deliberately without background/hover treatment - unlike {@link dev.voidpulsar.lc_claim_economy.client.gui.widget.EmptyMessageRow}, whose hover fill doesn't suit these narrower rows. */
    private static class EmptyStateLabel extends Button {
        EmptyStateLabel(Panel panel, Component label) {
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
        public void onClicked(MouseButton button) {
        }
    }

    private static class StandingRow extends Panel {
        private static final int INFO_BTN_SIZE = 16;

        private final ConflictTeamEntry entry;
        private final RelationKind kind;
        private final Component actionLabel;
        private final UUID actionTargetId;
        private final boolean suppressAction;
        private final boolean declareAction;
        private int badgeX;
        private int badgeWidth;

        StandingRow(
                Panel panel,
                ConflictTeamEntry entry,
                RelationKind kind,
                Component actionLabel,
                UUID actionTargetId,
                boolean suppressAction,
                boolean declareAction
        ) {
            super(panel);
            this.entry = entry;
            this.kind = kind;
            this.actionLabel = actionLabel;
            this.actionTargetId = actionTargetId;
            this.suppressAction = suppressAction;
            this.declareAction = declareAction;
        }

        private void sendToggle() {
            UUID target = actionTargetId != null ? actionTargetId : entry.teamId();
            PacketDistributor.sendToServer(new ToggleConflictPayload(target));
        }

        /** Purely informational - flags outgoing-war opponents missing a protection we could exploit. Not a layout concern. */
        private boolean isVulnerableRow() {
            return kind == RelationKind.OUTGOING && entry.hasWarVulnerability();
        }

        private Component currentBadgeText() {
            return entry.isPending()
                    ? Component.translatable("gui.lc_claim_economy.pending").withStyle(ChatFormatting.GOLD)
                    : formatEntryCostLabel(entry.warCostCopper());
        }

        @Override
        public void addWidgets() {
            if (entry.isPending() && ClientConflictState.canManageWar() && kind != RelationKind.INCOMING) {
                add(new Button(this, Component.empty(), Color4I.empty()) {
                    @Override
                    public void onClicked(MouseButton button) {
                        sendToggle();
                    }

                    @Override
                    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                    }
                });
            }

            if (!suppressAction && !entry.isPending() && actionLabel != null && ClientConflictState.canManageWar()) {
                boolean windowClosed = declareAction && !ClientConflictState.warDeclarationWindowOpen();
                add(new NordButton(
                        this,
                        actionLabel,
                        declareAction
                                ? (windowClosed ? ConflictIcons.SWORD.withTint(NordColors.POLAR_NIGHT_3) : ConflictIcons.SWORD)
                                : Icons.CANCEL.withTint(NordColors.SNOW_STORM_1)
                ) {
                    @Override
                    public void onClicked(MouseButton button) {
                        sendToggle();
                    }

                    @Override
                    public void addMouseOverText(TooltipList list) {
                        if (windowClosed) {
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
                    writeEntryTooltip(list, entry, kind);
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
            badgeWidth = theme.getStringWidth(currentBadgeText()) + 10;

            int rightEdge = width - ENTRY_PAD;
            Widget infoWidget = widgets.getLast();
            infoWidget.setPosAndSize(rightEdge - INFO_BTN_SIZE, (height - INFO_BTN_SIZE) / 2, INFO_BTN_SIZE, INFO_BTN_SIZE);
            rightEdge -= INFO_BTN_SIZE + ENTRY_BTN_GAP;

            int leftEdge = ENTRY_PAD;
            if (widgets.size() > 1) {
                Widget primaryWidget = widgets.getFirst();
                if (entry.isPending()) {
                    primaryWidget.setPosAndSize(ENTRY_PAD, 0, rightEdge - ENTRY_PAD, height);
                } else if (actionLabel != null) {
                    int buttonWidth = Math.min(88, Math.max(68, theme.getStringWidth(actionLabel) + 24));
                    primaryWidget.setPosAndSize(rightEdge - buttonWidth, (height - 18) / 2, buttonWidth, 18);
                    rightEdge -= buttonWidth + ENTRY_BTN_GAP;
                }
            }

            badgeX = Math.max(leftEdge + 40, rightEdge - badgeWidth);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            if (badgeWidth <= 0) {
                alignWidgets();
            }

            boolean vulnerable = isVulnerableRow();
            boolean hovered = isMouseOver();

            Color4I rowColor = (hovered || vulnerable) ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            int rowAlpha = hovered ? 220 : (vulnerable ? 200 : 180);
            rowColor.withAlpha(rowAlpha).draw(graphics, x + 1, y, w - 2, h);
            if (vulnerable) {
                NordColors.YELLOW.withAlpha(hovered ? 200 : 140).draw(graphics, x + 1, y, 2, h);
            } else if (hovered) {
                (declareAction ? NordColors.RED : NordColors.FROST_1).withAlpha(160).draw(graphics, x + 1, y, 2, h);
            }

            int maxNameWidth = Math.max(0, badgeX - ENTRY_PAD - ENTRY_BTN_GAP);
            Component displayName = trimToWidth(theme, Component.literal(entry.displayName()).withStyle(ChatFormatting.WHITE), maxNameWidth);
            Color4I labelColor = vulnerable ? NordColors.YELLOW : NordColors.SNOW_STORM_0;
            if (maxNameWidth > 0) {
                theme.drawString(graphics, displayName, x + ENTRY_PAD, y + 7, labelColor, 0);
            }

            NordColors.POLAR_NIGHT_0.withAlpha(200).draw(graphics, x + badgeX, y + 4, badgeWidth, h - 8);
            theme.drawString(graphics, currentBadgeText(), x + badgeX + 5, y + 7, NordColors.SNOW_STORM_0, 0);
        }
    }
}
