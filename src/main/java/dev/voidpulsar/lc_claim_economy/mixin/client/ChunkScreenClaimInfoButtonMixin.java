package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.voidpulsar.lc_claim_economy.client.gui.ClaimBreakdownScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds a button to the FTB Chunks map/claim screen itself that opens the same
 * {@link ClaimBreakdownScreen} otherwise only reachable via the emerald button
 * on the My Team screen. This mirrors {@code TeamHubScreenMixin}'s approach,
 * but is injected via {@code onInit} (the same confirmed-working injection
 * point already used by {@code ClaimMapScreenMixin}) rather than addWidgets/
 * alignWidgets, since those method names are not confirmed for this screen.
 * <p>
 * The button is placed at a fixed, self-computed position in the top-left
 * area of the screen rather than aligned relative to FTB Chunks' own
 * trash/edit/info/close buttons, since their exact field names aren't
 * available to this mod's source. If it visually overlaps anything in-game,
 * adjust the x/y offsets below.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreen")
public abstract class ChunkScreenClaimInfoButtonMixin {

    @Unique
    private SimpleButton lcClaimEconomy$claimInfoButton;

    @Inject(method = "onInit", at = @At("RETURN"))
    private void lcClaimEconomy$addClaimInfoButton(CallbackInfoReturnable<Boolean> callback) {
        if (!Boolean.TRUE.equals(callback.getReturnValue())) {
            return;
        }

        BaseScreen screen = (BaseScreen) (Object) this;

        lcClaimEconomy$claimInfoButton = new SimpleButton(
                screen,
                Component.translatable("gui.lc_claim_economy.claim_breakdown.title"),
                ItemIcon.getItemIcon(Items.EMERALD),
                (button, mouseButton) -> new ClaimBreakdownScreen(screen).openGui()
        );
        screen.add(lcClaimEconomy$claimInfoButton);

        // Top-left corner, just below FTB Chunks' own "Unclaim All" (trash)
        // button, clear of the top-right edit/info/close cluster.
        lcClaimEconomy$claimInfoButton.setPosAndSize(4, 24, 16, 16);
    }
}
