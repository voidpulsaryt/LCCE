package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.marketplace.ChunkOwnership;
import dev.voidpulsaryt.lcce.marketplace.MarketplaceManager;
import dev.voidpulsaryt.lcce.trust.ChunkTrustManager;
import dev.voidpulsaryt.lcce.trust.TrustLevel;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;

/**
 * {@code /lcce trust} - grants one specific outsider access to one specific chunk without adding
 * them to the team, without buying/selling anything, and without opening up a whole region. This
 * is deliberately the highest-precedence check in the protection listener: a trust grant wins over
 * region tiers, marketplace ownership rules, and FTB Chunks' own team-membership check.
 */
public final class TrustCommand {

    private TrustCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("trust")
                .then(Commands.literal("list").executes(TrustCommand::list))
                .then(Commands.literal("add")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(ctx -> add(ctx, TrustLevel.BUILD))
                                .then(Commands.literal("build").executes(ctx -> add(ctx, TrustLevel.BUILD)))
                                .then(Commands.literal("interact").executes(ctx -> add(ctx, TrustLevel.INTERACT)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                .executes(TrustCommand::remove)));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        Map<UUID, TrustLevel> grants = ChunkTrustManager.get(server).getTrustedPlayers(pos);
        if (grants.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("lcce.trust.none"), false);
            return 1;
        }
        grants.forEach((playerId, level) -> {
            ServerPlayer online = server.getPlayerList().getPlayer(playerId);
            String name = online != null ? online.getGameProfile().getName() : playerId.toString();
            source.sendSuccess(() -> Component.translatable("lcce.trust.list_entry", name, level), false);
        });
        return grants.size();
    }

    private static int add(CommandContext<CommandSourceStack> ctx, TrustLevel level) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());

        if (!isAuthorized(source, server, player, pos)) {
            source.sendFailure(Component.translatable("lcce.market.not_authorized"));
            return 0;
        }

        try {
            var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
            for (var profile : profiles) {
                ChunkTrustManager.get(server).trust(pos, profile.getId(), level);
            }
            source.sendSuccess(() -> Component.translatable("lcce.trust.added", level), true);
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("lcce.region.player_not_found"));
            return 0;
        }
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());

        if (!isAuthorized(source, server, player, pos)) {
            source.sendFailure(Component.translatable("lcce.market.not_authorized"));
            return 0;
        }

        try {
            var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
            boolean any = false;
            for (var profile : profiles) {
                any |= ChunkTrustManager.get(server).untrust(pos, profile.getId());
            }
            if (!any) {
                source.sendFailure(Component.translatable("lcce.trust.not_found"));
                return 0;
            }
            source.sendSuccess(() -> Component.translatable("lcce.trust.removed"), true);
            return 1;
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.translatable("lcce.region.player_not_found"));
            return 0;
        }
    }

    /** Officer+ of the claiming team for a state-owned chunk, or the private owner if it's been bought out. */
    private static boolean isAuthorized(CommandSourceStack source, MinecraftServer server, ServerPlayer player, ChunkDimPos pos) {
        ChunkOwnership ownership = MarketplaceManager.get(server).getOwnership(pos);
        if (ownership != null) {
            return ownership.ownerId().equals(player.getUUID());
        }
        ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
        if (claimed == null) {
            return false;
        }
        Team team = claimed.getTeamData().getTeam();
        return source.hasPermission(2) || team.getRankForPlayer(player.getUUID()).isOfficerOrBetter();
    }
}
