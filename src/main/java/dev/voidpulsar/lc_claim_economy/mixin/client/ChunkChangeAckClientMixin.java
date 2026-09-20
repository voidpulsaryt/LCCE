package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftbchunks.net.ChunkChangeResponsePacket;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(value = ChunkChangeResponsePacket.class, remap = false)
public class ChunkChangeAckClientMixin {
    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/ChunkChangeResponsePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At("HEAD"),
            remap = false
    )
    private static void lcClaimEconomy$trackChunkUpdate(
            ChunkChangeResponsePacket packet,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        ClientPricingCache.noteChunkUpdate(
                packet.totalChunks(),
                packet.changedChunks(),
                packet.problems()
        );
    }
}
