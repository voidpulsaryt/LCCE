package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * {@code /lcce bounty player|team|list}. Placing a bounty
 * escrows the money immediately (withdrawn from the placer on placement,
 * not on payout), so it's never double-counted and a bounty always has
 * real money behind it. Collection happens in {@link
 * dev.voidpulsar.lc_claim_economy.handler.BountyKillHandler}.
 */
public final class BountyCommand {
    private BountyCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("bounty")
                        .then(Commands.literal("player")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("amount_copper", LongArgumentType.longArg(1L))
                                                .executes(BountyCommand::placePlayerBounty))))
                        .then(Commands.literal("team")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .then(Commands.argument("amount_copper", LongArgumentType.longArg(1L))
                                                .executes(BountyCommand::placeTeamBounty))))
                        .then(Commands.literal("list").executes(BountyCommand::list))));
    }

    private static int placePlayerBounty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer placer = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");
        if (target.getUUID().equals(placer.getUUID())) {
            placer.displayClientMessage(Component.translatable("message.lc_claim_economy.bounty.no_self"), false);
            return 0;
        }

        long amountCopper = LongArgumentType.getLong(context, "amount_copper");
        MoneyValue amount = withdrawFromPlacer(placer, amountCopper);
        if (amount == null) {
            return 0;
        }

        LcClaimEconomySavedData.get(placer.server).addPlayerBounty(target.getUUID(), amountCopper);
        MutableComponent announcement = Component.translatable("message.lc_claim_economy.bounty.player_placed",
                placer.getDisplayName(), CurrencyTextFormat.formatValue(amount), target.getDisplayName());
        placer.server.getPlayerList().broadcastSystemMessage(announcement, false);
        return 1;
    }

    private static int placeTeamBounty(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer placer = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        Team targetTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(target).orElse(null);
        if (targetTeam == null) {
            placer.displayClientMessage(Component.translatable("message.lc_claim_economy.bounty.no_team"), false);
            return 0;
        }
        Team placerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(placer).orElse(null);
        if (placerTeam != null && placerTeam.getTeamId().equals(targetTeam.getTeamId())) {
            placer.displayClientMessage(Component.translatable("message.lc_claim_economy.bounty.no_self"), false);
            return 0;
        }

        long amountCopper = LongArgumentType.getLong(context, "amount_copper");
        MoneyValue amount = withdrawFromPlacer(placer, amountCopper);
        if (amount == null) {
            return 0;
        }

        LcClaimEconomySavedData.get(placer.server).addTeamBounty(targetTeam.getTeamId(), amountCopper);
        MutableComponent announcement = Component.translatable("message.lc_claim_economy.bounty.team_placed",
                placer.getDisplayName(), CurrencyTextFormat.formatValue(amount), targetTeam.getName());
        placer.server.getPlayerList().broadcastSystemMessage(announcement, false);
        return 1;
    }

    @Nullable
    private static MoneyValue withdrawFromPlacer(ServerPlayer placer, long amountCopper) {
        MoneyValue amount = CurrencyAmounts.fromCopper(amountCopper);
        if (amount.isEmpty()) {
            return null;
        }
        IBankAccount account = BankLedgerAccess.getAccountForPlayer(placer.server, placer);
        if (!account.getMoneyStorage().containsValue(amount)) {
            MutableComponent message = Component.translatable("message.lc_claim_economy.insufficient_funds",
                    CurrencyTextFormat.formatValue(amount), CurrencyTextFormat.formatBalance(account));
            placer.displayClientMessage(message, false);
            return null;
        }
        account.withdrawMoney(amount);
        return amount;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        Map<UUID, Long> playerBounties = data.playerBounties();
        Map<UUID, Long> teamBounties = data.teamBounties();

        if (playerBounties.isEmpty() && teamBounties.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.lc_claim_economy.bounty.list_empty"), false);
            return 1;
        }

        MutableComponent message = Component.translatable("message.lc_claim_economy.bounty.list_header").withStyle(ChatFormatting.YELLOW);
        for (Map.Entry<UUID, Long> entry : playerBounties.entrySet()) {
            String name = server.getProfileCache() != null
                    ? server.getProfileCache().get(entry.getKey()).map(p -> p.getName()).orElse(entry.getKey().toString())
                    : entry.getKey().toString();
            message.append("\n").append(Component.translatable("message.lc_claim_economy.bounty.list_player_line",
                    Component.literal(name).withStyle(ChatFormatting.AQUA),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(entry.getValue()))));
        }
        for (Map.Entry<UUID, Long> entry : teamBounties.entrySet()) {
            Team team = FTBTeamsAPI.api().getManager().getTeamByID(entry.getKey()).orElse(null);
            Component teamName = team != null ? team.getName() : Component.literal(entry.getKey().toString());
            message.append("\n").append(Component.translatable("message.lc_claim_economy.bounty.list_team_line",
                    teamName.copy().withStyle(ChatFormatting.AQUA),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(entry.getValue()))));
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.displayClientMessage(message, false);
        } else {
            source.sendSuccess(() -> message, false);
        }
        return 1;
    }
}
