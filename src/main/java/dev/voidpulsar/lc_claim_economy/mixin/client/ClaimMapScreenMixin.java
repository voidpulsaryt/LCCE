package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.voidpulsar.lc_claim_economy.network.PricingRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestLandChunksPayload;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateRequestPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen")
public class ClaimMapScreenMixin {
    @Inject(method = "onInit", at = @At("RETURN"))
    private void lcClaimEconomy$requestClaimPrices(CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            PacketDistributor.sendToServer(new PricingRequestPayload());
            PacketDistributor.sendToServer(new QueuedStateRequestPayload());
            PacketDistributor.sendToServer(new RequestLandChunksPayload());
        }
    }
}
