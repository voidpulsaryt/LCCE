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
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!isLevyCollector(event.getPlacedBlock())) {
            return;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        ClaimedChunk targetChunk = FTBChunksAPI.api().getManager().getChunk(
                new ChunkDimPos(serverLevel.dimension(), new ChunkPos(event.getPos()))
        );
        if (targetChunk == null) {
            // Unclaimed ground - nothing here for the claim economy to gate.
            return;
        }

        Team owningTeam = targetChunk.getTeamData().getTeam();
        if (owningTeam == null || !owningTeam.isValid()) {
            event.setCanceled(true);
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer placer)) {
            // Placed by something other than a player (dispenser, mod automation, etc.) -
            // there's no one to bill or notify, so block it outright.
            event.setCanceled(true);
            return;
        }

        Team placerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(placer).orElse(null);
        if (placerTeam == null || !placerTeam.getTeamId().equals(owningTeam.getTeamId())) {
            event.setCanceled(true);
            placer.displayClientMessage(Component.translatable("message.lc_claim_economy.tax_collector_wrong_team"), true);
            return;
        }

        if (!BankLedgerAccess.canPurchaseForTeam(owningTeam, placer.getUUID())) {
            event.setCanceled(true);
            placer.displayClientMessage(Component.translatable("message.lc_claim_economy.tax_collector_denied"), true);
        }
    }

    private static boolean isLevyCollector(BlockState state) {
        return state.getBlock() instanceof TaxCollectorBlock;
    }
}
