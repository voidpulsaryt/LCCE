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
 * Only ever called from {@link WebDataService} behind
 * {@code ModCompat.isFtbAvailable()} - see that class's javadoc for why
 * this separation matters.
 */
final class FtbWebDataSource {
    private FtbWebDataSource() {
    }

    static List<LeaderboardEntry> collectEntries(MinecraftServer server) {
        List<LeaderboardEntry> entries = new ArrayList<>();
        for (Team team : TeamRegistry.trackedTeams(server)) {
            try {
                entries.add(toEntry(server, team));
            } catch (Exception e) {
                LcClaimEconomy.LOGGER.warn("Web leaderboard: failed to read FTB team {}", team.getTeamId(), e);
            }
        }
        return entries;
    }

    private static LeaderboardEntry toEntry(MinecraftServer server, Team team) {
        BankLedgerAccess.ensurePartyAccountExists(server, team);
        IBankAccount account = BankLedgerAccess.getAccountForTeam(server, team);
        long balance = CurrencyAmounts.totalCopper(account);

        int claimedChunks = FTBChunksAPI.api().isManagerLoaded()
                ? FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size()
                : 0;

        return new LeaderboardEntry(ConflictService.displayName(team), balance, claimedChunks);
    }

    static int trackedAccountCount(MinecraftServer server) {
        return TeamRegistry.trackedTeams(server).size();
    }
}
