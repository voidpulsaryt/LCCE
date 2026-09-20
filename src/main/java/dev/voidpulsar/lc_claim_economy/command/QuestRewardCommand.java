package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lcce quest_deposit <amount_copper>} - intended to be
 * run from an FTB Quests Command Reward with permission level 2 and "Run
 * as Player" enabled, so quest completions can pay directly into a
 * player's (or their team's) bank account. Gated to permission level 2 so
 * players can't just run it themselves for free money; FTB Quests'
 * reward-level permission override is exactly the mechanism meant to grant
 * that level temporarily for the reward's own command execution.
 */
public final class QuestRewardCommand {
    private QuestRewardCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("quest_deposit")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount_copper", LongArgumentType.longArg(1L))
                                .executes(QuestRewardCommand::deposit))));
    }

    private static int deposit(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("message.lc_claim_economy.quest_deposit.no_player"));
            return 0;
        }

        long amountCopper = LongArgumentType.getLong(context, "amount_copper");
        MoneyValue amount = CurrencyAmounts.fromCopper(amountCopper);
        if (amount.isEmpty()) {
            return 0;
        }

        IBankAccount account = BankLedgerAccess.getAccountForPlayer(player.server, player);
        account.depositMoney(amount);
        player.displayClientMessage(Component.translatable("message.lc_claim_economy.quest_deposit.received",
                CurrencyTextFormat.formatValue(amount)), false);
        return 1;
    }
}
