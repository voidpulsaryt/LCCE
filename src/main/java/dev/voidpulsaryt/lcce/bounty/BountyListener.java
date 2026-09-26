package dev.voidpulsaryt.lcce.bounty;

import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.math.BigInteger;

/** Pays out a target's bounty to whoever kills them, straight into the killer's wallet. */
public final class BountyListener {

    private BountyListener() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(BountyListener::onDeath);
    }

    private static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer) || killer.getUUID().equals(victim.getUUID())) {
            return;
        }

        BountyManager bounties = BountyManager.get(level.getServer());
        BigInteger amount = bounties.clearBounty(victim.getUUID());
        if (amount.signum() <= 0) {
            return;
        }

        CurrencyBridge.depositToPlayer(killer, amount);
        killer.sendSystemMessage(Component.translatable("lcce.bounty.claimed", CurrencyBridge.formatValue(amount), victim.getGameProfile().getName()));
        victim.sendSystemMessage(Component.translatable("lcce.bounty.lost", CurrencyBridge.formatValue(amount)));
    }
}
