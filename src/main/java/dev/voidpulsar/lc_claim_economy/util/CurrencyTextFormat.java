package dev.voidpulsar.lc_claim_economy.util;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/** Shared "how do we show this account/price in chat or a GUI" rules, so every caller renders an empty balance or a free price the same way instead of each spelling out its own fallback text. */
public final class CurrencyTextFormat {
    private CurrencyTextFormat() {
    }

    public static Component formatBalance(IBankAccount account) {
        return pick(account.getMoneyStorage().isEmpty(), CurrencyTextFormat::emptyBalanceLabel, account.getMoneyStorage()::getAllValueText);
    }

    public static Component formatValue(MoneyValue value) {
        return pick(value.isEmpty(), CurrencyTextFormat::emptyBalanceLabel, value::getText);
    }

    public static Component formatPrice(long copper) {
        return pick(copper <= 0L, CurrencyTextFormat::freePriceLabel, () -> CurrencyAmounts.fromCopper(copper).getText());
    }

    public static Component formatPrice(MoneyValue value) {
        return pick(value.isEmpty(), CurrencyTextFormat::freePriceLabel, value::getText);
    }

    /** Shared branch for every method above: a fallback label when the amount is empty/zero, the real rendered text otherwise. */
    private static Component pick(boolean useFallback, Supplier<Component> fallback, Supplier<Component> rendered) {
        return useFallback ? fallback.get() : rendered.get();
    }

    private static Component emptyBalanceLabel() {
        return Component.translatable("message.lc_claim_economy.balance_empty");
    }

    private static Component freePriceLabel() {
        return Component.translatable("gui.lc_claim_economy.price_free").withStyle(ChatFormatting.GREEN);
    }
}
