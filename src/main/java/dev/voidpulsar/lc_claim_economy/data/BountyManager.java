package dev.voidpulsar.lc_claim_economy.data;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Escrowed player/team bounties (see {@code /lcce bounty}). Fully independent of team-claim state. */
final class BountyManager {
    private final Map<UUID, Long> playerBounties = new HashMap<>();
    private final Map<UUID, Long> teamBounties = new HashMap<>();
    private final Runnable markDirty;

    BountyManager(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void load(CompoundTag tag) {
        SavedDataTagHelpers.loadBountyMap(tag, "PlayerBounties", playerBounties);
        SavedDataTagHelpers.loadBountyMap(tag, "TeamBounties", teamBounties);
    }

    void save(CompoundTag tag) {
        SavedDataTagHelpers.saveBountyMap(tag, "PlayerBounties", playerBounties);
        SavedDataTagHelpers.saveBountyMap(tag, "TeamBounties", teamBounties);
    }

    /** Adds to (does not replace) any existing bounty on this player, in copper. */
    void addPlayerBounty(UUID victim, long copper) {
        if (copper <= 0L) {
            return;
        }
        playerBounties.merge(victim, copper, Long::sum);
        markDirty.run();
    }

    /** Adds to (does not replace) any existing bounty on this team, in copper. */
    void addTeamBounty(UUID team, long copper) {
        if (copper <= 0L) {
            return;
        }
        teamBounties.merge(team, copper, Long::sum);
        markDirty.run();
    }

    /** Removes and returns the full bounty amount (in copper) on this player, or 0 if none. */
    long takePlayerBounty(UUID victim) {
        Long copper = playerBounties.remove(victim);
        if (copper != null && copper > 0L) {
            markDirty.run();
            return copper;
        }
        return 0L;
    }

    /** Removes and returns the full bounty amount (in copper) on this team, or 0 if none. */
    long takeTeamBounty(UUID team) {
        Long copper = teamBounties.remove(team);
        if (copper != null && copper > 0L) {
            markDirty.run();
            return copper;
        }
        return 0L;
    }

    Map<UUID, Long> playerBounties() {
        return Map.copyOf(playerBounties);
    }

    Map<UUID, Long> teamBounties() {
        return Map.copyOf(teamBounties);
    }
}
