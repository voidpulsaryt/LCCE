package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    public static boolean isClaimTeam(MinecraftServer server, Team team) {
        if (!team.isValid() || !FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }
        if (team.isPartyTeam() && !TeamRegistry.isTracked(server, team)) {
            return false;
        }
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        return chunkData.getClaimedChunks().size() > 0;
    }

    public static long baseUpkeepCopper(MinecraftServer server, Team team) {
        return baseUpkeepCopper(server, team, LcClaimEconomySavedData.get(server).getPendingState(team.getTeamId()));
    }

    public static long baseUpkeepCopper(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        return SafeguardPricing.calculateTotalUpkeepCopper(server, team, pendingState);
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer server, Team team) {
        return calculateWarCosts(server, team, LcClaimEconomySavedData.get(server).getPendingState(team.getTeamId()));
    }

    public static WarCostBreakdown calculateWarCosts(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        return calculateWarCosts(server, team, pendingState, SafeguardRollbackService.pricingProperties(team, pendingState));
    }

    public static WarCostBreakdown calculateWarCosts(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            java.util.Map<String, String> pricingOverrides
    ) {
        long base = SafeguardPricing.calculateTotalUpkeepCopper(server, team, pendingState, pricingOverrides);
        if (!isEnabled()) {
            return new WarCostBreakdown(base, 0L, 0L, 0, 0);
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);

        // Only count declarers that still exist and have claimed chunks — the same
        // filter used by buildIncomingViews. Stale references from deleted or
        // claim-less teams would otherwise inflate the billing without showing up
        // in the war screen.
        int incomingCount = countEligibleIncomingWars(server, savedData, team.getTeamId());
        long incoming = ConflictBillingMath.sumOrdinalIncomingTerms(base, incomingCount);

        Set<UUID> targets = savedData.getWarTargets(team.getTeamId());
        long outgoing = 0L;
        for (UUID targetId : targets) {
            Team target = TeamRegistry.resolve(server, targetId);
            if (target != null && isClaimTeam(server, target)) {
                long targetBase = baseUpkeepCopper(server, target, savedData.getPendingState(targetId));
                outgoing += ConflictBillingMath.outgoingWarCostCopper(targetBase);
            }
        }

        return new WarCostBreakdown(base, incoming, outgoing, incomingCount, targets.size());
    }

    public static MoneyValue calculateTotalUpkeepCost(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        return CurrencyAmounts.fromCopper(calculateTotalUpkeepCostCopper(server, team, pendingState));
    }

    public static long calculateTotalUpkeepCostCopper(MinecraftServer server, Team team, TeamQueuedChanges pendingState) {
        return calculateWarCosts(server, team, pendingState).totalUpkeepCopper();
    }

    public static long calculateTotalUpkeepCostCopper(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            java.util.Map<String, String> pricingOverrides
    ) {
        return calculateWarCosts(server, team, pendingState, pricingOverrides).totalUpkeepCopper();
    }

    public static boolean canAffordUpkeepWithPendingProperty(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            IBankAccount account,
            dev.ftb.mods.ftbteams.api.property.TeamProperty<?> property
    ) {
        java.util.Map<String, String> pricing = SafeguardRollbackService.pricingWithAppliedPending(team, pendingState, property);
        long cost = calculateTotalUpkeepCostCopper(server, team, pendingState, pricing);
        if (cost <= 0L) {
            return true;
        }
        return account.getMoneyStorage().containsValue(CurrencyAmounts.fromCopper(cost));
    }

    public static long calculateProtectionAndIncomingUpkeepCopper(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState
    ) {
        WarCostBreakdown war = calculateWarCosts(server, team, pendingState);
        return war.baseUpkeepCopper() + war.incomingWarCopper();
    }

    public static boolean canAffordUpkeep(MinecraftServer server, Team team, TeamQueuedChanges pendingState, IBankAccount account) {
        long cost = calculateTotalUpkeepCostCopper(server, team, pendingState);
        if (cost <= 0L) {
            return true;
        }
        return account.getMoneyStorage().containsValue(CurrencyAmounts.fromCopper(cost));
    }

    public static boolean canAffordUpkeepWithOutgoingWar(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            IBankAccount account,
            LcClaimEconomySavedData savedData,
            UUID targetId
    ) {
        UUID teamId = team.getTeamId();
        if (savedData.isAtWarWith(teamId, targetId)) {
            return canAffordUpkeep(server, team, pendingState, account);
        }
        savedData.setWarTarget(teamId, targetId, true);
        boolean affordable = canAffordUpkeep(server, team, pendingState, account);
        savedData.setWarTarget(teamId, targetId, false);
        return affordable;
    }

    public static List<UUID> pendingWarRestoreOrder(
            MinecraftServer server,
            Team team,
            TeamQueuedChanges pendingState,
            LcClaimEconomySavedData savedData
    ) {
        UUID teamId = team.getTeamId();
        List<UUID> targets = new ArrayList<>();
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            if (!savedData.isAtWarWith(teamId, targetId)) {
                targets.add(targetId);
            }
        }
        targets.sort(Comparator.comparingLong(id -> {
            Team target = TeamRegistry.resolve(server, id);
            return target == null ? Long.MAX_VALUE : costToDeclareWar(server, team, target);
        }));
        return targets;
    }

    public static List<UUID> outgoingWarDismantleOrder(MinecraftServer server, Team team, LcClaimEconomySavedData savedData) {
        UUID teamId = team.getTeamId();
        List<UUID> targets = new ArrayList<>(savedData.getWarTargets(teamId));
        targets.sort(Comparator.comparing(UUID::toString));

        java.util.Map<UUID, Long> costByTarget = new java.util.HashMap<>();
        for (UUID targetId : targets) {
            Team target = TeamRegistry.resolve(server, targetId);
            long cost = target == null
                    ? 0L
                    : ConflictBillingMath.outgoingWarCostCopper(baseUpkeepCopper(server, target));
            costByTarget.put(targetId, cost);
        }

        targets.sort(Comparator.comparingLong(id -> costByTarget.getOrDefault(id, 0L)).reversed());
        return targets;
    }

    public static long costToDeclareWar(MinecraftServer server, Team declarer, Team target) {
        if (!isEnabled()) {
            return 0L;
        }
        return ConflictBillingMath.outgoingWarCostCopper(baseUpkeepCopper(server, target));
    }

    /** True if the given team currently has at least one active war, incoming or outgoing. */
    public static boolean isAtWar(MinecraftServer server, UUID teamId) {
        return !LcClaimEconomySavedData.get(server).collectWarPartnerIds(teamId).isEmpty();
    }

    public static boolean isWarEligibleTeam(MinecraftServer server, Team team) {
        if (!TeamRegistry.isTracked(server, team)) {
            return false;
        }
        if (isPeaceful(server, team.getTeamId())) {
            return false;
        }
        return meetsMinClaimThreshold(server, team);
    }

    /** A team that has opted out of the war system entirely via {@code /lcce war peaceful} - see {@link #setPeaceful}. */
    public static boolean isPeaceful(MinecraftServer server, UUID teamId) {
        return LcClaimEconomySavedData.get(server).isPeaceful(teamId);
    }

    /**
     * Attempts to set a team's peaceful flag. Fails (returns false) if trying to enable it while
     * the team has any active war - wars must be ended first, so peaceful mode can't be used as a
     * mid-siege escape hatch.
     */
    public static boolean setPeaceful(MinecraftServer server, UUID teamId, boolean peaceful) {
        if (peaceful && isAtWar(server, teamId)) {
            return false;
        }
        LcClaimEconomySavedData.get(server).setPeaceful(teamId, peaceful);
        return true;
    }

    public static boolean meetsMinClaimThreshold(MinecraftServer server, Team team) {
        int min = LcClaimEconomyConfig.SERVER.warMinClaimedChunks.get();
        if (min <= 0) {
            return true;
        }
        if (!FTBChunksAPI.api().isManagerLoaded()) {
            return false;
        }
        return FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size() > min;
    }

    public static List<WarTeamView> buildIncomingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID selfId = self.getTeamId();
        long selfBase = baseUpkeepCopper(server, self);
        List<WarTeamView> views = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();

        for (LcClaimEconomySavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (!entry.warTargets().contains(selfId)) {
                continue;
            }
            Team declarer = resolveEligibleTarget(server, entry.ftbTeamId());
            if (declarer == null) {
                continue;
            }
            seen.add(declarer.getTeamId());
            views.add(opponentView(
                    declarer,
                    baseUpkeepCopper(server, declarer),
                    selfBase,
                    ConflictEntryStatus.ACTIVE,
                    false
            ));
        }

        for (LcClaimEconomySavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            UUID declarerId = entry.ftbTeamId();
            if (declarerId.equals(selfId) || seen.contains(declarerId)) {
                continue;
            }
            if (!entry.pendingState().isPendingWarDeclare(selfId)) {
                continue;
            }
            Team declarer = resolveEligibleTarget(server, declarerId);
            if (declarer == null) {
                continue;
            }
            seen.add(declarerId);
            views.add(opponentView(
                    declarer,
                    baseUpkeepCopper(server, declarer),
                    selfBase,
                    ConflictEntryStatus.PENDING_DECLARE,
                    false
            ));
        }

        sortedByDisplayName(views);
        double step = warMultiplier();
        for (int i = 0; i < views.size(); i++) {
            WarTeamView view = views.get(i);
            long term = ConflictBillingMath.ordinalWarTermCopper(selfBase, i, step);
            views.set(i, new WarTeamView(
                    view.teamId(),
                    view.displayName(),
                    view.targetBaseUpkeepCopper(),
                    term,
                    view.status(),
                    view.opponentPendingDeclareOnViewer(),
                    view.blockEditProtected(),
                    view.explosionProtected(),
                    view.pvpProtected()
            ));
        }
        return views;
    }

    /** Active incoming wars only — used for upkeep billing breakdown lines. */
    public static List<WarTeamView> buildBilledIncomingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID selfId = self.getTeamId();
        long selfBase = baseUpkeepCopper(server, self);
        double step = warMultiplier();

        List<UUID> declarerIds = new ArrayList<>();
        for (LcClaimEconomySavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (!entry.warTargets().contains(selfId)) {
                continue;
            }
            Team declarer = resolveEligibleTarget(server, entry.ftbTeamId());
            if (declarer == null) {
                continue;
            }
            declarerIds.add(declarer.getTeamId());
        }
        declarerIds.sort(Comparator.comparing(UUID::toString));

        List<WarTeamView> views = new ArrayList<>();
        int index = 0;
        for (UUID declarerId : declarerIds) {
            Team declarer = TeamRegistry.resolve(server, declarerId);
            if (declarer == null) {
                continue;
            }
            views.add(opponentView(
                    declarer,
                    baseUpkeepCopper(server, declarer),
                    ConflictBillingMath.ordinalWarTermCopper(selfBase, index++, step),
                    ConflictEntryStatus.ACTIVE,
                    false
            ));
        }
        return sortedByDisplayName(views);
    }

    /** Active outgoing wars only — used for upkeep billing breakdown lines. */
    public static List<WarTeamView> buildBilledOutgoingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID selfId = self.getTeamId();
        TeamQueuedChanges pendingState = savedData.getPendingState(selfId);

        List<UUID> activeTargetIds = new ArrayList<>(savedData.getWarTargets(selfId));
        activeTargetIds.sort(Comparator.comparing(UUID::toString));

        List<WarTeamView> views = new ArrayList<>();
        for (UUID targetId : activeTargetIds) {
            Team target = resolveEligibleTarget(server, targetId);
            if (target == null) {
                continue;
            }
            ConflictEntryStatus status = pendingState.isPendingWarEnd(targetId)
                    ? ConflictEntryStatus.PENDING_END
                    : ConflictEntryStatus.ACTIVE;
            long targetBase = baseUpkeepCopper(server, target);
            views.add(opponentView(
                    target,
                    targetBase,
                    ConflictBillingMath.outgoingWarCostCopper(targetBase),
                    status,
                    false
            ));
        }
        return sortedByDisplayName(views);
    }

    public static List<WarTeamView> buildOutgoingViews(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID selfId = self.getTeamId();
        TeamQueuedChanges pendingState = savedData.getPendingState(selfId);
        List<WarTeamView> views = new ArrayList<>();
        List<UUID> activeTargetIds = new ArrayList<>(savedData.getWarTargets(selfId));
        activeTargetIds.sort(Comparator.comparing(UUID::toString));
        for (UUID targetId : activeTargetIds) {
            Team target = resolveEligibleTarget(server, targetId);
            if (target == null) {
                continue;
            }
            ConflictEntryStatus status = pendingState.isPendingWarEnd(targetId)
                    ? ConflictEntryStatus.PENDING_END
                    : ConflictEntryStatus.ACTIVE;
            long targetBase = baseUpkeepCopper(server, target);
            views.add(opponentView(
                    target,
                    targetBase,
                    ConflictBillingMath.outgoingWarCostCopper(targetBase),
                    status,
                    false
            ));
        }
        for (UUID targetId : pendingState.pendingWarDeclares()) {
            if (savedData.isAtWarWith(selfId, targetId)) {
                continue;
            }
            Team target = resolveEligibleTarget(server, targetId);
            if (target == null) {
                continue;
            }
            views.add(opponentView(
                    target,
                    baseUpkeepCopper(server, target),
                    costToDeclareWar(server, self, target),
                    ConflictEntryStatus.PENDING_DECLARE,
                    false
            ));
        }
        return sortedByDisplayName(views);
    }

    public static List<WarTeamView> buildAvailableTargets(MinecraftServer server, Team self) {
        if (!isEnabled()) {
            return List.of();
        }
        List<WarTeamView> views = new ArrayList<>();
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID selfId = self.getTeamId();
        Set<UUID> activeTargets = savedData.getWarTargets(selfId);
        TeamQueuedChanges selfPending = savedData.getPendingState(selfId);
        for (Team team : TeamRegistry.trackedTeams(server)) {
            UUID teamId = team.getTeamId();
            if (teamId.equals(selfId) || activeTargets.contains(teamId) || selfPending.isPendingWarDeclare(teamId)) {
                continue;
            }
            boolean opponentPending = savedData.getPendingState(teamId).isPendingWarDeclare(selfId);
            ConflictEntryStatus status = selfPending.isPendingWarDeclare(teamId)
                    ? ConflictEntryStatus.PENDING_DECLARE
                    : ConflictEntryStatus.ACTIVE;
            views.add(opponentView(
                    team,
                    baseUpkeepCopper(server, team),
                    costToDeclareWar(server, self, team),
                    status,
                    opponentPending
            ));
        }
        return sortedByDisplayName(views);
    }

    /**
     * Resolves a target team and confirms it's still eligible to be shown/billed as a war
     * opponent (exists, tracked, not peaceful, meets the min-claims threshold), or {@code null}
     * if either check fails.
     */
    @Nullable
    private static Team resolveEligibleTarget(MinecraftServer server, UUID targetId) {
        Team target = TeamRegistry.resolve(server, targetId);
        return target != null && isWarEligibleTeam(server, target) ? target : null;
    }

    /** The identical trailing sort every {@code buildXxxViews} method ends with. */
    private static List<WarTeamView> sortedByDisplayName(List<WarTeamView> views) {
        views.sort(Comparator.comparing(WarTeamView::displayName, String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    private static int countEligibleIncomingWars(MinecraftServer server, LcClaimEconomySavedData savedData, UUID targetId) {
        int count = 0;
        for (LcClaimEconomySavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            if (!entry.warTargets().contains(targetId)) {
                continue;
            }
            if (resolveEligibleTarget(server, entry.ftbTeamId()) == null) {
                continue;
            }
            count++;
        }
        return count;
    }

    public static boolean canManageWar(Team team, UUID playerId) {
        return BankLedgerAccess.canPurchaseForTeam(team, playerId);
    }

    @Nullable
    public static Component toggleWar(MinecraftServer server, ServerPlayer player, UUID targetTeamId) {
        if (!isEnabled()) {
            return Component.translatable("message.lc_claim_economy.war_disabled");
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Component.translatable("message.lc_claim_economy.war_unavailable");
        }

        Team self = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        Team target = TeamRegistry.resolve(server, targetTeamId);
        if (self == null || target == null) {
            return Component.translatable("message.lc_claim_economy.war_unavailable");
        }
        if (!canManageWar(self, player.getUUID())) {
            return Component.translatable("message.lc_claim_economy.war_denied");
        }
        if (self.getTeamId().equals(target.getTeamId())) {
            return Component.translatable("message.lc_claim_economy.war_self");
        }
        if (!isWarEligibleTeam(server, self) || !isWarEligibleTeam(server, target)) {
            if (isPeaceful(server, self.getTeamId())) {
                return Component.translatable("message.lc_claim_economy.war_self_peaceful");
            }
            if (isPeaceful(server, target.getTeamId())) {
                return Component.translatable("message.lc_claim_economy.war_target_peaceful", displayName(target));
            }
            int minClaims = LcClaimEconomyConfig.SERVER.warMinClaimedChunks.get();
            if (!meetsMinClaimThreshold(server, self)) {
                return Component.translatable("message.lc_claim_economy.war_self_too_small", minClaims);
            }
            if (!meetsMinClaimThreshold(server, target)) {
                return Component.translatable("message.lc_claim_economy.war_target_too_small", displayName(target), minClaims);
            }
            return Component.translatable("message.lc_claim_economy.war_unavailable");
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UUID selfId = self.getTeamId();
        UUID targetId = target.getTeamId();
        TeamQueuedChanges pendingState = savedData.getPendingState(selfId);

        if (pendingState.isPendingWarDeclare(targetId)) {
            savedData.setPendingState(selfId, pendingState.withoutPendingWarDeclare(targetId));
            return Component.translatable("message.lc_claim_economy.war_pending_cancelled", displayName(target));
        }

        boolean currentlyAtWar = savedData.isAtWarWith(selfId, targetId);
        if (currentlyAtWar) {
            if (pendingState.isPendingWarEnd(targetId)) {
                savedData.setPendingState(selfId, pendingState.withoutPendingWarEnd(targetId));
                return Component.translatable("message.lc_claim_economy.war_pending_cancelled", displayName(target));
            }
            savedData.setPendingState(selfId, pendingState.withPendingWarEnd(targetId));
            return Component.translatable("message.lc_claim_economy.war_end_pending", displayName(target));
        }

        if (!WarDeclarationWindow.isOpenNow()) {
            return Component.translatable("message.lc_claim_economy.war_declare_window_closed", WarDeclarationWindow.describeWindow());
        }

        savedData.setPendingState(selfId, pendingState.withPendingWarDeclare(targetId));
        dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements.grant(
                player, dev.voidpulsar.lc_claim_economy.integration.quest.QuestAdvancements.warDeclared());
        return Component.translatable("message.lc_claim_economy.war_declare_pending", displayName(target));
    }

    public static void onTeamRemoved(MinecraftServer server, UUID teamId) {
        TeamRegistry.onTeamDeleted(server, teamId);
    }

    /**
     * Removes all wars declared by or against the given team and refreshes
     * war/upkeep state for every team that was involved.
     */
    public static void cleanupTeamWars(MinecraftServer server, UUID teamId) {
        if (server == null || teamId == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        Set<UUID> partners = savedData.collectWarPartnerIds(teamId);
        boolean hadOutgoing = !savedData.getWarTargets(teamId).isEmpty();
        if (partners.isEmpty() && !hadOutgoing) {
            return;
        }

        savedData.clearWarReferences(teamId);
        for (UUID partnerId : partners) {
            ConflictSyncCoordinator.syncToTeam(server, partnerId);
            Team partner = TeamRegistry.resolve(server, partnerId);
            if (partner != null) {
                ConflictSyncCoordinator.onUpkeepFactorsChanged(server, partner);
            }
        }

        LcClaimEconomy.LOGGER.debug("Cleared war state for team {} and refreshed {} partner team(s)", teamId, partners.size());
    }

    public static double warMultiplier() {
        return LcClaimEconomyConfig.SERVER.warCostMultiplier.get();
    }

    private static WarTeamView opponentView(
            Team opponent,
            long targetBaseUpkeepCopper,
            long warCostCopper,
            ConflictEntryStatus status,
            boolean opponentPendingDeclareOnViewer
    ) {
        ConflictTargetSafeguards protections = ConflictTargetSafeguards.live(opponent);
        return new WarTeamView(
                opponent.getTeamId(),
                displayName(opponent),
                targetBaseUpkeepCopper,
                warCostCopper,
                status,
                opponentPendingDeclareOnViewer,
                protections.blockEditProtected(),
                protections.explosionProtected(),
                protections.pvpProtected()
        );
    }

    public static String displayName(Team team) {
        return team.getName().getString();
    }
}
