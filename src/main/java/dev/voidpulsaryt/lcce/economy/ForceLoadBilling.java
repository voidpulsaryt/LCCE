package dev.voidpulsaryt.lcce.economy;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.integration.ftb.TeamBalance;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Force-loading a chunk (keeping it ticking while nobody's nearby) is a per-chunk FTB Chunks
 * feature this mod bills for every upkeep period, same as it bills for region protection. Unlike
 * region line items, a force-loaded chunk that becomes unaffordable can't be "dismantled" in
 * place - there's nothing to fall back to - so instead the least-recently-force-loaded chunks get
 * un-force-loaded one at a time until what's left fits the team's remaining balance.
 */
public final class ForceLoadBilling {

    private ForceLoadBilling() {}

    /**
     * Charges (up to) the team's remaining balance for their currently force-loaded chunks,
     * un-force-loading the oldest ones first if that's not enough to cover all of them.
     *
     * @return the balance still remaining after this charge, for the next billing phase to use
     */
    public static BigInteger bill(MinecraftServer server, Team team, BigInteger availableBalance) {
        long pricePerChunk = LCCEConfig.UPKEEP_FORCE_LOAD_PRICE.get();
        if (pricePerChunk <= 0) {
            return availableBalance;
        }

        ChunkTeamData teamData = FTBChunksAPI.api().getManager().getOrCreateData(team);
        List<ClaimedChunk> forceLoaded = new ArrayList<>(teamData.getForceLoadedChunks());
        if (forceLoaded.isEmpty()) {
            return availableBalance;
        }

        // Newest-first: the oldest (least-recently-force-loaded) requests end up at the tail, so
        // the drop loop below - which walks from index `keep` to the end - evicts them first and
        // a team keeps whatever they set up most recently.
        forceLoaded.sort(Comparator.comparingLong(ClaimedChunk::getForceLoadedTime).reversed());

        BigInteger price = BigInteger.valueOf(pricePerChunk);
        BigInteger affordableCount = availableBalance.divide(price);

        if (BigInteger.valueOf(forceLoaded.size()).compareTo(affordableCount) <= 0) {
            BigInteger cost = price.multiply(BigInteger.valueOf(forceLoaded.size()));
            TeamBalance.charge(team, cost);
            return availableBalance.subtract(cost);
        }

        int keep = affordableCount.intValue();
        CommandSourceStack console = server.createCommandSourceStack();
        for (int i = keep; i < forceLoaded.size(); i++) {
            teamData.unForceLoad(console, forceLoaded.get(i).getPos(), false, true);
        }

        BigInteger cost = price.multiply(BigInteger.valueOf(keep));
        TeamBalance.charge(team, cost);
        return availableBalance.subtract(cost);
    }
}
