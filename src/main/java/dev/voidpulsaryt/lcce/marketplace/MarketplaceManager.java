package dev.voidpulsaryt.lcce.marketplace;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persists private chunk ownership and active for-sale listings (single-chunk and country), server-wide. */
public final class MarketplaceManager extends SavedData {

    private static final String DATA_NAME = "lcce_marketplace";

    public static final SavedData.Factory<MarketplaceManager> FACTORY = new SavedData.Factory<>(
            MarketplaceManager::new,
            (tag, registries) -> load(tag),
            null
    );

    private final Map<ChunkDimPos, ChunkOwnership> ownershipByChunk = new HashMap<>();
    private final Map<ChunkDimPos, ChunkListing> listingsByChunk = new HashMap<>();
    private final Map<UUID, CountryListing> countryListingsById = new HashMap<>();
    private final Map<ChunkDimPos, UUID> countryListingByChunk = new HashMap<>();

    public static MarketplaceManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    @Nullable
    public ChunkOwnership getOwnership(ChunkDimPos pos) {
        return ownershipByChunk.get(pos);
    }

    public boolean isPrivatelyOwned(ChunkDimPos pos) {
        return ownershipByChunk.containsKey(pos);
    }

    public void setOwnership(ChunkDimPos pos, ChunkOwnership ownership) {
        ownershipByChunk.put(pos, ownership);
        setDirty();
    }

    public void removeOwnership(ChunkDimPos pos) {
        if (ownershipByChunk.remove(pos) != null) {
            setDirty();
        }
    }

    @Nullable
    public ChunkListing getListing(ChunkDimPos pos) {
        return listingsByChunk.get(pos);
    }

    public void listForSale(ChunkDimPos pos, ChunkListing listing) {
        listingsByChunk.put(pos, listing);
        setDirty();
    }

    public void cancelListing(ChunkDimPos pos) {
        if (listingsByChunk.remove(pos) != null) {
            setDirty();
        }
    }

    /** {@code null} means this chunk isn't part of any "sell as country" package. */
    @Nullable
    public CountryListing getCountryListingAt(ChunkDimPos pos) {
        UUID id = countryListingByChunk.get(pos);
        return id == null ? null : countryListingsById.get(id);
    }

    public void listCountryForSale(CountryListing listing) {
        countryListingsById.put(listing.id(), listing);
        for (ChunkDimPos pos : listing.chunks()) {
            countryListingByChunk.put(pos, listing.id());
        }
        setDirty();
    }

    public void cancelCountryListing(UUID id) {
        CountryListing removed = countryListingsById.remove(id);
        if (removed != null) {
            removed.chunks().forEach(countryListingByChunk::remove);
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag ownershipList = new ListTag();
        ownershipByChunk.forEach((pos, ownership) -> {
            CompoundTag entry = ownership.save();
            entry.put("Pos", savePos(pos));
            ownershipList.add(entry);
        });
        tag.put("Ownership", ownershipList);

        ListTag listingList = new ListTag();
        listingsByChunk.forEach((pos, listing) -> {
            CompoundTag entry = listing.save();
            entry.put("Pos", savePos(pos));
            listingList.add(entry);
        });
        tag.put("Listings", listingList);

        ListTag countryListingList = new ListTag();
        countryListingsById.values().forEach(listing -> countryListingList.add(listing.save()));
        tag.put("CountryListings", countryListingList);

        return tag;
    }

    private static CompoundTag savePos(ChunkDimPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", pos.dimension().location().toString());
        tag.putInt("X", pos.x());
        tag.putInt("Z", pos.z());
        return tag;
    }

    private static ChunkDimPos loadPos(CompoundTag tag) {
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("Dim")));
        return new ChunkDimPos(dim, new ChunkPos(tag.getInt("X"), tag.getInt("Z")));
    }

    private static MarketplaceManager load(CompoundTag tag) {
        MarketplaceManager manager = new MarketplaceManager();

        ListTag ownershipList = tag.getList("Ownership", Tag.TAG_COMPOUND);
        for (Tag t : ownershipList) {
            CompoundTag entry = (CompoundTag) t;
            manager.ownershipByChunk.put(loadPos(entry.getCompound("Pos")), ChunkOwnership.load(entry));
        }

        ListTag listingList = tag.getList("Listings", Tag.TAG_COMPOUND);
        for (Tag t : listingList) {
            CompoundTag entry = (CompoundTag) t;
            manager.listingsByChunk.put(loadPos(entry.getCompound("Pos")), ChunkListing.load(entry));
        }

        ListTag countryListingList = tag.getList("CountryListings", Tag.TAG_COMPOUND);
        for (Tag t : countryListingList) {
            CountryListing listing = CountryListing.load((CompoundTag) t);
            manager.countryListingsById.put(listing.id(), listing);
            for (ChunkDimPos pos : listing.chunks()) {
                manager.countryListingByChunk.put(pos, listing.id());
            }
        }

        return manager;
    }
}
