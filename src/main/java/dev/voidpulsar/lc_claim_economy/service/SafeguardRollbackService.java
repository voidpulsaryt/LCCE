package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.BooleanProperty;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftbteams.api.property.PrivacyProperty;
import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.data.TeamQueuedChanges;
import dev.voidpulsar.lc_claim_economy.teams.LandProperties;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;

/**
 * Suspends and restores protection settings via the shared pendingProperties
 * queue. Paused and user-queued protections use the same state.
 */
public final class SafeguardRollbackService {
    private SafeguardRollbackService() {
    }

    public static boolean isLiveProtectionBillable(Team team, TeamProperty<?> property) {
        if (property instanceof BooleanProperty boolProp) {
            return isBillableBoolean(boolProp, team.getProperty(boolProp));
        }
        if (property instanceof PrivacyProperty privacyProp) {
            return team.getProperty(privacyProp) != PrivacyMode.PUBLIC;
        }
        return false;
    }

    public static boolean isLiveAtMinimum(Team team, TeamProperty<?> property) {
        if (property instanceof BooleanProperty boolProp) {
            return isLiveAtMinimumForBoolean(boolProp, team.getProperty(boolProp));
        }
        if (property instanceof PrivacyProperty privacyProp) {
            return team.getProperty(privacyProp) == PrivacyMode.PUBLIC;
        }
        return true;
    }

    private static boolean isLiveAtMinimumForBoolean(BooleanProperty boolProp, boolean value) {
        if (boolProp == FTBChunksProperties.ALLOW_MOB_GRIEFING
                || boolProp == FTBChunksProperties.ALLOW_EXPLOSIONS
                || boolProp == FTBChunksProperties.ALLOW_PVP) {
            return value;
        }
        return false;
    }

    public static boolean isDismantled(Team team, TeamProperty<?> property, TeamQueuedChanges pendingState) {
        String key = SafeguardPricing.propertyKey(property);
        return pendingState.hasPendingProperty(key) && isLiveAtMinimum(team, property);
    }

