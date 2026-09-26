package dev.voidpulsaryt.lcce.marketplace;

import dev.ftb.mods.ftbteams.api.Team;

import java.util.UUID;

/**
 * Who's allowed to buy a listed chunk, relative to the team that originally claimed it (whether
 * the seller is that team itself, listing state-owned land, or a private owner reselling).
 */
public enum BuyerRule {
    EVERYONE,
    ALLIES,
    TEAM;

    public boolean isAllowed(Team originalClaimingTeam, UUID buyerId) {
        return switch (this) {
            case EVERYONE -> true;
            case ALLIES -> originalClaimingTeam.getRankForPlayer(buyerId).isAllyOrBetter();
            case TEAM -> originalClaimingTeam.getRankForPlayer(buyerId).isMemberOrBetter();
        };
    }
}
