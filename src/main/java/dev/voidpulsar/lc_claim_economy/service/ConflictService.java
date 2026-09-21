package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements;
import dev.voidpulsar.lc_claim_economy.network.ConflictEntryStatus;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates the claim-war subsystem: upkeep/war-cost math is delegated to
 * {@link ConflictBillingMath} and {@link SafeguardPricing}, this class only
 * decides which teams are billed, in what order, and what a player sees on
 * the war screen.
 */
public final class ConflictService {
    public record WarCostBreakdown(
            long baseUpkeepCopper,
            long incomingWarCopper,
            long outgoingWarCopper,
            int incomingWarCount,
            int outgoingWarCount
    ) {
        public long totalWarCopper() {
            return incomingWarCopper + outgoingWarCopper;
        }

        public long totalUpkeepCopper() {
            return baseUpkeepCopper + totalWarCopper();
        }
    }

    public record WarTeamView(
            UUID teamId,
            String displayName,
            long targetBaseUpkeepCopper,
            long warCostCopper,
            ConflictEntryStatus status,
            boolean opponentPendingDeclareOnViewer,
            boolean blockEditProtected,
            boolean explosionProtected,
            boolean pvpProtected
    ) {
        public WarTeamView(
                UUID teamId,
                String displayName,
                long targetBaseUpkeepCopper,
                long warCostCopper
        ) {
            this(
                    teamId,
                    displayName,
                    targetBaseUpkeepCopper,
                    warCostCopper,
                    ConflictEntryStatus.ACTIVE,
                    false,
                    true,
                    true,
                    true
            );
        }

        public boolean hasWarVulnerability() {
            return !blockEditProtected || !explosionProtected || !pvpProtected;
        }
    }

    private ConflictService() {
    }

    public static boolean isEnabled() {
        return LcClaimEconomyConfig.SERVER.warEnabled.get();
    }

    public static boolean isClaimTeam(MinecraftServer srv, Team squad) {
        boolean claimSourceUsable = squad.isValid() && FTBChunksAPI.api().isManagerLoaded();
        if (!claimSourceUsable) {
            return false;
        }
        boolean untrackedParty = squad.isPartyTeam() && !TeamRegistry.isTracked(srv, squad);
        if (untrackedParty) {
            return false;
        }
        ChunkTeamData claimData = FTBChunksAPI.api().getManager().getOrCreateData(squad);
        return !claimData.getClaimedChunks().isEmpty();
    }

    public static long baseUpkeepCopper(MinecraftServer srv, Team squad) {
        return baseUpkeepCopper(srv, squad, LcClaimEconomySavedData.get(srv).getPendingState(squad.getTeamId()));
    }

    public static long baseUpkeepCopper(MinecraftServer srv, Team squad, TeamQueuedChanges queuedChanges) {
        return SafeguardPricing.calculateTotalUpkeepCopper(srv, squad, queuedChanges);
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer srv, Team squad) {
        return calculateWarCosts(srv, squad, LcClaimEconomySavedData.get(srv).getPendingState(squad.getTeamId()));
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer srv, Team squad, TeamQueuedChanges queuedChanges) {
        return calculateWarCosts(srv, squad, queuedChanges, SafeguardRollbackService.pricingProperties(squad, queuedChanges));
    }

    public static WarCostBreakdown calculateWarCosts(
            MinecraftServer srv,
            Team squad,
            TeamQueuedChanges queuedChanges,
            Map<String, String> priceOverrides
    ) {
        long baseline = SafeguardPricing.calculateTotalUpkeepCopper(srv, squad, queuedChanges, priceOverrides);
        if (!isEnabled()) {
            return new WarCostBreakdown(baseline, 0L, 0L, 0, 0);
        }

        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);

        // Skip aggressor links pointing at teams that were deleted or no longer hold
        // any claims - resolveEligibleTarget/isClaimTeam are the same gate buildIncomingViews
        // applies, so a team never gets billed for a war that wouldn't show up on its screen.
        int inboundCount = countBillableAggressors(srv, ledger, squad.getTeamId());
        long inboundCopper = ConflictBillingMath.sumOrdinalIncomingTerms(baseline, inboundCount);

