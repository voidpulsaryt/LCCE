package dev.voidpulsar.lc_claim_economy.mixin;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.Protection;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.voidpulsar.lc_claim_economy.service.ChunkUserPermissionService;
import dev.voidpulsar.lc_claim_economy.service.LandChunkService;
import dev.voidpulsar.lc_claim_economy.service.LandProtectionContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Publishes whether the chunk being checked is a land chunk so that
 * {@code canPlayerUse} (which only receives the privacy property) can swap in
 * the land protection counterpart.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl", remap = false)
public abstract class ClaimManagerGuardMixin {
    @Inject(method = "shouldPreventInteraction", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$markLandContext(
            Entity actor,
            InteractionHand hand,
            BlockPos pos,
            Protection protection,
            Entity targetEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (actor instanceof ServerPlayer player && player.level() != null) {
            ClaimedChunk chunk = ((dev.ftb.mods.ftbchunks.api.ClaimedChunkManager) this)
                    .getChunk(new ChunkDimPos(player.level(), pos));
            LandProtectionContext.set(chunk != null && LandChunkService.isLandChunk(chunk));
            if (ChunkUserPermissionService.isExplicitlyAllowed(player, chunk, protection)) {
                LandProtectionContext.clear();
                cir.setReturnValue(false);
            }
        } else {
            LandProtectionContext.set(false);
        }
    }

    @Inject(method = "shouldPreventInteraction", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$clearLandContext(
            Entity actor,
            InteractionHand hand,
            BlockPos pos,
            Protection protection,
            Entity targetEntity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        LandProtectionContext.clear();
    }
}
