package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.Optional;

import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;


/**
 * {@code /lcce balance}, {@code /lcce deposit <amount>} for anyone, {@code /lcce admin set <amount>}
 * for ops.
 */
public final class LCCECommand {

    private LCCECommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lcce")
                .then(Commands.literal("balance").executes(LCCECommand::balance))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("amount", longArg(1))
                                .executes(ctx -> deposit(ctx.getSource(), BigInteger.valueOf(getLong(ctx, "amount"))))))
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("set")
                                .then(Commands.argument("amount", longArg(0))
                                        .executes(ctx -> setBalance(ctx.getSource(), BigInteger.valueOf(getLong(ctx, "amount")))))))
                .then(RegionCommand.build())
                .then(UpkeepCommand.build())
                .then(WarCommand.build())
                .then(MarketCommand.build())
                .then(WarpCommand.build())
                .then(TrustCommand.build())
                .then(BountyCommand.build())
                .then(NationCommand.build()));
    }

    private static int balance(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("lcce.command.balance", CurrencyBridge.formatValue(TeamBalance.get(team.get()))), false);
        return 1;
    }

    private static int deposit(CommandSourceStack source, BigInteger amount) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        if (!CurrencyBridge.withdrawFromPlayer(player, amount)) {
            source.sendFailure(Component.translatable("lcce.command.deposit_insufficient_funds", CurrencyBridge.formatValue(amount)));
            return 0;
        }
        TeamBalance.refund(team.get(), amount);
        source.sendSuccess(() -> Component.translatable("lcce.command.deposited", CurrencyBridge.formatValue(amount), CurrencyBridge.formatValue(TeamBalance.get(team.get()))), true);
        return 1;
    }

    private static int setBalance(CommandSourceStack source, BigInteger amount) {
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        TeamBalance.set(team.get(), amount);
        source.sendSuccess(() -> Component.translatable("lcce.command.balance_set", CurrencyBridge.formatValue(amount)), true);
        return 1;
    }

    private static Optional<Team> teamOf(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return Optional.empty();
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return Optional.empty();
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
    }
}
