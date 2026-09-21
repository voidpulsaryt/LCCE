package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.voidpulsar.lc_claim_economy.network.PricingRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestLandChunksPayload;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestMarketListingsPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The claim map screen shows live prices, queued changes, land/build state, and now market
 * listings that this mod tracks server-side - none of which FTB Chunks itself pushes to the
 * client unprompted. Requesting a fresh copy of all four the moment the screen opens means a
 * player never sees stale data left over from before they opened the map.
 * {@link RequestMarketListingsPayload} specifically (rather than the market GUI's own request
 * payload) is what keeps opening the map from also popping the market screen open - see its
 * javadoc.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen")
public class ClaimMapScreenMixin {
    @Inject(method = "onInit", at = @At("RETURN"))
    private void lcClaimEconomy$requestClaimPrices(CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            PacketDistributor.sendToServer(new PricingRequestPayload());
            PacketDistributor.sendToServer(new QueuedStateRequestPayload());
            PacketDistributor.sendToServer(new RequestLandChunksPayload());
            PacketDistributor.sendToServer(new RequestMarketListingsPayload());
        }
    }
}
