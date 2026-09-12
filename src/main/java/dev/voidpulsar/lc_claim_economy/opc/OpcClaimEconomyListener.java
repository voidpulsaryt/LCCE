package dev.voidpulsar.lc_claim_economy.opc;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.service.FreeChunkAllowance;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.claims.player.api.IPlayerClaimInfoAPI;
import xaero.pac.common.claims.tracker.api.IClaimsManagerListenerAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OP&C equivalent of the FTB {@code ChunkClaimHandler} claim/unclaim
 * economy. OP&C's public API only exposes an AFTER-the-fact change
 * listener (no pre-claim veto hook), so insufficient-funds claims are
 * allowed to happen and then immediately reverted via
 * {@link IServerClaimsManagerAPI#unclaim}, rather than blocked outright
 * like the FTB Chunks integration does.
 * <p>
 * Since {@link #onChunkChange} only reports the new state (no "previous
 * owner" parameter), this class keeps its own in-memory map of chunk to
 * owner to tell a genuinely new claim apart from a forceload/sub-config
 * change on an already-claimed chunk owned by the same player/party. This
 * map is rebuilt from scratch on every server start (see
 * {@link OpcIntegration}), so claims that already existed before this mod
 * was added, and that never change afterward, are not retroactively
 * charged.
 * <p>
 * Handles the base claim price and unclaim refund (including clearing the
 * land/build chunk type on unclaim - see {@code LcClaimEconomySavedData}).
 * Periodic protection (force-load + land/build) upkeep billing is handled
 * separately by {@link OpcUpkeepService}. OP&C claims never participate in
 * wars - OP&C has no invasion/overclaim concept for that to map onto, and
 * it isn't planned.
 */
public final class OpcClaimEconomyListener implements IClaimsManagerListenerAPI {
    private final Map<String, UUID> knownOwners = new ConcurrentHashMap<>();

    private static String key(ResourceLocation dimension, int x, int z) {
        return dimension + "|" + x + "|" + z;
    }

    @Override
    public void onWholeRegionChange(ResourceLocation dimension, int regionX, int regionZ) {
        // A whole claimed region changed at once (e.g. bulk removal); we can't
        // tell individual chunk owners apart here, so just drop any tracked
        // chunks in this region and let the next individual change re-sync.
        String prefix = dimension + "|";
        knownOwners.keySet().removeIf(k -> k.startsWith(prefix));
    }

    @Override
    public void onChunkChange(ResourceLocation dimension, int x, int z, IPlayerChunkClaimAPI claim) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        String key = key(dimension, x, z);
        UUID previousOwner = knownOwners.get(key);

        if (claim == null) {
            if (previousOwner != null) {
                knownOwners.remove(key);
                LcClaimEconomySavedData.get(server).clearLandChunk(key);
                handleUnclaim(server, previousOwner);
            }
            return;
        }

        UUID owner = claim.getPlayerId();
        if (owner.equals(previousOwner)) {
            // Same owner as before - a forceload or sub-config change, not a new claim.
            return;
        }

        knownOwners.put(key, owner);
        handleClaim(server, dimension, x, z, owner);
    }

    @Override
    public void onDimensionChange(ResourceLocation dimension) {
        String prefix = dimension + "|";
        knownOwners.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** Seeds the tracking map from OP&C's current claim state at server start. Best-effort. */
    public void seedKnownOwner(ResourceLocation dimension, int x, int z, UUID owner) {
        knownOwners.put(key(dimension, x, z), owner);
    }

    private void handleClaim(MinecraftServer server, ResourceLocation dimension, int x, int z, UUID owner) {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        boolean partyOwned = isPartyOwned(claimsManager, owner);

        int claimCountAfter = claimCountFor(claimsManager, owner);
        int freeChunks = LcClaimEconomyConfig.SERVER.freeChunks.get();
        if (claimCountAfter <= freeChunks) {
            return;
        }

        MoneyValue price = MoneyUtil.fromCopper(LcClaimEconomyConfig.SERVER.claimPrice.get());
        if (price.isEmpty()) {
            return;
        }

        IBankAccount account = resolveAccount(server, claimsManager, owner, partyOwned);
        if (account == null) {
            LcClaimEconomy.LOGGER.warn("OP&C claim at {} {},{} has no resolvable bank account owner {}", dimension, x, z, owner);
            claimsManager.unclaim(dimension, x, z);
            knownOwners.remove(key(dimension, x, z));
            return;
        }

        if (!account.getMoneyStorage().containsValue(price)) {
            claimsManager.unclaim(dimension, x, z);
            knownOwners.remove(key(dimension, x, z));
            notify(server, owner, partyOwned, Component.translatable("message.lc_claim_economy.opc.claim_reverted_insufficient_funds"));
            return;
        }

        account.withdrawMoney(price);
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        savedData.recordClaimPurchase(LcClaimEconomyConfig.SERVER.claimPrice.get());
        BankAccountHelper.logTransaction(account, false, price, Component.translatable("message.lc_claim_economy.ledger.claim_purchase"));
    }

    private void handleUnclaim(MinecraftServer server, UUID owner) {
        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();

        // onChunkChange already reflects the claims manager's post-unclaim state (see
        // handleClaim's symmetric claimCountAfter), so +1 reconstructs the count the
        // owner held right before this particular chunk was removed.
        int claimCountBeforeUnclaim = claimCountFor(claimsManager, owner) + 1;
        if (!FreeChunkAllowance.shouldRefundOnUnclaim(claimCountBeforeUnclaim)) {
            // This chunk was within the free allowance and was never paid for.
            return;
        }

        double refundRatio = LcClaimEconomyConfig.SERVER.unclaimRefundRatio.get();
        long refundAmount = Math.round(LcClaimEconomyConfig.SERVER.claimPrice.get() * refundRatio);
        if (refundAmount <= 0) {
            return;
        }

        boolean partyOwned = isPartyOwned(claimsManager, owner);
        IBankAccount account = resolveAccount(server, claimsManager, owner, partyOwned);
        if (account == null) {
            return;
        }

        account.depositMoney(MoneyUtil.fromCopper(refundAmount));
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        savedData.recordUnclaimRefund(refundAmount);
        BankAccountHelper.logTransaction(account, true, MoneyUtil.fromCopper(refundAmount), Component.translatable("message.lc_claim_economy.ledger.unclaim_refund"));
    }

    private static boolean isPartyOwned(IServerClaimsManagerAPI claimsManager, UUID owner) {
        IPlayerClaimInfoAPI info = claimsManager.getPlayerInfo(owner);
        return info != null && info.isPartyOwned();
    }

    private static int claimCountFor(IServerClaimsManagerAPI claimsManager, UUID owner) {
        IPlayerClaimInfoAPI info = claimsManager.getPlayerInfo(owner);
        return info == null ? 0 : info.getClaimCount();
    }

    @Nullable
    private static IBankAccount resolveAccount(
            MinecraftServer server,
            IServerClaimsManagerAPI claimsManager,
            UUID owner,
            boolean partyOwned
    ) {
        return OpcAccountResolver.resolveAccount(server, owner, partyOwned);
    }

    private static void notify(MinecraftServer server, UUID owner, boolean partyOwned, Component message) {
        OpcAccountResolver.notify(server, owner, partyOwned, message);
    }
}
