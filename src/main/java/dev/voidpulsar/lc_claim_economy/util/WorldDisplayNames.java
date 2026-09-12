package dev.voidpulsar.lc_claim_economy.util;

import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import net.minecraft.resources.ResourceLocation;

/** Resolves a dimension to the friendly name server owners configured via {@code warpWorldDisplayNames}, or a prettified fallback. */
public final class WorldDisplayNames {
    private WorldDisplayNames() {
    }

    public static String resolve(ResourceLocation dimension) {
        String id = dimension.toString();
        for (String entry : LcClaimEconomyConfig.SERVER.warpWorldDisplayNames.get()) {
            int split = entry.indexOf('=');
            if (split <= 0) {
                continue;
            }
            if (entry.substring(0, split).trim().equals(id)) {
                String name = entry.substring(split + 1).trim();
                if (!name.isEmpty()) {
                    return name;
                }
            }
        }
        return prettify(dimension.getPath());
    }

    private static String prettify(String path) {
        String spaced = path.replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
