package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.service.ConflictService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lcce war peaceful [on|off]} - a team's own opt-out of the war
 * system entirely, independent of {@code warMinClaimedChunks}. A peaceful
 * team can neither declare war nor be targeted by one.
 */
public final class WarPeacefulCommand {
    private WarPeacefulCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("war")
                        .then(Commands.literal("peaceful")
                                .executes(WarPeacefulCommand::showStatus)
                                .then(Commands.literal("on").executes(context -> setPeaceful(context, true)))
                                .then(Commands.literal("off").executes(context -> setPeaceful(context, false))))));
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        Team team = resolveTeam(player);
        if (player == null || team == null) {
            return 0;
        }

        String key = ConflictService.isPeaceful(player.server, team.getTeamId())
                ? "message.lc_claim_economy.war_peaceful_status_on"
                : "message.lc_claim_economy.war_peaceful_status_off";
        player.displayClientMessage(Component.translatable(key), false);
        return 1;
    }

    private static int setPeaceful(CommandContext<CommandSourceStack> context, boolean peaceful) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        Team team = resolveTeam(player);
        if (player == null || team == null) {
            return 0;
        }

        if (!BankLedgerAccess.canPurchaseForTeam(team, player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.war_denied"), false);
            return 0;
        }

        boolean applied = ConflictService.setPeaceful(player.server, team.getTeamId(), peaceful);
        if (!applied) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.war_peaceful_blocked_active_war"), false);
            return 0;
        }

        player.displayClientMessage(
                Component.translatable(peaceful
                        ? "message.lc_claim_economy.war_peaceful_enabled"
                        : "message.lc_claim_economy.war_peaceful_disabled"),
                false
        );
        return 1;
    }

    private static Team resolveTeam(ServerPlayer player) {
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
    }
}
