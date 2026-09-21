package dev.voidpulsar.lc_claim_economy.util;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Shared "how do we show this account/price in chat or a GUI" rules, so every caller renders an empty balance or a free price the same way instead of each spelling out its own fallback text. */
public final class CurrencyTextFormat {
    private CurrencyTextFormat() {
    }

    public static Component formatBalance(IBankAccount account) {
        return account.getMoneyStorage().isEmpty() ? emptyBalanceLabel() : account.getMoneyStorage().getAllValueText();
    }

    public static Component formatValue(MoneyValue value) {
        return value.isEmpty() ? emptyBalanceLabel() : value.getText();
    }

    public static Component formatPrice(long copper) {
        return copper <= 0L ? freePriceLabel() : CurrencyAmounts.fromCopper(copper).getText();
    }

    public static Component formatPrice(MoneyValue value) {
        return value.isEmpty() ? freePriceLabel() : value.getText();
    }

    private static Component emptyBalanceLabel() {
        return Component.translatable("message.lc_claim_economy.balance_empty");
    }

    private static Component freePriceLabel() {
        return Component.translatable("gui.lc_claim_economy.price_free").withStyle(ChatFormatting.GREEN);
    }
}
