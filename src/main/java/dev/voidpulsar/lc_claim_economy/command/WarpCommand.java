package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.service.WarpService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lcce warp set|delete|public|list|tp|tpto} - player-owned warps set
 * inside the player's own team's claimed land (see {@link WarpService}).
 */
public final class WarpCommand {
    private WarpCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("warp")
                        .executes(WarpCommand::openGui)
                        .then(Commands.literal("set")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(WarpCommand::set)))
                        .then(Commands.literal("delete")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(WarpCommand::delete)))
                        .then(Commands.literal("public")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("public", BoolArgumentType.bool())
                                                .executes(WarpCommand::setPublic))))
                        .then(Commands.literal("alias")
                                .then(Commands.literal("add")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.argument("alias", StringArgumentType.word())
                                                        .executes(WarpCommand::aliasAdd))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .then(Commands.argument("alias", StringArgumentType.word())
                                                        .executes(WarpCommand::aliasRemove)))))
                        .then(Commands.literal("list").executes(WarpCommand::list))
                        .then(Commands.literal("tp")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(WarpCommand::teleportOwn)))
                        .then(Commands.literal("tpto")
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .then(Commands.argument("name", StringArgumentType.word())
                                                .executes(WarpCommand::teleportOther))))));
    }

    private static int openGui(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.syncToPlayer(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.createOrUpdateWarp(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"));
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.deleteWarp(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"));
        return 1;
    }

    private static int setPublic(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.setPublic(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "name"),
                BoolArgumentType.getBool(context, "public"));
        return 1;
    }

    private static int aliasAdd(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.addAlias(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "name"),
                StringArgumentType.getString(context, "alias"));
        return 1;
    }

    private static int aliasRemove(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.removeAlias(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "name"),
                StringArgumentType.getString(context, "alias"));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.list(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int teleportOwn(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.teleportToOwnWarp(context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "name"));
        return 1;
    }

    private static int teleportOther(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        WarpService.teleportToOtherWarp(
                context.getSource().getPlayerOrException(),
                StringArgumentType.getString(context, "player"),
                StringArgumentType.getString(context, "name"));
        return 1;
    }
}
