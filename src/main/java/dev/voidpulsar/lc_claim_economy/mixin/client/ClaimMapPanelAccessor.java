package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChunkScreenPanel.class, remap = false)
public interface ClaimMapPanelAccessor {
    @Accessor("chunkScreen")
    ChunkScreen lcClaimEconomy$hostScreen();
}
