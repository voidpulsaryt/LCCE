package dev.voidpulsar.lc_claim_economy.web;

import net.minecraft.server.MinecraftServer;

import java.util.List;

/** Assembles the single JSON document the public {@code /api/data} leaderboard endpoint returns. */
final class WebApiJson {
    private WebApiJson() {
    }

    static String buildDataPayload(MinecraftServer server) {
        ServerInfoSnapshot info = WebDataService.collectInfo(server);
        List<LeaderboardEntry> balanceBoard = WebDataService.balanceLeaderboard(server);
        List<LeaderboardEntry> claimsBoard = WebDataService.claimsLeaderboard(server);

        JsonWriter economyStats = JsonWriter.object()
                .field("upkeepChargedCopper", info.statUpkeepChargedCopper())
                .field("upkeepChargedCount", info.statUpkeepChargedCount())
                .field("upkeepMissedCount", info.statUpkeepMissedCount())
                .field("claimSpendCopper", info.statClaimSpendCopper())
                .field("claimCount", info.statClaimCount())
                .field("unclaimRefundCopper", info.statUnclaimRefundCopper())
                .field("unclaimCount", info.statUnclaimCount())
                .field("marketVolumeCopper", info.statMarketVolumeCopper())
                .field("marketSaleCount", info.statMarketSaleCount());

        JsonWriter protectionPrices = JsonWriter.object()
                .field("mobGriefing", info.mobGriefProtectionPrice())
                .field("explosions", info.explosionProtectionPrice())
                .field("pvp", info.pvpDisablePrice())
                .field("blockInteract", info.blockInteractProtectionPrice())
                .field("blockEdit", info.blockEditProtectionPrice())
                .field("entityInteract", info.entityInteractProtectionPrice());

        return JsonWriter.object()
                .field("backend", info.backendName())
                .field("claimPrice", info.claimPrice())
                .field("forceLoadUpkeepPrice", info.forceLoadUpkeepPrice())
                .field("upkeepPeriodMinutes", info.upkeepPeriodMinutes())
                .field("freeChunks", info.freeChunks())
                .field("landChunkGroupSize", info.landChunkGroupSize())
                .field("warEnabled", info.warEnabled())
                .field("trackedAccounts", info.trackedAccountCount())
                .field("onlinePlayers", info.onlinePlayerCount())
                .field("protectionPrices", protectionPrices)
                .field("economyStats", economyStats)
                .arrayField("leaderboardBalance", balanceBoard.stream().map(WebApiJson::entryJson).toList())
                .arrayField("leaderboardClaims", claimsBoard.stream().map(WebApiJson::entryJson).toList())
                .build();
    }

    private static JsonWriter entryJson(LeaderboardEntry entry) {
        return JsonWriter.object()
                .field("name", entry.name())
                .field("balanceCopper", entry.balanceCopper())
                .field("claimedChunks", entry.claimedChunks());
    }
}
