package dev.voidpulsar.lc_claim_economy.handler;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.voidpulsar.lc_claim_economy.bank.BankLedgerAccess;
import io.github.lightman314.lightmanscurrency.common.blocks.TaxCollectorBlock;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;

/**
 * A Levy Collector (Lightman's Currency's tax-collector block) is a claim's
 * own upkeep-withdrawal point, so placing one is gated the same way any
 * other claim-economy spend is: it has to land inside land the placer's
 * team actually holds, and the placer needs purchase rights on that team.
 * Both checks require a resolved {@link ClaimedChunk}, which is why the
 * cheap block-type check runs first and the FTB API lookups only happen
 * once we know there's actually a Levy Collector involved.
 */
public class LevyCollectorPlacementHandler {
    @SubscribeEvent
    public void onTaxCollectorPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!isRelevant(event)) {
            return;
        }

        ClaimedChunk claimedChunk = lookupClaimedChunk(event);
        if (claimedChunk == null) {
            // Unclaimed ground - nothing here for the claim economy to gate.
            return;
        }

        gateOnOwnership(event, claimedChunk);
    }

    /** Cheap pre-filter run before any FTB API lookups: wrong world side, wrong block, or FTB not ready yet. */
    private static boolean isRelevant(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return false;
        }
        if (!isLevyCollector(event.getPlacedBlock())) {
            return false;
        }
        return FTBTeamsAPI.api().isManagerLoaded() && FTBChunksAPI.api().isManagerLoaded();
    }

    @Nullable
    private static ClaimedChunk lookupClaimedChunk(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel worldLevel)) {
            return null;
        }
        return FTBChunksAPI.api().getManager().getChunk(
                new ChunkDimPos(worldLevel.dimension(), new ChunkPos(event.getPos()))
        );
    }

    private static void gateOnOwnership(BlockEvent.EntityPlaceEvent event, ClaimedChunk claimedChunk) {
        Team holdingTeam = claimedChunk.getTeamData().getTeam();
        if (holdingTeam == null || !holdingTeam.isValid()) {
            event.setCanceled(true);
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer placingPlayer)) {
            // Placed by something other than a player (dispenser, mod automation, etc.) -
            // there's no one to bill or notify, so block it outright.
            event.setCanceled(true);
            return;
        }

        gateOnPurchaseRights(event, holdingTeam, placingPlayer);
    }

    private static void gateOnPurchaseRights(BlockEvent.EntityPlaceEvent event, Team holdingTeam, ServerPlayer placingPlayer) {
        Team placingTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(placingPlayer).orElse(null);
        boolean sameTeam = placingTeam != null && placingTeam.getTeamId().equals(holdingTeam.getTeamId());
        if (!sameTeam) {
            deny(event, placingPlayer, "message.lc_claim_economy.tax_collector_wrong_team");
            return;
        }

        if (!BankLedgerAccess.canPurchaseForTeam(holdingTeam, placingPlayer.getUUID())) {
            deny(event, placingPlayer, "message.lc_claim_economy.tax_collector_denied");
        }
    }

    private static void deny(BlockEvent.EntityPlaceEvent event, ServerPlayer player, String messageKey) {
        event.setCanceled(true);
        player.displayClientMessage(Component.translatable(messageKey), true);
    }

    private static boolean isLevyCollector(BlockState blockState) {
        return blockState.getBlock() instanceof TaxCollectorBlock;
    }
}
