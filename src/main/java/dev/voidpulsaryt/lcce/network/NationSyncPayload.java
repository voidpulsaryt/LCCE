package dev.voidpulsaryt.lcce.network;

import dev.voidpulsaryt.lcce.LCCEMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Server -> a team's own online members only: the name of the nation that team currently belongs
 * to, or an empty string if it isn't in one. Like {@code TeamBalanceSyncPayload}/
 * {@code RegionSyncPayload}, this exists because {@code NationManager} is a plain server-side
 * {@code SavedData} - without this, a client would have no way to know its own team's nation
 * membership for display (e.g. the World Map info popup). Sent on every membership-changing
 * {@code /lcce nation} mutation (create/disband/add/kick/leave) and once on login.
 */
public record NationSyncPayload(UUID teamId, String nationName) implements CustomPacketPayload {

    public static final Type<NationSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCCEMod.MOD_ID, "nation_sync"));

    public static final StreamCodec<ByteBuf, NationSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, NationSyncPayload::teamId,
            ByteBufCodecs.STRING_UTF8, NationSyncPayload::nationName,
            NationSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
