package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.ui.TextField;
import dev.voidpulsar.lc_claim_economy.client.EditConfigScreenUiHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Repositions the title label to make room for the price/pending note
 * {@link EditConfigScreenUiHelper} draws underneath it - {@link EditConfigScreenMixin}
 * grows the panel's total height to fit that note, and this shrinks/moves the title
 * row within that taller panel so the two don't overlap. Only applies on the
 * properties screen this mod actually extends; every other use of this generic FTB
 * panel is left at its normal layout.
 */
@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen$CustomTopPanel", remap = false)
public class EditConfigScreenTopPanelMixin {
    @Shadow(remap = false)
    @Final
    EditConfigScreen this$0;

    @Shadow(remap = false)
    TextField titleLabel;

    @Inject(method = "alignWidgets", at = @At("TAIL"), remap = false)
    private void lcClaimEconomy$reserveTitleRow(CallbackInfo ci) {
        if (!EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(this$0.getTitle())) {
            return;
        }
        titleLabel.setPosAndSize(
                4,
                EditConfigScreenUiHelper.TITLE_Y,
                titleLabel.width,
                EditConfigScreenUiHelper.TITLE_HEIGHT
        );
    }
}
