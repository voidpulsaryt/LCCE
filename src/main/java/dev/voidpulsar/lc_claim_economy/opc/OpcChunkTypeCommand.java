package dev.voidpulsar.lc_claim_economy.opc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.teams.OpcRoleMapping;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * OP&C has no dedicated claims screen to hook a UI toggle into the way FTB
 * Chunks' chunk screen mixins do (see {@code ToggleChunkTypePayload} and
 * {@code ClaimMapPanelMixin} on the FTB side), so land/build chunk
 * typing is exposed as a command instead, applied to whichever claimed
 * chunk the executing player is currently standing in. Unlike the FTB
 * side's queued toggle, the change here takes effect immediately rather
 * than being deferred to the next upkeep period.
 */
public final class OpcChunkTypeCommand {
    private OpcChunkTypeCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("opc_chunktype")
                        .then(Commands.literal("land").executes(ctx -> setType(ctx, true)))
                        .then(Commands.literal("build").executes(ctx -> setType(ctx, false)))
                        .then(Commands.literal("status").executes(OpcChunkTypeCommand::showStatus))));
    }

    private static int setType(CommandContext<CommandSourceStack> context, boolean land) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.server;
        Claim claim = resolveOwnedClaim(server, player);
        if (claim == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.opc.chunktype.denied"), false);
            return 0;
        }

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        if (savedData.isProtectionLocked(claim.owner())) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.protection_locked_change"), false);
            return 0;
        }

        savedData.setLandChunk(claim.owner(), claim.chunkKey(), land);
        player.displayClientMessage(Component.translatable(land
                ? "message.lc_claim_economy.opc.chunktype.set_land"
                : "message.lc_claim_economy.opc.chunktype.set_build"), false);
        return 1;
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.server;
        Claim claim = resolveOwnedClaim(server, player);
        if (claim == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.opc.chunktype.denied"), false);
            return 0;
        }

        boolean land = LcClaimEconomySavedData.get(server).isLandChunk(claim.owner(), claim.chunkKey());
        player.displayClientMessage(Component.translatable(land
                ? "message.lc_claim_economy.opc.chunktype.status_land"
                : "message.lc_claim_economy.opc.chunktype.status_build"), false);
        return 1;
    }

    private record Claim(UUID owner, String chunkKey) {
    }

    @Nullable
    private static Claim resolveOwnedClaim(MinecraftServer server, ServerPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        ChunkPos chunkPos = player.chunkPosition();

        IServerClaimsManagerAPI claimsManager = OpenPACServerAPI.get(server).getServerClaimsManager();
        IPlayerChunkClaimAPI claim = claimsManager.get(dimension, chunkPos.x, chunkPos.z);
        if (claim == null) {
            return null;
        }

        UUID owner = claim.getPlayerId();
        if (!isAuthorized(server, player, owner)) {
            return null;
        }
        return new Claim(owner, ChunkCoordKey.encode(dimension, chunkPos.x, chunkPos.z));
    }

    private static boolean isAuthorized(MinecraftServer server, ServerPlayer player, UUID owner) {
        if (owner.equals(player.getUUID())) {
            return true;
        }
        IServerPartyAPI party = OpcAccountResolver.resolveParty(server, owner);
        if (party == null) {
            return false;
        }
        IPartyMemberAPI member = party.getMemberInfo(player.getUUID());
        if (member == null) {
            return false;
        }
        return member.isOwner() || OpcRoleMapping.isLcAdmin(member.getRank(), member.isOwner());
    }
}
