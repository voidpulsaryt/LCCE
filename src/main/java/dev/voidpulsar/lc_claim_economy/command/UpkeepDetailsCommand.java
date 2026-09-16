package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.service.UpkeepBreakdown;
import dev.voidpulsar.lc_claim_economy.service.UpkeepBreakdownStore;
import dev.voidpulsar.lc_claim_economy.service.UpkeepMessageBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class UpkeepDetailsCommand {
    private UpkeepDetailsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("upkeep_details")
                        .executes(UpkeepDetailsCommand::showDetails)));
    }

    private static int showDetails(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return 0;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return 0;
        }

        if (team.isPartyTeam() && !BankAccountHelper.canPurchaseForTeam(team, player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.upkeep_detail.denied"),
                    false
            );
            return 0;
        }

        long gameTime = source.getServer().overworld().getGameTime();
        long nextUpkeepTick = LcClaimEconomySavedData.get(source.getServer()).getNextUpkeepTick(team.getTeamId());
        player.displayClientMessage(UpkeepMessageBuilder.buildNextChargeLine(nextUpkeepTick, gameTime), false);

        UpkeepBreakdown breakdown = UpkeepBreakdownStore.get(team.getTeamId());
        if (breakdown == null) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.upkeep_detail.unavailable"),
                    false
            );
            return 1;
        }

        player.displayClientMessage(UpkeepMessageBuilder.buildDetails(breakdown), false);
        return 1;
    }
}
