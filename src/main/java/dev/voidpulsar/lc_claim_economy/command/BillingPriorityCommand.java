package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import dev.voidpulsar.lc_claim_economy.service.BillingPriorityService;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BillingPriorityCommand {
    private BillingPriorityCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("upkeep_priority")
                        .executes(BillingPriorityCommand::showPriority)));
    }

    private static int showPriority(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null || !FTBTeamsAPI.api().isManagerLoaded()) {
            return 0;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return 0;
        }

        if (team.isPartyTeam() && !BankLedgerAccess.canPurchaseForTeam(team, player.getUUID())) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.upkeep_priority.denied"),
                    false
            );
            return 0;
        }

        var entries = BillingPriorityService.buildOrder(source.getServer(), team);
        if (entries.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.upkeep_priority.empty"),
                    false
            );
            return 1;
        }

        Component period = SafeguardPriceDisplay.upkeepPeriodLabel();
        MutableComponent message = Component.translatable("message.lc_claim_economy.upkeep_priority.header")
                .withStyle(ChatFormatting.YELLOW);
        message.append("\n");
        message.append(Component.translatable("message.lc_claim_economy.upkeep_priority.legend")
                .withStyle(ChatFormatting.GRAY));
        message.append("\n");

        for (BillingPriorityService.PriorityEntry entry : entries) {
            Component cost = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(entry.costCopper()));
            String kindKey = entry.kind() == BillingPriorityService.EntryKind.PROTECTION
                    ? "message.lc_claim_economy.upkeep_priority.kind.protection"
                    : "message.lc_claim_economy.upkeep_priority.kind.war";
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_priority.line",
                    entry.priority(),
                    entry.label(),
                    Component.translatable(kindKey),
                    cost,
                    period
            ));
            message.append("\n");
        }

        player.displayClientMessage(message, false);
        return 1;
    }
}
