package dev.voidpulsar.lc_claim_economy.bank;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamManager;
import dev.voidpulsar.lc_claim_economy.teams.CurrencyTeamLinkService;
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
import java.util.UUID;

public final class BankLedgerAccess {
    private BankLedgerAccess() {
    }

    /**
     * Resolves a team's LC bank account: an FTB party gets the LC team account linked via
     * {@link CurrencyTeamLinkService}, while a player's own solo team (FTB Teams gives every
     * player one even if they've never joined a party) maps straight to their personal LC
     * account. Throws rather than returning {@code null} because every code path that reaches
     * here has already established the team is one this mod tracks, so a missing account means
     * the link/account bootstrap was skipped somewhere, not a legitimate "no account" state.
     */
    public static IBankAccount getAccountForTeam(MinecraftServer server, Team team) {
        return team.isPartyTeam()
                ? requireAccount(
                        CurrencyTeamLinkService.getBankAccount(server, team),
                        "Missing LC team bank account for FTB party " + team.getId())
                : requireAccount(
                        PlayerBankReference.of(team.getId()).get(),
                        "Missing personal bank account for player team " + team.getId());
    }

    /** Same resolution as {@link #getAccountForTeam}, but starting from a player rather than an already-known team. */
    public static IBankAccount getAccountForPlayer(MinecraftServer server, ServerPlayer player) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player)
                .map(playerTeam -> getAccountForTeam(server, playerTeam))
                .orElseGet(() -> requireAccount(
                        PlayerBankReference.of(player.getUUID()).get(),
                        "Missing personal bank account for player " + player.getUUID()
                ));
    }

    private static IBankAccount requireAccount(@Nullable IBankAccount account, String errorIfMissing) {
        if (account == null) {
            throw new IllegalStateException(errorIfMissing);
        }
        return account;
    }

    /** Lightweight, serializable counterpart to {@link #getAccountForTeam} for call sites (e.g. GUI screens) that just need to point at an account, not read/write it immediately - lazily links a party's LC team on first use. */
    public static BankReference getReferenceForTeam(MinecraftServer server, Team team) {
        if (team.isPartyTeam()) {
            long lcTeamId = CurrencyTeamLinkService.getLcTeamId(server, team.getId());
            if (lcTeamId <= 0) {
                CurrencyTeamLinkService.ensureLinked(server, team);
                lcTeamId = CurrencyTeamLinkService.getLcTeamId(server, team.getId());
            }
            if (lcTeamId <= 0) {
                throw new IllegalStateException("Missing LC team link for FTB party " + team.getId());
            }
            return TeamBankReference.of(lcTeamId);
        }
        return PlayerBankReference.of(team.getId());
    }

    /** A solo player always spends their own money freely; a party member needs officer+ rank so rank-and-file members can't drain the shared account. */
    public static boolean canPurchaseForTeam(Team team, UUID playerId) {
        return !team.isPartyTeam() || team.getRankForPlayer(playerId).isOfficerOrBetter();
    }

    /** No-op for a solo team or an already-disbanded one - otherwise makes sure the party's LC team link (and its bank account) exists before anything tries to touch it. */
    public static void ensurePartyAccountExists(MinecraftServer server, Team team) {
        if (!team.isPartyTeam() || !team.isValid()) {
            return;
        }
        CurrencyTeamLinkService.ensureLinked(server, team);
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
