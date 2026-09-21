package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientMarket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A background refresh of just {@link ClientMarket}'s listing list - sent when the claim map
 * screen opens so it can draw a "for sale" overlay on listed chunks, and by anything else that
 * wants fresh listings without the side effects {@link SyncMarketPayload} carries (it also
 * updates the current-chunk status and, on the client, pops the market GUI open/forward - neither
 * of which the map screen should trigger just by being opened).
 */
public record SyncMarketListingsPayload(List<MarketListingDto> listings) implements CustomPacketPayload {
    public static final Type<SyncMarketListingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_market_listings"));

    public static final StreamCodec<FriendlyByteBuf, SyncMarketListingsPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.listings.size());
                for (MarketListingDto listing : payload.listings) {
                    buffer.writeUtf(listing.chunkKey());
                    buffer.writeUtf(listing.dimensionDisplay());
                    buffer.writeVarInt(listing.x());
                    buffer.writeVarInt(listing.z());
                    buffer.writeUtf(listing.sellerName());
                    buffer.writeVarLong(listing.priceCopper());
                    buffer.writeBoolean(listing.listedByViewersTeam());
                }
            },
            buffer -> {
                int size = buffer.readVarInt();
                List<MarketListingDto> listings = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    listings.add(new MarketListingDto(
                            buffer.readUtf(),
                            buffer.readUtf(),
                            buffer.readVarInt(),
                            buffer.readVarInt(),
                            buffer.readUtf(),
                            buffer.readVarLong(),
                            buffer.readBoolean()
                    ));
                }
                return new SyncMarketListingsPayload(listings);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncMarketListingsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientMarket.updateListingsOnly(payload.listings));
    }
}
