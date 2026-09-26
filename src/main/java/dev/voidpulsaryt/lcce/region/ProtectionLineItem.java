package dev.voidpulsaryt.lcce.region;

import java.util.Optional;

/**
 * One billable line of a region's protection: something the upkeep system can charge for each
 * period, and dismantle (fall back to the unprotected/public default) if the team can't afford it.
 */
public enum ProtectionLineItem {
    MOB_GRIEFING,
    EXPLOSIONS,
    PVP,
    INTERACT_TIER,
    EDIT_TIER;

    public String configKey() {
        return name().toLowerCase();
    }

    public static Optional<ProtectionLineItem> fromConfigKey(String key) {
        for (ProtectionLineItem item : values()) {
            if (item.configKey().equals(key)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }
}
