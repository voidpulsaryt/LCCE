package dev.voidpulsaryt.lcce.network;

import dev.ftb.mods.ftblibrary.math.XZ;
import dev.voidpulsaryt.lcce.LCCEMod;
import dev.voidpulsaryt.lcce.marketplace.BuyerRule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client -> server: "sell this whole drag-selection as one country listing" - sent by the World
 * Map's "Sell as Country..." right-click option. The Towny-style counterpart to
 * {@code /lcce market sell}, just for a whole package of chunks at once instead of the one the
 * player is standing in. Like {@link CreateRegionFromSelectionPayload}, chunks carry no dimension
 * of their own - the server resolves them against the sending player's current level - and every
 * one of them is re-validated server-side (must be state-owned land actually claimed by the
 * player's own team, not already listed or privately owned) rather than trusted from the client.
 */
public record SellCountryPayload(String label, BigInteger price, BuyerRule buyerRule, Set<XZ> chunks) implements CustomPacketPayload {

    public static final Type<SellCountryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCCEMod.MOD_ID, "sell_country"));

    public static final StreamCodec<FriendlyByteBuf, SellCountryPayload> STREAM_CODEC = StreamCodec.of(
            SellCountryPayload::write, SellCountryPayload::read
    );

    private static void write(FriendlyByteBuf buf, SellCountryPayload payload) {
        buf.writeUtf(payload.label());
        buf.writeUtf(payload.price().toString());
        buf.writeEnum(payload.buyerRule());
        buf.writeVarInt(payload.chunks().size());
        for (XZ xz : payload.chunks()) {
            buf.writeVarInt(xz.x());
            buf.writeVarInt(xz.z());
        }
    }

    private static SellCountryPayload read(FriendlyByteBuf buf) {
        String label = buf.readUtf();
        String priceStr = buf.readUtf();
        BigInteger price;
        try {
            price = new BigInteger(priceStr);
        } catch (NumberFormatException e) {
            // A malformed price can't crash packet decoding over a bad/modified client - the
            // handler already rejects a non-positive price outright.
            price = BigInteger.ZERO;
        }
        BuyerRule rule = buf.readEnum(BuyerRule.class);
        int count = buf.readVarInt();
        List<XZ> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            chunks.add(XZ.of(buf.readVarInt(), buf.readVarInt()));
        }
        return new SellCountryPayload(label, price, rule, Set.copyOf(chunks));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