    public static boolean hasDismantledProtections(Team team, TeamQueuedChanges pendingState) {
        for (TeamProperty<?> property : SafeguardPricing.PROTECTION_PROPERTIES) {
            if (isDismantled(team, property, pendingState)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasPendingApply(Team team, TeamProperty<?> property, TeamQueuedChanges pendingState) {
        String key = SafeguardPricing.propertyKey(property);
        String serialized = pendingState.pendingProperties().get(key);
        if (serialized == null) {
            return false;
        }
        return !serialized.equals(SafeguardPricing.serializePropertyValue(property, team.getProperty(property)));
    }

    public static Map<String, String> pricingProperties(Team team, TeamQueuedChanges pendingState) {
        Map<String, String> pricing = new HashMap<>();
        for (var entry : pendingState.pendingProperties().entrySet()) {
            TeamProperty<?> property = propertyForKey(entry.getKey());
            if (property != null && !isLiveAtMinimum(team, property)) {
                pricing.put(entry.getKey(), entry.getValue());
            }
        }
        return pricing;
    }

    public static Map<String, String> pricingWithAppliedPending(
            Team team,
            TeamQueuedChanges pendingState,
            TeamProperty<?> applyProperty
    ) {
        Map<String, String> pricing = new HashMap<>(pricingProperties(team, pendingState));
        String key = SafeguardPricing.propertyKey(applyProperty);
        String serialized = pendingState.pendingProperties().get(key);
        if (serialized != null) {
            pricing.put(key, serialized);
        }
        return pricing;
    }

    public static TeamQueuedChanges suspendProtection(
            MinecraftServer server,
            Team team,
            TeamProperty<?> property,
            TeamQueuedChanges pendingState
    ) {
        String key = SafeguardPricing.propertyKey(property);
        TeamQueuedChanges updated = pendingState;
        if (!pendingState.hasPendingProperty(key)) {
            String restoreValue = SafeguardPricing.serializePropertyValue(property, team.getProperty(property));
            updated = pendingState.withPendingProperty(key, restoreValue);
        }
        applyLiveMinimum(team, property);
        return updated;
    }

    public static TeamQueuedChanges restoreProtection(
            MinecraftServer server,
            Team team,
            TeamProperty<?> property,
            TeamQueuedChanges pendingState
    ) {
        String key = SafeguardPricing.propertyKey(property);
        String serialized = pendingState.pendingProperties().get(key);
        if (serialized == null) {
            return pendingState;
        }
        applyPropertyValue(team, property, serialized);
        return pendingState.withoutPendingProperty(key);
    }

    public static TeamProperty<?> propertyForKey(String key) {
        for (TeamProperty<?> property : SafeguardPricing.PROTECTION_PROPERTIES) {
            if (SafeguardPricing.propertyKey(property).equals(key)) {
                return property;
            }
        }
        if (LandProperties.LAND_BLOCK_INTERACT_MODE.getId().getPath().equals(key)) {
            return LandProperties.LAND_BLOCK_INTERACT_MODE;
        }
        if (LandProperties.LAND_BLOCK_EDIT_MODE.getId().getPath().equals(key)) {
            return LandProperties.LAND_BLOCK_EDIT_MODE;
        }
        return null;
    }

    private static boolean isBillableBoolean(BooleanProperty property, boolean value) {
        if (property == FTBChunksProperties.ALLOW_MOB_GRIEFING
                || property == FTBChunksProperties.ALLOW_EXPLOSIONS
                || property == FTBChunksProperties.ALLOW_PVP) {
            return !value;
        }
        return false;
    }

    public static boolean isSerializedMinimum(TeamProperty<?> property, String serialized) {
        if (property instanceof BooleanProperty boolProp) {
            boolean value = SafeguardPricing.deserializePropertyValue(
                    boolProp,
                    serialized,
                    boolProp == FTBChunksProperties.ALLOW_MOB_GRIEFING
                            || boolProp == FTBChunksProperties.ALLOW_EXPLOSIONS
                            || boolProp == FTBChunksProperties.ALLOW_PVP
            );
            return isLiveAtMinimumForBoolean(boolProp, value);
        }
        if (property instanceof PrivacyProperty privacyProp) {
            PrivacyMode value = SafeguardPricing.deserializePropertyValue(
                    privacyProp,
                    serialized,
                    PrivacyMode.PUBLIC
            );
            return value == PrivacyMode.PUBLIC;
        }
        return true;
    }

    public static void revertLiveToMinimum(Team team, TeamProperty<?> property) {
        applyLiveMinimum(team, property);
    }

    private static void applyLiveMinimum(Team team, TeamProperty<?> property) {
        if (property instanceof BooleanProperty boolProp) {
            if (boolProp == FTBChunksProperties.ALLOW_MOB_GRIEFING
                    || boolProp == FTBChunksProperties.ALLOW_EXPLOSIONS
                    || boolProp == FTBChunksProperties.ALLOW_PVP) {
                setAndSync(team, boolProp, true);
                return;
            }
        }
        if (property instanceof PrivacyProperty privacyProp) {
            setAndSync(team, privacyProp, PrivacyMode.PUBLIC);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyPropertyValue(Team team, TeamProperty<?> property, String serialized) {
        T value = SafeguardPricing.deserializePropertyValue(
                (TeamProperty<T>) property,
                serialized,
                team.getProperty((TeamProperty<T>) property)
        );
        setAndSync(team, (TeamProperty<T>) property, value);
    }

    private static <T> void setAndSync(Team team, TeamProperty<T> property, T value) {
        SafeguardEnforcementService.runReverting(() -> {
            team.setProperty(property, value);
            team.syncOnePropertyToTeam(property, value);
        });
    }
}
