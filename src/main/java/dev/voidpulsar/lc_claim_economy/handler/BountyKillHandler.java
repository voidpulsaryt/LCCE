package dev.voidpulsar.lc_claim_economy.handler;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Player bounties pay out on any PvP kill of the target. Team bounties only
 * pay out if the killer's team is actually at war with the target's team
 * (see {@link LcClaimEconomySavedData#isAtWarWith}), so a team bounty can't
 * be cashed in by an unrelated kill - it has to be an enemy actually
 * killing them.
 */
public class BountyKillHandler {
    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) {
            return;
        }
        LivingEntity livingEntity = event.getEntity();
        if (!(livingEntity instanceof ServerPlayer victim)) {
            return;
        }
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer killer)) {
            return;
        }
        if (killer.getUUID().equals(victim.getUUID())) {
            return;
        }

        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(victim.server);
        long playerBountyCopper = data.takePlayerBounty(victim.getUUID());
        long teamBountyCopper = takeTeamBountyIfAtWar(data, killer, victim);
        long totalCopper = playerBountyCopper + teamBountyCopper;
        if (totalCopper <= 0L) {
            return;
        }

        MoneyValue reward = CurrencyAmounts.fromCopper(totalCopper);
        IBankAccount killerAccount = BankLedgerAccess.getAccountForPlayer(killer.server, killer);
        killerAccount.depositMoney(reward);

        MutableComponent announcement = Component.translatable("message.lc_claim_economy.bounty.collected",
                killer.getDisplayName(), victim.getDisplayName(), CurrencyTextFormat.formatValue(reward));
        killer.server.getPlayerList().broadcastSystemMessage(announcement, false);
    }

    private long takeTeamBountyIfAtWar(LcClaimEconomySavedData data, ServerPlayer killer, ServerPlayer victim) {
        Team victimTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(victim).orElse(null);
        if (victimTeam == null) {
            return 0L;
        }
        Team killerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(killer).orElse(null);
        if (killerTeam == null) {
            return 0L;
        }
        if (!data.isAtWarWith(killerTeam.getTeamId(), victimTeam.getTeamId())) {
            return 0L;
        }
        return data.takeTeamBounty(victimTeam.getTeamId());
    }
}
