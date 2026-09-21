package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.misc.AbstractThreePanelScreen;
import dev.voidpulsar.lc_claim_economy.client.EditConfigScreenUiHelper;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code EditConfigScreen$CustomTopPanel} ({@link EditConfigScreenTopPanelMixin})
 * only repositions the title; the actual price/pending text is drawn here instead,
 * on the shared {@code AbstractThreePanelScreen$TopPanel} superclass both FTB Library
 * screens with a top panel inherit from - targeting the superclass instead of FTB
 * Chunks' subclass keeps this mixin working even if FTB Chunks stops overriding
 * {@code drawBackground} itself. The {@code this$0} instanceof check is what actually
 * scopes the effect to just the properties screen despite mixing in at that shared level.
 */
@Mixin(targets = "dev.ftb.mods.ftblibrary.ui.misc.AbstractThreePanelScreen$TopPanel", remap = false)
public class EditConfigScreenParentTopPanelMixin {
    @Shadow(remap = false)
    @Final
    AbstractThreePanelScreen this$0;

    @Inject(method = "drawBackground", at = @At("TAIL"), remap = false)
    private void lcClaimEconomy$drawProtectionPricesNote(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int w,
            int h,
            CallbackInfo ci
    ) {
        if (!(this$0 instanceof EditConfigScreen editScreen)) {
            return;
        }
        if (!EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(editScreen.getTitle())) {
            return;
        }
        EditConfigScreenUiHelper.drawProtectionPricesNote(graphics, theme, x, y, w);
    }
}
