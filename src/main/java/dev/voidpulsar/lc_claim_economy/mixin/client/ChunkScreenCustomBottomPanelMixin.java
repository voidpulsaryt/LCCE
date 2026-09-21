package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FTB Chunks' claim-map bottom panel has no extension point for a mod to add its own
 * row, so this paints directly onto it after FTB finishes its own draw. The extra
 * {@code lineHeight} offset drops the price line below whatever FTB itself already
 * drew in that panel on this frame, rather than overlapping it.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen$CustomBottomPanel")
public class ChunkScreenCustomBottomPanelMixin {
    @Inject(method = "drawBackground", at = @At("RETURN"))
    private void lcClaimEconomy$drawClaimPrices(GuiGraphics graphics, Theme theme, int x, int y, int w, int h, CallbackInfo ci) {
        if (!ClientPricingCache.isSynced()) {
            return;
        }

        int lineHeight = theme.getFontHeight() + 2;
        int textY = y + 4 + lineHeight;

        ClientPricingCache.renderBottomPanel(graphics, theme, x + 4, textY);
    }
}
