package dev.voidpulsar.lc_claim_economy.util;

import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public final class CurrencyTextFormat {
    private CurrencyTextFormat() {
    }

    public static Component formatBalance(IBankAccount account) {
        if (account.getMoneyStorage().isEmpty()) {
            return Component.translatable("message.lc_claim_economy.balance_empty");
        }
        return account.getMoneyStorage().getAllValueText();
    }

    public static Component formatValue(MoneyValue value) {
        if (value.isEmpty()) {
            return Component.translatable("message.lc_claim_economy.balance_empty");
        }
        return value.getText();
    }

    public static Component formatPrice(long copper) {
        if (copper <= 0L) {
            return Component.translatable("gui.lc_claim_economy.price_free").withStyle(ChatFormatting.GREEN);
        }
        return CurrencyAmounts.fromCopper(copper).getText();
    }

    public static Component formatPrice(MoneyValue value) {
        if (value.isEmpty()) {
            return Component.translatable("gui.lc_claim_economy.price_free").withStyle(ChatFormatting.GREEN);
        }
        return value.getText();
    }
}
