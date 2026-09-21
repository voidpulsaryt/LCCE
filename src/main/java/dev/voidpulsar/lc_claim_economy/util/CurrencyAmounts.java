package dev.voidpulsar.lc_claim_economy.util;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;

public final class CurrencyAmounts {
    private CurrencyAmounts() {
    }

    /** {@code amount <= 0} collapses to {@link MoneyValue#empty()} rather than a zero-valued {@link CoinValue}, since LC treats "empty" as the canonical zero/free sentinel that its own text and comparison helpers already special-case. */
    public static MoneyValue fromCopper(long amount) {
        return amount <= 0 ? MoneyValue.empty() : CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, amount);
    }

    /** An empty (zero/disabled) cost is deliberately never "affordable" - callers that want a disabled price to pass unconditionally should short-circuit on {@code cost.isEmpty()} themselves before calling this, not rely on it here. */
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
