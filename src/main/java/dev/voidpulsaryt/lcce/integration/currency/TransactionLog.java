package dev.voidpulsaryt.lcce.integration.currency;

import dev.ftb.mods.ftbteams.api.Team;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.api.notifications.NotificationAPI;
import io.github.lightman314.lightmanscurrency.common.notifications.types.bank.DepositWithdrawNotification;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;

/**
 * Pushes every LCCE transaction into Lightman's Currency's own notification log
 * ({@code NotificationAPI.PushPlayerNotification}), using the same
 * {@code DepositWithdrawNotification} type its own bank accounts use - so a team's members (or a
 * player, for a personal-wallet transaction) see LCCE's charges/refunds/payouts show up right
 * alongside their regular banking activity, not off in some LCCE-only log nobody thinks to check.
 * <p>
 * Called from the two choke points every transaction in this mod already goes through -
 * {@code TeamBalance.charge}/{@code refund} for the team ledger, and
 * {@code CurrencyBridge.withdrawFromPlayer}/{@code depositToPlayer} for real wallet movement - so
 * nothing elsewhere in the mod needs to remember to log anything itself.
 */
public final class TransactionLog {

    private TransactionLog() {}

    /** Logs a team-ledger transaction to every currently-online member of that team. */
    public static void logTeam(Team team, boolean isDeposit, BigInteger amount) {
        if (amount.signum() <= 0) {
            return;
        }
        MoneyValue value = toMoneyValue(amount);
        Component accountName = team.getName();
        for (ServerPlayer player : team.getOnlineMembers()) {
            NotificationAPI.getApi().PushPlayerNotification(
                    player.getUUID(), DepositWithdrawNotification.Server.create(accountName, isDeposit, value).get()
            );
        }
    }

    /** Logs a personal-wallet transaction (deposit/withdraw/payout) for one player. */
    public static void logPlayer(ServerPlayer player, boolean isDeposit, BigInteger amount) {
        if (amount.signum() <= 0) {
            return;
        }
        MoneyValue value = toMoneyValue(amount);
        NotificationAPI.getApi().PushPlayerNotification(
                player.getUUID(), DepositWithdrawNotification.Server.create(Component.literal("LCCE"), isDeposit, value).get()
        );
    }

    private static MoneyValue toMoneyValue(BigInteger amount) {
        return CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount.longValueExact());
    }
}
