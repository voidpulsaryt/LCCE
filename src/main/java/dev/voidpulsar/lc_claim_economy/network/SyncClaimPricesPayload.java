package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientClaimPrices;
import dev.voidpulsar.lc_claim_economy.client.ClientWarState;
import dev.voidpulsar.lc_claim_economy.client.PendingStateUiRefresh;
import dev.voidpulsar.lc_claim_economy.client.TeamUiRefresh;
import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SyncClaimPricesPayload(
        long claimPrice,
        long forceLoadUpkeepPrice,
        int upkeepPeriodMinutes,
        int freeChunks,
        int claimedChunks,
        boolean balanceSynced,
        boolean balanceEmpty,
        String balanceText,
        long mobGriefProtectionPrice,
        long explosionProtectionPrice,
        long pvpDisablePrice,
        long blockInteractProtectionPrice,
        long blockEditProtectionPrice,
        long entityInteractProtectionPrice,
        int landChunkGroupSize,
        boolean warEnabled
) implements CustomPacketPayload {
    public static final Type<SyncClaimPricesPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_claim_prices"));
    public static final StreamCodec<FriendlyByteBuf, SyncClaimPricesPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeLong(payload.claimPrice);
                buffer.writeLong(payload.forceLoadUpkeepPrice);
                buffer.writeVarInt(payload.upkeepPeriodMinutes);
                buffer.writeVarInt(payload.freeChunks);
                buffer.writeVarInt(payload.claimedChunks);
                buffer.writeBoolean(payload.balanceSynced);
                buffer.writeBoolean(payload.balanceEmpty);
                buffer.writeUtf(payload.balanceText);
                buffer.writeLong(payload.mobGriefProtectionPrice);
                buffer.writeLong(payload.explosionProtectionPrice);
                buffer.writeLong(payload.pvpDisablePrice);
                buffer.writeLong(payload.blockInteractProtectionPrice);
                buffer.writeLong(payload.blockEditProtectionPrice);
                buffer.writeLong(payload.entityInteractProtectionPrice);
                buffer.writeVarInt(payload.landChunkGroupSize());
                buffer.writeBoolean(payload.warEnabled());
            },
            buffer -> new SyncClaimPricesPayload(
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readVarInt(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readUtf(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readLong(),
                    buffer.readVarInt(),
                    buffer.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncClaimPricesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientClaimPrices.update(
                    payload.claimPrice(),
                    payload.forceLoadUpkeepPrice(),
                    payload.upkeepPeriodMinutes(),
                    payload.freeChunks(),
                    payload.claimedChunks(),
                    payload.balanceSynced(),
                    payload.balanceEmpty(),
                    payload.balanceText(),
                    payload.mobGriefProtectionPrice(),
                    payload.explosionProtectionPrice(),
                    payload.pvpDisablePrice(),
                    payload.blockInteractProtectionPrice(),
                    payload.blockEditProtectionPrice(),
                    payload.entityInteractProtectionPrice(),
                    payload.landChunkGroupSize()
            );
            ClientWarState.setWarModuleEnabled(payload.warEnabled());
            // ftblibrary/ftbteams client screens - only touch these classes when
            // that backend is actually installed (ftblibrary is optional; an
            // OP&C-only client may not have it, and merely referencing these
            // types would throw NoClassDefFoundError).
            if (ModCompat.isFtbAvailable()) {
                PendingStateUiRefresh.refreshOpenScreens();
                TeamUiRefresh.refreshMyTeamScreenIfOpen();
            }
        });
    }
}
