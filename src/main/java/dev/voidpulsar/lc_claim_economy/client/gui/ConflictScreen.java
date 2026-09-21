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
 * Standing-conflict management panel reachable from the team hub: three
 * stacked lists (wars against us, wars we started, and teams we could still
 * declare on) each with their own name filter. Every accept/decline/declare
 * click just fires {@link ToggleConflictPayload} at the server and waits for
 * the next {@link dev.voidpulsar.lc_claim_economy.network.ConflictStateBroadcastPayload}
 * to redraw the lists - there's no optimistic local state here.
 */
public class ConflictScreen extends BaseScreen {
    private static final int HEADER_HEIGHT = LcScreenChrome.HEADER_HEIGHT;
    private static final int HEADER_BUTTON_SIZE = LcScreenChrome.HEADER_BUTTON_SIZE;
    private static final int CONTENT_PAD = LcScreenChrome.CONTENT_PAD;
    private static final int SCROLLBAR_WIDTH = LcScreenChrome.SCROLLBAR_WIDTH;
    private static final int SECTION_GAP = 6;
    private static final int SECTION_HEADER_HEIGHT = 20;
    private static final int SECTION_HEADER_GAP = 2;
    private static final int MIN_SECTION_LIST_HEIGHT = 28;
    private static final int ROW_HEIGHT = 22;
    private static final int ROW_GAP = 2;
    private static final int ROW_PAD = 6;
    private static final int ROW_BUTTON_GAP = 4;
    private static final int EMPTY_ROW_HEIGHT = 14;
    private static final int FILTER_BOX_HEIGHT = 16;
    private static final int FILTER_BOX_MIN_WIDTH = 96;
    private static final int FILTER_BOX_MAX_WIDTH = 160;

    private final MyTeamScreen parentScreen;
    private SimpleButton returnButton;
    private SimpleButton costInfoButton;
    private ConflictSection incomingPanel;
    private ConflictSection outgoingPanel;
    private ConflictSection declarablePanel;

    public ConflictScreen(MyTeamScreen parentScreen) {
        this.parentScreen = parentScreen;
    }

