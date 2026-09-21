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

import javax.annotation.Nullable;

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
        if (!(actor instanceof ServerPlayer player) || player.level() == null) {
            LandProtectionContext.set(false);
            return;
        }

        ClaimedChunk chunk = resolveChunk(player, pos);
        LandProtectionContext.set(isLandChunk(chunk));

        boolean explicitlyAllowed = ChunkUserPermissionService.isExplicitlyAllowed(player, chunk, protection);
        if (explicitlyAllowed) {
            LandProtectionContext.clear();
            cir.setReturnValue(false);
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

    private ClaimedChunk resolveChunk(ServerPlayer player, BlockPos pos) {
        dev.ftb.mods.ftbchunks.api.ClaimedChunkManager manager = (dev.ftb.mods.ftbchunks.api.ClaimedChunkManager) this;
        return manager.getChunk(new ChunkDimPos(player.level(), pos));
    }

    private static boolean isLandChunk(@Nullable ClaimedChunk chunk) {
        return chunk != null && LandChunkService.isLandChunk(chunk);
    }
}
