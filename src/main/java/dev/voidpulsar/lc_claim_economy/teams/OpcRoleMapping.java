package dev.voidpulsar.lc_claim_economy.teams;

import xaero.pac.common.parties.party.member.PartyMemberRank;

/**
 * OP&C party rank to LC team role mapping, mirroring
 * {@link TeamRankBridge}'s owner -&gt; owner, officer -&gt; admin,
 * member -&gt; member scheme.
 * <p>
 * OP&C ranks (lowest to highest, excluding the owner who isn't a rank tier
 * but a separate flag): MEMBER, CLAIMER, MODERATOR, ADMIN. ADMIN is treated
 * as the LC "admin"/officer equivalent; everything else that's still a
 * member is treated as a plain LC member.
 */
public final class OpcRoleMapping {
    private OpcRoleMapping() {
    }

    public static boolean isLcAdmin(PartyMemberRank rank, boolean isOwner) {
        return !isOwner && rank == PartyMemberRank.ADMIN;
    }

    public static boolean isLcMember(PartyMemberRank rank, boolean isOwner) {
        return !isOwner && rank != PartyMemberRank.ADMIN;
    }
}
