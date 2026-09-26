package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.nation.Nation;
import dev.voidpulsaryt.lcce.nation.NationManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.Optional;

import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;

/**
 * {@code /lcce nation ...} - the Towny-style layer of allied teams above a single team, each with
 * its own pooled balance and an optional per-member-team tax. Only the capital team's officers
 * (the team that created the nation) can add/kick member teams, change the tax, or disband it -
 * a member team's own officers can always leave voluntarily, and can never be trapped in.
 */
public final class NationCommand {

    private NationCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("nation")
                .then(Commands.literal("list").executes(NationCommand::list))
                .then(Commands.literal("info").executes(NationCommand::info))
                .then(Commands.literal("create")
                        .requires(NationCommand::isOfficerOrBetter)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> create(ctx, StringArgumentType.getString(ctx, "name")))))
                .then(Commands.literal("disband").requires(NationCommand::isOfficerOrBetter).executes(NationCommand::disband))
                .then(Commands.literal("add")
                        .requires(NationCommand::isOfficerOrBetter)
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(ctx -> add(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("kick")
                        .requires(NationCommand::isOfficerOrBetter)
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(ctx -> kick(ctx, StringArgumentType.getString(ctx, "team")))))
                .then(Commands.literal("leave").requires(NationCommand::isOfficerOrBetter).executes(NationCommand::leave))
                .then(Commands.literal("tax")
                        .requires(NationCommand::isOfficerOrBetter)
                        .then(Commands.argument("amount", longArg(0))
                                .executes(ctx -> setTax(ctx, getLong(ctx, "amount")))))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("amount", longArg(1))
                                .executes(ctx -> deposit(ctx, BigInteger.valueOf(getLong(ctx, "amount"))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        var nations = NationManager.get(source.getServer()).getAllNations();
        if (nations.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("lcce.nation.none_exist"), false);
            return 1;
        }
        for (Nation nation : nations) {
            source.sendSuccess(() -> Component.translatable(
                    "lcce.nation.list_entry", nation.name(), nation.memberTeamIds().size()
            ), false);
        }
        return nations.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        Nation nation = NationManager.get(source.getServer()).getNationForTeam(team.get().getId());
        if (nation == null) {
            source.sendSuccess(() -> Component.translatable("lcce.nation.not_in_one"), false);
            return 1;
        }
        String capitalName = nameOf(nation.capitalTeamId());
        source.sendSuccess(() -> Component.translatable(
                "lcce.nation.info", nation.name(), capitalName, nation.memberTeamIds().size(),
                CurrencyBridge.formatValue(nation.balance()), CurrencyBridge.formatValue(nation.taxPerMemberPerPeriod())
        ), false);
        return 1;
    }

    private static int create(CommandContext<CommandSourceStack> ctx, String name) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        NationManager manager = NationManager.get(source.getServer());
        if (manager.getNationForTeam(team.get().getId()) != null) {
            source.sendFailure(Component.translatable("lcce.nation.already_in_one"));
            return 0;
        }
        if (manager.findNationByName(name).isPresent()) {
            source.sendFailure(Component.translatable("lcce.nation.name_taken", name));
            return 0;
        }
        manager.createNation(team.get().getId(), name);
        NationSync.pushToTeam(source.getServer(), team.get());
        source.sendSuccess(() -> Component.translatable("lcce.nation.created", name), true);
        return 1;
    }

    private static int disband(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        NationManager manager = NationManager.get(source.getServer());
        Nation nation = manager.getNationForTeam(team.get().getId());
        if (nation == null || !nation.capitalTeamId().equals(team.get().getId())) {
            source.sendFailure(Component.translatable("lcce.nation.not_capital"));
            return 0;
        }
        java.util.Set<java.util.UUID> formerMembers = new java.util.HashSet<>(nation.memberTeamIds());
        manager.disbandNation(nation.id());
        for (java.util.UUID memberTeamId : formerMembers) {
            FTBTeamsAPI.api().getManager().getTeamByID(memberTeamId)
                    .ifPresent(memberTeam -> NationSync.pushToTeam(source.getServer(), memberTeam));
        }
        source.sendSuccess(() -> Component.translatable("lcce.nation.disbanded", nation.name()), true);
        return 1;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, String targetTeamName) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        NationManager manager = NationManager.get(source.getServer());
        Nation nation = manager.getNationForTeam(team.get().getId());
        if (nation == null || !nation.capitalTeamId().equals(team.get().getId())) {
            source.sendFailure(Component.translatable("lcce.nation.not_capital"));
            return 0;
        }
        Optional<Team> targetOpt = FTBTeamsAPI.api().getManager().getTeamByName(targetTeamName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.war.team_not_found", targetTeamName));
            return 0;
        }
        if (!manager.addMember(nation, targetOpt.get().getId())) {
            source.sendFailure(Component.translatable("lcce.nation.target_already_in_one", targetTeamName));
            return 0;
        }
        NationSync.pushToTeam(source.getServer(), targetOpt.get());
        source.sendSuccess(() -> Component.translatable("lcce.nation.added", targetTeamName, nation.name()), true);
        return 1;
    }

    private static int kick(CommandContext<CommandSourceStack> ctx, String targetTeamName) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        NationManager manager = NationManager.get(source.getServer());
        Nation nation = manager.getNationForTeam(team.get().getId());
        if (nation == null || !nation.capitalTeamId().equals(team.get().getId())) {
            source.sendFailure(Component.translatable("lcce.nation.not_capital"));
            return 0;
        }
        Optional<Team> targetOpt = FTBTeamsAPI.api().getManager().getTeamByName(targetTeamName);
        if (targetOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.war.team_not_found", targetTeamName));
            return 0;
        }
        if (targetOpt.get().getId().equals(nation.capitalTeamId())) {
            source.sendFailure(Component.translatable("lcce.nation.cannot_kick_capital"));
            return 0;
        }
        manager.removeMember(nation, targetOpt.get().getId());
        NationSync.pushToTeam(source.getServer(), targetOpt.get());
        source.sendSuccess(() -> Component.translatable("lcce.nation.kicked", targetTeamName, nation.name()), true);
        return 1;
    }

    private static int leave(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        NationManager manager = NationManager.get(source.getServer());
        Nation nation = manager.getNationForTeam(team.get().getId());
        if (nation == null) {
            source.sendFailure(Component.translatable("lcce.nation.not_in_one"));
            return 0;
        }
        if (nation.capitalTeamId().equals(team.get().getId())) {
            source.sendFailure(Component.translatable("lcce.nation.capital_cannot_leave"));
            return 0;
        }
        manager.removeMember(nation, team.get().getId());
        NationSync.pushToTeam(source.getServer(), team.get());
        source.sendSuccess(() -> Component.translatable("lcce.nation.left", nation.name()), true);
        return 1;
    }

    private static int setTax(CommandContext<CommandSourceStack> ctx, long amount) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        NationManager manager = NationManager.get(source.getServer());
        Nation nation = manager.getNationForTeam(team.get().getId());
        if (nation == null || !nation.capitalTeamId().equals(team.get().getId())) {
            source.sendFailure(Component.translatable("lcce.nation.not_capital"));
            return 0;
        }
        nation.setTaxPerMemberPerPeriod(amount);
        manager.markDirty();
        source.sendSuccess(() -> Component.translatable("lcce.nation.tax_set", CurrencyBridge.formatValue(amount)), true);
        return 1;
    }

    private static int deposit(CommandContext<CommandSourceStack> ctx, BigInteger amount) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> team = teamOf(source);
        if (team.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        NationManager manager = NationManager.get(source.getServer());
        Nation nation = manager.getNationForTeam(team.get().getId());
        if (nation == null) {
            source.sendFailure(Component.translatable("lcce.nation.not_in_one"));
            return 0;
        }
        if (!TeamBalance.canAfford(team.get(), amount)) {
            source.sendFailure(Component.translatable("lcce.claim.insufficient_funds", CurrencyBridge.formatValue(amount), CurrencyBridge.formatValue(TeamBalance.get(team.get()))));
            return 0;
        }
        TeamBalance.charge(team.get(), amount);
        nation.setBalance(nation.balance().add(amount));
        manager.markDirty();
        source.sendSuccess(() -> Component.translatable("lcce.nation.deposited", CurrencyBridge.formatValue(amount), nation.name(), CurrencyBridge.formatValue(nation.balance())), true);
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

    private static String nameOf(java.util.UUID teamId) {
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId).map(t -> t.getName().getString()).orElse(teamId.toString());
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
