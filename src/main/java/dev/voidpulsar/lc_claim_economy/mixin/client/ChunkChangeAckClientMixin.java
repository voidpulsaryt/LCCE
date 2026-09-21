package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftbchunks.net.ChunkChangeResponsePacket;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/**
 * FTB Chunks' own bulk-claim response packet already carries per-chunk problem ids
 * (from our {@code ChunkAcquisitionHandler} rejecting individual chunks server-side),
 * but has no client-side hook of its own for a mod to react to it. This mixin taps the
 * packet's own {@code handle} to feed those counts into {@link ClientPricingCache},
 * which {@code claimProblemLine} later reads to build a bulk-vs-single "insufficient
 * funds" chat message instead of a generic one.
 */
@Mixin(value = ChunkChangeResponsePacket.class, remap = false)
public class ChunkChangeAckClientMixin {
    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/ChunkChangeResponsePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At("HEAD"),
            remap = false
    )
    private static void lcClaimEconomy$trackChunkUpdate(
            ChunkChangeResponsePacket ackPacket,
            dev.architectury.networking.NetworkManager.PacketContext senderContext,
            CallbackInfo callback
    ) {
        ClientPricingCache.noteChunkUpdate(
                ackPacket.totalChunks(),
                ackPacket.changedChunks(),
                ackPacket.problems()
        );
    }
}
