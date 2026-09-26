package dev.voidpulsaryt.lcce.economy;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.integration.ftb.TeamBalance;
import dev.voidpulsaryt.lcce.region.ProtectionLineItem;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import dev.voidpulsaryt.lcce.war.WarManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Runs each upkeep period: first tops up the team's balance with a per-online-member tax (see
 * {@link #collectMemberTax}), then collects the team's unavoidable war upkeep (see
 * {@link WarPricing}) straight off the top of what's left, then charges whatever's left after
 * that for their regions' active protections, dismantling the lowest-priority ones first if that's
 * not enough to cover everything, and silently restoring previously-dismantled ones the moment the
 * team can afford them again. This ordering is what makes a war "occupy a team economically" -
 * the war tax is paid first (after the resident tax tops the pool up), no matter what, and region
 * protections are what actually gives when money gets tight.
 */
public final class UpkeepBilling {

    private UpkeepBilling() {}

    private record LineItemEntry(Region region, ProtectionLineItem item, BigInteger cost, int priorityRank) {}

    public static void billAllTeams(MinecraftServer server) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        RegionManager regionManager = RegionManager.get(server);
        WarManager warManager = WarManager.get(server);
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            collectMemberTax(team);
            BigInteger balanceAfterWar = billWar(server, warManager, team);
            BigInteger balanceAfterForceLoad = ForceLoadBilling.bill(server, team, balanceAfterWar);
            billRegions(regionManager, team, balanceAfterForceLoad);
        }
    }

    /**
     * Towny-style resident tax: each of a team's <em>online</em> members is charged a small flat
     * amount straight from their own wallet into the team's pooled balance every period - the
     * same automatic top-up a Towny town gets from its residents, so a team doesn't have to rely
     * on someone remembering to run {@code /lcce deposit} to keep upkeep funded. Offline members
     * aren't taxed (not worth an offline-wallet lookup for what's an optional top-up - 0 disables
     * it entirely), and a member who can't afford it that period simply isn't taxed; nothing else
     * about them changes.
     */
    private static void collectMemberTax(Team team) {
        long taxPerMember = LCCEConfig.UPKEEP_PER_MEMBER_TAX.get();
        if (taxPerMember <= 0) {
            return;
        }
        BigInteger tax = BigInteger.valueOf(taxPerMember);
        for (ServerPlayer member : team.getOnlineMembers()) {
            if (CurrencyBridge.withdrawFromPlayer(member, tax)) {
                TeamBalance.refund(team, tax);
            }
        }
    }

    /** Charges the team's war upkeep (capped at what they actually have) and returns what's left. */
    private static BigInteger billWar(MinecraftServer server, WarManager warManager, Team team) {
        BigInteger balance = TeamBalance.get(team);
        BigInteger warCost = WarPricing.totalWarCostForTeam(warManager, team.getId(), server);
        if (warCost.signum() <= 0) {
            return balance;
        }
        BigInteger charge = warCost.min(balance);
        if (charge.signum() > 0) {
            TeamBalance.charge(team, charge);
        }
        return balance.subtract(charge);
    }

    private static void billRegions(RegionManager regionManager, Team team, BigInteger availableBalance) {
        List<Region> regions = regionManager.getRegionsForTeam(team.getId());
        if (regions.isEmpty()) {
            return;
        }

        List<String> priorityOrder = new ArrayList<>(LCCEConfig.UPKEEP_DISMANTLE_PRIORITY.get());

        List<LineItemEntry> entries = new ArrayList<>();
        for (Region region : regions) {
            for (ProtectionLineItem item : ProtectionLineItem.values()) {
                if (!UpkeepPricing.isIntended(region, item)) {
                    continue;
                }
                int rank = priorityOrder.indexOf(item.configKey());
                entries.add(new LineItemEntry(region, item, UpkeepPricing.costOf(region, item),
                        rank < 0 ? Integer.MIN_VALUE : rank));
            }
        }

        if (entries.isEmpty()) {
            return;
        }

        // Highest priority first (kept longest); once the running total can't fit any more, every
        // remaining (lower-priority, and within a tier, more expensive) entry gets dismantled.
        entries.sort(Comparator.comparingInt(LineItemEntry::priorityRank).reversed()
                .thenComparing(LineItemEntry::cost));

        BigInteger runningTotal = BigInteger.ZERO;

        for (LineItemEntry entry : entries) {
            boolean wasAlreadyDismantled = entry.region().dismantled().contains(entry.item());
            BigInteger next = runningTotal.add(entry.cost());
            if (next.compareTo(availableBalance) <= 0) {
                runningTotal = next;
                entry.region().dismantled().remove(entry.item());
            } else {
                entry.region().dismantled().add(entry.item());
                if (!wasAlreadyDismantled) {
                    notifyDismantled(team, entry.region(), entry.item());
                }
            }
        }

        if (runningTotal.signum() > 0) {
            TeamBalance.charge(team, runningTotal);
        }
        regionManager.setDirty();
    }

    private static void notifyDismantled(Team team, Region region, ProtectionLineItem item) {
        Component message = Component.translatable("lcce.upkeep.dismantled", region.name(), item.configKey());
        for (ServerPlayer player : team.getOnlineMembers()) {
            player.sendSystemMessage(message);
        }
    }
}
