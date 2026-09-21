package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;

/**
 * Snapshot of a war opponent's build-chunk defenses at the moment their war view was
 * built. A team is exposed to attack whenever any one of the three flags is off -
 * public block edit, allowed explosions, or allowed PvP are each individually enough
 * to make raiding possible.
 */
public record ConflictTargetSafeguards(
        boolean blockEditProtected,
        boolean explosionProtected,
        boolean pvpProtected
) {
    public boolean hasWarVulnerability() {
        return !(blockEditProtected && explosionProtected && pvpProtected);
    }

    public static ConflictTargetSafeguards live(Team team) {
        boolean editLocked = team.getProperty(FTBChunksProperties.BLOCK_EDIT_MODE) != PrivacyMode.PUBLIC;
        boolean explosionsBlocked = !team.getProperty(FTBChunksProperties.ALLOW_EXPLOSIONS);
        boolean pvpBlocked = !team.getProperty(FTBChunksProperties.ALLOW_PVP);
        return new ConflictTargetSafeguards(editLocked, explosionsBlocked, pvpBlocked);
    }

    public static ConflictTargetSafeguards allProtected() {
        return new ConflictTargetSafeguards(true, true, true);
    }
}
