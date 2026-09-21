package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import dev.voidpulsar.lc_claim_economy.client.ClientConflictState;
import dev.voidpulsar.lc_claim_economy.client.QueuedStateUiRefresh;
import dev.voidpulsar.lc_claim_economy.client.TeamPanelUiRefresh;
import dev.voidpulsar.lc_claim_economy.compat.ModCompat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server's reply to {@link PricingRequestPayload}, and re-sent after anything that could change
 * a displayed number (a claim, a config reload, a protection purchase, an upkeep charge). Feeds
 * both the plain LC Claim Economy HUD/GUIs and, when FTB Library is present, the FTB Chunks/Teams
 * panels this mod hooks into - {@code balanceText} is pre-formatted server-side so neither side
 * needs its own currency-formatting logic.
 */
public record PricingBroadcastPayload(
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
    public static final Type<PricingBroadcastPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_claim_prices"));
    public static final StreamCodec<FriendlyByteBuf, PricingBroadcastPayload> STREAM_CODEC = StreamCodec.of(
            (sink, snapshot) -> {
                sink.writeLong(snapshot.claimPrice);
                sink.writeLong(snapshot.forceLoadUpkeepPrice);
                sink.writeVarInt(snapshot.upkeepPeriodMinutes);
                sink.writeVarInt(snapshot.freeChunks);
                sink.writeVarInt(snapshot.claimedChunks);
                sink.writeBoolean(snapshot.balanceSynced);
                sink.writeBoolean(snapshot.balanceEmpty);
                sink.writeUtf(snapshot.balanceText);
                sink.writeLong(snapshot.mobGriefProtectionPrice);
                sink.writeLong(snapshot.explosionProtectionPrice);
                sink.writeLong(snapshot.pvpDisablePrice);
                sink.writeLong(snapshot.blockInteractProtectionPrice);
                sink.writeLong(snapshot.blockEditProtectionPrice);
                sink.writeLong(snapshot.entityInteractProtectionPrice);
                sink.writeVarInt(snapshot.landChunkGroupSize());
                sink.writeBoolean(snapshot.warEnabled());
            },
            source -> new PricingBroadcastPayload(
                    source.readLong(),
                    source.readLong(),
                    source.readVarInt(),
                    source.readVarInt(),
                    source.readVarInt(),
                    source.readBoolean(),
                    source.readBoolean(),
                    source.readUtf(),
                    source.readLong(),
                    source.readLong(),
                    source.readLong(),
                    source.readLong(),
                    source.readLong(),
                    source.readLong(),
                    source.readVarInt(),
                    source.readBoolean()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(PricingBroadcastPayload snapshot, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            ClientPricingCache.update(
                    snapshot.claimPrice(),
                    snapshot.forceLoadUpkeepPrice(),
                    snapshot.upkeepPeriodMinutes(),
                    snapshot.freeChunks(),
                    snapshot.claimedChunks(),
                    snapshot.balanceSynced(),
                    snapshot.balanceEmpty(),
                    snapshot.balanceText(),
                    snapshot.mobGriefProtectionPrice(),
                    snapshot.explosionProtectionPrice(),
                    snapshot.pvpDisablePrice(),
                    snapshot.blockInteractProtectionPrice(),
                    snapshot.blockEditProtectionPrice(),
                    snapshot.entityInteractProtectionPrice(),
                    snapshot.landChunkGroupSize()
            );
            ClientConflictState.setWarModuleEnabled(snapshot.warEnabled());
            // Screens from ftblibrary/ftbteams are only touched when that optional
            // backend is present - an OP&C-only client may not ship it, and even
            // referencing the types otherwise would throw NoClassDefFoundError.
            if (ModCompat.isFtbAvailable()) {
                QueuedStateUiRefresh.refreshOpenScreens();
                TeamPanelUiRefresh.refreshMyTeamScreenIfOpen();
            }
        });
    }
}
