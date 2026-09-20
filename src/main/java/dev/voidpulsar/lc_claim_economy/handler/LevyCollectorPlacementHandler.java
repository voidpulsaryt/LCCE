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

public class LevyCollectorPlacementHandler {
    @SubscribeEvent
    public void onTaxCollectorPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!isTaxCollector(event.getPlacedBlock())) {
            return;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            return;
        }

        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(
                new ChunkDimPos(level.dimension(), new ChunkPos(event.getPos()))
        );
        if (claimed == null) {
            return;
        }

        Team chunkTeam = claimed.getTeamData().getTeam();
        if (chunkTeam == null || !chunkTeam.isValid()) {
            event.setCanceled(true);
            return;
        }

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            return;
        }

        Team playerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (playerTeam == null || !playerTeam.getTeamId().equals(chunkTeam.getTeamId())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.tax_collector_wrong_team"), true);
            return;
        }

        if (!BankLedgerAccess.canPurchaseForTeam(chunkTeam, player.getUUID())) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.tax_collector_denied"), true);
        }
    }

    private static boolean isTaxCollector(BlockState state) {
        return state.getBlock() instanceof TaxCollectorBlock;
    }
}
