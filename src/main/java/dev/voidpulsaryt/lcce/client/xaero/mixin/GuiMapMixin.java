package dev.voidpulsaryt.lcce.client.xaero.mixin;

import dev.voidpulsaryt.lcce.client.xaero.WorldMapClaimMenu;
import dev.voidpulsaryt.lcce.client.xaero.WorldMapInfoPanel;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;
import xaero.map.gui.MapTileSelection;
import xaero.map.gui.dropdown.rightclick.RightClickOption;

import java.util.ArrayList;

/**
 * Appends this mod's own claim/unclaim/force-load options to Xaero World Map's right-click menu.
 * {@code GuiMap#getRightClickOptions()} builds and returns its list of options with no other
 * extension point (confirmed by decompiling the real jar - the same reason the real
 * {@code ftbxaerocompat} mod also needs a Mixin here, though it captures the local {@code options}
 * variable mid-method via a MixinExtras {@code @Local} instead). Injecting at the method's single
 * {@code return options;} statement and mutating the about-to-be-returned list directly needs
 * nothing beyond vanilla Mixin, avoiding a MixinExtras dependency.
 * <p>
 * {@code targets} (a string, not a {@code GuiMap.class} literal) so this class loads - and simply
 * doesn't apply - on a client without Xaero's World Map installed, since it's an optional
 * dependency for the rest of this mod.
 */
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public class GuiMapMixin {

    @Shadow
    private MapTileSelection mapTileSelection;

    @Shadow
    private int mouseBlockPosX;

    @Shadow
    private int mouseBlockPosZ;

    @Inject(method = "getRightClickOptions", at = @At("RETURN"), remap = false)
    private void lcce$addClaimOptions(CallbackInfoReturnable<ArrayList<RightClickOption>> cir) {
        ArrayList<RightClickOption> options = cir.getReturnValue();
        if (options != null) {
            WorldMapClaimMenu.addOptions((GuiMap) (Object) this, options, this.mapTileSelection);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), remap = false)
    private void lcce$renderInfoPanel(GuiGraphics guiGraphics, int scaledMouseX, int scaledMouseY, float delta, CallbackInfo ci) {
        WorldMapInfoPanel.render(guiGraphics, this.mouseBlockPosX, this.mouseBlockPosZ);
    }
}
