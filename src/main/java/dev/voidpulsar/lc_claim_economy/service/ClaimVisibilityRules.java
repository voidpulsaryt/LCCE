package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;

/** Claim visibility is never billable and never allowed to go private - this mod always forces it public. */
public final class ClaimVisibilityRules {
    public static final String PROPERTY_KEY = "claim_visibility";

    private ClaimVisibilityRules() {
    }

    public static void ensurePublic(Team team) {
        if (team == null || !team.isValid()) {
            return;
        }
        boolean alreadyPublic = team.getProperty(FTBChunksProperties.CLAIM_VISIBILITY) == PrivacyMode.PUBLIC;
        if (!alreadyPublic) {
            team.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PUBLIC);
        }
    }

    public static boolean isClaimVisibilityConfigId(String configId) {
        return PROPERTY_KEY.equals(SafeguardPriceDisplay.normalizePropertyKey(configId));
    }
}
