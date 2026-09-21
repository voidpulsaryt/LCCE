package dev.voidpulsar.lc_claim_economy.client;

import dev.voidpulsar.lc_claim_economy.network.MarketListingDto;
import dev.voidpulsar.lc_claim_economy.network.SyncMarketPayload;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

public final class ClientMarket {
    private static boolean currentChunkIsClaim;
    private static boolean currentChunkIsOwnClaim;
    private static boolean currentChunkListed;
    private static long currentChunkListingPriceCopper;
    private static List<MarketListingDto> listings = List.of();
    private static Map<String, MarketListingDto> listingsByChunkKey = Map.of();

    private ClientMarket() {
    }

    public static void update(SyncMarketPayload payload) {
        currentChunkIsClaim = payload.currentChunkIsClaim();
        currentChunkIsOwnClaim = payload.currentChunkIsOwnClaim();
        currentChunkListed = payload.currentChunkListed();
        currentChunkListingPriceCopper = payload.currentChunkListingPriceCopper();
        setListings(payload.listings());
    }

    /** Refreshes just the listing list, leaving the current-chunk fields as they were - see {@link dev.voidpulsar.lc_claim_economy.network.SyncMarketListingsPayload}. */
    public static void updateListingsOnly(List<MarketListingDto> newListings) {
        setListings(newListings);
    }

    private static void setListings(List<MarketListingDto> newListings) {
        listings = newListings;
        listingsByChunkKey = newListings.stream()
                .collect(java.util.stream.Collectors.toMap(MarketListingDto::chunkKey, dto -> dto, (a, b) -> a));
    }

    public static boolean currentChunkIsClaim() {
        return currentChunkIsClaim;
    }

    public static boolean currentChunkIsOwnClaim() {
        return currentChunkIsOwnClaim;
    }

    public static boolean currentChunkListed() {
        return currentChunkListed;
    }

    public static long currentChunkListingPriceCopper() {
        return currentChunkListingPriceCopper;
    }

    public static List<MarketListingDto> listings() {
        return listings;
    }

    /** The active listing on this chunk, if any - used by the claim map overlay to color/tooltip for-sale chunks without a linear scan per tile. */
    @Nullable
    public static MarketListingDto listingFor(String chunkKey) {
        return listingsByChunkKey.get(chunkKey);
    }
}
