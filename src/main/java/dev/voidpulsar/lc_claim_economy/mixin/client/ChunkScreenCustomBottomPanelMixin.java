package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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
