package dev.voidpulsar.lc_claim_economy.data;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LcClaimEconomySavedData extends SavedData {
    private static final String DATA_NAME = LcClaimEconomy.MOD_ID + "_team_accounts";

    private final Map<UUID, TeamLinkEntry> teamLinks = new HashMap<>();
    private final Set<UUID> peacefulTeams = new HashSet<>();
    private final Map<UUID, Long> warActiveSinceMillis = new HashMap<>();
    private boolean pioneerClaimGranted = false;
    private final Map<UUID, Long> playerBounties = new HashMap<>();
    private final Map<UUID, Long> teamBounties = new HashMap<>();
    private long nextUpkeepTick = -1L;
    private long nextOpcUpkeepTick = -1L;

    private final Map<String, MarketListing> marketListings = new HashMap<>();
    private final Map<UUID, Map<String, WarpEntry>> playerWarps = new HashMap<>();

    private long statUpkeepChargedCopper = 0L;
    private int statUpkeepChargedCount = 0;
    private int statUpkeepMissedCount = 0;
    private long statClaimSpendCopper = 0L;
    private int statClaimCount = 0;
    private long statUnclaimRefundCopper = 0L;
    private int statUnclaimCount = 0;
    private long statMarketVolumeCopper = 0L;
    private int statMarketSaleCount = 0;

    public static LcClaimEconomySavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(LcClaimEconomySavedData::new, LcClaimEconomySavedData::load), DATA_NAME);
    }

    private static LcClaimEconomySavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        LcClaimEconomySavedData data = new LcClaimEconomySavedData();
        data.pioneerClaimGranted = tag.getBoolean("PioneerClaimGranted");
        data.nextUpkeepTick = tag.contains("NextUpkeepTick", Tag.TAG_LONG) ? tag.getLong("NextUpkeepTick") : -1L;
        data.nextOpcUpkeepTick = tag.contains("NextOpcUpkeepTick", Tag.TAG_LONG) ? tag.getLong("NextOpcUpkeepTick") : -1L;
        loadBountyMap(tag, "PlayerBounties", data.playerBounties);
        loadBountyMap(tag, "TeamBounties", data.teamBounties);

        if (tag.contains("PeacefulTeams", Tag.TAG_LIST)) {
            ListTag peacefulList = tag.getList("PeacefulTeams", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < peacefulList.size(); i++) {
                data.peacefulTeams.add(net.minecraft.nbt.NbtUtils.loadUUID(peacefulList.get(i)));
            }
        }
        loadBountyMap(tag, "WarActiveSince", data.warActiveSinceMillis);

        data.statUpkeepChargedCopper = tag.getLong("StatUpkeepChargedCopper");
        data.statUpkeepChargedCount = tag.getInt("StatUpkeepChargedCount");
        data.statUpkeepMissedCount = tag.getInt("StatUpkeepMissedCount");
        data.statClaimSpendCopper = tag.getLong("StatClaimSpendCopper");
        data.statClaimCount = tag.getInt("StatClaimCount");
        data.statUnclaimRefundCopper = tag.getLong("StatUnclaimRefundCopper");
        data.statUnclaimCount = tag.getInt("StatUnclaimCount");
        data.statMarketVolumeCopper = tag.getLong("StatMarketVolumeCopper");
        data.statMarketSaleCount = tag.getInt("StatMarketSaleCount");

        ListTag marketList = tag.getList("MarketListings", Tag.TAG_COMPOUND);
        for (int i = 0; i < marketList.size(); i++) {
            CompoundTag listingTag = marketList.getCompound(i);
            String chunkKey = listingTag.getString("ChunkKey");
            if (chunkKey.isEmpty()) {
                continue;
            }
            data.marketListings.put(chunkKey, new MarketListing(
                    listingTag.getUUID("SellerTeamId"),
                    listingTag.getString("SellerName"),
                    listingTag.getLong("PriceCopper"),
                    listingTag.getLong("Listed")
            ));
        }
        ListTag warpList = tag.getList("PlayerWarps", Tag.TAG_COMPOUND);
        for (int i = 0; i < warpList.size(); i++) {
            CompoundTag entryTag = warpList.getCompound(i);
            if (!entryTag.hasUUID("OwnerId") || entryTag.getString("Name").isEmpty()) {
                continue;
            }
            Set<String> aliases = new HashSet<>();
            if (entryTag.contains("Aliases", Tag.TAG_LIST)) {
                ListTag aliasList = entryTag.getList("Aliases", Tag.TAG_STRING);
                for (int j = 0; j < aliasList.size(); j++) {
                    aliases.add(aliasList.getString(j));
                }
            }
            WarpEntry entry = new WarpEntry(
                    entryTag.getString("Name"),
                    entryTag.getUUID("OwnerId"),
                    entryTag.getString("OwnerName"),
                    ResourceLocation.parse(entryTag.getString("Dimension")),
                    entryTag.getDouble("X"),
                    entryTag.getDouble("Y"),
                    entryTag.getDouble("Z"),
                    entryTag.getFloat("Yaw"),
                    entryTag.getFloat("Pitch"),
                    entryTag.getString("ChunkKey"),
                    entryTag.getBoolean("Public"),
                    entryTag.getLong("Created"),
                    Set.copyOf(aliases)
            );
            data.playerWarps.computeIfAbsent(entry.ownerId(), id -> new HashMap<>())
                    .put(entry.name().toLowerCase(Locale.ROOT), entry);
        }

        ListTag list = tag.getList("Teams", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entryTag = list.getCompound(i);
            UUID teamId = entryTag.getUUID("TeamId");
            long lcTeamId = entryTag.contains("LcTeamId", Tag.TAG_LONG) ? entryTag.getLong("LcTeamId") : -1L;
            BankAccount legacyAccount = null;
            if (entryTag.contains("Account", Tag.TAG_COMPOUND)) {
                legacyAccount = new BankAccount(() -> data.setDirty(), entryTag.getCompound("Account"), lookup);
            }
            boolean locked = entryTag.getBoolean("ProtectionLocked");
            TeamPendingState pending = loadPendingState(entryTag);
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
                    warTargets.add(net.minecraft.nbt.NbtUtils.loadUUID(warList.get(j)));
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
            data.teamLinks.put(teamId, new TeamLinkEntry(
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
        return data;
    }

    private static void loadBountyMap(CompoundTag tag, String key, Map<UUID, Long> target) {
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

    private static TeamPendingState loadPendingState(CompoundTag entryTag) {
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
                pendingWarDeclares.add(net.minecraft.nbt.NbtUtils.loadUUID(declares.get(i)));
            }
        }

        Set<UUID> pendingWarEnds = new HashSet<>();
        if (entryTag.contains("PendingWarEnds", Tag.TAG_LIST)) {
            ListTag ends = entryTag.getList("PendingWarEnds", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < ends.size(); i++) {
                pendingWarEnds.add(net.minecraft.nbt.NbtUtils.loadUUID(ends.get(i)));
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
                pendingWarDeclares.add(net.minecraft.nbt.NbtUtils.loadUUID(suspendedWars.get(i)));
            }
        }

        return new TeamPendingState(
                pendingProperties,
                pendingLoads,
                pendingUnloads,
                pendingLandChunks,
                pendingBuildChunks,
                pendingWarDeclares,
                pendingWarEnds
        );
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("PioneerClaimGranted", pioneerClaimGranted);
        if (nextUpkeepTick >= 0L) {
            tag.putLong("NextUpkeepTick", nextUpkeepTick);
        }
        if (nextOpcUpkeepTick >= 0L) {
            tag.putLong("NextOpcUpkeepTick", nextOpcUpkeepTick);
        }
        saveBountyMap(tag, "PlayerBounties", playerBounties);
        saveBountyMap(tag, "TeamBounties", teamBounties);

        if (!peacefulTeams.isEmpty()) {
            ListTag peacefulList = new ListTag();
            peacefulTeams.forEach(id -> peacefulList.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            tag.put("PeacefulTeams", peacefulList);
        }
        saveBountyMap(tag, "WarActiveSince", warActiveSinceMillis);

        tag.putLong("StatUpkeepChargedCopper", statUpkeepChargedCopper);
        tag.putInt("StatUpkeepChargedCount", statUpkeepChargedCount);
        tag.putInt("StatUpkeepMissedCount", statUpkeepMissedCount);
        tag.putLong("StatClaimSpendCopper", statClaimSpendCopper);
        tag.putInt("StatClaimCount", statClaimCount);
        tag.putLong("StatUnclaimRefundCopper", statUnclaimRefundCopper);
        tag.putInt("StatUnclaimCount", statUnclaimCount);
        tag.putLong("StatMarketVolumeCopper", statMarketVolumeCopper);
        tag.putInt("StatMarketSaleCount", statMarketSaleCount);

        ListTag marketList = new ListTag();
        for (Map.Entry<String, MarketListing> entry : marketListings.entrySet()) {
            CompoundTag listingTag = new CompoundTag();
            listingTag.putString("ChunkKey", entry.getKey());
            listingTag.putUUID("SellerTeamId", entry.getValue().sellerTeamId());
            listingTag.putString("SellerName", entry.getValue().sellerName());
            listingTag.putLong("PriceCopper", entry.getValue().priceCopper());
            listingTag.putLong("Listed", entry.getValue().listedAt());
            marketList.add(listingTag);
        }
        tag.put("MarketListings", marketList);

        ListTag warpList = new ListTag();
        for (Map<String, WarpEntry> warps : playerWarps.values()) {
            for (WarpEntry entry : warps.values()) {
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("Name", entry.name());
                entryTag.putUUID("OwnerId", entry.ownerId());
                entryTag.putString("OwnerName", entry.ownerName());
                entryTag.putString("Dimension", entry.dimension().toString());
                entryTag.putDouble("X", entry.x());
                entryTag.putDouble("Y", entry.y());
                entryTag.putDouble("Z", entry.z());
                entryTag.putFloat("Yaw", entry.yaw());
                entryTag.putFloat("Pitch", entry.pitch());
                entryTag.putString("ChunkKey", entry.chunkKey());
                entryTag.putBoolean("Public", entry.isPublic());
                entryTag.putLong("Created", entry.createdAtMillis());
                if (!entry.aliases().isEmpty()) {
                    ListTag aliasList = new ListTag();
                    entry.aliases().forEach(alias -> aliasList.add(StringTag.valueOf(alias)));
                    entryTag.put("Aliases", aliasList);
                }
                warpList.add(entryTag);
            }
        }
        tag.put("PlayerWarps", warpList);

        ListTag list = new ListTag();
        for (TeamLinkEntry entry : teamLinks.values()) {
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
                entry.landChunks().forEach(key -> landList.add(StringTag.valueOf(key)));
                entryTag.put("LandChunks", landList);
            }
            if (!entry.warTargets().isEmpty()) {
                ListTag warList = new ListTag();
                entry.warTargets().forEach(id -> warList.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
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
        return tag;
    }

    private static void saveBountyMap(CompoundTag tag, String key, Map<UUID, Long> source) {
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

    /** Adds to (does not replace) any existing bounty on this player, in copper. */
    public void addPlayerBounty(UUID victim, long copper) {
        if (copper <= 0L) {
            return;
        }
        playerBounties.merge(victim, copper, Long::sum);
        setDirty();
    }

    /** Adds to (does not replace) any existing bounty on this team, in copper. */
    public void addTeamBounty(UUID team, long copper) {
        if (copper <= 0L) {
            return;
        }
        teamBounties.merge(team, copper, Long::sum);
        setDirty();
    }

    /** Removes and returns the full bounty amount (in copper) on this player, or 0 if none. */
    public long takePlayerBounty(UUID victim) {
        Long copper = playerBounties.remove(victim);
        if (copper != null && copper > 0L) {
            setDirty();
            return copper;
        }
        return 0L;
    }

    /** Removes and returns the full bounty amount (in copper) on this team, or 0 if none. */
    public long takeTeamBounty(UUID team) {
        Long copper = teamBounties.remove(team);
        if (copper != null && copper > 0L) {
            setDirty();
            return copper;
        }
        return 0L;
    }

    public Map<UUID, Long> playerBounties() {
        return Map.copyOf(playerBounties);
    }

    public Map<UUID, Long> teamBounties() {
        return Map.copyOf(teamBounties);
    }

    /**
     * Marks the server-wide Pioneer Bonus as claimed and returns true, the
     * one time this is ever called successfully - every call after the
     * first (on this server, forever) returns false. Callers use this to
     * gate a one-time reward for whoever claims the very first chunk ever
     * claimed on the server.
     */
    public boolean claimPioneerBonus() {
        if (pioneerClaimGranted) {
            return false;
        }
        pioneerClaimGranted = true;
        setDirty();
        return true;
    }

    /** Next world-time tick (persisted so it survives server restarts) the FTB upkeep loop should fire at, or -1 if not yet scheduled. */
    public long getNextUpkeepTick() {
        return nextUpkeepTick;
    }

    public void setNextUpkeepTick(long tick) {
        if (this.nextUpkeepTick != tick) {
            this.nextUpkeepTick = tick;
            setDirty();
        }
    }

    /** Next world-time tick (persisted so it survives server restarts) the OP&C upkeep loop should fire at, or -1 if not yet scheduled. */
    public long getNextOpcUpkeepTick() {
        return nextOpcUpkeepTick;
    }

    public void setNextOpcUpkeepTick(long tick) {
        if (this.nextOpcUpkeepTick != tick) {
            this.nextOpcUpkeepTick = tick;
            setDirty();
        }
    }

    private static void savePendingState(CompoundTag entryTag, TeamPendingState pendingState) {
        if (!pendingState.pendingProperties().isEmpty()) {
            CompoundTag propertiesTag = new CompoundTag();
            pendingState.pendingProperties().forEach(propertiesTag::putString);
            entryTag.put("PendingProperties", propertiesTag);
        }
        if (!pendingState.pendingForceLoads().isEmpty()) {
            ListTag loads = new ListTag();
            pendingState.pendingForceLoads().forEach(key -> loads.add(StringTag.valueOf(key)));
            entryTag.put("PendingForceLoads", loads);
        }
        if (!pendingState.pendingForceUnloads().isEmpty()) {
            ListTag unloads = new ListTag();
            pendingState.pendingForceUnloads().forEach(key -> unloads.add(StringTag.valueOf(key)));
            entryTag.put("PendingForceUnloads", unloads);
        }
        if (!pendingState.pendingLandChunks().isEmpty()) {
            ListTag landPending = new ListTag();
            pendingState.pendingLandChunks().forEach(key -> landPending.add(StringTag.valueOf(key)));
            entryTag.put("PendingLandChunks", landPending);
        }
        if (!pendingState.pendingBuildChunks().isEmpty()) {
            ListTag buildPending = new ListTag();
            pendingState.pendingBuildChunks().forEach(key -> buildPending.add(StringTag.valueOf(key)));
            entryTag.put("PendingBuildChunks", buildPending);
        }
        if (!pendingState.pendingWarDeclares().isEmpty()) {
            ListTag declares = new ListTag();
            pendingState.pendingWarDeclares().forEach(id -> declares.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            entryTag.put("PendingWarDeclares", declares);
        }
        if (!pendingState.pendingWarEnds().isEmpty()) {
            ListTag ends = new ListTag();
            pendingState.pendingWarEnds().forEach(id -> ends.add(net.minecraft.nbt.NbtUtils.createUUID(id)));
            entryTag.put("PendingWarEnds", ends);
        }
    }

    public TeamLinkEntry getOrCreateLink(UUID ftbTeamId) {
        return teamLinks.computeIfAbsent(ftbTeamId, id -> {
            setDirty();
            return new TeamLinkEntry(id, -1L, null, false, new TeamPendingState(), Set.of(), Set.of(), Map.of(), Map.of());
        });
    }

    @Nullable
    public TeamLinkEntry get(UUID ftbTeamId) {
        return teamLinks.get(ftbTeamId);
    }

    @Nullable
    public TeamLinkEntry findByLcTeamId(long lcTeamId) {
        if (lcTeamId <= 0) {
            return null;
        }
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() == lcTeamId) {
                return entry;
            }
        }
        return null;
    }

    public java.util.Collection<TeamLinkEntry> getAllLinks() {
        return java.util.List.copyOf(teamLinks.values());
    }

    @Nullable
    public TeamLinkEntry removeLink(UUID ftbTeamId) {
        TeamLinkEntry removed = teamLinks.remove(ftbTeamId);
        if (removed != null) {
            setDirty();
        }
        return removed;
    }

    /** Clears the LC bank link only; keeps land chunks, pending state, and protection lock. */
    public boolean clearLcTeamLink(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry == null || (entry.lcTeamId() <= 0 && entry.legacyAccount() == null)) {
            return false;
        }
        teamLinks.put(ftbTeamId, entry.withLcTeamId(-1L).withLegacyAccount(null));
        setDirty();
        return true;
    }

    public void removeLinkByLcTeamId(long lcTeamId) {
        TeamLinkEntry entry = findByLcTeamId(lcTeamId);
        if (entry != null) {
            removeLink(entry.ftbTeamId());
        }
    }

    public TeamPendingState getPendingState(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        return entry == null ? new TeamPendingState() : entry.pendingState();
    }

    public void setPendingState(UUID ftbTeamId, TeamPendingState pendingState) {
        TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        teamLinks.put(ftbTeamId, entry.withPendingState(pendingState));
        setDirty();
    }

    public void setLcTeamId(UUID ftbTeamId, long lcTeamId) {
        TeamLinkEntry entry = getOrCreateLink(ftbTeamId);
        if (entry.lcTeamId() != lcTeamId) {
            teamLinks.put(ftbTeamId, entry.withLcTeamId(lcTeamId));
            setDirty();
        }
    }

    public void clearLegacyAccount(UUID ftbTeamId) {
        TeamLinkEntry entry = teamLinks.get(ftbTeamId);
        if (entry != null && entry.legacyAccount() != null) {
            teamLinks.put(ftbTeamId, entry.withLegacyAccount(null));
            setDirty();
        }
    }

    public void setProtectionLocked(UUID teamId, boolean locked) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry != null && entry.protectionLocked() != locked) {
            teamLinks.put(teamId, entry.withProtectionLocked(locked));
            setDirty();
        } else if (entry == null && locked) {
            teamLinks.put(teamId, new TeamLinkEntry(teamId, -1L, null, true, new TeamPendingState(), Set.of(), Set.of(), Map.of(), Map.of()));
            setDirty();
        }
    }

    public Set<String> getLandChunks(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Set.of() : entry.landChunks();
    }

    public boolean isLandChunk(UUID teamId, String chunkKey) {
        return getLandChunks(teamId).contains(chunkKey);
    }

    /**
     * Marks or unmarks a chunk as land chunk. Returns true if the stored
     * state actually changed.
     */
    public boolean setLandChunk(UUID teamId, String chunkKey, boolean land) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
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
        setDirty();
        return true;
    }

    /** Removes a chunk from every team's land set (e.g. after unclaiming). */
    public boolean clearLandChunk(String chunkKey) {
        boolean changed = false;
        for (TeamLinkEntry entry : java.util.List.copyOf(teamLinks.values())) {
            if (entry.landChunks().contains(chunkKey)) {
                Set<String> updated = new HashSet<>(entry.landChunks());
                updated.remove(chunkKey);
                teamLinks.put(entry.ftbTeamId(), entry.withLandChunks(updated));
                changed = true;
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public Map<UUID, Integer> getChunkUserPermissions(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return Map.of();
        }
        return entry.chunkUserPermissions().getOrDefault(chunkKey, Map.of());
    }

    public int getChunkUserPermissionFlags(UUID teamId, String chunkKey, UUID playerId) {
        Integer flags = getChunkUserPermissions(teamId, chunkKey).get(playerId);
        return flags == null ? 0 : flags;
    }

    public int getChunkAllPlayerPermissionFlags(UUID teamId, String chunkKey) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        if (entry == null) {
            return 0;
        }
        Integer flags = entry.chunkAllPlayerPermissions().get(chunkKey);
        return flags == null ? 0 : flags;
    }

    public boolean setChunkUserPermissionFlags(UUID teamId, String chunkKey, UUID playerId, int flags) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
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
        setDirty();
        return true;
    }

    public boolean setChunkAllPlayerPermissionFlags(UUID teamId, String chunkKey, int flags) {
        TeamLinkEntry entry = getOrCreateLink(teamId);
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
        setDirty();
        return true;
    }

    public boolean clearChunkUserPermissions(String chunkKey) {
        boolean changed = false;
        for (TeamLinkEntry entry : java.util.List.copyOf(teamLinks.values())) {
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
            setDirty();
        }
        return changed;
    }

    public Set<String> getAllLandChunks() {
        Set<String> all = new HashSet<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            all.addAll(entry.landChunks());
        }
        return all;
    }

    public boolean isProtectionLocked(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry != null && entry.protectionLocked();
    }

    public boolean isManagedLcTeam(long lcTeamId) {
        if (lcTeamId <= 0) {
            return false;
        }
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() == lcTeamId) {
                return true;
            }
        }
        return false;
    }

    public Set<Long> getLinkedLcTeamIds() {
        Set<Long> linkedIds = new HashSet<>();
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.lcTeamId() > 0) {
                linkedIds.add(entry.lcTeamId());
            }
        }
        return linkedIds;
    }

    public boolean isPeaceful(UUID teamId) {
        return peacefulTeams.contains(teamId);
    }

    public void setPeaceful(UUID teamId, boolean peaceful) {
        boolean changed = peaceful ? peacefulTeams.add(teamId) : peacefulTeams.remove(teamId);
        if (changed) {
            setDirty();
        }
    }

    public Set<UUID> getWarTargets(UUID teamId) {
        TeamLinkEntry entry = teamLinks.get(teamId);
        return entry == null ? Set.of() : entry.warTargets();
    }

    public boolean isAtWarWith(UUID declarerTeamId, UUID targetTeamId) {
        return getWarTargets(declarerTeamId).contains(targetTeamId);
    }

    public boolean setWarTarget(UUID declarerTeamId, UUID targetTeamId, boolean atWar) {
        TeamLinkEntry entry = getOrCreateLink(declarerTeamId);
        Set<UUID> updated = new HashSet<>(entry.warTargets());
        if (atWar) {
            if (!updated.add(targetTeamId)) {
                return false;
            }
        } else if (!updated.remove(targetTeamId)) {
            return false;
        }
        teamLinks.put(declarerTeamId, entry.withWarTargets(Set.copyOf(updated)));
        setDirty();
        updateWarActiveSince(declarerTeamId);
        updateWarActiveSince(targetTeamId);
        return true;
    }

    /**
     * Records when a team most recently went from at-peace to at-war (used to gate
     * {@code siegeModeGraceHours}), and clears it once they return to peace. Edge-triggered on the
     * 0-to-nonzero and nonzero-to-0 transitions of {@link #collectWarPartnerIds}, so it stays correct
     * even through {@link dev.voidpulsar.lc_claim_economy.service.WarService}'s add-then-immediately-
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

    /** Epoch millis this team most recently transitioned from at-peace to at-war, or 0 if not currently at war. */
    public long getWarActiveSince(UUID teamId) {
        return warActiveSinceMillis.getOrDefault(teamId, 0L);
    }

    public Set<UUID> collectWarPartnerIds(UUID teamId) {
        Set<UUID> partners = new HashSet<>();
        partners.addAll(getWarTargets(teamId));
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.warTargets().contains(teamId)) {
                partners.add(entry.ftbTeamId());
            }
        }
        partners.remove(teamId);
        return partners;
    }

    public void clearWarReferences(UUID teamId) {
        boolean changed = false;
        TeamLinkEntry ownEntry = teamLinks.get(teamId);
        if (ownEntry != null && !ownEntry.warTargets().isEmpty()) {
            teamLinks.put(teamId, ownEntry.withWarTargets(Set.of()));
            changed = true;
        }
        for (TeamLinkEntry entry : java.util.List.copyOf(teamLinks.values())) {
            UUID entryTeamId = entry.ftbTeamId();
            TeamPendingState pending = entry.pendingState();
            TeamPendingState cleaned = pending.withoutWarReferences(teamId);
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
            setDirty();
        }
    }

    public void recordUpkeepCharged(long copper) {
        statUpkeepChargedCopper += copper;
        statUpkeepChargedCount++;
        setDirty();
    }

    public void recordUpkeepMissed() {
        statUpkeepMissedCount++;
        setDirty();
    }

    public void recordClaimPurchase(long copper) {
        statClaimSpendCopper += copper;
        statClaimCount++;
        setDirty();
    }

    public void recordUnclaimRefund(long copper) {
        statUnclaimRefundCopper += copper;
        statUnclaimCount++;
        setDirty();
    }

    public void recordMarketSale(long copper) {
        statMarketVolumeCopper += copper;
        statMarketSaleCount++;
        setDirty();
    }

    public long getStatUpkeepChargedCopper() {
        return statUpkeepChargedCopper;
    }

    public int getStatUpkeepChargedCount() {
        return statUpkeepChargedCount;
    }

    public int getStatUpkeepMissedCount() {
        return statUpkeepMissedCount;
    }

    public long getStatClaimSpendCopper() {
        return statClaimSpendCopper;
    }

    public int getStatClaimCount() {
        return statClaimCount;
    }

    public long getStatUnclaimRefundCopper() {
        return statUnclaimRefundCopper;
    }

    public int getStatUnclaimCount() {
        return statUnclaimCount;
    }

    public long getStatMarketVolumeCopper() {
        return statMarketVolumeCopper;
    }

    public int getStatMarketSaleCount() {
        return statMarketSaleCount;
    }

    /** Lists a claimed chunk for sale on the public marketplace, replacing any existing listing for it. */
    public void setMarketListing(String chunkKey, MarketListing listing) {
        marketListings.put(chunkKey, listing);
        setDirty();
    }

    @Nullable
    public MarketListing getMarketListing(String chunkKey) {
        return marketListings.get(chunkKey);
    }

    /** Removes a listing (sold, cancelled, or the chunk was unclaimed/lost). Returns false if none existed. */
    public boolean removeMarketListing(String chunkKey) {
        boolean removed = marketListings.remove(chunkKey) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public Map<String, MarketListing> getAllMarketListings() {
        return Map.copyOf(marketListings);
    }

    public List<MarketListing> getMarketListingsBySeller(UUID sellerTeamId) {
        return marketListings.values().stream()
                .filter(listing -> listing.sellerTeamId().equals(sellerTeamId))
                .toList();
    }

    public Map<String, WarpEntry> getWarps(UUID ownerId) {
        return Map.copyOf(playerWarps.getOrDefault(ownerId, Map.of()));
    }

    @Nullable
    public WarpEntry getWarp(UUID ownerId, String nameLower) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        return warps == null ? null : warps.get(nameLower);
    }

    /** Looks up a warp by its primary (lowercased) name first, then by alias, both scoped to this one owner. */
    @Nullable
    public WarpEntry resolveWarp(UUID ownerId, String nameOrAliasLower) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        if (warps == null) {
            return null;
        }
        WarpEntry direct = warps.get(nameOrAliasLower);
        if (direct != null) {
            return direct;
        }
        for (WarpEntry entry : warps.values()) {
            if (entry.aliases().contains(nameOrAliasLower)) {
                return entry;
            }
        }
        return null;
    }

    public int countWarps(UUID ownerId) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        return warps == null ? 0 : warps.size();
    }

    /** Stores (or replaces) a warp, keyed by its owner and lowercased name. */
    public void setWarp(WarpEntry entry) {
        playerWarps.computeIfAbsent(entry.ownerId(), id -> new HashMap<>())
                .put(entry.name().toLowerCase(Locale.ROOT), entry);
        setDirty();
    }

    public boolean removeWarp(UUID ownerId, String nameLower) {
        Map<String, WarpEntry> warps = playerWarps.get(ownerId);
        if (warps == null || warps.remove(nameLower) == null) {
            return false;
        }
        if (warps.isEmpty()) {
            playerWarps.remove(ownerId);
        }
        setDirty();
        return true;
    }

    /** Every warp (any owner) currently marked public, for browsing/teleporting to other players' warps. */
    public List<WarpEntry> getAllPublicWarps() {
        List<WarpEntry> result = new ArrayList<>();
        for (Map<String, WarpEntry> warps : playerWarps.values()) {
            for (WarpEntry entry : warps.values()) {
                if (entry.isPublic()) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    /** Removes any warp (any owner) sitting on this chunk, e.g. after the chunk is unclaimed. */
    public boolean clearWarpsInChunk(String chunkKey) {
        boolean changed = false;
        for (Map<String, WarpEntry> warps : playerWarps.values()) {
            Iterator<WarpEntry> iterator = warps.values().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().chunkKey().equals(chunkKey)) {
                    iterator.remove();
                    changed = true;
                }
            }
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    public int countIncomingWars(UUID targetTeamId) {
        int count = 0;
        for (TeamLinkEntry entry : teamLinks.values()) {
            if (entry.warTargets().contains(targetTeamId)) {
                count++;
            }
        }
        return count;
    }

    public record TeamLinkEntry(
            UUID ftbTeamId,
            long lcTeamId,
            @Nullable BankAccount legacyAccount,
            boolean protectionLocked,
            TeamPendingState pendingState,
            Set<String> landChunks,
            Set<UUID> warTargets,
            Map<String, Map<UUID, Integer>> chunkUserPermissions,
            Map<String, Integer> chunkAllPlayerPermissions
    ) {
        TeamLinkEntry withLcTeamId(long id) {
            return new TeamLinkEntry(ftbTeamId, id, legacyAccount, protectionLocked, pendingState, landChunks, warTargets, chunkUserPermissions, chunkAllPlayerPermissions);
        }

        TeamLinkEntry withLegacyAccount(@Nullable BankAccount account) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, account, protectionLocked, pendingState, landChunks, warTargets, chunkUserPermissions, chunkAllPlayerPermissions);
        }

        TeamLinkEntry withProtectionLocked(boolean locked) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, locked, pendingState, landChunks, warTargets, chunkUserPermissions, chunkAllPlayerPermissions);
        }

        TeamLinkEntry withPendingState(TeamPendingState pending) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pending, landChunks, warTargets, chunkUserPermissions, chunkAllPlayerPermissions);
        }

        TeamLinkEntry withLandChunks(Set<String> chunks) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, chunks, warTargets, chunkUserPermissions, chunkAllPlayerPermissions);
        }

        TeamLinkEntry withWarTargets(Set<UUID> targets) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, landChunks, targets, chunkUserPermissions, chunkAllPlayerPermissions);
        }

        TeamLinkEntry withChunkUserPermissions(Map<String, Map<UUID, Integer>> permissions) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, landChunks, warTargets, permissions, chunkAllPlayerPermissions);
        }

        TeamLinkEntry withChunkAllPlayerPermissions(Map<String, Integer> permissions) {
            return new TeamLinkEntry(ftbTeamId, lcTeamId, legacyAccount, protectionLocked, pendingState, landChunks, warTargets, chunkUserPermissions, permissions);
        }
    }

    /** A claimed chunk currently listed for sale on the public marketplace. */
    public record MarketListing(UUID sellerTeamId, String sellerName, long priceCopper, long listedAt) {
    }
}
