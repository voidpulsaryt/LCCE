package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.economy.UpkeepPricing;
import dev.voidpulsaryt.lcce.economy.UpkeepScheduler;
import dev.voidpulsaryt.lcce.economy.WarPricing;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.region.ProtectionLineItem;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import dev.voidpulsaryt.lcce.war.War;
import dev.voidpulsaryt.lcce.war.WarManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/** {@code /lcce upkeep} - a full itemized breakdown of what a team is paying for, and when. */
public final class UpkeepCommand {

    private UpkeepCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("upkeep").executes(UpkeepCommand::breakdown);
    }

    private static int breakdown(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
        if (teamOpt.isEmpty()) {
            source.sendFailure(Component.translatable("lcce.command.no_team"));
            return 0;
        }
        Team team = teamOpt.get();
        BigInteger total = BigInteger.ZERO;

        long periodTicks = LCCEConfig.UPKEEP_PERIOD_TICKS.get();
        long now = source.getServer().overworld().getGameTime();
        WarManager warManager = WarManager.get(source.getServer());
        for (War war : warManager.getOutgoingWars(team.getId())) {
            BigInteger cost = WarPricing.attackerCost(war, now, periodTicks);
            total = total.add(cost);
            source.sendSuccess(() -> Component.translatable("lcce.upkeep.line_war", CurrencyBridge.formatValue(cost)), false);
        }
        for (War war : warManager.getIncomingWars(team.getId())) {
            BigInteger cost = WarPricing.defenderCost(war, now, periodTicks);
            total = total.add(cost);
            source.sendSuccess(() -> Component.translatable("lcce.upkeep.line_war", CurrencyBridge.formatValue(cost)), false);
        }

        long forceLoadPrice = LCCEConfig.UPKEEP_FORCE_LOAD_PRICE.get();
        int forceLoadedCount = FTBChunksAPI.api().getManager().getOrCreateData(team).getForceLoadedChunks().size();
        if (forceLoadedCount > 0 && forceLoadPrice > 0) {
            BigInteger cost = BigInteger.valueOf(forceLoadPrice).multiply(BigInteger.valueOf(forceLoadedCount));
            total = total.add(cost);
            int count = forceLoadedCount;
            source.sendSuccess(() -> Component.translatable("lcce.upkeep.line_forceload", count, CurrencyBridge.formatValue(cost)), false);
        }

        List<Region> regions = RegionManager.get(source.getServer()).getRegionsForTeam(team.getId());
        for (Region region : regions) {
            for (ProtectionLineItem item : ProtectionLineItem.values()) {
                if (!UpkeepPricing.isIntended(region, item)) {
                    continue;
                }
                BigInteger cost = UpkeepPricing.costOf(region, item);
                total = total.add(cost);
                boolean dismantled = region.dismantled().contains(item);
                String statusKey = dismantled ? "lcce.upkeep.line_dismantled" : "lcce.upkeep.line_active";
                source.sendSuccess(() -> Component.translatable(statusKey, region.name(), item.configKey(), CurrencyBridge.formatValue(cost)), false);
            }
        }

        if (total.signum() == 0) {
            source.sendSuccess(() -> Component.translatable("lcce.upkeep.none"), false);
        } else {
            BigInteger finalTotal = total;
            source.sendSuccess(() -> Component.translatable("lcce.upkeep.total", CurrencyBridge.formatValue(finalTotal), CurrencyBridge.formatValue(TeamBalance.get(team))), false);
        }

        long ticksLeft = UpkeepScheduler.ticksUntilNextBilling(source.getServer());
        long secondsLeft = ticksLeft / 20;
        source.sendSuccess(() -> Component.translatable("lcce.upkeep.next_payment", secondsLeft), false);
        return 1;
    }
}
