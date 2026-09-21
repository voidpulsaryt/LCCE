package dev.voidpulsar.lc_claim_economy.network;

/** One row of the market browse list: where the chunk is, who's selling it, and for how much. */
public record MarketListingDto(
        String chunkKey,
        String dimensionDisplay,
        int x,
        int z,
        String sellerName,
        long priceCopper,
        boolean listedByViewersTeam
) {
}
