package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.voidpulsaryt.lcce.warp.WarpManager;
import dev.voidpulsaryt.lcce.warp.WarpPoint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * {@code /lcce warp} - simple named teleport points, personal by default or shared publicly (a
 * private chunk owner can point people at their "Shop" this way, for instance).
 */
public final class WarpCommand {

    private WarpCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("warp")
                .then(Commands.literal("list").executes(WarpCommand::list))
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "name"), false))
                                .then(Commands.literal("public")
                                        .executes(ctx -> set(ctx, StringArgumentType.getString(ctx, "name"), true)))))
                .then(Commands.literal("del")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("go")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> go(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("visit")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> visit(ctx, StringArgumentType.getString(ctx, "name"))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        Map<String, WarpPoint> warps = WarpManager.get(source.getServer()).getWarpsFor(player.getUUID());
        if (warps.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("lcce.warp.none"), false);
            return 1;
        }
        warps.forEach((name, point) -> source.sendSuccess(() -> Component.translatable(
                "lcce.warp.list_entry", name, point.isPublic()
        ), false));
        return warps.size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, String name, boolean isPublic) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        WarpPoint point = new WarpPoint(
                player.level().dimension(), player.blockPosition(), player.getYRot(), player.getXRot(), isPublic
        );
        WarpManager.get(source.getServer()).setWarp(player.getUUID(), name, point);
        source.sendSuccess(() -> Component.translatable("lcce.warp.set", name), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        if (WarpManager.get(source.getServer()).deleteWarp(player.getUUID(), name)) {
            source.sendSuccess(() -> Component.translatable("lcce.warp.deleted", name), true);
            return 1;
        }
        source.sendFailure(Component.translatable("lcce.warp.not_found", name));
        return 0;
    }

    private static int go(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        WarpPoint point = WarpManager.get(source.getServer()).getWarp(player.getUUID(), name);
        if (point == null) {
            source.sendFailure(Component.translatable("lcce.warp.not_found", name));
            return 0;
        }
        return teleport(source, player, point);
    }

    private static int visit(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        try {
            var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
            UUID targetId = profiles.iterator().next().getId();
            WarpPoint point = WarpManager.get(source.getServer()).getWarp(targetId, name);
            if (point == null || !point.isPublic()) {
                source.sendFailure(Component.translatable("lcce.warp.not_found", name));
                return 0;
            }
            return teleport(source, player, point);
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("lcce.region.player_not_found"));
            return 0;
        }
    }

    private static int teleport(CommandSourceStack source, ServerPlayer player, WarpPoint point) {
        ServerLevel level = source.getServer().getLevel(point.dimension());
        if (level == null) {
            source.sendFailure(Component.translatable("lcce.warp.dimension_missing"));
            return 0;
        }
        player.teleportTo(level, point.pos().getX() + 0.5, point.pos().getY(), point.pos().getZ() + 0.5, point.yaw(), point.pitch());
        return 1;
    }
}
