package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.MarketService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lcce market sell|cancel|buy|browse}, or the bare {@code /lcce market} to open the
 * market GUI - the chunk a player is currently standing in is always the target for sell/cancel/
 * buy, so there are no coordinate arguments to get wrong.
 */
public final class MarketCommand {
    private MarketCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("market")
                        .executes(MarketCommand::openGui)
                        .then(Commands.literal("sell")
                                .then(Commands.argument("price_copper", LongArgumentType.longArg(1L))
                                        .executes(MarketCommand::sell)))
                        .then(Commands.literal("cancel").executes(MarketCommand::cancel))
                        .then(Commands.literal("buy").executes(MarketCommand::buy))
                        .then(Commands.literal("browse").executes(MarketCommand::browse))));
    }

    private static int openGui(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        MarketService.syncToPlayer(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int sell(CommandContext<CommandSourceStack> context) {
        MarketService.list(context.getSource(), LongArgumentType.getLong(context, "price_copper"));
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> context) {
        MarketService.cancel(context.getSource());
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> context) {
        MarketService.buy(context.getSource());
        return 1;
    }

    private static int browse(CommandContext<CommandSourceStack> context) {
        MarketService.browse(context.getSource());
        return 1;
    }
}
