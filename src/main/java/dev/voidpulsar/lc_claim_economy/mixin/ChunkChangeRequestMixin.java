package dev.voidpulsar.lc_claim_economy.mixin;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.net.RequestChunkChangePacket;
import dev.voidpulsar.lc_claim_economy.bank.ClaimTransferContext;
import dev.voidpulsar.lc_claim_economy.handler.MassClaimHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RequestChunkChangePacket.class, remap = false)
public class ChunkChangeRequestMixin {
    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/RequestChunkChangePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/Set;iterator()Ljava/util/Iterator;",
                    shift = At.Shift.BEFORE
            ),
            cancellable = true,
            remap = false
    )
    private static void lcClaimEconomy$beginBulkOperation(
            RequestChunkChangePacket message,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        CommandSourceStack source = player.createCommandSourceStack();
        ChunkTeamData chunkTeamData = MassClaimHandler.resolveTeamData(message, player);
        if (chunkTeamData == null) {
            return;
        }

        if (MassClaimHandler.rejectIfInsufficientFunds(message, player, source, chunkTeamData)) {
            ci.cancel();
            return;
        }

        ClaimTransferContext.beginExecution(message.action(), message.chunks().size(), player.getUUID());
    }

    @Inject(
            method = "handle(Ldev/ftb/mods/ftbchunks/net/RequestChunkChangePacket;Ldev/architectury/networking/NetworkManager$PacketContext;)V",
            at = @At("RETURN"),
            remap = false
    )
    private static void lcClaimEconomy$flushBulkMessages(
            RequestChunkChangePacket message,
            dev.architectury.networking.NetworkManager.PacketContext context,
            CallbackInfo ci
    ) {
        if (context.getPlayer() instanceof ServerPlayer player) {
            ClaimTransferContext.flush(player);
        }
    }
}
