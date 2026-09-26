package dev.voidpulsaryt.lcce.marketplace;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A "sell as country" listing - one price for a whole set of chunks sold together as a single
 * package, the Towny-style "sell the whole town/territory" counterpart to {@link ChunkListing}'s
 * one-chunk-at-a-time sale. Only ever created for state-owned (team-claimed, not already privately
 * owned) chunks belonging to a single team - buying one transfers every chunk in it to the buyer's
 * private ownership at once (the same {@link ChunkOwnership} an ordinary single-chunk purchase
 * creates), for the one combined price.
 */
public final class CountryListing {

    private final UUID id;
    private final UUID sellerId;
    private final boolean sellerIsTeam;
    private final BigInteger price;
    private final BuyerRule buyerRule;
    private final long listedAtTime;
    private final String label;
    private final Set<ChunkDimPos> chunks;

    public CountryListing(UUID id, UUID sellerId, boolean sellerIsTeam, BigInteger price, BuyerRule buyerRule,
                           long listedAtTime, String label, Set<ChunkDimPos> chunks) {
        this.id = id;
        this.sellerId = sellerId;
        this.sellerIsTeam = sellerIsTeam;
        this.price = price;
        this.buyerRule = buyerRule;
        this.listedAtTime = listedAtTime;
        this.label = label;
        this.chunks = chunks;
    }

    public UUID id() { return id; }
    public UUID sellerId() { return sellerId; }
    public boolean sellerIsTeam() { return sellerIsTeam; }
    public BigInteger price() { return price; }
    public BuyerRule buyerRule() { return buyerRule; }
    public long listedAtTime() { return listedAtTime; }
    public String label() { return label; }
    public Set<ChunkDimPos> chunks() { return chunks; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("Seller", sellerId);
        tag.putBoolean("SellerIsTeam", sellerIsTeam);
        tag.putString("Price", price.toString());
        tag.putString("BuyerRule", buyerRule.name());
        tag.putLong("ListedAt", listedAtTime);
        tag.putString("Label", label);

        ListTag chunkList = new ListTag();
        for (ChunkDimPos pos : chunks) {
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.putString("Dim", pos.dimension().location().toString());
            chunkTag.putInt("X", pos.x());
            chunkTag.putInt("Z", pos.z());
            chunkList.add(chunkTag);
        }
        tag.put("Chunks", chunkList);
        return tag;
    }

    public static CountryListing load(CompoundTag tag) {
        BuyerRule rule = BuyerRule.TEAM;
        try {
            rule = BuyerRule.valueOf(tag.getString("BuyerRule"));
        } catch (IllegalArgumentException ignored) {
            // keep default
        }

        Set<ChunkDimPos> chunks = new HashSet<>();
        ListTag chunkList = tag.getList("Chunks", Tag.TAG_COMPOUND);
        for (Tag t : chunkList) {
            CompoundTag chunkTag = (CompoundTag) t;
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(chunkTag.getString("Dim")));
            chunks.add(new ChunkDimPos(dim, new ChunkPos(chunkTag.getInt("X"), chunkTag.getInt("Z"))));
        }

        return new CountryListing(
                tag.getUUID("Id"),
                tag.getUUID("Seller"),
                tag.getBoolean("SellerIsTeam"),
                new BigInteger(tag.getString("Price")),
                rule,
                tag.getLong("ListedAt"),
                tag.getString("Label"),
                chunks
        );
    }
}
