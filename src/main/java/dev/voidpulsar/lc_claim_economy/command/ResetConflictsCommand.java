package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.service.ConflictSyncCoordinator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code /lcce clear_wars} - an admin escape hatch for wiping every team's war
 * state server-wide in one shot. Exists mainly for recovering from a bad
 * config change or a data migration: both the settled war links and any
 * still-queued declare/end requests need clearing, since a queued declare
 * left behind would otherwise start a war on its own at the next upkeep tick.
 */
public final class ResetConflictsCommand {
    private ResetConflictsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                        .then(Commands.literal("clear_wars")
                                .requires(src -> src.hasPermission(2))
                                .executes(ResetConflictsCommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        LcClaimEconomySavedData economyData = LcClaimEconomySavedData.get(server);

        List<UUID> touchedTeams = new ArrayList<>();
        for (LcClaimEconomySavedData.TeamLinkEntry link : economyData.getAllLinks()) {
            UUID teamId = link.ftbTeamId();
            boolean clearedSomething = clearSettledWars(economyData, link, teamId);
            clearedSomething |= clearQueuedWarChanges(economyData, teamId);

            if (clearedSomething) {
                touchedTeams.add(teamId);
            }
        }

        for (UUID teamId : touchedTeams) {
            ConflictSyncCoordinator.syncToTeam(server, teamId);
        }

        int clearedCount = touchedTeams.size();
        LcClaimEconomy.LOGGER.info("clear_wars: cleared war state for {} team(s)", clearedCount);
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared all wars for " + clearedCount + " team(s)."),
                true
        );
        return clearedCount;
    }

    private static boolean clearSettledWars(LcClaimEconomySavedData economyData, LcClaimEconomySavedData.TeamLinkEntry link, UUID teamId) {
        if (link.warTargets().isEmpty()) {
            return false;
        }
        economyData.clearWarReferences(teamId);
        return true;
    }

    private static boolean clearQueuedWarChanges(LcClaimEconomySavedData economyData, UUID teamId) {
        TeamQueuedChanges queued = economyData.getPendingState(teamId);
        if (queued.pendingWarDeclares().isEmpty() && queued.pendingWarEnds().isEmpty()) {
            return false;
        }

        TeamQueuedChanges cleared = queued.copy().withoutWarReferences(teamId);
        for (UUID declareTargetId : new java.util.HashSet<>(queued.pendingWarDeclares())) {
            cleared = cleared.withoutPendingWarDeclare(declareTargetId);
        }
        for (UUID endTargetId : new java.util.HashSet<>(queued.pendingWarEnds())) {
            cleared = cleared.withoutPendingWarEnd(endTargetId);
        }
        economyData.setPendingState(teamId, cleared);
        return true;
    }
}
