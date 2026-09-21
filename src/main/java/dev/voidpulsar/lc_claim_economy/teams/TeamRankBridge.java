package dev.voidpulsar.lc_claim_economy.teams;

import dev.ftb.mods.ftbteams.api.TeamRank;

import java.util.UUID;

/**
 * LC's team model has exactly three roles (owner/admin/member) while FTB parties have a
 * wider rank ladder; this collapses FTB's ranks onto LC's three so
 * {@link CurrencyTeamLinkService} can mirror party membership into the linked LC team
 * without LC needing to understand FTB's rank system at all. The owner is handled
 * separately by its caller (an FTB party's owner is tracked via
 * {@code Team.getOwner()}, not by rank), which is why every check here excludes the
 * owner explicitly rather than mapping "owner rank" to "LC owner" directly.
 */
public final class TeamRankBridge {
    private TeamRankBridge() {
    }

    /** Whether this rank is high enough that its holder should exist in the linked LC team at all. */
    public static boolean isTrackedMember(TeamRank rank) {
        return rank.isMemberOrBetter();
    }

    public static boolean isLcAdmin(TeamRank rank, UUID playerId, UUID ownerId) {
        return !playerId.equals(ownerId) && rank.isOfficerOrBetter() && !rank.isOwner();
    }

    public static boolean isLcMember(TeamRank rank, UUID playerId, UUID ownerId) {
        return !playerId.equals(ownerId) && rank.isMemberOrBetter() && !rank.isOfficerOrBetter();
    }
}
