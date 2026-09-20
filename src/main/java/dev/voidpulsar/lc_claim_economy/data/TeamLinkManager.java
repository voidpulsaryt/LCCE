package dev.voidpulsar.lc_claim_economy.data;

import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-team claim state: {@link LcClaimEconomySavedData.TeamLinkEntry} bundles LC-team
 * linking, protection lock, pending changes, land chunks, war targets, and per-chunk
 * permissions into one record on one {@code teamLinks} map, all mutated through the same
 * copy-on-write {@code with*} pattern - plus the peaceful-team set and war-active-since
 * timestamps, which are cross-team-graph concerns that stayed alongside {@code teamLinks}
 * rather than becoming their own manager, since splitting them out would mean either an
 * unsafe NBT restructuring or a new cross-manager dependency back into this map for
 * {@link #setWarTarget} to mutate war targets on two teams' entries at once. This is
 * deliberately the one "big" manager - {@link LcClaimEconomySavedData.TeamLinkEntry}'s many
 * facets are a single cohesive domain, not independent ones bolted together.
 * <p>
 * {@link LcClaimEconomySavedData.TeamLinkEntry} and {@link LcClaimEconomySavedData.MarketListing}
 * stay nested on {@link LcClaimEconomySavedData} itself (not moved here) since several other
 * classes reference {@code LcClaimEconomySavedData.TeamLinkEntry} by that exact name.
 */
final class TeamLinkManager {
    private final Map<UUID, LcClaimEconomySavedData.TeamLinkEntry> teamLinks = new HashMap<>();
    private final Set<UUID> peacefulTeams = new HashSet<>();
    private final Map<UUID, Long> warActiveSinceMillis = new HashMap<>();
    private final Runnable markDirty;

    TeamLinkManager(Runnable markDirty) {
        this.markDirty = markDirty;
    }

    void load(CompoundTag tag, HolderLookup.Provider lookup) {
        if (tag.contains("PeacefulTeams", Tag.TAG_LIST)) {
            ListTag peacefulList = tag.getList("PeacefulTeams", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < peacefulList.size(); i++) {
                peacefulTeams.add(NbtUtils.loadUUID(peacefulList.get(i)));
            }
        }
        SavedDataTagHelpers.loadBountyMap(tag, "WarActiveSince", warActiveSinceMillis);

        ListTag list = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID teamId = entryTag.getUUID("TeamId");
            long lcTeamId = entryTag.contains("LcTeamId", Tag.TAG_LONG) ? entryTag.getLong("LcTeamId") : -1L;
            BankAccount legacyAccount = null;
            if (entryTag.contains("Account", Tag.TAG_COMPOUND)) {
                legacyAccount = new BankAccount(markDirty, entryTag.getCompound("Account"), lookup);
            }
            boolean locked = entryTag.getBoolean("ProtectionLocked");
            TeamQueuedChanges pending = loadPendingState(entryTag);
            Set<String> landChunks = new HashSet<>();
            if (entryTag.contains("LandChunks", Tag.TAG_LIST)) {
                ListTag landList = entryTag.getList("LandChunks", Tag.TAG_STRING);
                for (int j = 0; j < landList.size(); j++) {
                    landChunks.add(landList.getString(j));
                }
            }
            Set<UUID> warTargets = new HashSet<>();
            if (entryTag.contains("WarTargets", Tag.TAG_LIST)) {
                ListTag warList = entryTag.getList("WarTargets", Tag.TAG_INT_ARRAY);
                for (int j = 0; j < warList.size(); j++) {
                    warTargets.add(NbtUtils.loadUUID(warList.get(j)));
                }
            }
            Map<String, Map<UUID, Integer>> chunkUserPermissions = new HashMap<>();
            Map<String, Integer> chunkAllPlayerPermissions = new HashMap<>();
            if (entryTag.contains("ChunkUserPermissions", Tag.TAG_LIST)) {
                ListTag chunks = entryTag.getList("ChunkUserPermissions", Tag.TAG_COMPOUND);
                for (int j = 0; j < chunks.size(); j++) {
                    CompoundTag chunkEntry = chunks.getCompound(j);
                    String chunkKey = chunkEntry.getString("ChunkKey");
                    if (chunkKey.isEmpty()) {
                        continue;
                    }

                    int allFlags = chunkEntry.contains("AllFlags", Tag.TAG_INT) ? chunkEntry.getInt("AllFlags") : 0;
                    if (allFlags > 0) {
                        chunkAllPlayerPermissions.put(chunkKey, allFlags);
                    }

                    Map<UUID, Integer> perPlayer = new HashMap<>();
                    if (chunkEntry.contains("Players", Tag.TAG_LIST)) {
                        ListTag players = chunkEntry.getList("Players", Tag.TAG_COMPOUND);
                        for (int k = 0; k < players.size(); k++) {
                            CompoundTag playerEntry = players.getCompound(k);
                            if (!playerEntry.hasUUID("PlayerId")) {
                                continue;
                            }
                            int flags = playerEntry.getInt("Flags");
                            if (flags <= 0) {
                                continue;
                            }
                            perPlayer.put(playerEntry.getUUID("PlayerId"), flags);
                        }
                    }
                    if (!perPlayer.isEmpty()) {
                        chunkUserPermissions.put(chunkKey, Map.copyOf(perPlayer));
                    }
                }
            }
            teamLinks.put(teamId, new LcClaimEconomySavedData.TeamLinkEntry(
                    teamId,
                    lcTeamId,
                    legacyAccount,
                    locked,
                    pending,
                    landChunks,
                    warTargets,
                    Map.copyOf(chunkUserPermissions),
                    Map.copyOf(chunkAllPlayerPermissions)
            ));
        }
    }

    private static TeamQueuedChanges loadPendingState(CompoundTag entryTag) {
        Map<String, String> pendingProperties = new HashMap<>();
        if (entryTag.contains("PendingProperties", Tag.TAG_COMPOUND)) {
            CompoundTag propertiesTag = entryTag.getCompound("PendingProperties");
            for (String key : propertiesTag.getAllKeys()) {
                pendingProperties.put(key, propertiesTag.getString(key));
            }
        }

        Set<String> pendingLoads = new HashSet<>();
        if (entryTag.contains("PendingForceLoads", Tag.TAG_LIST)) {
            ListTag loads = entryTag.getList("PendingForceLoads", Tag.TAG_STRING);
            for (int i = 0; i < loads.size(); i++) {
                pendingLoads.add(loads.getString(i));
            }
        }

        Set<String> pendingUnloads = new HashSet<>();
        if (entryTag.contains("PendingForceUnloads", Tag.TAG_LIST)) {
            ListTag unloads = entryTag.getList("PendingForceUnloads", Tag.TAG_STRING);
            for (int i = 0; i < unloads.size(); i++) {
                pendingUnloads.add(unloads.getString(i));
            }
        }

        Set<UUID> pendingWarDeclares = new HashSet<>();
        if (entryTag.contains("PendingWarDeclares", Tag.TAG_LIST)) {
            ListTag declares = entryTag.getList("PendingWarDeclares", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < declares.size(); i++) {
                pendingWarDeclares.add(NbtUtils.loadUUID(declares.get(i)));
            }
        }

        Set<UUID> pendingWarEnds = new HashSet<>();
        if (entryTag.contains("PendingWarEnds", Tag.TAG_LIST)) {
            ListTag ends = entryTag.getList("PendingWarEnds", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < ends.size(); i++) {
                pendingWarEnds.add(NbtUtils.loadUUID(ends.get(i)));
            }
        }

        Set<String> pendingLandChunks = new HashSet<>();
        if (entryTag.contains("PendingLandChunks", Tag.TAG_LIST)) {
            ListTag landPending = entryTag.getList("PendingLandChunks", Tag.TAG_STRING);
            for (int i = 0; i < landPending.size(); i++) {
                pendingLandChunks.add(landPending.getString(i));
            }
        }

        Set<String> pendingBuildChunks = new HashSet<>();
        if (entryTag.contains("PendingBuildChunks", Tag.TAG_LIST)) {
            ListTag buildPending = entryTag.getList("PendingBuildChunks", Tag.TAG_STRING);
            for (int i = 0; i < buildPending.size(); i++) {
                pendingBuildChunks.add(buildPending.getString(i));
            }
        }

        if (entryTag.contains("AutoSuspendedWars", Tag.TAG_LIST)) {
            ListTag suspendedWars = entryTag.getList("AutoSuspendedWars", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < suspendedWars.size(); i++) {
                pendingWarDeclares.add(NbtUtils.loadUUID(suspendedWars.get(i)));
            }
        }

        return new TeamQueuedChanges(
                pendingProperties,
                pendingLoads,
                pendingUnloads,
                pendingLandChunks,
                pendingBuildChunks,
                pendingWarDeclares,
                pendingWarEnds
        );
    }

    void save(CompoundTag tag, HolderLookup.Provider lookup) {
        if (!peacefulTeams.isEmpty()) {
            ListTag peacefulList = new ListTag();
            peacefulTeams.forEach(id -> peacefulList.add(NbtUtils.createUUID(id)));
            tag.put("PeacefulTeams", peacefulList);
        }
        SavedDataTagHelpers.saveBountyMap(tag, "WarActiveSince", warActiveSinceMillis);

        ListTag list = new ListTag();
        for (LcClaimEconomySavedData.TeamLinkEntry entry : teamLinks.values()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putUUID("TeamId", entry.ftbTeamId());
            if (entry.lcTeamId() > 0) {
                entryTag.putLong("LcTeamId", entry.lcTeamId());
            }
            if (entry.legacyAccount() != null) {
                entryTag.put("Account", entry.legacyAccount().save(lookup));
            }
            entryTag.putBoolean("ProtectionLocked", entry.protectionLocked());
            savePendingState(entryTag, entry.pendingState());
            if (!entry.landChunks().isEmpty()) {
                ListTag landList = new ListTag();
                entry.landChunks().forEach(key -> landList.add(net.minecraft.nbt.StringTag.valueOf(key)));
                entryTag.put("LandChunks", landList);
            }
            if (!entry.warTargets().isEmpty()) {
                ListTag warList = new ListTag();
                entry.warTargets().forEach(id -> warList.add(NbtUtils.createUUID(id)));
                entryTag.put("WarTargets", warList);
            }
            if (!entry.chunkUserPermissions().isEmpty() || !entry.chunkAllPlayerPermissions().isEmpty()) {
                Set<String> chunkKeys = new HashSet<>(entry.chunkUserPermissions().keySet());
                chunkKeys.addAll(entry.chunkAllPlayerPermissions().keySet());
                ListTag chunkList = new ListTag();
                for (String chunkKey : chunkKeys) {
                    CompoundTag chunkTag = new CompoundTag();
                    chunkTag.putString("ChunkKey", chunkKey);

                    int allFlags = entry.chunkAllPlayerPermissions().getOrDefault(chunkKey, 0);
                    if (allFlags > 0) {
                        chunkTag.putInt("AllFlags", allFlags);
                    }

                    ListTag players = new ListTag();
                    for (Map.Entry<UUID, Integer> playerEntry : entry.chunkUserPermissions().getOrDefault(chunkKey, Map.of()).entrySet()) {
                        int flags = playerEntry.getValue() == null ? 0 : playerEntry.getValue();
                        if (flags <= 0) {
                            continue;
                        }
                        CompoundTag playerTag = new CompoundTag();
                        playerTag.putUUID("PlayerId", playerEntry.getKey());
                        playerTag.putInt("Flags", flags);
                        players.add(playerTag);
                    }
                    if (!players.isEmpty()) {
                        chunkTag.put("Players", players);
                    }
                    if (allFlags > 0 || !players.isEmpty()) {
                        chunkList.add(chunkTag);
                    }
                }
                if (!chunkList.isEmpty()) {
                    entryTag.put("ChunkUserPermissions", chunkList);
                }
            }
            list.add(entryTag);
        }
        tag.put("Teams", list);
    }

    private static void savePendingState(CompoundTag entryTag, TeamQueuedChanges pendingState) {
        if (!pendingState.pendingProperties().isEmpty()) {
            CompoundTag propertiesTag = new CompoundTag();
            pendingState.pendingProperties().forEach(propertiesTag::putString);
            entryTag.put("PendingProperties", propertiesTag);
        }
        if (!pendingState.pendingForceLoads().isEmpty()) {
            ListTag loads = new ListTag();
            pendingState.pendingForceLoads().forEach(key -> loads.add(net.minecraft.nbt.StringTag.valueOf(key)));
            entryTag.put("PendingForceLoads", loads);
        }
        if (!pendingState.pendingForceUnloads().isEmpty()) {
            ListTag unloads = new ListTag();
            pendingState.pendingForceUnloads().forEach(key -> unloads.add(net.minecraft.nbt.StringTag.valueOf(key)));
            entryTag.put("PendingForceUnloads", unloads);
        }
        if (!pendingState.pendingLandChunks().isEmpty()) {
            ListTag landPending = new ListTag();
            pendingState.pendingLandChunks().forEach(key -> landPending.add(net.minecraft.nbt.StringTag.valueOf(key)));
            entryTag.put("PendingLandChunks", landPending);
        }
        if (!pendingState.pendingBuildChunks().isEmpty()) {
            ListTag buildPending = new ListTag();
            pendingState.pendingBuildChunks().forEach(key -> buildPending.add(net.minecraft.nbt.StringTag.valueOf(key)));
            entryTag.put("PendingBuildChunks", buildPending);
        }
        if (!pendingState.pendingWarDeclares().isEmpty()) {
            ListTag declares = new ListTag();
            pendingState.pendingWarDeclares().forEach(id -> declares.add(NbtUtils.createUUID(id)));
            entryTag.put("PendingWarDeclares", declares);
        }
        if (!pendingState.pendingWarEnds().isEmpty()) {
            ListTag ends = new ListTag();
            pendingState.pendingWarEnds().forEach(id -> ends.add(NbtUtils.createUUID(id)));
            entryTag.put("PendingWarEnds", ends);
        }
    }

    LcClaimEconomySavedData.TeamLinkEntry getOrCreateLink(UUID ftbTeamId) {
        return teamLinks.computeIfAbsent(ftbTeamId, id -> {
            markDirty.run();
            return new LcClaimEconomySavedData.TeamLinkEntry(id, -1L, null, false, new TeamQueuedChanges(), Set.of(), Set.of(), Map.of(), Map.of());
        });
    }

    @Nullable
    LcClaimEconomySavedData.TeamLinkEntry get(UUID ftbTeamId) {
        return teamLinks.get(ftbTeamId);
    }

    @Nullable
    LcClaimEconomySavedData.TeamLinkEntry findByLcTeamId(long lcTeamId) {
        if (lcTeamId <= 0) {
            return null;
        }
        for (LcClaimEconomySavedData.TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() == lcTeamId) {
                return entry;
            }
        }
        return null;
    }

    List<LcClaimEconomySavedData.TeamLinkEntry> getAllLinks() {
        return List.copyOf(teamLinks.values());
    }

    @Nullable
    LcClaimEconomySavedData.TeamLinkEntry removeLink(UUID ftbTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry removed = teamLinks.remove(ftbTeamId);
        if (removed != null) {
            markDirty.run();
        }
        return removed;
    }

    boolean clearLcTeamLink(UUID ftbTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry == null || (entry.lcTeamId() <= 0 && entry.legacyAccount() == null)) {
            return false;
        }
        teamLinks.put(ftbTeamId, entry.withLcTeamId(-1L).withLegacyAccount(null));
        markDirty.run();
        return true;
    }

    void removeLinkByLcTeamId(long lcTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = findByLcTeamId(lcTeamId);
        if (entry != null) {
            removeLink(entry.ftbTeamId());
        }
    }

    TeamQueuedChanges getPendingState(UUID ftbTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        return entry == null ? new TeamQueuedChanges() : entry.pendingState();
    }

    void setPendingState(UUID ftbTeamId, TeamQueuedChanges pendingState) {
        LcClaimEconomySavedData.TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        teamLinks.put(ftbTeamId, entry.withPendingState(pendingState));
        markDirty.run();
    }

    void setLcTeamId(UUID ftbTeamId, long lcTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        if (entry.lcTeamId() != lcTeamId) {
            teamLinks.put(ftbTeamId, entry.withLcTeamId(lcTeamId));
            markDirty.run();
        }
    }

    void clearLegacyAccount(UUID ftbTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry != null && entry.legacyAccount() != null) {
            teamLinks.put(ftbTeamId, entry.withLegacyAccount(null));
            markDirty.run();
        }
    }

    void setProtectionLocked(UUID teamId, boolean locked) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry != null && entry.protectionLocked() != locked) {
            teamLinks.put(teamId, entry.withProtectionLocked(locked));
            markDirty.run();
        } else if (entry == null && locked) {
            teamLinks.put(teamId, new LcClaimEconomySavedData.TeamLinkEntry(teamId, -1L, null, true, new TeamQueuedChanges(), Set.of(), Set.of(), Map.of(), Map.of()));
            markDirty.run();
        }
    }

    Set<String> getLandChunks(UUID teamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Set.of() : entry.landChunks();
    }

    boolean isLandChunk(UUID teamId, String chunkKey) {
        return getLandChunks(teamId).contains(chunkKey);
    }

    boolean setLandChunk(UUID teamId, String chunkKey, boolean land) {
        LcClaimEconomySavedData.TeamLinkEntry entry = getOrCreateLink(teamId);
        if (entry.landChunks().contains(chunkKey) == land) {
            return false;
        }
        Set<String> updated = new HashSet<>(entry.landChunks());
        if (land) {
            updated.add(chunkKey);
        } else {
            updated.remove(chunkKey);
        }
        teamLinks.put(teamId, entry.withLandChunks(updated));
        markDirty.run();
        return true;
    }

    boolean clearLandChunk(String chunkKey) {
        boolean changed = false;
        for (LcClaimEconomySavedData.TeamLinkEntry entry : List.copyOf(teamLinks.values())) {
            if (entry.landChunks().contains(chunkKey)) {
                Set<String> updated = new HashSet<>(entry.landChunks());
                updated.remove(chunkKey);
                teamLinks.put(entry.ftbTeamId(), entry.withLandChunks(updated));
                changed = true;
            }
        }
        if (changed) {
            markDirty.run();
        }
        return changed;
    }

    Map<UUID, Integer> getChunkUserPermissions(UUID teamId, String chunkKey) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return Map.of();
        }
        return entry.chunkUserPermissions().getOrDefault(chunkKey, Map.of());
    }

    int getChunkUserPermissionFlags(UUID teamId, String chunkKey, UUID playerId) {
        Integer flags = getChunkUserPermissions(teamId, chunkKey).get(playerId);
        return flags == null ? 0 : flags;
    }

    int getChunkAllPlayerPermissionFlags(UUID teamId, String chunkKey) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return 0;
        }
        Integer flags = entry.chunkAllPlayerPermissions().get(chunkKey);
        return flags == null ? 0 : flags;
    }

    boolean setChunkUserPermissionFlags(UUID teamId, String chunkKey, UUID playerId, int flags) {
        LcClaimEconomySavedData.TeamLinkEntry entry = getOrCreateLink(teamId);
        Map<String, Map<UUID, Integer>> updatedChunks = new HashMap<>(entry.chunkUserPermissions());
        Map<UUID, Integer> existingChunk = updatedChunks.get(chunkKey);
        Map<UUID, Integer> updatedPlayers = new HashMap<>(existingChunk == null ? Map.of() : existingChunk);

        if (flags <= 0) {
            if (updatedPlayers.remove(playerId) == null) {
                return false;
            }
        } else {
            Integer previous = updatedPlayers.put(playerId, flags);
            if (previous != null && previous == flags) {
                return false;
            }
        }

        if (updatedPlayers.isEmpty()) {
            updatedChunks.remove(chunkKey);
        } else {
            updatedChunks.put(chunkKey, Map.copyOf(updatedPlayers));
        }

        teamLinks.put(teamId, entry.withChunkUserPermissions(Map.copyOf(updatedChunks)));
        markDirty.run();
        return true;
    }

    boolean setChunkAllPlayerPermissionFlags(UUID teamId, String chunkKey, int flags) {
        LcClaimEconomySavedData.TeamLinkEntry entry = getOrCreateLink(teamId);
        Map<String, Integer> updated = new HashMap<>(entry.chunkAllPlayerPermissions());

        if (flags <= 0) {
            if (updated.remove(chunkKey) == null) {
                return false;
            }
        } else {
            Integer previous = updated.put(chunkKey, flags);
            if (previous != null && previous == flags) {
                return false;
            }
        }

        teamLinks.put(teamId, entry.withChunkAllPlayerPermissions(Map.copyOf(updated)));
        markDirty.run();
        return true;
    }

    boolean clearChunkUserPermissions(String chunkKey) {
        boolean changed = false;
        for (LcClaimEconomySavedData.TeamLinkEntry entry : List.copyOf(teamLinks.values())) {
            if (entry.chunkUserPermissions().containsKey(chunkKey) || entry.chunkAllPlayerPermissions().containsKey(chunkKey)) {
                Map<String, Map<UUID, Integer>> updated = new HashMap<>(entry.chunkUserPermissions());
                updated.remove(chunkKey);
                Map<String, Integer> updatedAll = new HashMap<>(entry.chunkAllPlayerPermissions());
                updatedAll.remove(chunkKey);
                teamLinks.put(entry.ftbTeamId(), entry.withChunkUserPermissions(Map.copyOf(updated)).withChunkAllPlayerPermissions(Map.copyOf(updatedAll)));
                changed = true;
            }
        }
        if (changed) {
            markDirty.run();
        }
        return changed;
    }

    Set<String> getAllLandChunks() {
        Set<String> all = new HashSet<>();
        for (LcClaimEconomySavedData.TeamLinkEntry entry : teamLinks.values()) {
            all.addAll(entry.landChunks());
        }
        return all;
    }

    boolean isProtectionLocked(UUID teamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(teamId);
        return entry != null && entry.protectionLocked();
    }

    boolean isManagedLcTeam(long lcTeamId) {
        if (lcTeamId <= 0) {
            return false;
        }
        for (LcClaimEconomySavedData.TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() == lcTeamId) {
                return true;
            }
        }
        return false;
    }

    Set<Long> getLinkedLcTeamIds() {
        Set<Long> linkedIds = new HashSet<>();
        for (LcClaimEconomySavedData.TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() > 0) {
                linkedIds.add(entry.lcTeamId());
            }
        }
        return linkedIds;
    }

    boolean isPeaceful(UUID teamId) {
        return peacefulTeams.contains(teamId);
    }

    void setPeaceful(UUID teamId, boolean peaceful) {
        boolean changed = peaceful ? peacefulTeams.add(teamId) : peacefulTeams.remove(teamId);
        if (changed) {
            markDirty.run();
        }
    }

    Set<UUID> getWarTargets(UUID teamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Set.of() : entry.warTargets();
    }

    boolean isAtWarWith(UUID declarerTeamId, UUID targetTeamId) {
        return getWarTargets(declarerTeamId).contains(targetTeamId);
    }

    boolean setWarTarget(UUID declarerTeamId, UUID targetTeamId, boolean atWar) {
        LcClaimEconomySavedData.TeamLinkEntry entry = getOrCreateLink(declarerTeamId);
        Set<UUID> updated = new HashSet<>(entry.warTargets());
        if (atWar) {
            if (!updated.add(targetTeamId)) {
                return false;
            }
        } else if (!updated.remove(targetTeamId)) {
            return false;
        }
        teamLinks.put(declarerTeamId, entry.withWarTargets(Set.copyOf(updated)));
        markDirty.run();
        updateWarActiveSince(declarerTeamId);
        updateWarActiveSince(targetTeamId);
        return true;
    }

    /**
     * Records when a team most recently went from at-peace to at-war (used to gate
     * {@code siegeModeGraceHours}), and clears it once they return to peace. Edge-triggered on the
     * 0-to-nonzero and nonzero-to-0 transitions of {@link #collectWarPartnerIds}, so it stays correct
     * even through {@link dev.voidpulsar.lc_claim_economy.service.ConflictService}'s add-then-immediately-
     * remove affordability probe: that probe's add and remove both run through this same method, so a
     * team already at war sees no transition (timestamp untouched), and a team not at war sees the
     * timestamp set then immediately cleared again, leaving no lasting trace.
     */
    private void updateWarActiveSince(UUID teamId) {
        boolean atWarNow = !collectWarPartnerIds(teamId).isEmpty();
        if (atWarNow) {
            warActiveSinceMillis.putIfAbsent(teamId, System.currentTimeMillis());
        } else {
            warActiveSinceMillis.remove(teamId);
        }
    }

    long getWarActiveSince(UUID teamId) {
        return warActiveSinceMillis.getOrDefault(teamId, 0L);
    }

    Set<UUID> collectWarPartnerIds(UUID teamId) {
        Set<UUID> partners = new HashSet<>();
        partners.addAll(getWarTargets(teamId));
        for (LcClaimEconomySavedData.TeamLinkEntry entry : teamLinks.values()) {
            if (entry.warTargets().contains(teamId)) {
                partners.add(entry.ftbTeamId());
            }
        }
        partners.remove(teamId);
        return partners;
    }

    void clearWarReferences(UUID teamId) {
        boolean changed = false;
        LcClaimEconomySavedData.TeamLinkEntry ownEntry = teamLinks.get(teamId);
        if (ownEntry != null && !ownEntry.warTargets().isEmpty()) {
            teamLinks.put(teamId, ownEntry.withWarTargets(Set.of()));
            changed = true;
        }
        for (LcClaimEconomySavedData.TeamLinkEntry entry : List.copyOf(teamLinks.values())) {
            UUID entryTeamId = entry.ftbTeamId();
            TeamQueuedChanges pending = entry.pendingState();
            TeamQueuedChanges cleaned = pending.withoutWarReferences(teamId);
            if (cleaned != pending) {
                teamLinks.put(entryTeamId, entry.withPendingState(cleaned));
                changed = true;
            }
            if (entry.warTargets().contains(teamId)) {
                Set<UUID> updated = new HashSet<>(entry.warTargets());
                updated.remove(teamId);
                teamLinks.put(entryTeamId, teamLinks.get(entryTeamId).withWarTargets(Set.copyOf(updated)));
                changed = true;
            }
        }
        if (changed) {
            markDirty.run();
        }
    }

    int countIncomingWars(UUID targetTeamId) {
        int count = 0;
        for (LcClaimEconomySavedData.TeamLinkEntry entry : teamLinks.values()) {
            if (entry.warTargets().contains(targetTeamId)) {
                count++;
            }
        }
        return count;
    }
}
