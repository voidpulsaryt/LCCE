package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.icon.ItemIcon;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.SimpleButton;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import dev.voidpulsar.lc_claim_economy.client.ClientConflictState;
import dev.voidpulsar.lc_claim_economy.client.ConflictIcons;
import dev.voidpulsar.lc_claim_economy.client.gui.ClaimBreakdownScreen;
import dev.voidpulsar.lc_claim_economy.client.gui.ConflictScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds this mod's claim-breakdown and (if wars are enabled) conflict buttons to the
 * right-side toolbar of FTB's team hub screen, to the left of FTB's own settings
 * button. Since the war button only exists when {@code warModuleEnabled}, the slot
 * count in {@code alignWidgets} is computed dynamically rather than assuming a fixed
 * one or two extra buttons - and everything already left of our buttons (invite/ally/
 * chat-toggle) gets shifted over by however much room we actually claimed, so FTB's
 * own buttons never end up overlapping ours regardless of which of ours are present.
 */
@Mixin(value = MyTeamScreen.class, remap = false)
public class TeamHubScreenMixin {
    private static final int TOOLBAR_BUTTON_SIZE = 16;
    private static final int TOOLBAR_BUTTON_Y = 3;
    private static final int TOOLBAR_BUTTON_SPACING = 18;

    @Shadow(remap = false)
    private Button settingsButton;

    @Shadow(remap = false)
    private Button inviteButton;

    @Shadow(remap = false)
    private Button allyButton;

    @Shadow(remap = false)
    private Button toggleChatButton;

    @Unique
    private SimpleButton lcClaimEconomy$warButton;

    @Unique
    private SimpleButton lcClaimEconomy$pricesButton;

    @Inject(method = "addWidgets", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$addWarButton(CallbackInfo ci) {
        MyTeamScreen screen = (MyTeamScreen) (Object) this;

        lcClaimEconomy$pricesButton = new SimpleButton(
                screen,
                Component.translatable("gui.lc_claim_economy.claim_breakdown.title"),
                ItemIcon.getItemIcon(Items.EMERALD),
                (button, mouseButton) -> new ClaimBreakdownScreen(screen).openGui()
        );
        screen.add(lcClaimEconomy$pricesButton);

        if (!ClientConflictState.warModuleEnabled()) {
            lcClaimEconomy$warButton = null;
            return;
        }

        lcClaimEconomy$warButton = new SimpleButton(
                screen,
                Component.translatable("gui.lc_claim_economy.war.title"),
                ConflictIcons.SWORD,
                (button, mouseButton) -> new ConflictScreen(screen).openGui()
        );
        screen.add(lcClaimEconomy$warButton);
    }

    @Inject(method = "alignWidgets", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$alignWarButton(CallbackInfo ci) {
        if (settingsButton == null) {
            return;
        }

        int slot = 1;

        if (lcClaimEconomy$pricesButton != null) {
            lcClaimEconomy$pricesButton.setPosAndSize(
                    settingsButton.getPosX() - TOOLBAR_BUTTON_SPACING * slot,
                    TOOLBAR_BUTTON_Y,
                    TOOLBAR_BUTTON_SIZE,
                    TOOLBAR_BUTTON_SIZE
            );
            slot++;
        }

        if (ClientConflictState.warModuleEnabled() && lcClaimEconomy$warButton != null) {
            lcClaimEconomy$warButton.setPosAndSize(
                    settingsButton.getPosX() - TOOLBAR_BUTTON_SPACING * slot,
                    TOOLBAR_BUTTON_Y,
                    TOOLBAR_BUTTON_SIZE,
                    TOOLBAR_BUTTON_SIZE
            );
            slot++;
        }

        // Make room for our extra button(s) in FTB's right-side toolbar.
        int shiftAmount = TOOLBAR_BUTTON_SPACING * (slot - 1);
        lcClaimEconomy$shiftToolbarButton(inviteButton, shiftAmount);
        lcClaimEconomy$shiftToolbarButton(allyButton, shiftAmount);
        lcClaimEconomy$shiftToolbarButton(toggleChatButton, shiftAmount);
    }

    @Unique
    private void lcClaimEconomy$shiftToolbarButton(Button button, int amount) {
        if (button != null) {
            button.setPosAndSize(
                    button.getPosX() - amount,
                    button.getPosY(),
                    button.getWidth(),
                    button.getHeight()
            );
        }
    }
}
