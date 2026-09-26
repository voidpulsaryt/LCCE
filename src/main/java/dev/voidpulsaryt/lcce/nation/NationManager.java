package dev.voidpulsaryt.lcce.nation;

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
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/** Persists every nation, server-wide - the Towny-style layer of allied teams above a single team. */
public final class NationManager extends SavedData {

    private static final String DATA_NAME = "lcce_nations";

    public static final SavedData.Factory<NationManager> FACTORY = new SavedData.Factory<>(
            NationManager::new,
            (tag, registries) -> load(tag),
            null
    );

    private final Map<UUID, Nation> nationsById = new HashMap<>();
    private final Map<UUID, UUID> nationIdByTeam = new HashMap<>();

    public static NationManager get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Nation createNation(UUID capitalTeamId, String name) {
        Nation nation = Nation.create(capitalTeamId, name);
        nationsById.put(nation.id(), nation);
        nationIdByTeam.put(capitalTeamId, nation.id());
        setDirty();
        return nation;
    }

    public boolean disbandNation(UUID nationId) {
        Nation removed = nationsById.remove(nationId);
        if (removed == null) {
            return false;
        }
        removed.memberTeamIds().forEach(nationIdByTeam::remove);
        setDirty();
        return true;
    }

    @Nullable
    public Nation getNation(UUID nationId) {
        return nationsById.get(nationId);
    }

    /** {@code null} means this team isn't part of any nation. */
    @Nullable
    public Nation getNationForTeam(UUID teamId) {
        UUID nationId = nationIdByTeam.get(teamId);
        return nationId == null ? null : nationsById.get(nationId);
    }

    public Optional<Nation> findNationByName(String name) {
        return nationsById.values().stream().filter(n -> n.name().equalsIgnoreCase(name)).findFirst();
    }

    public List<Nation> getAllNations() {
        return nationsById.values().stream().collect(Collectors.toList());
    }

    /**
     * @return true if the team was added; false if it was already in a (this or another) nation.
     */
    public boolean addMember(Nation nation, UUID teamId) {
        if (nationIdByTeam.containsKey(teamId)) {
            return false;
        }
        nation.memberTeamIds().add(teamId);
        nationIdByTeam.put(teamId, nation.id());
        setDirty();
        return true;
    }

    public void removeMember(Nation nation, UUID teamId) {
        if (nation.memberTeamIds().remove(teamId)) {
            nationIdByTeam.remove(teamId);
            setDirty();
        }
    }

    public void markDirty() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag nationList = new ListTag();
        nationsById.values().forEach(n -> nationList.add(n.save()));
        tag.put("Nations", nationList);
        return tag;
    }

    private static NationManager load(CompoundTag tag) {
        NationManager manager = new NationManager();
        ListTag nationList = tag.getList("Nations", Tag.TAG_COMPOUND);
        for (Tag t : nationList) {
            Nation nation = Nation.load((CompoundTag) t);
            manager.nationsById.put(nation.id(), nation);
            for (UUID teamId : nation.memberTeamIds()) {
                manager.nationIdByTeam.put(teamId, nation.id());
            }
        }
        return manager;
    }
}
