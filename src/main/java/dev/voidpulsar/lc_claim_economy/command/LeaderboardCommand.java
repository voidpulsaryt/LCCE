package dev.voidpulsar.lc_claim_economy.command;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyStorage;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * {@code /lcce leaderboard [land|wealth]} - a quick, read-only,
 * chat-based ranking. This is separate from (and complementary to) the
 * optional web server's live leaderboard: this command works with zero
 * setup and no open port, the web page is a shareable, always-current view
 * for people who aren't in-game. Both read from the same underlying data,
 * just through different paths.
 */
public final class LeaderboardCommand {
    private static final int MAX_ENTRIES = 10;

    private LeaderboardCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("leaderboard")
                        .executes(context -> showLand(context.getSource()))
                        .then(Commands.literal("land").executes(context -> showLand(context.getSource())))
                        .then(Commands.literal("wealth").executes(context -> showWealth(context.getSource())))));
    }

    private static int showLand(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<Ranked> ranked = new ArrayList<>();
        for (Team team : TeamRegistry.trackedTeams(server)) {
            int claimed = FTBChunksAPI.api().isManagerLoaded()
                    ? FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks().size()
                    : 0;
            if (claimed <= 0) {
                continue;
            }
            ranked.add(new Ranked(teamLabel(team), Component.translatable("message.lc_claim_economy.leaderboard.land_value", claimed), claimed));
        }
        ranked.sort(Comparator.comparingLong(Ranked::sortValue).reversed());
        send(source, "message.lc_claim_economy.leaderboard.land_header", ranked);
        return 1;
    }

    private static int showWealth(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        List<Ranked> ranked = new ArrayList<>();
        for (Team team : TeamRegistry.trackedTeams(server)) {
            IBankAccount account;
            try {
                account = BankLedgerAccess.getAccountForTeam(server, team);
            } catch (IllegalStateException ignored) {
                continue;
            }
            MoneyStorage storage = account.getMoneyStorage();
            if (storage.isEmpty()) {
                continue;
            }
            MoneyValue balance = storage.allValues().iterator().next();
            ranked.add(new Ranked(teamLabel(team), CurrencyTextFormat.formatBalance(account), 0L, balance));
        }
        ranked.sort((a, b) -> compareMoney(b.balance(), a.balance()));
        send(source, "message.lc_claim_economy.leaderboard.wealth_header", ranked);
        return 1;
    }

    private static int compareMoney(MoneyValue a, MoneyValue b) {
        boolean aCoversB = a.containsValue(b);
        boolean bCoversA = b.containsValue(a);
        if (aCoversB && bCoversA) {
            return 0;
        }
        return aCoversB ? -1 : 1;
    }

    private static void send(CommandSourceStack source, String headerKey, List<Ranked> ranked) {
        if (ranked.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.lc_claim_economy.leaderboard.empty"), false);
            return;
        }

        MutableComponent message = Component.translatable(headerKey).withStyle(ChatFormatting.YELLOW);
        int shown = Math.min(MAX_ENTRIES, ranked.size());
        for (int i = 0; i < shown; i++) {
            Ranked entry = ranked.get(i);
            ChatFormatting rankColor = switch (i) {
                case 0 -> ChatFormatting.GOLD;
                case 1 -> ChatFormatting.WHITE;
                case 2 -> ChatFormatting.RED;
                default -> ChatFormatting.GRAY;
            };
            message.append("\n").append(Component.translatable("message.lc_claim_economy.leaderboard.line",
                    Component.literal("#" + (i + 1)).withStyle(rankColor), entry.label(), entry.valueText()));
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.displayClientMessage(message, false);
        } else {
            source.sendSuccess(() -> message, false);
        }
    }

    private static Component teamLabel(Team team) {
        return team.getName().copy().withStyle(ChatFormatting.AQUA);
    }

    private record Ranked(Component label, Component valueText, long sortValue, @Nullable MoneyValue balance) {
        Ranked(Component label, Component valueText, long sortValue) {
            this(label, valueText, sortValue, null);
        }
    }
}
