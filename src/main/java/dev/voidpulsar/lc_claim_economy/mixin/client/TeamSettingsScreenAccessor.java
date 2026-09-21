package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the screen's own config group and dirty flag to {@code QueuedStateUiRefresh}, which needs both to re-pull pending values into the open properties screen after a server sync without disturbing edits the player hasn't accepted yet. */
@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen", remap = false)
public interface TeamSettingsScreenAccessor {
    @Accessor(value = "group", remap = false)
    ConfigGroup lcClaimEconomy$rootConfigGroup();

    @Accessor(value = "changed", remap = false)
    boolean lcClaimEconomy$hasUnsavedEdits();
}
