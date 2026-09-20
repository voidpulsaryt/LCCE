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
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);

        List<UUID> affected = new ArrayList<>();
        for (LcClaimEconomySavedData.TeamLinkEntry entry : savedData.getAllLinks()) {
            UUID teamId = entry.ftbTeamId();
            boolean changed = false;

            if (!entry.warTargets().isEmpty()) {
                savedData.clearWarReferences(teamId);
                changed = true;
            }

            TeamQueuedChanges pending = savedData.getPendingState(teamId);
            if (!pending.pendingWarDeclares().isEmpty() || !pending.pendingWarEnds().isEmpty()) {
                TeamQueuedChanges cleared = pending.copy().withoutWarReferences(teamId);
                for (UUID targetId : new java.util.HashSet<>(pending.pendingWarDeclares())) {
                    cleared = cleared.withoutPendingWarDeclare(targetId);
                }
                for (UUID targetId : new java.util.HashSet<>(pending.pendingWarEnds())) {
                    cleared = cleared.withoutPendingWarEnd(targetId);
                }
                savedData.setPendingState(teamId, cleared);
                changed = true;
            }

            if (changed) {
                affected.add(teamId);
            }
        }

        int count = affected.size();
        for (UUID teamId : affected) {
            ConflictSyncCoordinator.syncToTeam(server, teamId);
        }

        LcClaimEconomy.LOGGER.info("clear_wars: cleared war state for {} team(s)", count);
        context.getSource().sendSuccess(
                () -> Component.literal("Cleared all wars for " + count + " team(s)."),
                true
        );
        return count;
    }
}
