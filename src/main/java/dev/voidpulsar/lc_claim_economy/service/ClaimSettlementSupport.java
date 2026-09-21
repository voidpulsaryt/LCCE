package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.bank.ClaimTransferContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;

/** Shared helper for the "wipe out every claim a team/player holds" settlement paths (disband, party join, admin clear). */
public final class ClaimSettlementSupport {
    private ClaimSettlementSupport() {
    }

    public static long refundPerChunk() {
        long claimPrice = LcClaimEconomyConfig.SERVER.claimPrice.get();
        double refundRatio = LcClaimEconomyConfig.SERVER.unclaimRefundRatio.get();
        if (claimPrice <= 0 || refundRatio <= 0) {
            return 0;
        }
        return (long) Math.floor(claimPrice * refundRatio);
    }

    public static int unclaimAll(ChunkTeamData chunkData, CommandSourceStack source) {
        return unclaimAll(chunkData, source, true);
    }

    /**
     * Unclaims every chunk currently held by {@code chunkData}. Settlement callers
     * snapshot the claim list up front since the loop itself mutates {@code chunkData}
     * as it goes, and iterating a collection while removing from it underneath is asking
     * for trouble.
     */
    public static int unclaimAll(ChunkTeamData chunkData, CommandSourceStack source, boolean suppressNotifications) {
        List<ClaimedChunk> snapshot = new ArrayList<>(chunkData.getClaimedChunks());
        if (snapshot.isEmpty()) {
            return 0;
        }

        int[] successCount = {0};
        Runnable unclaimEach = () -> {
            for (ClaimedChunk chunk : snapshot) {
                if (chunkData.unclaim(source, chunk.getPos(), false).isSuccess()) {
                    successCount[0]++;
                }
            }
        };

        if (suppressNotifications) {
            ClaimTransferContext.runSuppressingNotifications(unclaimEach);
        } else {
            unclaimEach.run();
        }
        return successCount[0];
    }
}
