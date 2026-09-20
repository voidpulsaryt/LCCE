package dev.voidpulsar.lc_claim_economy.data;

import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Per-team (FTB) and per-owner (OP&C) next-upkeep-tick schedules. Fully independent of team-claim state. */
final class UpkeepScheduleManager {
    private final Map<UUID, Long> nextUpkeepTickByTeam = new HashMap<>();
    private final Map<UUID, Long> nextOpcUpkeepTickByOwner = new HashMap<>();
    private final Runnable markDirty;

    UpkeepScheduleManager(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void load(CompoundTag tag) {
        SavedDataTagHelpers.loadTickMap(tag, "NextUpkeepTickByTeam", nextUpkeepTickByTeam);
        SavedDataTagHelpers.loadTickMap(tag, "NextOpcUpkeepTickByOwner", nextOpcUpkeepTickByOwner);
    }

    void save(CompoundTag tag) {
        SavedDataTagHelpers.saveTickMap(tag, "NextUpkeepTickByTeam", nextUpkeepTickByTeam);
        SavedDataTagHelpers.saveTickMap(tag, "NextOpcUpkeepTickByOwner", nextOpcUpkeepTickByOwner);
    }

    /** Next world-time tick (persisted so it survives server restarts) this FTB team's upkeep should fire at, or -1 if not yet scheduled. */
    long getNextUpkeepTick(UUID teamId) {
        return nextUpkeepTickByTeam.getOrDefault(teamId, -1L);
    }

    void setNextUpkeepTick(UUID teamId, long tick) {
        if (!Long.valueOf(tick).equals(nextUpkeepTickByTeam.get(teamId))) {
            nextUpkeepTickByTeam.put(teamId, tick);
            markDirty.run();
        }
    }

    /** Next world-time tick (persisted so it survives server restarts) this OP&C claim owner's upkeep should fire at, or -1 if not yet scheduled. */
    long getNextOpcUpkeepTick(UUID ownerId) {
        return nextOpcUpkeepTickByOwner.getOrDefault(ownerId, -1L);
    }

    void setNextOpcUpkeepTick(UUID ownerId, long tick) {
        if (!Long.valueOf(tick).equals(nextOpcUpkeepTickByOwner.get(ownerId))) {
            nextOpcUpkeepTickByOwner.put(ownerId, tick);
            markDirty.run();
        }
    }
}
