package dev.voidpulsaryt.lcce.region;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;

import java.util.UUID;

/**
 * Who's allowed to interact with/edit a region: from "anyone" down to "just the people I chose".
 * <p>
 * ALLIES and TEAM map directly onto FTB Teams' own {@link TeamRank} ladder (an "ally" in FTB Teams
 * is a specific player granted ally-level access to a team, not a whole other team), so checking
 * them is just a rank comparison. PRIVATE is region-specific: it falls back to a per-region
 * whitelist since it's meant to carve out a smaller set of people than "the whole team".
 */
public enum PermissionTier {
    PUBLIC,
    ALLIES,
    TEAM,
    PRIVATE;

    public boolean isAllowed(Team owningTeam, UUID playerId, RegionProtectionSettings settings) {
        return switch (this) {
            case PUBLIC -> true;
            case ALLIES -> owningTeam.getRankForPlayer(playerId).isAllyOrBetter();
            case TEAM -> owningTeam.getRankForPlayer(playerId).isMemberOrBetter();
            case PRIVATE -> owningTeam.getRankForPlayer(playerId).isOfficerOrBetter()
                    || settings.privateWhitelist().contains(playerId);
        };
    }
}
