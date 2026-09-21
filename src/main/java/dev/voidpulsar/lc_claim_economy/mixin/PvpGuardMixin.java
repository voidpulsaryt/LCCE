package dev.voidpulsar.lc_claim_economy.mixin;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.data.ClaimedChunkManagerImpl;
import dev.ftb.mods.ftbchunks.data.PvPMode;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.voidpulsar.lc_claim_economy.service.LandChunkService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Land chunks cannot be PvP-protected by the team (only block edit/interact are
 * configurable there), so PvP stays allowed on land chunks unless the server's
 * global PvP mode forbids it entirely.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.FTBChunks", remap = false)
public abstract class PvpGuardMixin {
    @Inject(method = "isPvPProtectedChunk", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$landPvp(PvPMode mode, Player player, CallbackInfoReturnable<Boolean> cir) {
        ClaimedChunk chunk = chunkUnderPlayer(player);
        boolean applies = chunk != null && LandChunkService.isLandChunk(chunk);
        if (!applies) {
            return;
        }
        cir.setReturnValue(mode == PvPMode.NEVER);
    }

    private static ClaimedChunk chunkUnderPlayer(Player player) {
        return ClaimedChunkManagerImpl.getInstance()
                .getChunk(new ChunkDimPos(player.level(), player.blockPosition()));
    }
}
