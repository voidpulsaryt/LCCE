package dev.voidpulsaryt.lcce.marketplace;

import net.minecraft.nbt.CompoundTag;

import java.math.BigInteger;
import java.util.UUID;

/**
 * An active for-sale listing on a chunk - either an officer selling state-owned (team) land, or a
 * private owner reselling theirs.
 */
public final class ChunkListing {

    private final UUID sellerId;
    private final boolean sellerIsTeam;
    private final BigInteger price;
    private final BuyerRule buyerRule;
    private final long listedAtTime;

    public ChunkListing(UUID sellerId, boolean sellerIsTeam, BigInteger price, BuyerRule buyerRule, long listedAtTime) {
        this.sellerId = sellerId;
        this.sellerIsTeam = sellerIsTeam;
        this.price = price;
        this.buyerRule = buyerRule;
        this.listedAtTime = listedAtTime;
    }

    public UUID sellerId() { return sellerId; }
    public boolean sellerIsTeam() { return sellerIsTeam; }
    public BigInteger price() { return price; }
    public BuyerRule buyerRule() { return buyerRule; }
    public long listedAtTime() { return listedAtTime; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Seller", sellerId);
        tag.putBoolean("SellerIsTeam", sellerIsTeam);
        tag.putString("Price", price.toString());
        tag.putString("BuyerRule", buyerRule.name());
        tag.putLong("ListedAt", listedAtTime);
        return tag;
    }

    public static ChunkListing load(CompoundTag tag) {
        BuyerRule rule = BuyerRule.TEAM;
        try {
            rule = BuyerRule.valueOf(tag.getString("BuyerRule"));
        } catch (IllegalArgumentException ignored) {
            // keep default
        }
        return new ChunkListing(
                tag.getUUID("Seller"),
                tag.getBoolean("SellerIsTeam"),
                new BigInteger(tag.getString("Price")),
                rule,
                tag.getLong("ListedAt")
        );
    }
}
