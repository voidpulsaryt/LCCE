package dev.voidpulsar.lc_claim_economy.network;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.ClientMarket;
import dev.voidpulsar.lc_claim_economy.client.gui.MarketScreen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Pushed to a player both to open the market GUI (a bare {@code /lcce market}) and to refresh it
 * after any action taken from inside it (list/cancel/buy) - {@link #handleClient} opens the
 * screen if it isn't already open, or just rebuilds it in place if it is, mirroring how
 * {@link SyncWarpsPayload} drives {@code WarpListScreen}. The four {@code currentChunk*} fields
 * describe the chunk the player is standing in at the moment this was sent, since every market
 * action (list/cancel/buy) targets that chunk rather than one picked from the browse list.
 */
public record SyncMarketPayload(
        boolean currentChunkIsClaim,
        boolean currentChunkIsOwnClaim,
        boolean currentChunkListed,
        long currentChunkListingPriceCopper,
        List<MarketListingDto> listings
) implements CustomPacketPayload {
    public static final Type<SyncMarketPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LcClaimEconomy.MOD_ID, "sync_market"));

    public static final StreamCodec<FriendlyByteBuf, SyncMarketPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeBoolean(payload.currentChunkIsClaim);
                buffer.writeBoolean(payload.currentChunkIsOwnClaim);
                buffer.writeBoolean(payload.currentChunkListed);
                buffer.writeVarLong(payload.currentChunkListingPriceCopper);
                writeListings(buffer, payload.listings);
            },
            buffer -> new SyncMarketPayload(
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readBoolean(),
                    buffer.readVarLong(),
                    readListings(buffer)
            )
    );

    private static void writeListings(FriendlyByteBuf buffer, List<MarketListingDto> listings) {
        buffer.writeVarInt(listings.size());
        for (MarketListingDto listing : listings) {
            buffer.writeUtf(listing.chunkKey());
            buffer.writeUtf(listing.dimensionDisplay());
            buffer.writeVarInt(listing.x());
            buffer.writeVarInt(listing.z());
            buffer.writeUtf(listing.sellerName());
            buffer.writeVarLong(listing.priceCopper());
            buffer.writeBoolean(listing.listedByViewersTeam());
        }
    }

    private static List<MarketListingDto> readListings(FriendlyByteBuf buffer) {
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
        return listings;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(SyncMarketPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientMarket.update(payload);
            MarketScreen.openOrRefresh();
        });
    }
}