    public static void refreshIfOpen() {
        ConflictScreen open = ClientUtils.getCurrentGuiAs(ConflictScreen.class);
        if (open != null) {
            open.refreshSections();
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
        returnButton = new SimpleButton(this, Component.translatable("gui.back"), Icons.BACK, (button, mouseButton) -> parentScreen.openGui());
        add(returnButton);

        costInfoButton = new SimpleButton(this, Component.empty(), Icons.INFO, (button, mouseButton) -> {}) {
            @Override
            public void addMouseOverText(TooltipList list) {
                populateCostTooltip(list);
            }

            @Override
            public void playClickSound() {
            }
        };
        add(costInfoButton);

        incomingPanel = new ConflictSection(
                () -> Component.translatable("gui.lc_claim_economy.war.incoming_heading"),
                ClientConflictState::incoming,
                PanelRole.INCOMING,
                Component.translatable("gui.lc_claim_economy.war.empty_incoming").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
        outgoingPanel = new ConflictSection(
                () -> Component.translatable("gui.lc_claim_economy.war.outgoing_heading"),
                ClientConflictState::outgoing,
                PanelRole.OUTGOING,
                Component.translatable("gui.lc_claim_economy.war.empty_outgoing").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );
        declarablePanel = new ConflictSection(
                ConflictScreen::buildDeclareHeading,
                ClientConflictState::availableTargets,
                PanelRole.DECLARE,
                Component.translatable("gui.lc_claim_economy.war.empty_targets").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)
        );

        incomingPanel.addWidgets();
        outgoingPanel.addWidgets();
        declarablePanel.addWidgets();
    }

    @Override
    public void alignWidgets() {
        returnButton.setPosAndSize(5, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);
        costInfoButton.setPosAndSize(5 + HEADER_BUTTON_SIZE + 4, 5, HEADER_BUTTON_SIZE, HEADER_BUTTON_SIZE);

        int top = HEADER_HEIGHT + 6;
        int innerHeight = height - top - CONTENT_PAD;
        int panelWidth = width - CONTENT_PAD * 2;
        int panelHeight = (innerHeight - SECTION_GAP * 2) / 3;

        int cursorY = top;
        incomingPanel.setBounds(CONTENT_PAD, cursorY, panelWidth, panelHeight);
        cursorY += panelHeight + SECTION_GAP;
        outgoingPanel.setBounds(CONTENT_PAD, cursorY, panelWidth, panelHeight);
        cursorY += panelHeight + SECTION_GAP;
        declarablePanel.setBounds(CONTENT_PAD, cursorY, panelWidth, innerHeight - (cursorY - top));
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

    /** Re-pulls each section's rows from the client cache and re-lays-out the screen; called after a fresh broadcast lands. */
    private void refreshSections() {
        if (incomingPanel != null) {
            incomingPanel.refreshList();
        }
        if (outgoingPanel != null) {
            outgoingPanel.refreshList();
        }
        if (declarablePanel != null) {
            declarablePanel.refreshList();
        }
        alignWidgets();
    }

    private enum PanelRole {
        INCOMING,
        OUTGOING,
        DECLARE
    }

    private static Component buildDeclareHeading() {
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

    private static void populateCostTooltip(TooltipList list) {
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
                renderMultiplier(ClientConflictState.warCostMultiplier())
        ).withStyle(ChatFormatting.GRAY));
    }

    /** Whole numbers print without a trailing ".0"; the multiplier is a display value only, never re-parsed. */
    private static String renderMultiplier(double multiplier) {
        if (Math.rint(multiplier) == multiplier) {
            return String.valueOf((long) multiplier);
        }
        return String.valueOf(multiplier);
    }

    private static Component formatUpkeepCost(long copper) {
        if (copper <= 0) {
            return CurrencyTextFormat.formatPrice(copper);
        }
        return Component.translatable(
                "gui.lc_claim_economy.war.entry_cost",
                CurrencyTextFormat.formatPrice(copper),
                SafeguardPriceDisplay.upkeepPeriodLabel()
        ).withStyle(ChatFormatting.GOLD);
    }

    private static void populateEntryTooltip(TooltipList list, ConflictTeamEntry entry, PanelRole role) {
        Component period = SafeguardPriceDisplay.upkeepPeriodLabel();
        boolean pendingDeclare = entry.status() == ConflictEntryStatus.PENDING_DECLARE;
        boolean pendingEnd = entry.status() == ConflictEntryStatus.PENDING_END;

        if (pendingDeclare && role == PanelRole.DECLARE) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_declare")
                    .withStyle(ChatFormatting.GOLD));
            appendCostBreakdown(list, entry, period);
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

        if (pendingDeclare && role == PanelRole.INCOMING) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_incoming")
                    .withStyle(ChatFormatting.GOLD));
            appendCostBreakdown(list, entry, period);
            return;
        }

        if (pendingEnd && role == PanelRole.OUTGOING) {
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.pending_end")
                    .withStyle(ChatFormatting.GOLD));
            appendCostBreakdown(list, entry, period);
            appendVulnerabilityLines(list, entry);
            if (ClientConflictState.canManageWar()) {
                list.blankLine();
                list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.click_to_cancel")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        appendCostBreakdown(list, entry, period);
        if (role == PanelRole.DECLARE && entry.opponentPendingDeclareOnViewer()) {
            list.blankLine();
            list.add(Component.translatable("gui.lc_claim_economy.war.entry_tooltip.opponent_pending_declare")
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (role == PanelRole.DECLARE && !ClientConflictState.warDeclarationWindowOpen()) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.war.entry_tooltip.declare_window_closed",
                    ClientConflictState.warDeclarationWindowDescription()
            ).withStyle(ChatFormatting.RED));
        }
        if (role == PanelRole.OUTGOING) {
            appendVulnerabilityLines(list, entry);
        }
    }

    private static void appendVulnerabilityLines(TooltipList list, ConflictTeamEntry entry) {
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

    private static void appendCostBreakdown(TooltipList list, ConflictTeamEntry entry, Component period) {
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

    /** Case-insensitive substring filter; exact and prefix matches float to the top of the surviving set via {@link #matchScore}. */
    private static List<ConflictTeamEntry> matchingEntries(List<ConflictTeamEntry> entries, String query) {
        if (entries.isEmpty()) {
            return List.of();
        }

        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<ConflictTeamEntry> matches = new ArrayList<>();
        for (ConflictTeamEntry candidate : entries) {
            if (normalized.isEmpty() || candidate.displayName().toLowerCase(Locale.ROOT).contains(normalized)) {
                matches.add(candidate);
            }
        }

        matches.sort(Comparator
                .comparingInt((ConflictTeamEntry candidate) -> matchScore(candidate.displayName(), normalized))
                .thenComparing(ConflictTeamEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return matches;
    }

    private static int matchScore(String name, String query) {
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

    /** Bundles one of the three stacked lists (header + filterable rows + its own scrollbar) as a single layout unit. */
    private final class ConflictSection {
        private final Supplier<Component> headingSupplier;
        private final Supplier<List<ConflictTeamEntry>> entriesSource;
        private final PanelRole role;
        private final Component placeholderText;

        private SectionHeader header;
        private EntryListPanel entryList;
        private PanelScrollBar scrollBar;

        private ConflictSection(
                Supplier<Component> headingSupplier,
                Supplier<List<ConflictTeamEntry>> entriesSource,
                PanelRole role,
                Component placeholderText
        ) {
            this.headingSupplier = headingSupplier;
            this.entriesSource = entriesSource;
            this.role = role;
            this.placeholderText = placeholderText;
        }

        void addWidgets() {
            header = new SectionHeader(ConflictScreen.this, headingSupplier, this::refreshList);
            entryList = new EntryListPanel(ConflictScreen.this, this::visibleEntries, role, placeholderText);
            scrollBar = new CollapsingScrollBar(ConflictScreen.this, entryList);

            ConflictScreen.this.add(header);
            ConflictScreen.this.add(entryList);
            ConflictScreen.this.add(scrollBar);
        }

        void setBounds(int x, int y, int sectionWidth, int sectionHeight) {
            header.setPosAndSize(x, y, sectionWidth, SECTION_HEADER_HEIGHT);
            header.alignWidgets();
            int listTop = y + SECTION_HEADER_HEIGHT + SECTION_HEADER_GAP;
            int listHeight = Math.max(MIN_SECTION_LIST_HEIGHT, sectionHeight - SECTION_HEADER_HEIGHT - SECTION_HEADER_GAP);
            int listWidth = Math.max(0, sectionWidth - SCROLLBAR_WIDTH - 2);
            entryList.setPosAndSize(x, listTop, listWidth, listHeight);
            entryList.alignWidgets();
            scrollBar.setPosAndSize(x + sectionWidth - SCROLLBAR_WIDTH, listTop, SCROLLBAR_WIDTH, listHeight);
        }

        void refreshList() {
            if (entryList != null) {
                entryList.setScrollY(0);
                entryList.refreshWidgets();
                entryList.alignWidgets();
            }
        }

        private String currentQuery() {
            return header == null ? "" : header.currentQuery();
        }

        private List<ConflictTeamEntry> visibleEntries() {
            return matchingEntries(entriesSource.get(), currentQuery());
        }
    }

    /** Same as a stock {@link PanelScrollBar}, but disappears entirely instead of drawing a full-length track when nothing scrolls. */
    private static class CollapsingScrollBar extends PanelScrollBar {
        CollapsingScrollBar(BaseScreen screen, Panel panel) {
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

    private static class SectionHeader extends Panel {
        private final Supplier<Component> headingSupplier;
        private final Runnable onQueryChanged;
        private TextBox queryBox;

        SectionHeader(BaseScreen screen, Supplier<Component> headingSupplier, Runnable onQueryChanged) {
            super(screen);
            this.headingSupplier = headingSupplier;
            this.onQueryChanged = onQueryChanged;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        String currentQuery() {
            return queryBox == null ? "" : queryBox.getText();
        }

        @Override
        public void addWidgets() {
            queryBox = new TextBox(this) {
                @Override
                public void onTextChanged() {
                    onQueryChanged.run();
                }
            };
            queryBox.ghostText = Component.translatable("gui.lc_claim_economy.war.filter_ghost").getString();
            queryBox.charLimit = 48;
            add(queryBox);
        }

        @Override
        public void alignWidgets() {
            if (queryBox == null) {
                return;
            }
            int filterWidth = Math.min(FILTER_BOX_MAX_WIDTH, Math.max(FILTER_BOX_MIN_WIDTH, width / 3));
            int filterX = Math.max(width - filterWidth - 2, 0);
            queryBox.setPosAndSize(filterX, (height - FILTER_BOX_HEIGHT) / 2, filterWidth, FILTER_BOX_HEIGHT);
        }

        @Override
        public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            NordColors.POLAR_NIGHT_1.draw(graphics, x, y + h - 1, w, 1);

            if (queryBox != null && queryBox.width > 0) {
                int boxX = x + queryBox.getX();
                int boxY = y + queryBox.getY();
                NordColors.POLAR_NIGHT_0.draw(graphics, boxX, boxY, queryBox.width, queryBox.height);
                NordColors.POLAR_NIGHT_3.draw(graphics, boxX, boxY + queryBox.height - 1, queryBox.width, 1);
            }

            int titleMaxWidth = queryBox == null || queryBox.width <= 0
                    ? w - 4
                    : Math.max(0, queryBox.getX() - 6);
            Component heading = headingSupplier.get().copy().withStyle(ChatFormatting.BOLD);
            if (titleMaxWidth > 0 && theme.getStringWidth(heading) > titleMaxWidth) {
                heading = Component.literal(theme.trimStringToWidth(heading.getString(), titleMaxWidth - 4) + "...");
            }
            if (titleMaxWidth > 0) {
                theme.drawString(graphics, heading, x + 2, y + 6, NordColors.FROST_2, 0);
            }

            super.draw(graphics, theme, x, y, w, h);
        }
    }

    private static class EntryListPanel extends Panel {
        private final Supplier<List<ConflictTeamEntry>> entriesSource;
        private final PanelRole role;
        private final Component placeholderText;

        EntryListPanel(BaseScreen screen, Supplier<List<ConflictTeamEntry>> entriesSource, PanelRole role, Component placeholderText) {
            super(screen);
            this.entriesSource = entriesSource;
            this.role = role;
            this.placeholderText = placeholderText;
            setOnlyRenderWidgetsInside(true);
            setOnlyInteractWithWidgetsInside(true);
        }

        @Override
        public void addWidgets() {
            if (role == PanelRole.DECLARE && !ClientConflictState.canManageWar()) {
                add(new PlaceholderRow(this, Component.translatable("gui.lc_claim_economy.war.view_only").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
                return;
            }

            List<ConflictTeamEntry> entries = entriesSource.get();
            if (entries.isEmpty()) {
                Component message = placeholderText;
                if (role == PanelRole.DECLARE && !ClientConflictState.availableTargets().isEmpty()) {
                    message = Component.translatable("gui.lc_claim_economy.war.search_empty").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
                }
                add(new PlaceholderRow(this, message));
                return;
            }

            for (ConflictTeamEntry entry : entries) {
                add(buildRow(entry));
            }
        }

        private ConflictEntryRow buildRow(ConflictTeamEntry entry) {
            return switch (role) {
                case INCOMING -> new ConflictEntryRow(this, entry, role, null, null, true, false);
                case OUTGOING -> new ConflictEntryRow(
                        this,
                        entry,
                        role,
                        entry.isPending() ? null : Component.translatable("gui.lc_claim_economy.war.end_war"),
                        entry.teamId(),
                        false,
                        false
                );
                case DECLARE -> new ConflictEntryRow(
                        this,
                        entry,
                        role,
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
                if (widget instanceof PlaceholderRow) {
                    widget.setHeight(EMPTY_ROW_HEIGHT);
                    y += EMPTY_ROW_HEIGHT;
                } else if (widget instanceof ConflictEntryRow row) {
                    widget.setHeight(ROW_HEIGHT);
                    row.alignWidgets();
                    y += ROW_HEIGHT + ROW_GAP;
                }
            }
        }
    }

    /** Italic placeholder label with no background/hover treatment - deliberately not {@link dev.voidpulsar.lc_claim_economy.client.gui.widget.EmptyMessageRow}, whose default hover fill doesn't fit these narrower list rows. */
    private static class PlaceholderRow extends Button {
        PlaceholderRow(Panel panel, Component label) {
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

    private static class ConflictEntryRow extends Panel {
        private static final int INFO_BUTTON_SIZE = 16;

        private final ConflictTeamEntry entry;
        private final PanelRole role;
        private final Component primaryActionLabel;
        private final UUID targetTeamId;
        private final boolean noActionButton;
        private final boolean isDeclareAction;
        private int costBadgeX;
        private int costBadgeWidth;

        ConflictEntryRow(
                Panel panel,
                ConflictTeamEntry entry,
                PanelRole role,
                Component primaryActionLabel,
                UUID targetTeamId,
                boolean noActionButton,
                boolean isDeclareAction
        ) {
            super(panel);
            this.entry = entry;
            this.role = role;
            this.primaryActionLabel = primaryActionLabel;
            this.targetTeamId = targetTeamId;
            this.noActionButton = noActionButton;
            this.isDeclareAction = isDeclareAction;
        }

        private void dispatchToggle() {
            UUID target = targetTeamId != null ? targetTeamId : entry.teamId();
            PacketDistributor.sendToServer(new ToggleConflictPayload(target));
        }

        /** Not a layout concern - flags outgoing-war opponents who are missing a protection we could exploit. */
        private boolean showsVulnerability() {
            return role == PanelRole.OUTGOING && entry.hasWarVulnerability();
        }

        @Override
        public void addWidgets() {
            if (entry.isPending() && ClientConflictState.canManageWar() && role != PanelRole.INCOMING) {
                add(new Button(this, Component.empty(), Color4I.empty()) {
                    @Override
                    public void onClicked(MouseButton button) {
                        dispatchToggle();
                    }

                    @Override
                    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
                    }
                });
            }

            if (!noActionButton && !entry.isPending() && primaryActionLabel != null && ClientConflictState.canManageWar()) {
                boolean declareWindowClosed = isDeclareAction && !ClientConflictState.warDeclarationWindowOpen();
                add(new NordButton(
                        this,
                        primaryActionLabel,
                        isDeclareAction
                                ? (declareWindowClosed ? ConflictIcons.SWORD.withTint(NordColors.POLAR_NIGHT_3) : ConflictIcons.SWORD)
                                : Icons.CANCEL.withTint(NordColors.SNOW_STORM_1)
                ) {
                    @Override
                    public void onClicked(MouseButton button) {
                        dispatchToggle();
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
                    populateEntryTooltip(list, entry, role);
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
            Component badgeText = entry.isPending()
                    ? Component.translatable("gui.lc_claim_economy.pending").withStyle(ChatFormatting.GOLD)
                    : formatUpkeepCost(entry.warCostCopper());
            costBadgeWidth = theme.getStringWidth(badgeText) + 10;

            int rightEdge = width - ROW_PAD;
            Widget infoWidget = widgets.getLast();
            infoWidget.setPosAndSize(rightEdge - INFO_BUTTON_SIZE, (height - INFO_BUTTON_SIZE) / 2, INFO_BUTTON_SIZE, INFO_BUTTON_SIZE);
            rightEdge -= INFO_BUTTON_SIZE + ROW_BUTTON_GAP;

            int leftEdge = ROW_PAD;
            if (widgets.size() > 1) {
                Widget primaryWidget = widgets.getFirst();
                if (entry.isPending()) {
                    primaryWidget.setPosAndSize(ROW_PAD, 0, rightEdge - ROW_PAD, height);
                } else if (primaryActionLabel != null) {
                    int buttonWidth = Math.min(88, Math.max(68, theme.getStringWidth(primaryActionLabel) + 24));
                    primaryWidget.setPosAndSize(rightEdge - buttonWidth, (height - 18) / 2, buttonWidth, 18);
                    rightEdge -= buttonWidth + ROW_BUTTON_GAP;
                }
            }

            costBadgeX = Math.max(leftEdge + 40, rightEdge - costBadgeWidth);
        }

        @Override
        public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
            if (costBadgeWidth <= 0) {
                alignWidgets();
            }

            boolean vulnerable = showsVulnerability();
            Color4I rowColor = isMouseOver() ? NordColors.POLAR_NIGHT_1 : NordColors.POLAR_NIGHT_2;
            if (vulnerable && !isMouseOver()) {
                rowColor = NordColors.POLAR_NIGHT_1;
            }
            rowColor.withAlpha(isMouseOver() ? 220 : (vulnerable ? 200 : 180)).draw(graphics, x + 1, y, w - 2, h);
            if (vulnerable) {
                NordColors.YELLOW.withAlpha(isMouseOver() ? 200 : 140).draw(graphics, x + 1, y, 2, h);
            } else if (isMouseOver()) {
                (isDeclareAction ? NordColors.RED : NordColors.FROST_1).withAlpha(160).draw(graphics, x + 1, y, 2, h);
            }

            Component badgeText = entry.isPending()
                    ? Component.translatable("gui.lc_claim_economy.pending").withStyle(ChatFormatting.GOLD)
                    : formatUpkeepCost(entry.warCostCopper());
            int maxNameWidth = Math.max(0, costBadgeX - ROW_PAD - ROW_BUTTON_GAP);
            Component displayName = Component.literal(entry.displayName()).withStyle(ChatFormatting.WHITE);
            Color4I labelColor = vulnerable ? NordColors.YELLOW : NordColors.SNOW_STORM_0;
            if (maxNameWidth > 0 && theme.getStringWidth(displayName) > maxNameWidth) {
                displayName = Component.literal(theme.trimStringToWidth(displayName.getString(), maxNameWidth - 4) + "...");
            }

            if (maxNameWidth > 0) {
                theme.drawString(graphics, displayName, x + ROW_PAD, y + 7, labelColor, 0);
            }

            NordColors.POLAR_NIGHT_0.withAlpha(200).draw(graphics, x + costBadgeX, y + 4, costBadgeWidth, h - 8);
            theme.drawString(graphics, badgeText, x + costBadgeX + 5, y + 7, NordColors.SNOW_STORM_0, 0);
        }
    }
}
