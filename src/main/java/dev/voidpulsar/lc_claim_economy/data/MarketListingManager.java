package dev.voidpulsar.lc_claim_economy.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Public chunk-marketplace listings (see {@code /lcce market}). Fully independent of team-claim state. */
final class MarketListingManager {
    private final Map<String, LcClaimEconomySavedData.MarketListing> marketListings = new HashMap<>();
    private final Runnable markDirty;

    MarketListingManager(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void load(CompoundTag tag) {
        ListTag marketList = tag.getList("MarketListings", Tag.TAG_COMPOUND);
        for (int i = 0; i < marketList.size(); i++) {
            CompoundTag listingTag = marketList.getCompound(i);
            String chunkKey = listingTag.getString("ChunkKey");
            if (chunkKey.isEmpty()) {
                continue;
            }
            marketListings.put(chunkKey, new LcClaimEconomySavedData.MarketListing(
                    listingTag.getUUID("SellerTeamId"),
                    listingTag.getString("SellerName"),
                    listingTag.getLong("PriceCopper"),
                    listingTag.getLong("Listed")
            ));
        }
    }

    void save(CompoundTag tag) {
        ListTag marketList = new ListTag();
        for (Map.Entry<String, LcClaimEconomySavedData.MarketListing> entry : marketListings.entrySet()) {
            CompoundTag listingTag = new CompoundTag();
            listingTag.putString("ChunkKey", entry.getKey());
            listingTag.putUUID("SellerTeamId", entry.getValue().sellerTeamId());
            listingTag.putString("SellerName", entry.getValue().sellerName());
            listingTag.putLong("PriceCopper", entry.getValue().priceCopper());
            listingTag.putLong("Listed", entry.getValue().listedAt());
            marketList.add(listingTag);
        }
        tag.put("MarketListings", marketList);
    }

    /** Lists a claimed chunk for sale on the public marketplace, replacing any existing listing for it. */
    void setMarketListing(String chunkKey, LcClaimEconomySavedData.MarketListing listing) {
        marketListings.put(chunkKey, listing);
        markDirty.run();
    }

    @Nullable
    LcClaimEconomySavedData.MarketListing getMarketListing(String chunkKey) {
        return marketListings.get(chunkKey);
    }

    /** Removes a listing (sold, cancelled, or the chunk was unclaimed/lost). Returns false if none existed. */
    boolean removeMarketListing(String chunkKey) {
        boolean removed = marketListings.remove(chunkKey) != null;
        if (removed) {
            markDirty.run();
        }
        return removed;
    }

    Map<String, LcClaimEconomySavedData.MarketListing> getAllMarketListings() {
        return Map.copyOf(marketListings);
    }

    List<LcClaimEconomySavedData.MarketListing> getMarketListingsBySeller(UUID sellerTeamId) {
        return marketListings.values().stream()
                .filter(listing -> listing.sellerTeamId().equals(sellerTeamId))
                .toList();
    }
}
