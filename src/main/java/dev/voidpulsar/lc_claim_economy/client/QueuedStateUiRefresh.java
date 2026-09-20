package dev.voidpulsar.lc_claim_economy.client;

import dev.ftb.mods.ftblibrary.config.ConfigGroup;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen;
import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.mixin.client.TeamSettingsScreenAccessor;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPricing;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class QueuedStateUiRefresh {
    private QueuedStateUiRefresh() {
    }

    public static void refreshOpenScreens() {
        EditConfigScreen configScreen = ClientUtils.getCurrentGuiAs(EditConfigScreen.class);
        if (configScreen != null) {
            configScreen.refreshWidgets();
        }
    }

    /**
     * Like {@link #syncOpenScreenValues(Team)}, but resolves the player's own
     * team first. Used when only the pending state changed (our own packet),
     * which carries no team reference.
     */
    public static void syncSelfTeamOpenScreen() {
        EditConfigScreen screen = ClientUtils.getCurrentGuiAs(EditConfigScreen.class);
        if (screen != null) {
            syncScreenValues(screen);
        }
    }

    /**
     * Pushes the team's current (server-synced) property values into an open
     * properties screen. Without this, the screen keeps showing the value
     * snapshots taken when it was opened, so server-side changes (pending
     * changes applied at upkeep, reverts, resets) only become visible after
     * closing and reopening the menu.
     *
     * <p>Properties with a queued (pending) change are pre-filled with the
     * queued value instead. This keeps the player's queued choice visible and
     * editable, and it lets the server distinguish "untouched" (submits the
     * queued value) from "switched back to the current value" (cancels the
     * queued change) on the next accept.
     */
    public static void syncOpenScreenValues(Team team) {
        EditConfigScreen screen = ClientUtils.getCurrentGuiAs(EditConfigScreen.class);
        if (screen != null) {
            syncScreenValues(screen, team);
        }
    }

    /** Resolves the player's own team and pre-fills the given screen. */
    public static void syncScreenValues(EditConfigScreen screen) {
        Team selfTeam = selfTeam();
        if (selfTeam != null) {
            syncScreenValues(screen, selfTeam);
        }
    }

    public static void syncScreenValues(EditConfigScreen screen, Team team) {
        TeamSettingsScreenAccessor accessor = (TeamSettingsScreenAccessor) (Object) screen;

        // Never overwrite edits the player has made but not yet accepted.
        // Background syncs (upkeep pending-state pushes, property broadcasts
        // from other players' accepts) would otherwise silently reset the
        // clicked values, making every subsequent Accept a no-op.
        if (accessor.lcClaimEconomy$getChanged()) {
            return;
        }

        ConfigGroup root = accessor.lcClaimEconomy$getGroup();
        if (root == null) {
            return;
        }

        // Index configs by the normalized protection key (the property path,
        // e.g. "allow_pvp" / "land_allow_pvp"). The build protections are moved
        // into a custom "build_protection" subgroup, so keying by group id is
        // unreliable; the normalized path is stable regardless of grouping.
        Map<String, ConfigValue<?>> configs = new HashMap<>();
        collectConfigs(root, configs);

        boolean[] changed = {false};
        team.getProperties().forEach((property, propertyValue) -> {
            String key = SafeguardPricing.propertyKey(property);
            if (!SafeguardPriceDisplay.isProtectionPropertyKey(key)) {
                return;
            }
            ConfigValue<?> config = configs.get(key);
            if (config == null) {
                return;
            }

            // Some property types expose a different value type than their
            // screen config works on (e.g. set-backed properties vs a
            // ListConfig). Skip those per entry instead of letting one
            // mismatch abort the sync for all remaining entries.
            try {
                Object desired = ClientQueuedChanges.getDisplayValue(property, propertyValue.getValue());
                if (Objects.equals(config.getValue(), desired)) {
                    return;
                }

                setConfigValue(config, desired);
                changed[0] = true;
            } catch (RuntimeException ignored) {
                // Type mismatch between property value and config; leave the
                // entry as it is.
            }
        });

        if (changed[0]) {
            screen.refreshWidgets();
        }
    }

    @Nullable
    private static Team selfTeam() {
        if (!FTBTeamsAPI.api().isClientManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getClientManager().selfTeam();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setConfigValue(ConfigValue config, Object value) {
        config.setValue(config.copy(value));
    }

    private static void collectConfigs(ConfigGroup group, Map<String, ConfigValue<?>> out) {
        for (ConfigValue<?> value : group.getValues()) {
            String key = SafeguardPriceDisplay.protectionPropertyKey(value.id, value.getPath());
            if (SafeguardPriceDisplay.isProtectionPropertyKey(key)) {
                out.putIfAbsent(key, value);
            }
        }
        for (ConfigGroup subgroup : group.getSubgroups()) {
            collectConfigs(subgroup, out);
        }
    }
}
