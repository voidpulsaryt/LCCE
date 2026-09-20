package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen", remap = false)
public interface TeamSettingsScreenAccessor {
    @Accessor(value = "group", remap = false)
    ConfigGroup lcClaimEconomy$getGroup();

    @Accessor(value = "changed", remap = false)
    boolean lcClaimEconomy$getChanged();
}
