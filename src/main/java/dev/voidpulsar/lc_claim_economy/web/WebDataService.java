package dev.voidpulsar.lc_claim_economy.web;

import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.opc.OpcWebDataSource;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPricing;
import dev.voidpulsar.lc_claim_economy.service.ConflictService;
import net.minecraft.server.MinecraftServer;

import java.util.Comparator;
import java.util.List;

/**
 * Backend-agnostic data access for the built-in web server. This class's
 * own method signatures/fields must never reference any
 * {@code dev.ftb.mods.*} or {@code xaero.pac.*} type - see
 * {@code ModCompat}'s javadoc. The actual FTB/OP&C-specific collection work
 * lives in {@link FtbWebDataSource} (this package) and
 * {@link OpcWebDataSource} (the {@code opc} package), each only ever called
 * from here behind the matching {@code ModCompat} check.
 */
public final class WebDataService {
    private WebDataService() {
    }

    public static List<LeaderboardEntry> balanceLeaderboard(MinecraftServer server) {
        return collectEntries(server).stream()
                .sorted(Comparator.comparingLong(LeaderboardEntry::balanceCopper).reversed())
                .limit(LcClaimEconomyConfig.SERVER.webLeaderboardSize.get())
                .toList();
    }

    public static List<LeaderboardEntry> claimsLeaderboard(MinecraftServer server) {
        return collectEntries(server).stream()
                .sorted(Comparator.comparingInt(LeaderboardEntry::claimedChunks).reversed())
                .limit(LcClaimEconomyConfig.SERVER.webLeaderboardSize.get())
                .toList();
    }

    private static List<LeaderboardEntry> collectEntries(MinecraftServer server) {
        if (ModCompat.isFtbAvailable()) {
            return FtbWebDataSource.collectEntries(server);
        }
        if (ModCompat.isOpcAvailable()) {
            return OpcWebDataSource.collectEntries(server);
        }
        return List.of();
    }

    public static ServerInfoSnapshot collectInfo(MinecraftServer server) {
        var config = LcClaimEconomyConfig.SERVER;
        var savedData = dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData.get(server);

        String backendName;
        int trackedAccounts;
        boolean warEnabled;
        if (ModCompat.isFtbAvailable()) {
            backendName = "FTB Chunks";
            trackedAccounts = FtbWebDataSource.trackedAccountCount(server);
            warEnabled = ConflictService.isEnabled();
        } else if (ModCompat.isOpcAvailable()) {
            backendName = "Open Parties & Claims";
            trackedAccounts = OpcWebDataSource.trackedAccountCount(server);
            warEnabled = false;
        } else {
            backendName = "None";
            trackedAccounts = 0;
            warEnabled = false;
        }

        return new ServerInfoSnapshot(
                backendName,
                config.claimPrice.get(),
                config.forceLoadUpkeepPrice.get(),
                config.upkeepPeriodMinutes.get(),
                config.freeChunks.get(),
                SafeguardPricing.landChunkGroupSize(),
                warEnabled,
                config.mobGriefProtectionPrice.get(),
                config.explosionProtectionPrice.get(),
                config.pvpDisablePrice.get(),
                config.blockInteractProtectionPrice.get(),
                config.blockEditProtectionPrice.get(),
                config.entityInteractProtectionPrice.get(),
                trackedAccounts,
                server.getPlayerList().getPlayerCount(),
                savedData.getStatUpkeepChargedCopper(),
                savedData.getStatUpkeepChargedCount(),
                savedData.getStatUpkeepMissedCount(),
                savedData.getStatClaimSpendCopper(),
                savedData.getStatClaimCount(),
                savedData.getStatUnclaimRefundCopper(),
                savedData.getStatUnclaimCount(),
                savedData.getStatMarketVolumeCopper(),
                savedData.getStatMarketSaleCount()
        );
    }
}
