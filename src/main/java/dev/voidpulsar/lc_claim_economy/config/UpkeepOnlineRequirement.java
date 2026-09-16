package dev.voidpulsar.lc_claim_economy.config;

/**
 * Governs when a team's (FTB) or claim owner's (OP&C) upkeep countdown is
 * allowed to advance - see {@code LcClaimEconomyConfig.Server#upkeepOnlineRequirement}.
 */
public enum UpkeepOnlineRequirement {
    /** Counts down only while at least one player is online anywhere on the server. */
    ANYONE_ONLINE,
    /** Counts down for a team/owner only while one of its own members is online. */
    TEAM_MEMBER_ONLINE,
    /** Counts down off server uptime alone, regardless of who (if anyone) is online. */
    ALWAYS_CHARGE
}
