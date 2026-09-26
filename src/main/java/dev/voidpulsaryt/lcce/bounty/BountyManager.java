package dev.voidpulsaryt.lcce.bounty;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.math.BigInteger;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A currency reward on a player's head, paid out in full to whoever kills them. Multiple
 * placements on the same target stack into one total rather than tracking separate bounties, so
 * there's nothing to split or partially claim.
 */
public final class BountyManager extends SavedData {

    private static final String DATA_NAME = "lcce_bounties";

    public static final SavedData.Factory<BountyManager> FACTORY = new SavedData.Factory<>(
            BountyManager::new,
            (tag, registries) -> load(tag),
            null
    );

    private final Map<UUID, BigInteger> bountyByTarget = new HashMap<>();

    public static BountyManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void addBounty(UUID targetId, BigInteger amount) {
        if (amount.signum() <= 0) {
            return;
        }
        bountyByTarget.merge(targetId, amount, BigInteger::add);
        setDirty();
    }

    public BigInteger getBounty(UUID targetId) {
        return bountyByTarget.getOrDefault(targetId, BigInteger.ZERO);
    }

    public BigInteger clearBounty(UUID targetId) {
        BigInteger removed = bountyByTarget.remove(targetId);
        if (removed != null) {
            setDirty();
            return removed;
        }
        return BigInteger.ZERO;
    }

    public Map<UUID, BigInteger> getAllBounties() {
        return Collections.unmodifiableMap(bountyByTarget);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        bountyByTarget.forEach((target, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Target", target);
            entry.putString("Amount", amount.toString());
            list.add(entry);
        });
        tag.put("Bounties", list);
        return tag;
    }

    private static BountyManager load(CompoundTag tag) {
        BountyManager manager = new BountyManager();
        ListTag list = tag.getList("Bounties", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;
            manager.bountyByTarget.put(entry.getUUID("Target"), new BigInteger(entry.getString("Amount")));
        }
        return manager;
    }
}