        Set<UUID> hostileIds = ledger.getWarTargets(squad.getTeamId());
        long outboundCopper = 0L;
        for (UUID hostileId : hostileIds) {
            Team foe = TeamRegistry.resolve(srv, hostileId);
            if (foe == null || !isClaimTeam(srv, foe)) {
                continue;
            }
            long foeBaseline = baseUpkeepCopper(srv, foe, ledger.getPendingState(hostileId));
            outboundCopper += ConflictBillingMath.outgoingWarCostCopper(foeBaseline);
        }

        return new WarCostBreakdown(baseline, inboundCopper, outboundCopper, inboundCount, hostileIds.size());
    }

    public static MoneyValue calculateTotalUpkeepCost(MinecraftServer srv, Team squad, TeamQueuedChanges queuedChanges) {
        return CurrencyAmounts.fromCopper(calculateTotalUpkeepCostCopper(srv, squad, queuedChanges));
    }

    public static long calculateTotalUpkeepCostCopper(MinecraftServer srv, Team squad, TeamQueuedChanges queuedChanges) {
        return calculateWarCosts(srv, squad, queuedChanges).totalUpkeepCopper();
    }

    public static long calculateTotalUpkeepCostCopper(
            MinecraftServer srv,
            Team squad,
            TeamQueuedChanges queuedChanges,
            Map<String, String> priceOverrides
    ) {
        return calculateWarCosts(srv, squad, queuedChanges, priceOverrides).totalUpkeepCopper();
    }

    public static boolean canAffordUpkeepWithPendingProperty(
            MinecraftServer srv,
            Team squad,
            TeamQueuedChanges queuedChanges,
            IBankAccount wallet,
            TeamProperty<?> trait
    ) {
        Map<String, String> pricingSnapshot = SafeguardRollbackService.pricingWithAppliedPending(squad, queuedChanges, trait);
        long priceCopper = calculateTotalUpkeepCostCopper(srv, squad, queuedChanges, pricingSnapshot);
        return canCoverCopper(wallet, priceCopper);
    }

    public static long calculateProtectionAndIncomingUpkeepCopper(
            MinecraftServer srv,
            Team squad,
            TeamQueuedChanges queuedChanges
    ) {
        WarCostBreakdown breakdown = calculateWarCosts(srv, squad, queuedChanges);
        return breakdown.baseUpkeepCopper() + breakdown.incomingWarCopper();
    }

    public static boolean canAffordUpkeep(MinecraftServer srv, Team squad, TeamQueuedChanges queuedChanges, IBankAccount wallet) {
        return canCoverCopper(wallet, calculateTotalUpkeepCostCopper(srv, squad, queuedChanges));
    }

    /** Shared affordability check: no charge is ever refused for a non-positive amount. */
    private static boolean canCoverCopper(IBankAccount wallet, long amountCopper) {
        if (amountCopper <= 0L) {
            return true;
        }
        return wallet.getMoneyStorage().containsValue(CurrencyAmounts.fromCopper(amountCopper));
    }

    public static boolean canAffordUpkeepWithOutgoingWar(
            MinecraftServer srv,
            Team squad,
            TeamQueuedChanges queuedChanges,
            IBankAccount wallet,
            LcClaimEconomySavedData ledger,
            UUID opponentId
    ) {
        UUID squadId = squad.getTeamId();
        if (ledger.isAtWarWith(squadId, opponentId)) {
            return canAffordUpkeep(srv, squad, queuedChanges, wallet);
        }
        ledger.setWarTarget(squadId, opponentId, true);
        boolean canPay = canAffordUpkeep(srv, squad, queuedChanges, wallet);
        ledger.setWarTarget(squadId, opponentId, false);
        return canPay;
    }

    public static List<UUID> pendingWarRestoreOrder(
            MinecraftServer srv,
            Team squad,
            TeamQueuedChanges queuedChanges,
            LcClaimEconomySavedData ledger
    ) {
        UUID squadId = squad.getTeamId();
        List<UUID> candidates = queuedChanges.pendingWarDeclares().stream()
                .filter(id -> !ledger.isAtWarWith(squadId, id))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        candidates.sort(Comparator.comparingLong(id -> {
            Team foe = TeamRegistry.resolve(srv, id);
            return foe == null ? Long.MAX_VALUE : costToDeclareWar(srv, squad, foe);
        }));
        return candidates;
    }

    public static List<UUID> outgoingWarDismantleOrder(MinecraftServer srv, Team squad, LcClaimEconomySavedData ledger) {
        UUID squadId = squad.getTeamId();
        List<UUID> opponents = new ArrayList<>(ledger.getWarTargets(squadId));
        opponents.sort(Comparator.comparing(UUID::toString));

        Map<UUID, Long> priceByOpponent = new HashMap<>();
        for (UUID opponentId : opponents) {
            Team foe = TeamRegistry.resolve(srv, opponentId);
            long price = foe == null ? 0L : ConflictBillingMath.outgoingWarCostCopper(baseUpkeepCopper(srv, foe));
            priceByOpponent.put(opponentId, price);
        }

        opponents.sort(Comparator.comparingLong((UUID id) -> priceByOpponent.getOrDefault(id, 0L)).reversed());
        return opponents;
    }

    public static long costToDeclareWar(MinecraftServer srv, Team aggressor, Team foe) {
        if (!isEnabled()) {
            return 0L;
        }
        return ConflictBillingMath.outgoingWarCostCopper(baseUpkeepCopper(srv, foe));
    }

    /** Whether this team is currently a party to any war, as attacker or defender. */
    public static boolean isAtWar(MinecraftServer srv, UUID squadId) {
        return !LcClaimEconomySavedData.get(srv).collectWarPartnerIds(squadId).isEmpty();
    }

    public static boolean isWarEligibleTeam(MinecraftServer srv, Team squad) {
        return TeamRegistry.isTracked(srv, squad)
                && !isPeaceful(srv, squad.getTeamId())
                && meetsMinClaimThreshold(srv, squad);
    }

    /** Whether a team has opted out of the war system via {@code /lcce war peaceful}; see {@link #setPeaceful}. */
    public static boolean isPeaceful(MinecraftServer srv, UUID squadId) {
        return LcClaimEconomySavedData.get(srv).isPeaceful(squadId);
    }

    /**
     * Flips a team's peaceful flag, unless that would mean going peaceful while still
     * at war - active wars must be ended first so peaceful mode can't be used to dodge
     * an ongoing siege. Returns whether the change was applied.
     */
    public static boolean setPeaceful(MinecraftServer srv, UUID squadId, boolean wantsPeaceful) {
        if (wantsPeaceful && isAtWar(srv, squadId)) {
            return false;
        }
        LcClaimEconomySavedData.get(srv).setPeaceful(squadId, wantsPeaceful);
        return true;
    }

    public static boolean meetsMinClaimThreshold(MinecraftServer srv, Team squad) {
        int claimFloor = LcClaimEconomyConfig.SERVER.warMinClaimedChunks.get();
        if (claimFloor <= 0) {
            return true;
        }
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }
        return FTBChunksAPI.api().getManager().getOrCreateData(squad).getClaimedChunks().size() > claimFloor;
    }

    public static List<WarTeamView> buildIncomingViews(MinecraftServer srv, Team viewer) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);
        UUID viewerId = viewer.getTeamId();
        long viewerBaseline = baseUpkeepCopper(srv, viewer);
        List<WarTeamView> rows = new ArrayList<>();
        Set<UUID> claimed = new HashSet<>();

        for (LcClaimEconomySavedData.TeamLinkEntry link : ledger.getAllLinks()) {
            if (!link.warTargets().contains(viewerId)) {
                continue;
            }
            Team aggressor = resolveEligibleTarget(srv, link.ftbTeamId());
            if (aggressor == null) {
                continue;
            }
            claimed.add(aggressor.getTeamId());
            rows.add(describeOpponent(
                    aggressor,
                    baseUpkeepCopper(srv, aggressor),
                    viewerBaseline,
                    ConflictEntryStatus.ACTIVE,
                    false
            ));
        }

        for (LcClaimEconomySavedData.TeamLinkEntry link : ledger.getAllLinks()) {
            UUID candidateId = link.ftbTeamId();
            boolean alreadyHandled = candidateId.equals(viewerId) || claimed.contains(candidateId);
            boolean pendingAgainstViewer = link.pendingState().isPendingWarDeclare(viewerId);
            if (alreadyHandled || !pendingAgainstViewer) {
                continue;
            }
            Team aggressor = resolveEligibleTarget(srv, candidateId);
            if (aggressor == null) {
                continue;
            }
            claimed.add(candidateId);
            rows.add(describeOpponent(
                    aggressor,
                    baseUpkeepCopper(srv, aggressor),
                    viewerBaseline,
                    ConflictEntryStatus.PENDING_DECLARE,
                    false
            ));
        }

        sortByName(rows);
        double multiplier = warMultiplier();
        for (int position = 0; position < rows.size(); position++) {
            WarTeamView row = rows.get(position);
            long ordinalCost = ConflictBillingMath.ordinalWarTermCopper(viewerBaseline, position, multiplier);
            rows.set(position, new WarTeamView(
                    row.teamId(),
                    row.displayName(),
                    row.targetBaseUpkeepCopper(),
                    ordinalCost,
                    row.status(),
                    row.opponentPendingDeclareOnViewer(),
                    row.blockEditProtected(),
                    row.explosionProtected(),
                    row.pvpProtected()
            ));
        }
        return rows;
    }

    /** Lists only the wars presently counted in this team's incoming upkeep bill (no pending declares). */
    public static List<WarTeamView> buildBilledIncomingViews(MinecraftServer srv, Team viewer) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);
        UUID viewerId = viewer.getTeamId();
        long viewerBaseline = baseUpkeepCopper(srv, viewer);
        double multiplier = warMultiplier();

        List<UUID> aggressorIds = new ArrayList<>();
        for (LcClaimEconomySavedData.TeamLinkEntry link : ledger.getAllLinks()) {
            if (!link.warTargets().contains(viewerId)) {
                continue;
            }
            Team aggressor = resolveEligibleTarget(srv, link.ftbTeamId());
            if (aggressor != null) {
                aggressorIds.add(aggressor.getTeamId());
            }
        }
        aggressorIds.sort(Comparator.comparing(UUID::toString));

        List<WarTeamView> rows = new ArrayList<>();
        int position = 0;
        for (UUID aggressorId : aggressorIds) {
            Team aggressor = TeamRegistry.resolve(srv, aggressorId);
            if (aggressor == null) {
                continue;
            }
            long billedCost = ConflictBillingMath.ordinalWarTermCopper(viewerBaseline, position++, multiplier);
            rows.add(describeOpponent(aggressor, baseUpkeepCopper(srv, aggressor), billedCost, ConflictEntryStatus.ACTIVE, false));
        }
        return sortByName(rows);
    }

    /** Lists only the wars this team is presently paying for as the aggressor. */
    public static List<WarTeamView> buildBilledOutgoingViews(MinecraftServer srv, Team viewer) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);
        UUID viewerId = viewer.getTeamId();
        TeamQueuedChanges queuedChanges = ledger.getPendingState(viewerId);

        List<UUID> hostileIds = new ArrayList<>(ledger.getWarTargets(viewerId));
        hostileIds.sort(Comparator.comparing(UUID::toString));

        List<WarTeamView> rows = new ArrayList<>();
        for (UUID hostileId : hostileIds) {
            Team foe = resolveEligibleTarget(srv, hostileId);
            if (foe == null) {
                continue;
            }
            ConflictEntryStatus status = queuedChanges.isPendingWarEnd(hostileId)
                    ? ConflictEntryStatus.PENDING_END
                    : ConflictEntryStatus.ACTIVE;
            long foeBaseline = baseUpkeepCopper(srv, foe);
            rows.add(describeOpponent(foe, foeBaseline, ConflictBillingMath.outgoingWarCostCopper(foeBaseline), status, false));
        }
        return sortByName(rows);
    }

    public static List<WarTeamView> buildOutgoingViews(MinecraftServer srv, Team viewer) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);
        UUID viewerId = viewer.getTeamId();
        TeamQueuedChanges queuedChanges = ledger.getPendingState(viewerId);
        List<WarTeamView> rows = new ArrayList<>();

        List<UUID> hostileIds = new ArrayList<>(ledger.getWarTargets(viewerId));
        hostileIds.sort(Comparator.comparing(UUID::toString));
        for (UUID hostileId : hostileIds) {
            Team foe = resolveEligibleTarget(srv, hostileId);
            if (foe == null) {
                continue;
            }
            ConflictEntryStatus status = queuedChanges.isPendingWarEnd(hostileId)
                    ? ConflictEntryStatus.PENDING_END
                    : ConflictEntryStatus.ACTIVE;
            long foeBaseline = baseUpkeepCopper(srv, foe);
            rows.add(describeOpponent(foe, foeBaseline, ConflictBillingMath.outgoingWarCostCopper(foeBaseline), status, false));
        }

        for (UUID candidateId : queuedChanges.pendingWarDeclares()) {
            if (ledger.isAtWarWith(viewerId, candidateId)) {
                continue;
            }
            Team foe = resolveEligibleTarget(srv, candidateId);
            if (foe == null) {
                continue;
            }
            rows.add(describeOpponent(
                    foe,
                    baseUpkeepCopper(srv, foe),
                    costToDeclareWar(srv, viewer, foe),
                    ConflictEntryStatus.PENDING_DECLARE,
                    false
            ));
        }
        return sortByName(rows);
    }

    public static List<WarTeamView> buildAvailableTargets(MinecraftServer srv, Team viewer) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);
        UUID viewerId = viewer.getTeamId();
        Set<UUID> hostileIds = ledger.getWarTargets(viewerId);
        TeamQueuedChanges viewerQueuedChanges = ledger.getPendingState(viewerId);

        List<WarTeamView> rows = new ArrayList<>();
        for (Team candidate : TeamRegistry.trackedTeams(srv)) {
            UUID candidateId = candidate.getTeamId();
            boolean unavailable = candidateId.equals(viewerId)
                    || hostileIds.contains(candidateId)
                    || viewerQueuedChanges.isPendingWarDeclare(candidateId);
            if (unavailable) {
                continue;
            }
            boolean candidatePendingOnViewer = ledger.getPendingState(candidateId).isPendingWarDeclare(viewerId);
            ConflictEntryStatus status = viewerQueuedChanges.isPendingWarDeclare(candidateId)
                    ? ConflictEntryStatus.PENDING_DECLARE
                    : ConflictEntryStatus.ACTIVE;
            rows.add(describeOpponent(
                    candidate,
                    baseUpkeepCopper(srv, candidate),
                    costToDeclareWar(srv, viewer, candidate),
                    status,
                    candidatePendingOnViewer
            ));
        }
        return sortByName(rows);
    }

    /**
     * Resolves a team id to a live {@link Team} that is still allowed to appear as a war
     * opponent (exists, tracked, not peaceful, above the min-claims floor); {@code null}
     * if it no longer qualifies.
     */
    @Nullable
    private static Team resolveEligibleTarget(MinecraftServer srv, UUID candidateId) {
        Team candidate = TeamRegistry.resolve(srv, candidateId);
        return candidate != null && isWarEligibleTeam(srv, candidate) ? candidate : null;
    }

    /** Common trailing sort shared by every {@code buildXxxViews} method. */
    private static List<WarTeamView> sortByName(List<WarTeamView> rows) {
        rows.sort(Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private static int countBillableAggressors(MinecraftServer srv, LcClaimEconomySavedData ledger, UUID viewerId) {
        return (int) ledger.getAllLinks().stream()
                .filter(link -> link.warTargets().contains(viewerId))
                .filter(link -> resolveEligibleTarget(srv, link.ftbTeamId()) != null)
                .count();
    }

    public static boolean canManageWar(Team squad, UUID playerId) {
        return BankLedgerAccess.canPurchaseForTeam(squad, playerId);
    }

    @Nullable
    public static Component toggleWar(MinecraftServer srv, ServerPlayer actor, UUID targetTeamId) {
        if (!isEnabled()) {
            return Component.translatable("message.lc_claim_economy.war_disabled");
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Component.translatable("message.lc_claim_economy.war_unavailable");
        }

        Team viewer = FTBTeamsAPI.api().getManager().getTeamForPlayer(actor).orElse(null);
        Team foe = TeamRegistry.resolve(srv, targetTeamId);
        if (viewer == null || foe == null) {
            return Component.translatable("message.lc_claim_economy.war_unavailable");
        }
        if (!canManageWar(viewer, actor.getUUID())) {
            return Component.translatable("message.lc_claim_economy.war_denied");
        }
        if (viewer.getTeamId().equals(foe.getTeamId())) {
            return Component.translatable("message.lc_claim_economy.war_self");
        }

        Component ineligibilityReason = describeWarIneligibility(srv, viewer, foe);
        if (ineligibilityReason != null) {
            return ineligibilityReason;
        }

        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);
        UUID viewerId = viewer.getTeamId();
        UUID foeId = foe.getTeamId();
        TeamQueuedChanges queuedChanges = ledger.getPendingState(viewerId);

        if (queuedChanges.isPendingWarDeclare(foeId)) {
            ledger.setPendingState(viewerId, queuedChanges.withoutPendingWarDeclare(foeId));
            return Component.translatable("message.lc_claim_economy.war_pending_cancelled", displayName(foe));
        }

        boolean alreadyHostile = ledger.isAtWarWith(viewerId, foeId);
        if (alreadyHostile) {
            if (queuedChanges.isPendingWarEnd(foeId)) {
                ledger.setPendingState(viewerId, queuedChanges.withoutPendingWarEnd(foeId));
                return Component.translatable("message.lc_claim_economy.war_pending_cancelled", displayName(foe));
            }
            ledger.setPendingState(viewerId, queuedChanges.withPendingWarEnd(foeId));
            return Component.translatable("message.lc_claim_economy.war_end_pending", displayName(foe));
        }

        if (!WarDeclarationWindow.isOpenNow()) {
            return Component.translatable("message.lc_claim_economy.war_declare_window_closed", WarDeclarationWindow.describeWindow());
        }

        ledger.setPendingState(viewerId, queuedChanges.withPendingWarDeclare(foeId));
        QuestAdvancements.grant(actor, QuestAdvancements.warDeclared());
        return Component.translatable("message.lc_claim_economy.war_declare_pending", displayName(foe));
    }

    /** Builds the "why can't these two teams fight" message, or {@code null} if they can. */
    @Nullable
    private static Component describeWarIneligibility(MinecraftServer srv, Team viewer, Team foe) {
        if (isWarEligibleTeam(srv, viewer) && isWarEligibleTeam(srv, foe)) {
            return null;
        }
        if (isPeaceful(srv, viewer.getTeamId())) {
            return Component.translatable("message.lc_claim_economy.war_self_peaceful");
        }
        if (isPeaceful(srv, foe.getTeamId())) {
            return Component.translatable("message.lc_claim_economy.war_target_peaceful", displayName(foe));
        }
        int claimFloor = LcClaimEconomyConfig.SERVER.warMinClaimedChunks.get();
        if (!meetsMinClaimThreshold(srv, viewer)) {
            return Component.translatable("message.lc_claim_economy.war_self_too_small", claimFloor);
        }
        if (!meetsMinClaimThreshold(srv, foe)) {
            return Component.translatable("message.lc_claim_economy.war_target_too_small", displayName(foe), claimFloor);
        }
        return Component.translatable("message.lc_claim_economy.war_unavailable");
    }

    public static void onTeamRemoved(MinecraftServer srv, UUID squadId) {
        TeamRegistry.onTeamDeleted(srv, squadId);
    }

    /**
     * Tears down every war link touching the given team, whether it was the attacker or the
     * defender, then pings each formerly-linked team so its cached war/upkeep state gets rebuilt.
     */
    public static void cleanupTeamWars(MinecraftServer srv, UUID squadId) {
        if (srv == null || squadId == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }

        LcClaimEconomySavedData ledger = LcClaimEconomySavedData.get(srv);
        Set<UUID> partnerIds = ledger.collectWarPartnerIds(squadId);
        boolean hadOutgoingWars = !ledger.getWarTargets(squadId).isEmpty();
        if (partnerIds.isEmpty() && !hadOutgoingWars) {
            return;
        }

        ledger.clearWarReferences(squadId);
        for (UUID partnerId : partnerIds) {
            ConflictSyncCoordinator.syncToTeam(srv, partnerId);
            Team ally = TeamRegistry.resolve(srv, partnerId);
            if (ally != null) {
                ConflictSyncCoordinator.onUpkeepFactorsChanged(srv, ally);
            }
        }

        LcClaimEconomy.LOGGER.debug("Cleared war state for team {} and refreshed {} partner team(s)", squadId, partnerIds.size());
    }

    public static double warMultiplier() {
        return LcClaimEconomyConfig.SERVER.warCostMultiplier.get();
    }

    private static WarTeamView describeOpponent(
            Team foe,
            long foeBaseUpkeepCopper,
            long hostilityCostCopper,
            ConflictEntryStatus status,
            boolean foePendingDeclareOnViewer
    ) {
        ConflictTargetSafeguards shields = ConflictTargetSafeguards.live(foe);
        return new WarTeamView(
                foe.getTeamId(),
                displayName(foe),
                foeBaseUpkeepCopper,
                hostilityCostCopper,
                status,
                foePendingDeclareOnViewer,
                shields.blockEditProtected(),
                shields.explosionProtected(),
                shields.pvpProtected()
        );
    }

    public static String displayName(Team squad) {
        return squad.getName().getString();
    }
}
