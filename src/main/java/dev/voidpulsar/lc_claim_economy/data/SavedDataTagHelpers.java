package dev.voidpulsar.lc_claim_economy.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Map;
import java.util.UUID;

/**
 * NBT read/write for the {@code Map<UUID, Long>} shapes reused across {@link
 * LcClaimEconomySavedData}'s managers: a "tick map" (any long, including negative/zero -
 * upkeep schedules) and a "bounty map" (positive longs only, zero/negative entries dropped -
 * bounties and war-active-since timestamps).
 */
final class SavedDataTagHelpers {
    private SavedDataTagHelpers() {
    }

    static void loadTickMap(CompoundTag tag, String key, Map<UUID, Long> target) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id")) {
                continue;
            }
            target.put(entry.getUUID("Id"), entry.getLong("Tick"));
        }
    }

    static void saveTickMap(CompoundTag tag, String key, Map<UUID, Long> source) {
        if (source.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> entry : source.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Id", entry.getKey());
            entryTag.putLong("Tick", entry.getValue());
            list.add(entryTag);
        }
        tag.put(key, list);
    }

    static void loadBountyMap(CompoundTag tag, String key, Map<UUID, Long> target) {
        ListTag list = tag.getList(key, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            long copper = entry.getLong("Copper");
            if (copper <= 0L) {
                continue;
            }
            target.put(entry.getUUID("Id"), copper);
        }
    }

    static void saveBountyMap(CompoundTag tag, String key, Map<UUID, Long> source) {
        if (source.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Long> entry : source.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0L) {
                continue;
            }
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("Id", entry.getKey());
            entryTag.putLong("Copper", entry.getValue());
            list.add(entryTag);
        }
        if (!list.isEmpty()) {
            tag.put(key, list);
        }
    }
}
