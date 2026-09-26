package dev.voidpulsaryt.lcce.economy;

import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.nation.NationBilling;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives the upkeep billing cycle off the server's game time. The schedule isn't persisted - on a
 * fresh server start it just picks a new "next billing" time one period out, which only means the
 * exact billing moment can drift slightly across restarts, not that any billing gets skipped or
 * doubled.
 */
public final class UpkeepScheduler {

    private static long nextBillingTime = -1;

    private UpkeepScheduler() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(UpkeepScheduler::onServerTick);
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        long period = LCCEConfig.UPKEEP_PERIOD_TICKS.get();

        if (nextBillingTime < 0) {
            nextBillingTime = now + period;
            return;
        }

        if (now >= nextBillingTime) {
            UpkeepBilling.billAllTeams(server);
            // Runs after each team's own upkeep, so nation dues are paid from whatever a team has
            // left over rather than competing with its own war/region/force-load bills.
            NationBilling.billAllNations(server);
            nextBillingTime = now + period;
        }
    }

    /** For the {@code /lcce upkeep} breakdown: how long until the next billing cycle. */
    public static long ticksUntilNextBilling(MinecraftServer server) {
        if (nextBillingTime < 0) {
            return LCCEConfig.UPKEEP_PERIOD_TICKS.get();
        }
        return Math.max(0, nextBillingTime - server.overworld().getGameTime());
    }
}
