package dev.voidpulsar.lc_claim_economy.client;

import dev.voidpulsar.lc_claim_economy.network.MarketListingDto;
import dev.voidpulsar.lc_claim_economy.network.SyncMarketPayload;

import java.util.List;

public final class ClientMarket {
    private static boolean currentChunkIsClaim;
    private static boolean currentChunkIsOwnClaim;
    private static boolean currentChunkListed;
    private static long currentChunkListingPriceCopper;
    private static List<MarketListingDto> listings = List.of();

    private ClientMarket() {
    }

    public static void update(SyncMarketPayload payload) {
        currentChunkIsClaim = payload.currentChunkIsClaim();
        currentChunkIsOwnClaim = payload.currentChunkIsOwnClaim();
        currentChunkListed = payload.currentChunkListed();
        currentChunkListingPriceCopper = payload.currentChunkListingPriceCopper();
        listings = payload.listings();
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
}
