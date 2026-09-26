package dev.voidpulsaryt.lcce.integration.currency;

import io.github.lightman314.lightmanscurrency.api.money.MoneyAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import io.github.lightman314.lightmanscurrency.api.money.value.holder.IMoneyHolder;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;

/**
 * The one integration point every player-facing money movement in this mod (funding a team's
 * balance, buying/selling chunks on the marketplace, force-buyback payouts, bounties) goes
 * through: taking real Lightman's Currency money out of, or putting it into, an individual
 * player's wallet.
 * <p>
 * {@code insertMoney}/{@code extractMoney} return whatever amount could <em>not</em> be moved -
 * an empty {@code MoneyValue} means the full amount succeeded. {@code CoinAPI.MAIN_CHAIN} is used
 * rather than a hardcoded chain name so this follows Lightman's own default automatically.
 */
public final class CurrencyBridge {

    private CurrencyBridge() {}

    /**
     * @return true if the full amount was taken from the player's wallet, false if they didn't
     * have enough (in which case nothing is taken - this simulates first, then only commits the
     * real extraction if the simulation says the whole amount is available).
     */
    public static boolean withdrawFromPlayer(ServerPlayer player, BigInteger amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        IMoneyHolder holder = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        MoneyValue requested = CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount.longValueExact());

        if (!holder.extractMoney(requested, true).isEmpty()) {
            return false;
        }
        boolean success = holder.extractMoney(requested, false).isEmpty();
        if (success) {
            TransactionLog.logPlayer(player, false, amount);
        }
        return success;
    }

    /** Read-only affordability check - never takes anything from the wallet, even on success. */
    public static boolean canAfford(ServerPlayer player, BigInteger amount) {
        if (amount.signum() <= 0) {
            return true;
        }
        IMoneyHolder holder = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        MoneyValue requested = CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount.longValueExact());
        return holder.extractMoney(requested, true).isEmpty();
    }

    public static void depositToPlayer(ServerPlayer player, BigInteger amount) {
        if (amount.signum() <= 0) {
            return;
        }
        IMoneyHolder holder = MoneyAPI.getApi().GetPlayersMoneyHandler(player);
        holder.insertMoney(CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount.longValueExact()), false);
        TransactionLog.logPlayer(player, true, amount);
    }

    /**
     * Formats a raw currency amount using Lightman's Currency's own configured coin-value display
     * (coin icons, wordy text, or a numerical format like {@code $100}) so every LCCE message,
     * command, and UI shows amounts the same way the server has configured them, instead of a bare
     * unformatted number.
     */
    public static MutableComponent formatValue(BigInteger amount) {
        return CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount.longValueExact()).getText("0");
    }

    public static MutableComponent formatValue(long amount) {
        return CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount).getText("0");
    }
}
