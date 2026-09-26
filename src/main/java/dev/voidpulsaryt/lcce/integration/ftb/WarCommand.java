package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.economy.WarPricing;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.war.War;
import dev.voidpulsaryt.lcce.war.WarManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** {@code /lcce war declare|end|list} - declaring, ending, and listing wars. */
public final class WarCommand {

    private WarCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("war")
                .then(Commands.literal("list").executes(WarCommand::list))
                .then(Commands.literal("declare")
                        .requires(WarCommand::isOfficerOrBetter)
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(ctx -> declare(ctx, StringArgumentType.getString(ctx, "team"), false))
                                .then(Commands.literal("siege")
                                        .executes(ctx -> declare(ctx, StringArgumentType.getString(ctx, "team"), true)))))
                .then(Commands.literal("end")
                        .requires(WarCommand::isOfficerOrBetter)
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(ctx -> end(ctx, StringArgumentType.getString(ctx, "team")))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> teamOpt = teamOf(source);
        if (teamOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        Team team = teamOpt.get();
        WarManager warManager = WarManager.get(source.getServer());
        long now = source.getServer().overworld().getGameTime();
        long periodTicks = LCCEConfig.UPKEEP_PERIOD_TICKS.get();

        List<War> outgoing = warManager.getOutgoingWars(team.getId());
        List<War> incoming = warManager.getIncomingWars(team.getId());
        if (outgoing.isEmpty() && incoming.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("lcce.war.none"), false);
            return 1;
        }

        for (War war : outgoing) {
            BigInteger cost = WarPricing.attackerCost(war, now, periodTicks);
            String defenderName = nameOf(war.defenderTeamId());
            String key = war.isSiege() ? "lcce.war.outgoing_entry_siege" : "lcce.war.outgoing_entry";
            source.sendSuccess(() -> Component.translatable(key, defenderName, CurrencyBridge.formatValue(cost)), false);
        }
        for (War war : incoming) {
            BigInteger cost = WarPricing.defenderCost(war, now, periodTicks);
            String attackerName = nameOf(war.attackerTeamId());
            String key = war.isSiege() ? "lcce.war.incoming_entry_siege" : "lcce.war.incoming_entry";
            source.sendSuccess(() -> Component.translatable(key, attackerName, CurrencyBridge.formatValue(cost)), false);
        }
        return outgoing.size() + incoming.size();
    }

    private static int declare(CommandContext<CommandSourceStack> ctx, String targetName, boolean siege) {
        CommandSourceStack source = ctx.getSource();
        if (siege && !LCCEConfig.WAR_SIEGE_MODE_ENABLED.get()) {
            source.sendFailure(Component.translatable("lcce.war.siege_disabled"));
            return 0;
        }

        Optional<Team> attackerOpt = teamOf(source);
        if (attackerOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        Team attacker = attackerOpt.get();

        Optional<Team> defenderOpt = FTBTeamsAPI.api().getManager().getTeamByName(targetName);
        if (defenderOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.war.team_not_found", targetName));
            return 0;
        }
        Team defender = defenderOpt.get();

        if (defender.getId().equals(attacker.getId())) {
            source.sendFailure(Component.translatable("lcce.war.cannot_declare_on_self"));
            return 0;
        }

        WarManager warManager = WarManager.get(source.getServer());
        if (warManager.isAtWar(attacker.getId(), defender.getId())) {
            source.sendFailure(Component.translatable("lcce.war.already_at_war", targetName));
            return 0;
        }

        BigInteger fee = BigInteger.valueOf(LCCEConfig.WAR_DECLARATION_FEE.get());
        if (!TeamBalance.canAfford(attacker, fee)) {
            source.sendFailure(Component.translatable("lcce.war.cannot_afford_declaration", CurrencyBridge.formatValue(fee), CurrencyBridge.formatValue(TeamBalance.get(attacker))));
            return 0;
        }
        TeamBalance.charge(attacker, fee);

        long now = source.getServer().overworld().getGameTime();
        warManager.declareWar(attacker.getId(), defender.getId(), now, siege);

        String attackerKey = siege ? "lcce.war.declared_attacker_siege" : "lcce.war.declared_attacker";
        String defenderKey = siege ? "lcce.war.declared_defender_siege" : "lcce.war.declared_defender";
        notifyBothSides(attacker, defender, attackerKey, defenderKey);
        return 1;
    }

    private static int end(CommandContext<CommandSourceStack> ctx, String targetName) {
        CommandSourceStack source = ctx.getSource();
        Optional<Team> teamOpt = teamOf(source);
        if (teamOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        Team ourTeam = teamOpt.get();

        Optional<Team> otherOpt = FTBTeamsAPI.api().getManager().getTeamByName(targetName);
        if (otherOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.war.team_not_found", targetName));
            return 0;
        }
        Team other = otherOpt.get();

        WarManager warManager = WarManager.get(source.getServer());
        Optional<War> war = warManager.getWarsInvolving(ourTeam.getId()).stream()
                .filter(w -> w.involves(other.getId()))
                .findFirst();
        if (war.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.war.not_at_war", targetName));
            return 0;
        }

        warManager.endWar(war.get().id());
        notifyBothSides(ourTeam, other, "lcce.war.ended", "lcce.war.ended");
        return 1;
    }

    private static void notifyBothSides(Team teamA, Team teamB, String keyForA, String keyForB) {
        Component messageForA = Component.translatable(keyForA, teamB.getName());
        Component messageForB = Component.translatable(keyForB, teamA.getName());
        teamA.getOnlineMembers().forEach(p -> p.sendSystemMessage(messageForA));
        teamB.getOnlineMembers().forEach(p -> p.sendSystemMessage(messageForB));
    }

    private static String nameOf(java.util.UUID teamId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return teamId.toString();
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId)
                .map(t -> t.getName().getString())
                .orElse(teamId.toString());
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
