package dev.voidpulsar.lc_claim_economy.bank;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.voidpulsar.lc_claim_economy.teams.LcTeamSyncService;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.TeamBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.common.notifications.types.bank.DepositWithdrawNotification;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;

public final class BankAccountHelper {
    private BankAccountHelper() {
    }

    public static IBankAccount getAccountForTeam(MinecraftServer server, Team team) {
        if (team.isPartyTeam()) {
            IBankAccount account = LcTeamSyncService.getBankAccount(server, team);
            if (account == null) {
                throw new IllegalStateException("Missing LC team bank account for FTB party " + team.getId());
            }
            return account;
        }
        IBankAccount account = PlayerBankReference.of(team.getId()).get();
        if (account == null) {
            throw new IllegalStateException("Missing personal bank account for player team " + team.getId());
        }
        return account;
    }

    public static IBankAccount getAccountForPlayer(MinecraftServer server, ServerPlayer player) {
        Optional<Team> team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
        if (team.isPresent()) {
            return getAccountForTeam(server, team.get());
        }
        IBankAccount account = PlayerBankReference.of(player.getUUID()).get();
        if (account == null) {
            throw new IllegalStateException("Missing personal bank account for player " + player.getUUID());
        }
        return account;
    }

    public static BankReference getReferenceForTeam(MinecraftServer server, Team team) {
        if (team.isPartyTeam()) {
            long lcTeamId = LcTeamSyncService.getLcTeamId(server, team.getId());
            if (lcTeamId <= 0) {
                LcTeamSyncService.ensureLinked(server, team);
                lcTeamId = LcTeamSyncService.getLcTeamId(server, team.getId());
            }
            if (lcTeamId <= 0) {
                throw new IllegalStateException("Missing LC team link for FTB party " + team.getId());
            }
            return TeamBankReference.of(lcTeamId);
        }
        return PlayerBankReference.of(team.getId());
    }

    public static boolean canPurchaseForTeam(Team team, UUID playerId) {
        if (!team.isPartyTeam()) {
            return true;
        }
        TeamRank rank = team.getRankForPlayer(playerId);
        return rank.isOfficerOrBetter();
    }

    public static void ensurePartyAccountExists(MinecraftServer server, Team team) {
        if (!team.isPartyTeam() || !team.isValid()) {
            return;
        }
        LcTeamSyncService.ensureLinked(server, team);
    }

    /**
     * The identity key that {@link #getAccountForPlayer} actually deposits
     * into for this player: their party's team ID if they're in one,
     * otherwise their own UUID (which is also what FTB Teams uses as the ID
     * of a player's personal team). Used to key ledger entries so a
     * player's History tab reads from the same account their money lives
     * in.
     */
    public static UUID ledgerKeyForPlayer(ServerPlayer player) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player)
                .map(Team::getId)
                .orElse(player.getUUID());
    }

    public static TeamManager teamManager() {
        return FTBTeamsAPI.api().getManager();
    }

    public static PlayerReference playerReference(ServerPlayer player) {
        return PlayerReference.of(player);
    }

    /**
     * Records a claim-economy money movement directly into Lightman's
     * Currency's own per-account transaction log (the same notification
     * feed LC uses for interest, transfers, and salary payments), instead
     * of a separate ledger this mod would have to maintain and show its
     * own UI for. {@code label} is shown as the "who/what" in LC's
     * standard "{label} deposited/withdrew {amount}" notification line.
     */
    public static void logTransaction(IBankAccount account, boolean isDeposit, MoneyValue amount, Component label) {
        account.pushNotification(() -> new DepositWithdrawNotification.Custom(label, label, isDeposit, amount));
    }
}
