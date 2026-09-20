package dev.voidpulsar.lc_claim_economy.teams;

import dev.ftb.mods.ftbteams.api.TeamRank;

import java.util.UUID;

/**
 * FTB party rank to LC team role mapping from the mod spec:
 * owner -> owner, officer -> admin, member -> member.
 */
public final class TeamRankBridge {
    private TeamRankBridge() {
    }

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
