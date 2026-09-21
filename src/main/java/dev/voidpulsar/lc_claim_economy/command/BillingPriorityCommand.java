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

/**
 * {@code /lcce upkeep_priority} - shows the order {@link BillingPriorityService}
 * will actually charge a team's line items in when there isn't enough in the
 * bank to cover everything at once. Matters because partial payment isn't
 * spread evenly across protections and wars; it drains top to bottom, so
 * this listing is what tells a team which safeguard would lapse first.
 */
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
        ServerPlayer viewer = source.getPlayer();
        if (!canRun(viewer)) {
            return 0;
        }

        Team team = resolveTeam(viewer);
        if (team == null) {
            return 0;
        }

        if (!isAuthorized(team, viewer)) {
            denyAccess(viewer);
            return 0;
        }

        var orderedEntries = BillingPriorityService.buildOrder(source.getServer(), team);
        if (orderedEntries.isEmpty()) {
            viewer.displayClientMessage(
                    Component.translatable("message.lc_claim_economy.upkeep_priority.empty"),
                    false
            );
            return 1;
        }

        viewer.displayClientMessage(buildPriorityListing(orderedEntries), false);
        return 1;
    }

    private static boolean canRun(ServerPlayer viewer) {
        return viewer != null && FTBTeamsAPI.api().isManagerLoaded();
    }

    private static Team resolveTeam(ServerPlayer viewer) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(viewer).orElse(null);
    }

    private static boolean isAuthorized(Team team, ServerPlayer viewer) {
        return !team.isPartyTeam() || BankLedgerAccess.canPurchaseForTeam(team, viewer.getUUID());
    }

    private static void denyAccess(ServerPlayer viewer) {
        viewer.displayClientMessage(Component.translatable("message.lc_claim_economy.upkeep_priority.denied"), false);
    }

    private static MutableComponent buildPriorityListing(java.util.List<BillingPriorityService.PriorityEntry> orderedEntries) {
        MutableComponent message = buildHeader();
        Component period = SafeguardPriceDisplay.upkeepPeriodLabel();
        for (BillingPriorityService.PriorityEntry entry : orderedEntries) {
            message.append(buildEntryLine(entry, period));
        }
        return message;
    }

    private static MutableComponent buildHeader() {
        return Component.translatable("message.lc_claim_economy.upkeep_priority.header")
                .withStyle(ChatFormatting.YELLOW)
                .append("\n")
                .append(Component.translatable("message.lc_claim_economy.upkeep_priority.legend")
                        .withStyle(ChatFormatting.GRAY))
                .append("\n");
    }

    private static MutableComponent buildEntryLine(BillingPriorityService.PriorityEntry entry, Component period) {
        Component cost = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(entry.costCopper()));
        String kindKey = entry.kind() == BillingPriorityService.EntryKind.PROTECTION
                ? "message.lc_claim_economy.upkeep_priority.kind.protection"
                : "message.lc_claim_economy.upkeep_priority.kind.war";
        return Component.translatable(
                        "message.lc_claim_economy.upkeep_priority.line",
                        entry.priority(),
                        entry.label(),
                        Component.translatable(kindKey),
                        cost,
                        period
                )
                .append("\n");
    }
}
