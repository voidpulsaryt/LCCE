package dev.voidpulsaryt.lcce.war;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** Persists every active war, server-wide. */
public final class WarManager extends SavedData {

    private static final String DATA_NAME = "lcce_wars";

    public static final SavedData.Factory<WarManager> FACTORY = new SavedData.Factory<>(
            WarManager::new,
            (tag, registries) -> load(tag),
            null
    );

    private final Map<UUID, War> warsById = new HashMap<>();

    public static WarManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public boolean isAtWar(UUID teamA, UUID teamB) {
        return warsById.values().stream().anyMatch(w ->
                (w.attackerTeamId().equals(teamA) && w.defenderTeamId().equals(teamB))
                        || (w.attackerTeamId().equals(teamB) && w.defenderTeamId().equals(teamA)));
    }

    public War declareWar(UUID attackerTeamId, UUID defenderTeamId, long now, boolean siege) {
        War war = War.declare(attackerTeamId, defenderTeamId, now, siege);
        warsById.put(war.id(), war);
        setDirty();
        return war;
    }

    public boolean endWar(UUID warId) {
        War removed = warsById.remove(warId);
        if (removed != null) {
            setDirty();
            return true;
        }
        return false;
    }

    @Nullable
    public War getWar(UUID warId) {
        return warsById.get(warId);
    }

    public List<War> getAllWars() {
        return List.copyOf(warsById.values());
    }

    public List<War> getWarsInvolving(UUID teamId) {
        return warsById.values().stream().filter(w -> w.involves(teamId)).collect(Collectors.toList());
    }

    public List<War> getOutgoingWars(UUID teamId) {
        return warsById.values().stream().filter(w -> w.attackerTeamId().equals(teamId)).collect(Collectors.toList());
    }

    public List<War> getIncomingWars(UUID teamId) {
        return warsById.values().stream().filter(w -> w.defenderTeamId().equals(teamId)).collect(Collectors.toList());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        warsById.values().forEach(w -> list.add(w.save()));
        tag.put("Wars", list);
        return tag;
    }

    private static WarManager load(CompoundTag tag) {
        WarManager manager = new WarManager();
        ListTag list = tag.getList("Wars", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            War war = War.load((CompoundTag) t);
            manager.warsById.put(war.id(), war);
        }
        return manager;
    }
}
