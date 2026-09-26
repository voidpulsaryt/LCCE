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
 * Server -> a team's own online members only: that team's current currency balance. FTB Teams'
 * own {@code TeamProperty} system - which {@code TeamBalance} otherwise sits on top of - never
 * syncs a property's value to any client on its own ({@code AbstractTeamBase#setProperty} only
 * updates the in-memory value and marks the team dirty for disk save, confirmed by decompiling FTB
 * Teams itself), so a client has no way to know its own team's balance without a payload like this
 * one. Sent whenever the balance changes (see {@code TeamBalance}) and once on login, so a client
 * that just joined isn't stuck showing a stale or missing value until the next change.
 */
public record TeamBalanceSyncPayload(UUID teamId, long balance) implements CustomPacketPayload {

    public static final Type<TeamBalanceSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LCCEMod.MOD_ID, "team_balance_sync"));

    public static final StreamCodec<ByteBuf, TeamBalanceSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TeamBalanceSyncPayload::teamId,
            ByteBufCodecs.VAR_LONG, TeamBalanceSyncPayload::balance,
            TeamBalanceSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
