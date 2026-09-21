package dev.voidpulsar.lc_claim_economy.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.ftb.mods.ftbteams.api.property.TeamPropertyValue;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPricing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tweaks the team properties screen:
 * <ul>
 *   <li>Keep the player on the settings page after accepting (cancel still
 *       returns to the team screen).</li>
 *   <li>Move the FTB Chunks build protection settings out of the generic
 *       "FTB Chunks Properties" section into their own "Build Chunk Protection"
 *       section placed directly below the land protections.</li>
 * </ul>
 */
@Mixin(targets = "dev.ftb.mods.ftbteams.client.gui.MyTeamScreen$SettingsButton", remap = false)
public class TeamHubSettingsButtonMixin {
    private static final String BUILD_GROUP_ID = "build_protection";
    private static final String LAND_GROUP_ID = "lc_claim_economy";

    @Redirect(
            method = "lambda$new$0",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbteams/client/gui/MyTeamScreen;openGui()V"
            ),
            remap = false
    )
    private static void lcClaimEconomy$stayOnSettingsAfterAccept(MyTeamScreen screen, MyTeamScreen capturedScreen, boolean accepted) {
        if (!accepted) {
            screen.openGui();
        }
    }

    @Redirect(
            method = "lambda$new$2",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbteams/api/property/TeamProperty;config(Ldev/ftb/mods/ftblibrary/config/ConfigGroup;Ldev/ftb/mods/ftbteams/api/property/TeamPropertyValue;)Ldev/ftb/mods/ftblibrary/config/ConfigValue;"
            ),
            remap = false
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static ConfigValue<?> lcClaimEconomy$routeBuildProtection(TeamProperty key, ConfigGroup cfg, TeamPropertyValue value) {
        if (!SafeguardPricing.BUILD_PROTECTION_PROPERTIES.contains(key) || cfg.getParent() == null) {
            return key.config(cfg, value);
        }

        ConfigGroup buildGroup = cfg.getParent().getOrCreateSubgroup(BUILD_GROUP_ID);
        ConfigValue<?> configValue = key.config(buildGroup, value);
        if (configValue != null) {
            // Reuse the FTB Chunks translations so the entries keep their
            // familiar names inside the new section.
            configValue.setNameKey("ftbteamsconfig.ftbchunks." + key.getId().getPath());
        }
        return configValue;
    }

    @Inject(
            method = "lambda$new$3",
            at = @At(value = "NEW", target = "dev/ftb/mods/ftblibrary/config/ui/EditConfigScreen"),
            remap = false
    )
    private static void lcClaimEconomy$orderProtectionSections(
            MyTeamScreen screen,
            dev.ftb.mods.ftblibrary.ui.SimpleButton button,
            dev.ftb.mods.ftblibrary.ui.input.MouseButton mouseButton,
            CallbackInfo ci,
            @Local ConfigGroup config
    ) {
        Map<String, ConfigGroup> subgroups = ((ConfigGroupAccessor) config).lcClaimEconomy$getSubgroups();
        if (!subgroups.containsKey(BUILD_GROUP_ID)) {
            return;
        }

        LinkedHashMap<String, ConfigGroup> ordered = new LinkedHashMap<>();
        for (String priorityId : List.of(LAND_GROUP_ID, BUILD_GROUP_ID)) {
            ConfigGroup group = subgroups.get(priorityId);
            if (group != null) {
                ordered.put(priorityId, group);
            }
        }
        ordered.putAll(subgroups);

        subgroups.clear();
        subgroups.putAll(ordered);
    }
}
