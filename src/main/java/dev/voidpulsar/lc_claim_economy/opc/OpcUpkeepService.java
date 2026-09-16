package dev.voidpulsar.lc_claim_economy.opc;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.config.UpkeepOnlineRequirement;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.player.api.IServerPlayerClaimInfoAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * OP&C equivalent of the FTB {@code UpkeepService}. Charges periodic
 * protection upkeep for every OP&C claim owner (solo players and
 * party-owned claims alike, mirrored by iterating
 * {@link IServerClaimsManagerAPI#getPlayerInfoStream()}), scoped to what
 * OP&C's public API and config model can actually support:
 * <ul>
 *     <li>Force-load upkeep: {@code forceLoadUpkeepPrice} per force-loaded
 *     chunk, same as the FTB side.</li>
 *     <li>Protection upkeep: build chunks pay {@link OpcProtectionPricing}'s
 *     build base price per chunk, land chunks (marked via
 *     {@code /lcce opc_chunktype land}, see
 *     {@link OpcChunkTypeCommand}) pay the land base price once per
 *     {@code landChunkGroupSize} chunks, and the first {@code freeChunks}
 *     chunks are exempt - all mirroring the FTB side's
 *     {@code ProtectionPricing}. The land/build split itself is tracked
 *     directly (no queued pending-change step like the FTB side has to
 *     stop players dodging costs by re-typing chunks right before upkeep;
 *     that would need its own per-owner pending-state plumbing to add
 *     here).</li>
 *     <li>When upkeep can't be paid, there is no team-wide "protection
 *     locked" switch to flip like FTB Chunks has, so this disables the
 *     owner's {@code FORCELOAD} config option instead (see
 *     {@link OpcPlayerConfigAccess}) - the closest available lever, which
 *     stops the force-loaded chunks from actually staying loaded until
 *     upkeep is paid again. Claimed chunks themselves are left alone.</li>
 * </ul>
 */
public final class OpcUpkeepService {

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long periodTicks = LcClaimEconomyConfig.SERVER.upkeepPeriodMinutes.get() * 60L * 20L;
        long gameTime = server.overworld().getGameTime();
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        UpkeepOnlineRequirement requirement = LcClaimEconomyConfig.SERVER.upkeepOnlineRequirement.get();

        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        List<IPlayerClaimInfoAPI> owners;
        try (Stream<IServerPlayerClaimInfoAPI> stream = claimsManager.getPlayerInfoStream()) {
            owners = stream.map(info -> (IPlayerClaimInfoAPI) info).toList();
        }

        for (IPlayerClaimInfoAPI info : owners) {
            try {
                tickOwner(server, info, savedData, requirement, gameTime, periodTicks);
            } catch (Exception e) {
                LcClaimEconomy.LOGGER.error("Failed to process OP&C upkeep for owner {}", info.getPlayerId(), e);
            }
        }
    }

    /**
     * Each owner (solo player or party) gets its own countdown - see the FTB side's
     * {@code UpkeepService#tickTeam} for why a single shared clock can't support
     * {@link UpkeepOnlineRequirement#TEAM_MEMBER_ONLINE}.
     */
    private void tickOwner(
            MinecraftServer server,
            IPlayerClaimInfoAPI info,
            LcClaimEconomySavedData savedData,
            UpkeepOnlineRequirement requirement,
            long gameTime,
            long periodTicks
    ) {
        UUID owner = info.getPlayerId();
        long nextUpkeepTick = savedData.getNextOpcUpkeepTick(owner);

        if (nextUpkeepTick < 0L) {
            savedData.setNextOpcUpkeepTick(owner, gameTime + periodTicks);
            return;
        }

        if (isPaused(server, owner, info.isPartyOwned(), requirement)) {
            savedData.setNextOpcUpkeepTick(owner, nextUpkeepTick + 1);
            return;
        }

        if (gameTime < nextUpkeepTick) {
            return;
        }
        savedData.setNextOpcUpkeepTick(owner, gameTime + periodTicks);

        processOwnerUpkeep(server, info);
    }

    private static boolean isPaused(MinecraftServer server, UUID owner, boolean partyOwned, UpkeepOnlineRequirement requirement) {
        return switch (requirement) {
            case ALWAYS_CHARGE -> false;
            case ANYONE_ONLINE -> server.getPlayerList().getPlayerCount() <= 0;
            case TEAM_MEMBER_ONLINE -> !hasOnlineMember(server, owner, partyOwned);
        };
    }

    private static boolean hasOnlineMember(MinecraftServer server, UUID owner, boolean partyOwned) {
        if (!partyOwned) {
            return server.getPlayerList().getPlayer(owner) != null;
        }
        IServerPartyAPI party = OpcAccountResolver.resolveParty(server, owner);
        return party != null && party.getOnlineMemberStream().findAny().isPresent();
    }

    private void processOwnerUpkeep(MinecraftServer server, IPlayerClaimInfoAPI info) {
        UUID owner = info.getPlayerId();
        boolean partyOwned = info.isPartyOwned();
        int forceloadCount = info.getForceloadCount();
        int totalClaims = info.getClaimCount();

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        boolean wasLocked = savedData.isProtectionLocked(owner);

        long forceLoadCopper = forceloadCount > 0
                ? LcClaimEconomyConfig.SERVER.forceLoadUpkeepPrice.get() * forceloadCount
                : 0L;
        long protectionCopper = calculateProtectionCopper(server, owner, partyOwned, totalClaims, savedData);
        long costCopper = forceLoadCopper + protectionCopper;

        if (costCopper <= 0L) {
            if (wasLocked) {
                restoreForceload(server, owner, partyOwned, savedData);
            }
            return;
        }

        MoneyValue cost = MoneyUtil.fromCopper(costCopper);
        IBankAccount account = OpcAccountResolver.resolveAccount(server, owner, partyOwned);
        if (account == null) {
            LcClaimEconomy.LOGGER.warn("OP&C upkeep: no resolvable bank account for owner {}", owner);
            return;
        }

        if (account.getMoneyStorage().containsValue(cost)) {
            account.withdrawMoney(cost);
            savedData.recordUpkeepCharged(costCopper);
            BankAccountHelper.logTransaction(account, false, cost, Component.translatable("message.lc_claim_economy.ledger.upkeep_charge"));
            if (wasLocked) {
                restoreForceload(server, owner, partyOwned, savedData);
            }
            return;
        }

        savedData.recordUpkeepMissed();
        if (!wasLocked) {
            boolean applied = OpcPlayerConfigAccess.setForceloadEnabled(server, owner, partyOwned, false);
            savedData.setProtectionLocked(owner, true);
            if (applied) {
                OpcAccountResolver.notify(server, owner, partyOwned,
                        Component.translatable("message.lc_claim_economy.opc.forceload_locked"));
            }
        }
    }

    /**
     * Build chunks pay the build base price per chunk; land chunks pay the
     * land base price once per group of {@code landChunkGroupSize} chunks
     * (rounded up), same shape as the FTB side's
     * {@code ProtectionPricing.calculateProtectionCopper}. The stored land
     * chunk set is trusted to only contain currently-claimed chunks, since
     * {@link OpcClaimEconomyListener} clears an owner's land flag on
     * unclaim; it's still defensively capped at the live claim count in
     * case of drift (e.g. a bulk/region unclaim, which that listener can't
     * attribute to individual chunks).
     */
    private long calculateProtectionCopper(
            MinecraftServer server,
            UUID owner,
            boolean partyOwned,
            int totalClaims,
            LcClaimEconomySavedData savedData
    ) {
        if (totalClaims <= 0) {
            return 0L;
        }

        int landCount = Math.min(totalClaims, savedData.getLandChunks(owner).size());
        int buildCount = Math.max(0, totalClaims - landCount);

        int allowance = Math.max(0, LcClaimEconomyConfig.SERVER.freeChunks.get());
        int buildBillable = Math.max(0, buildCount - allowance);
        int allowanceLeft = Math.max(0, allowance - buildCount);
        int landBillable = Math.max(0, landCount - allowanceLeft);

        if (buildBillable <= 0 && landBillable <= 0) {
            return 0L;
        }

        IPlayerConfigAPI config = OpcPlayerConfigAccess.resolveConfig(server, owner, partyOwned);
        if (config == null) {
            return 0L;
        }

        long copper = 0L;
        if (buildBillable > 0) {
            copper += OpcProtectionPricing.calculateBuildBasePrice(config) * buildBillable;
        }
        if (landBillable > 0) {
            int groupSize = Math.max(1, LcClaimEconomyConfig.SERVER.landChunkGroupSize.get());
            int units = (landBillable + groupSize - 1) / groupSize;
            copper += OpcProtectionPricing.calculateLandBasePrice(config) * units;
        }
        return copper;
    }

    private void restoreForceload(MinecraftServer server, UUID owner, boolean partyOwned, LcClaimEconomySavedData savedData) {
        savedData.setProtectionLocked(owner, false);
        boolean applied = OpcPlayerConfigAccess.setForceloadEnabled(server, owner, partyOwned, true);
        if (applied) {
            OpcAccountResolver.notify(server, owner, partyOwned,
                    Component.translatable("message.lc_claim_economy.opc.forceload_unlocked"));
        }
    }
}
