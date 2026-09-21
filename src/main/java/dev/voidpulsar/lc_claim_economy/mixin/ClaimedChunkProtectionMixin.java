package dev.voidpulsar.lc_claim_economy.mixin;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.voidpulsar.lc_claim_economy.service.LandChunkService;
import dev.voidpulsar.lc_claim_economy.service.SiegeModeService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "Land" style claims opt out of block-edit protection entirely, so it would be
 * inconsistent to still let explosions or griefing wreck them - both checks short
 * circuit to "allowed" the moment {@link LandChunkService} recognizes the chunk.
 * Separately, a chunk whose owning team is actively sieged may also waive explosion
 * protection when the {@code siegeModeEnabled} toggle is set - see
 * {@link SiegeModeService} for the war-state lookup behind that.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkImpl", remap = false)
public abstract class ClaimedChunkProtectionMixin {
    @Inject(method = "allowExplosions", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$landExplosions(CallbackInfoReturnable<Boolean> returnValue) {
        ClaimedChunk claim = (ClaimedChunk) this;
        if (LandChunkService.isLandChunk(claim) || SiegeModeService.explosionsBypassed(claim)) {
            returnValue.setReturnValue(true);
        }
    }

    @Inject(method = "allowMobGriefing", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$landMobGriefing(CallbackInfoReturnable<Boolean> returnValue) {
        if (LandChunkService.isLandChunk((ClaimedChunk) this)) {
            returnValue.setReturnValue(true);
        }
    }
}
