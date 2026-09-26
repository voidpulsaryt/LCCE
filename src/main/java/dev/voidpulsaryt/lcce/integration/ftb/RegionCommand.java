package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.voidpulsaryt.lcce.region.PermissionTier;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code /lcce region ...} - creating/configuring named subdivisions of a team's claims.
 * Anyone on the team can list/inspect regions; changing them (create/delete/claim/unclaim/set/
 * whitelist) requires officer rank or better.
 */
public final class RegionCommand {

    private RegionCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("region")
                .then(Commands.literal("list").executes(RegionCommand::list))
                .then(Commands.literal("info").executes(RegionCommand::info))
                .then(Commands.literal("create")
                        .requires(RegionCommand::isOfficerOrBetter)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("delete")
                        .requires(RegionCommand::isOfficerOrBetter)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> delete(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("claim")
                        .requires(RegionCommand::isOfficerOrBetter)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> claimChunk(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("unclaim")
                        .requires(RegionCommand::isOfficerOrBetter)
                        .executes(RegionCommand::unclaimChunk))
                .then(Commands.literal("set")
                        .requires(RegionCommand::isOfficerOrBetter)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(boolSetting("mobgriefing", RegionCommand::setMobGriefing))
                                .then(boolSetting("explosions", RegionCommand::setExplosions))
                                .then(boolSetting("pvp", RegionCommand::setPvp))
                                .then(tierSetting("interact", RegionCommand::setInteractTier))
                                .then(tierSetting("edit", RegionCommand::setEditTier))))
                .then(Commands.literal("whitelist")
                        .requires(RegionCommand::isOfficerOrBetter)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .then(Commands.literal("add")
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(ctx -> whitelist(ctx, true))))
                                .then(Commands.literal("remove")
                                        .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                                .executes(ctx -> whitelist(ctx, false))))));
    }

    // --- Argument subtree builders ---------------------------------------------------------------

    private interface BoolSettingHandler {
        int apply(CommandContext<CommandSourceStack> ctx, boolean value);
    }

    private interface TierSettingHandler {
        int apply(CommandContext<CommandSourceStack> ctx, PermissionTier tier);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> boolSetting(String literal, BoolSettingHandler handler) {
        return Commands.literal(literal)
                .then(Commands.argument("value", BoolArgumentType.bool())
                        .executes(ctx -> handler.apply(ctx, BoolArgumentType.getBool(ctx, "value"))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> tierSetting(String literal, TierSettingHandler handler) {
        LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(literal);
        for (PermissionTier tier : PermissionTier.values()) {
            node.then(Commands.literal(tier.name().toLowerCase()).executes(ctx -> handler.apply(ctx, tier)));
        }
        return node;
    }

    // --- Handlers -----------------------------------------------------------------------------

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        var regions = RegionManager.get(source.getServer()).getRegionsForTeam(team.get().getId());
        if (regions.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("lcce.region.none"), false);
            return 1;
        }
        for (Region region : regions) {
            source.sendSuccess(() -> Component.translatable(
                    "lcce.region.list_entry", region.name(), region.chunks().size()
            ), false);
        }
        return regions.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        Region region = RegionManager.get(source.getServer()).getRegionAt(pos);
        if (region == null) {
            source.sendSuccess(() -> Component.translatable("lcce.region.info_none"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "lcce.region.info",
                    region.name(),
                    region.settings().allowMobGriefing(),
                    region.settings().allowExplosions(),
                    region.settings().allowPvp(),
                    region.settings().interactTier(),
                    region.settings().editTier()
            ), false);
        }
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        RegionManager manager = RegionManager.get(source.getServer());
        if (manager.findRegionByName(team.get().getId(), name).isPresent()) {
            source.sendFailure(Component.translatable("lcce.region.already_exists", name));
            return 0;
        }
        manager.createRegion(team.get().getId(), name);
        RegionSync.pushToTeam(source.getServer(), team.get());
        source.sendSuccess(() -> Component.translatable("lcce.region.created", name), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx, String name) {
        return withRegion(ctx, name, (manager, team, region) -> {
            manager.deleteRegion(region.id());
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.region.deleted", name), true);
            return 1;
        });
    }

    private static int claimChunk(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        return withRegion(ctx, name, (manager, team, region) -> {
            ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
            ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
            if (claimed == null || !claimed.getTeamData().getTeam().getId().equals(team.getId())) {
                source.sendFailure(Component.translatable("lcce.region.not_your_claim"));
                return 0;
            }
            manager.addChunkToRegion(region, pos);
            source.sendSuccess(() -> Component.translatable("lcce.region.chunk_added", name), true);
            return 1;
        });
    }

    private static int unclaimChunk(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        RegionManager manager = RegionManager.get(source.getServer());
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        Region region = manager.getRegionAt(pos);
        if (region == null) {
            source.sendFailure(Component.translatable("lcce.region.info_none"));
            return 0;
        }
        manager.removeChunkFromRegion(region, pos);
        RegionSync.pushToTeam(source.getServer(), team.get());
        source.sendSuccess(() -> Component.translatable("lcce.region.chunk_removed", region.name()), true);
        return 1;
    }

    private static int whitelist(CommandContext<CommandSourceStack> ctx, boolean add) {
        String name = StringArgumentType.getString(ctx, "name");
        return withRegion(ctx, name, (manager, team, region) -> {
            try {
                var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
                for (var profile : profiles) {
                    UUID id = profile.getId();
                    if (add) {
                        region.settings().privateWhitelist().add(id);
                    } else {
                        region.settings().privateWhitelist().remove(id);
                    }
                }
                manager.setDirty();
                ctx.getSource().sendSuccess(() -> Component.translatable(
                        add ? "lcce.region.whitelist_added" : "lcce.region.whitelist_removed", name
                ), true);
                return 1;
            } catch (CommandSyntaxException e) {
                ctx.getSource().sendFailure(Component.translatable("lcce.region.player_not_found"));
                return 0;
            }
        });
    }

    // --- Setting mutators -----------------------------------------------------------------------

    private static int setMobGriefing(CommandContext<CommandSourceStack> ctx, boolean value) {
        return withRegion(ctx, StringArgumentType.getString(ctx, "name"), (manager, team, region) -> {
            region.settings().setAllowMobGriefing(value);
            manager.setDirty();
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.region.setting_updated", "mobgriefing", value), true);
            return 1;
        });
    }

    private static int setExplosions(CommandContext<CommandSourceStack> ctx, boolean value) {
        return withRegion(ctx, StringArgumentType.getString(ctx, "name"), (manager, team, region) -> {
            region.settings().setAllowExplosions(value);
            manager.setDirty();
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.region.setting_updated", "explosions", value), true);
            return 1;
        });
    }

    private static int setPvp(CommandContext<CommandSourceStack> ctx, boolean value) {
        return withRegion(ctx, StringArgumentType.getString(ctx, "name"), (manager, team, region) -> {
            region.settings().setAllowPvp(value);
            manager.setDirty();
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.region.setting_updated", "pvp", value), true);
            return 1;
        });
    }

    private static int setInteractTier(CommandContext<CommandSourceStack> ctx, PermissionTier tier) {
        return withRegion(ctx, StringArgumentType.getString(ctx, "name"), (manager, team, region) -> {
            region.settings().setInteractTier(tier);
            manager.setDirty();
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.region.setting_updated", "interact", tier), true);
            return 1;
        });
    }

    private static int setEditTier(CommandContext<CommandSourceStack> ctx, PermissionTier tier) {
        return withRegion(ctx, StringArgumentType.getString(ctx, "name"), (manager, team, region) -> {
            region.settings().setEditTier(tier);
            manager.setDirty();
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.region.setting_updated", "edit", tier), true);
            return 1;
        });
    }

    // --- Shared plumbing --------------------------------------------------------------------------

    private interface RegionAction {
        int run(RegionManager manager, Team team, Region region);
    }

    private static int withRegion(CommandContext<CommandSourceStack> ctx, String name, RegionAction action) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        RegionManager manager = RegionManager.get(source.getServer());
        Optional<Region> region = manager.findRegionByName(team.get().getId(), name);
        if (region.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.region.not_found", name));
            return 0;
        }
        int result = action.run(manager, team.get(), region.get());
        if (result != 0) {
            RegionSync.pushToTeam(source.getServer(), team.get());
        }
        return result;
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

    private static boolean isOfficerOrBetter(CommandSourceStack source) {
        if (source.hasPermission(2)) {
            return true;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return false;
        }
        return teamOf(source)
                .map(team -> team.getRankForPlayer(player.getUUID()))
                .map(TeamRank::isOfficerOrBetter)
                .orElse(false);
    }
}
