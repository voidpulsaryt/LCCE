package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.service.BillingBreakdown;
import dev.voidpulsar.lc_claim_economy.service.BillingBreakdownStore;
import dev.voidpulsar.lc_claim_economy.service.BillingMessageComposer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lcce upkeep_details} - a chat breakdown of what a team is currently
 * being billed for. {@link BillingBreakdownStore} only ever holds the
 * *last computed* breakdown (it's refreshed each upkeep tick, not on demand),
 * so a brand-new team that hasn't hit its first tick yet has nothing to show
 * here even though the "next charge" line above it is still accurate.
 */
public final class BillingDetailsCommand {
    private BillingDetailsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("upkeep_details")
                        .executes(BillingDetailsCommand::showDetails)));
    }

    private static int showDetails(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer viewer = source.getPlayer();
        if (viewer == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return 0;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(viewer).orElse(null);
        if (team == null) {
            return 0;
        }

        if (team.isPartyTeam() && !BankLedgerAccess.canPurchaseForTeam(team, viewer.getUUID())) {
            viewer.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.upkeep_detail.denied"),
                    false
            );
            return 0;
        }

        long currentGameTime = source.getServer().overworld().getGameTime();
        long nextUpkeepTick = LcClaimEconomySavedData.get(source.getServer()).getNextUpkeepTick(team.getTeamId());
        viewer.displayClientMessage(BillingMessageComposer.buildNextChargeLine(nextUpkeepTick, currentGameTime), false);

        BillingBreakdown breakdown = BillingBreakdownStore.get(team.getTeamId());
        if (breakdown == null) {
            viewer.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.upkeep_detail.unavailable"),
                    false
            );
            return 1;
        }

        viewer.displayClientMessage(BillingMessageComposer.buildDetails(breakdown), false);
        return 1;
    }
}
