package dev.voidpulsar.lc_claim_economy.util;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;

public final class CurrencyAmounts {
    private CurrencyAmounts() {
    }

    public static MoneyValue fromCopper(long amount) {
        if (amount <= 0) {
            return MoneyValue.empty();
        }
        return CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount);
    }

    public static boolean canAfford(MoneyValue balance, MoneyValue cost) {
        return !cost.isEmpty() && balance.containsValue(cost);
    }

    /**
     * Total balance of an account in copper (base currency unit), summed
     * across every {@link MoneyValue} entry in its storage (normally just
     * the one main-chain {@code CoinValue}, but this is safe even if
     * other currency types/chains are ever stored alongside it).
     * {@link MoneyValue#getCoreValue()} is the raw numeric amount each
     * value type round-trips through {@code fromNumber}/{@code fromCopper}
     * with, so this is directly comparable/sortable across accounts.
     */
    public static long totalCopper(IBankAccount account) {
        long total = 0L;
        for (MoneyValue value : account.getMoneyStorage().allValues()) {
            total += value.getCoreValue();
        }
        return total;
    }
}
