package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.bank.ClaimTransferContext;
import net.minecraft.commands.CommandSourceStack;

import java.util.ArrayList;
import java.util.List;

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

    public static int unclaimAll(ChunkTeamData chunkData, CommandSourceStack source, boolean suppressNotifications) {
        List<ClaimedChunk> claimedChunks = new ArrayList<>(chunkData.getClaimedChunks());
        if (claimedChunks.isEmpty()) {
            return 0;
        }

        int[] unclaimed = {0};
        Runnable action = () -> {
            for (ClaimedChunk chunk : claimedChunks) {
                if (chunkData.unclaim(source, chunk.getPos(), false).isSuccess()) {
                    unclaimed[0]++;
                }
            }
        };
        if (suppressNotifications) {
            ClaimTransferContext.runSuppressingNotifications(action);
        } else {
            action.run();
        }
        return unclaimed[0];
    }
}
