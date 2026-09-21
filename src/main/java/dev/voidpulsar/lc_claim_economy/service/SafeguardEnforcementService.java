package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Affordability checks and lock/unlock bookkeeping for a team's chunk protection, plus the
 * reentrancy flags other services check before mutating protection state on their own.
 *
 * <p>{@link #isReverting()} / {@link #isApplying()} exist so that code which programmatically
 * reverts or (re)applies protection can tell whether it is already inside one of those
 * operations, to avoid recursing back into itself.
 */
public final class SafeguardEnforcementService {

    private static final ThreadLocal<Boolean> REVERT_FLAG = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> APPLY_FLAG = ThreadLocal.withInitial(() -> false);

    private SafeguardEnforcementService() {
    }

    // ------------------------------------------------------------------
    // Reentrancy flags
    // ------------------------------------------------------------------

    public static boolean isReverting() {
        return REVERT_FLAG.get();
    }

    public static boolean isApplying() {
        return APPLY_FLAG.get();
    }

    public static void setApplying(boolean applying) {
        if (applying) {
            APPLY_FLAG.set(true);
        } else {
            APPLY_FLAG.remove();
        }
    }

    /** Runs {@code action} with {@link #isReverting()} reporting true for the duration. */
    public static void runReverting(Runnable action) {
        REVERT_FLAG.set(true);
        try {
            action.run();
        } finally {
            REVERT_FLAG.remove();
        }
    }

    // ------------------------------------------------------------------
    // Affordability
    // ------------------------------------------------------------------

    public static boolean canAffordNextPeriod(MinecraftServer server, Team team) {
        TeamQueuedChanges queuedChanges = LcClaimEconomySavedData.get(server).getPendingState(team.getTeamId());
        return canAffordNextPeriod(server, team, queuedChanges);
    }

    public static boolean canAffordNextPeriod(MinecraftServer server, Team team, TeamQueuedChanges queuedChanges) {
        ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        int claimedChunkCount = chunkData.getClaimedChunks().size();
        int forceLoadCount = SafeguardPricing.countEffectiveForceLoads(chunkData, queuedChanges);
        if (claimedChunkCount <= 0 && forceLoadCount <= 0) {
            // Nothing billable means nothing to fail to afford.
            return true;
        }

        MoneyValue upkeepCost = ConflictService.calculateTotalUpkeepCost(server, team, queuedChanges);
        if (upkeepCost.isEmpty()) {
            return true;
        }

        IBankAccount teamAccount = BankLedgerAccess.getAccountForTeam(server, team);
        return teamAccount.getMoneyStorage().containsValue(upkeepCost);
    }

    // ------------------------------------------------------------------
    // Lock / unlock
    // ------------------------------------------------------------------

    public static void enforceInsufficientFunds(MinecraftServer server, Team team) {
        LcClaimEconomySavedData.get(server).setProtectionLocked(team.getTeamId(), true);
        notifyTeam(server, team, "message.lc_claim_economy.protection_locked");
    }

    public static void tryUnlock(MinecraftServer server, Team team) {
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        if (!data.isProtectionLocked(team.getTeamId())) {
            return;
        }
        if (!canAffordNextPeriod(server, team)) {
            return;
        }
        data.setProtectionLocked(team.getTeamId(), false);
        notifyTeam(server, team, "message.lc_claim_economy.protection_unlocked");
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    public static void notifyTeam(MinecraftServer server, Team team, String messageKey) {
        notifyTeam(server, team, Component.translatable(messageKey));
    }

    public static void notifyTeam(MinecraftServer server, Team team, Component message) {
        for (ServerPlayer onlineMember : team.getOnlineMembers()) {
            onlineMember.displayClientMessage(message, false);
        }
    }

    /** Same as {@link #notifyTeam(MinecraftServer, Team, Component)} but skips regular party members. */
    public static void notifyTeamManagers(MinecraftServer server, Team team, Component message) {
        for (ServerPlayer onlineMember : team.getOnlineMembers()) {
            boolean isManager = !team.isPartyTeam() || team.getRankForPlayer(onlineMember.getUUID()).isOfficerOrBetter();
            if (isManager) {
                onlineMember.displayClientMessage(message, false);
            }
        }
    }
}
