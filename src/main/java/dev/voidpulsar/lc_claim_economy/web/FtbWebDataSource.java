package dev.voidpulsar.lc_claim_economy.web;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.service.ConflictService;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the live set of FTB teams into the plain {@link LeaderboardEntry} rows
 * the public leaderboard page renders. Called only from {@link WebDataService}
 * behind {@code ModCompat.isFtbAvailable()} - see that class's javadoc for why
 * FTB types are confined to this file.
 */
final class FtbWebDataSource {
    private FtbWebDataSource() {
    }

    static List<LeaderboardEntry> collectEntries(MinecraftServer server) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Team team : TeamRegistry.trackedTeams(server)) {
            try {
                entries.add(buildEntry(server, team));
            } catch (Exception e) {
                // One team failing to resolve (e.g. mid-deletion) shouldn't blank the whole
                // leaderboard for everyone else - skip it and keep going.
                LcClaimEconomy.LOGGER.warn("Web leaderboard: failed to read FTB team {}", team.getTeamId(), e);
            }
        }
        return entries;
    }

    private static LeaderboardEntry buildEntry(MinecraftServer server, Team team) {
        BankLedgerAccess.ensurePartyAccountExists(server, team);
        IBankAccount account = BankLedgerAccess.getAccountForTeam(server, team);
        long balanceCopper = CurrencyAmounts.totalCopper(account);

        int claimedChunks = FTBChunksAPI.api().isManagerLoaded()
                ? FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size()
                : 0;

        return new LeaderboardEntry(ConflictService.displayName(team), balanceCopper, claimedChunks);
    }

    static int trackedAccountCount(MinecraftServer server) {
        return TeamRegistry.trackedTeams(server).size();
    }
}
