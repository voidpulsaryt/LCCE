package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.voidpulsar.lc_claim_economy.client.EditConfigScreenUiHelper;
import dev.voidpulsar.lc_claim_economy.client.QueuedStateUiRefresh;
import dev.voidpulsar.lc_claim_economy.network.PricingRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestLandChunksPayload;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictStateRequestPayload;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FTB's generic config-property editor doubles as this mod's protection-toggle screen
 * (team properties like block-edit/PvP live in the same {@code ftbteamsconfig} group
 * FTB already renders), so this mixin adapts its generic behavior for that specific
 * use: keep it open after Accept instead of FTB's default jump back to the team
 * screen, refresh this mod's own pending-state caches around Accept, skip FTB's
 * unsaved-changes confirmation on Cancel (our own price/pending display already makes
 * the effect of a change clear before it's committed), and make room in the header for
 * {@link EditConfigScreenUiHelper}'s note line. Every behavior is gated on
 * {@code isFtbChunksPropertiesTitle} so a player editing an unrelated FTB config group
 * through this same screen class sees FTB's untouched default behavior.
 */
@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen", remap = false)
public class EditConfigScreenMixin {
    @Shadow(remap = false)
    @Final
    private Component title;
    @Shadow(remap = false)
    private boolean autoclose;
    @Shadow(remap = false)
    private boolean changed;

    @Unique
    private boolean lcClaimEconomy$suppressedAutoclose;

    @Inject(method = "onInit", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$requestPendingState(CallbackInfoReturnable<Boolean> callback) {
        if (Boolean.TRUE.equals(callback.getReturnValue())) {
            PacketDistributor.sendToServer(new PricingRequestPayload());
            PacketDistributor.sendToServer(new QueuedStateRequestPayload());
            PacketDistributor.sendToServer(new RequestLandChunksPayload());
            PacketDistributor.sendToServer(new ConflictStateRequestPayload());
            // Pre-fill from the already-cached pending state right away so the
            // screen shows the queued (new) values from the first frame. The
            // request above refreshes this once the server's reply arrives.
            QueuedStateUiRefresh.syncScreenValues((EditConfigScreen) (Object) this);
        }
    }

    @Inject(method = "doAccept", at = @At("HEAD"), remap = false)
    private void lcClaimEconomy$stayOpenOnAccept(CallbackInfo ci) {
        // Keep the properties screen open after Accept instead of jumping
        // back to the team screen.
        if (title != null && EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(title) && autoclose) {
            autoclose = false;
            lcClaimEconomy$suppressedAutoclose = true;
        }
    }

    @Inject(method = "doAccept", at = @At("TAIL"), remap = false)
    private void lcClaimEconomy$refreshPendingAfterAccept(CallbackInfo ci) {
        if (lcClaimEconomy$suppressedAutoclose) {
            // Restore so Cancel/ESC still closes the screen as usual, and
            // clear the dirty flag so a later Cancel does not warn about
            // unsaved changes that were in fact accepted.
            autoclose = true;
            lcClaimEconomy$suppressedAutoclose = false;
            changed = false;
        }
        PacketDistributor.sendToServer(new PricingRequestPayload());
        PacketDistributor.sendToServer(new QueuedStateRequestPayload());
        PacketDistributor.sendToServer(new RequestLandChunksPayload());
        PacketDistributor.sendToServer(new ConflictStateRequestPayload());
    }

    @Inject(method = "doCancel", at = @At("HEAD"), remap = false)
    private void lcClaimEconomy$discardWithoutConfirm(CallbackInfo ci) {
        // Cancel/ESC should always discard silently instead of asking
        // "Discard unsaved changes?". Clearing the dirty flag up front makes
        // doCancel take the direct close path without the confirmation popup.
        if (title != null && EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(title)) {
            changed = false;
        }
    }

    @Inject(method = "getTopPanelHeight", at = @At("RETURN"), cancellable = true, remap = false)
    private void lcClaimEconomy$expandTopPanelForNote(CallbackInfoReturnable<Integer> callback) {
        if (title != null && EditConfigScreenUiHelper.isFtbChunksPropertiesTitle(title)) {
            callback.setReturnValue(callback.getReturnValue() + EditConfigScreenUiHelper.TOP_PANEL_EXTRA_HEIGHT);
        }
    }
}
