package dev.voidpulsar.lc_claim_economy.data;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The single NBT persistence root for this mod's non-bank server-wide state. Internally
 * decomposed into focused per-domain managers (see the {@code data} package) that this class
 * owns and delegates every public method to - the NBT tag structure/key names are unchanged
 * from before that decomposition, and every method here keeps its original signature, so no
 * consumer needed to change. See {@link TeamLinkManager}'s javadoc for why per-team claim
 * state (this class's {@link TeamLinkEntry}) is the one manager that stayed as a single big
 * cohesive unit rather than splitting further.
 */
public class LcClaimEconomySavedData extends SavedData {
    private static final String DATA_NAME = LcClaimEconomy.MOD_ID + "_team_accounts";

    private final TeamLinkManager teamLinks = new TeamLinkManager(this::setDirty);
    private final BountyManager bounties = new BountyManager(this::setDirty);
    private final UpkeepScheduleManager upkeepSchedule = new UpkeepScheduleManager(this::setDirty);
    private final ServerStatsManager stats = new ServerStatsManager(this::setDirty);
    private final MarketListingManager marketListings = new MarketListingManager(this::setDirty);
    private final WarpManager warps = new WarpManager(this::setDirty);

    public static LcClaimEconomySavedData get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(new SavedData.Factory<>(LcClaimEconomySavedData::new, LcClaimEconomySavedData::load), DATA_NAME);
    }

    static LcClaimEconomySavedData load(CompoundTag tag, HolderLookup.Provider lookup) {
        LcClaimEconomySavedData data = new LcClaimEconomySavedData();
        data.teamLinks.load(tag, lookup);
        data.bounties.load(tag);
        data.upkeepSchedule.load(tag);
        data.stats.load(tag);
        data.marketListings.load(tag);
        data.warps.load(tag);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        teamLinks.save(tag, lookup);
        bounties.save(tag);
        upkeepSchedule.save(tag);
        stats.save(tag);
        marketListings.save(tag);
        warps.save(tag);
        return tag;
    }

    // ---------------- Bounties ----------------

    /** Adds to (does not replace) any existing bounty on this player, in copper. */
    public void addPlayerBounty(UUID victim, long copper) {
        bounties.addPlayerBounty(victim, copper);
    }

    /** Adds to (does not replace) any existing bounty on this team, in copper. */
    public void addTeamBounty(UUID team, long copper) {
        bounties.addTeamBounty(team, copper);
    }

    /** Removes and returns the full bounty amount (in copper) on this player, or 0 if none. */
    public long takePlayerBounty(UUID victim) {
        return bounties.takePlayerBounty(victim);
    }

    /** Removes and returns the full bounty amount (in copper) on this team, or 0 if none. */
    public long takeTeamBounty(UUID team) {
        return bounties.takeTeamBounty(team);
    }

    public Map<UUID, Long> playerBounties() {
        return bounties.playerBounties();
    }

    public Map<UUID, Long> teamBounties() {
        return bounties.teamBounties();
    }

    // ---------------- Pioneer bonus / server stats ----------------

    /**
     * Marks the server-wide Pioneer Bonus as claimed and returns true, the
     * one time this is ever called successfully - every call after the
     * first (on this server, forever) returns false. Callers use this to
     * gate a one-time reward for whoever claims the very first chunk ever
     * claimed on the server.
     */
    public boolean claimPioneerBonus() {
        return stats.claimPioneerBonus();
    }

    public void recordUpkeepCharged(long copper) {
        stats.recordUpkeepCharged(copper);
    }

    public void recordUpkeepMissed() {
        stats.recordUpkeepMissed();
    }

    public void recordClaimPurchase(long copper) {
        stats.recordClaimPurchase(copper);
    }

    public void recordUnclaimRefund(long copper) {
        stats.recordUnclaimRefund(copper);
    }

    public void recordMarketSale(long copper) {
        stats.recordMarketSale(copper);
    }

    public long getStatUpkeepChargedCopper() {
        return stats.getStatUpkeepChargedCopper();
    }

    public int getStatUpkeepChargedCount() {
        return stats.getStatUpkeepChargedCount();
    }

    public int getStatUpkeepMissedCount() {
        return stats.getStatUpkeepMissedCount();
    }

    public long getStatClaimSpendCopper() {
        return stats.getStatClaimSpendCopper();
    }

    public int getStatClaimCount() {
        return stats.getStatClaimCount();
    }

    public long getStatUnclaimRefundCopper() {
        return stats.getStatUnclaimRefundCopper();
    }

    public int getStatUnclaimCount() {
        return stats.getStatUnclaimCount();
    }

    public long getStatMarketVolumeCopper() {
        return stats.getStatMarketVolumeCopper();
    }

    public int getStatMarketSaleCount() {
        return stats.getStatMarketSaleCount();
    }

    // ---------------- Upkeep scheduling ----------------

    /** Next world-time tick (persisted so it survives server restarts) this FTB team's upkeep should fire at, or -1 if not yet scheduled. */
    public long getNextUpkeepTick(UUID teamId) {
        return upkeepSchedule.getNextUpkeepTick(teamId);
    }

    public void setNextUpkeepTick(UUID teamId, long tick) {
        upkeepSchedule.setNextUpkeepTick(teamId, tick);
    }

    /** Next world-time tick (persisted so it survives server restarts) this OP&C claim owner's upkeep should fire at, or -1 if not yet scheduled. */
    public long getNextOpcUpkeepTick(UUID ownerId) {
        return upkeepSchedule.getNextOpcUpkeepTick(ownerId);
    }

    public void setNextOpcUpkeepTick(UUID ownerId, long tick) {
        upkeepSchedule.setNextOpcUpkeepTick(ownerId, tick);
    }

    // ---------------- Team links / pending state / protection lock ----------------

    public TeamLinkEntry getOrCreateLink(UUID ftbTeamId) {
        return teamLinks.getOrCreateLink(ftbTeamId);
    }

    @Nullable
    public TeamLinkEntry get(UUID ftbTeamId) {
        return teamLinks.get(ftbTeamId);
    }

    @Nullable
    public TeamLinkEntry findByLcTeamId(long lcTeamId) {
        return teamLinks.findByLcTeamId(lcTeamId);
    }

    public Collection<TeamLinkEntry> getAllLinks() {
        return teamLinks.getAllLinks();
    }

    @Nullable
    public TeamLinkEntry removeLink(UUID ftbTeamId) {
        return teamLinks.removeLink(ftbTeamId);
    }

    /** Clears the LC bank link only; keeps land chunks, pending state, and protection lock. */
    public boolean clearLcTeamLink(UUID ftbTeamId) {
        return teamLinks.clearLcTeamLink(ftbTeamId);
    }

    public void removeLinkByLcTeamId(long lcTeamId) {
        teamLinks.removeLinkByLcTeamId(lcTeamId);
    }

    public TeamQueuedChanges getPendingState(UUID ftbTeamId) {
        return teamLinks.getPendingState(ftbTeamId);
    }

    public void setPendingState(UUID ftbTeamId, TeamQueuedChanges pendingState) {
        teamLinks.setPendingState(ftbTeamId, pendingState);
    }

    public void setLcTeamId(UUID ftbTeamId, long lcTeamId) {
        teamLinks.setLcTeamId(ftbTeamId, lcTeamId);
    }

    public void clearLegacyAccount(UUID ftbTeamId) {
        teamLinks.clearLegacyAccount(ftbTeamId);
    }

    public void setProtectionLocked(UUID teamId, boolean locked) {
        teamLinks.setProtectionLocked(teamId, locked);
    }

    public boolean isProtectionLocked(UUID teamId) {
        return teamLinks.isProtectionLocked(teamId);
    }

    public boolean isManagedLcTeam(long lcTeamId) {
        return teamLinks.isManagedLcTeam(lcTeamId);
    }

    public Set<Long> getLinkedLcTeamIds() {
        return teamLinks.getLinkedLcTeamIds();
    }

    // ---------------- Land chunks / chunk permissions ----------------

    public Set<String> getLandChunks(UUID teamId) {
        return teamLinks.getLandChunks(teamId);
    }

    public boolean isLandChunk(UUID teamId, String chunkKey) {
        return teamLinks.isLandChunk(teamId, chunkKey);
    }

    /**
     * Marks or unmarks a chunk as land chunk. Returns true if the stored
     * state actually changed.
     */
    public boolean setLandChunk(UUID teamId, String chunkKey, boolean land) {
        return teamLinks.setLandChunk(teamId, chunkKey, land);
    }

    /** Removes a chunk from every team's land set (e.g. after unclaiming). */
    public boolean clearLandChunk(String chunkKey) {
        return teamLinks.clearLandChunk(chunkKey);
    }

    public Map<UUID, Integer> getChunkUserPermissions(UUID teamId, String chunkKey) {
        return teamLinks.getChunkUserPermissions(teamId, chunkKey);
    }

    public int getChunkUserPermissionFlags(UUID teamId, String chunkKey, UUID playerId) {
        return teamLinks.getChunkUserPermissionFlags(teamId, chunkKey, playerId);
    }

    public int getChunkAllPlayerPermissionFlags(UUID teamId, String chunkKey) {
        return teamLinks.getChunkAllPlayerPermissionFlags(teamId, chunkKey);
    }

    public boolean setChunkUserPermissionFlags(UUID teamId, String chunkKey, UUID playerId, int flags) {
        return teamLinks.setChunkUserPermissionFlags(teamId, chunkKey, playerId, flags);
    }

    public boolean setChunkAllPlayerPermissionFlags(UUID teamId, String chunkKey, int flags) {
        return teamLinks.setChunkAllPlayerPermissionFlags(teamId, chunkKey, flags);
    }

    public boolean clearChunkUserPermissions(String chunkKey) {
        return teamLinks.clearChunkUserPermissions(chunkKey);
    }

    public Set<String> getAllLandChunks() {
        return teamLinks.getAllLandChunks();
    }

    // ---------------- Peaceful mode / wars ----------------

    public boolean isPeaceful(UUID teamId) {
        return teamLinks.isPeaceful(teamId);
    }

    public void setPeaceful(UUID teamId, boolean peaceful) {
        teamLinks.setPeaceful(teamId, peaceful);
    }

    public Set<UUID> getWarTargets(UUID teamId) {
        return teamLinks.getWarTargets(teamId);
    }

    public boolean isAtWarWith(UUID declarerTeamId, UUID targetTeamId) {
        return teamLinks.isAtWarWith(declarerTeamId, targetTeamId);
    }

    public boolean setWarTarget(UUID declarerTeamId, UUID targetTeamId, boolean atWar) {
        return teamLinks.setWarTarget(declarerTeamId, targetTeamId, atWar);
    }

    /** Epoch millis this team most recently transitioned from at-peace to at-war, or 0 if not currently at war. */
    public long getWarActiveSince(UUID teamId) {
        return teamLinks.getWarActiveSince(teamId);
    }

    public Set<UUID> collectWarPartnerIds(UUID teamId) {
        return teamLinks.collectWarPartnerIds(teamId);
    }

    public void clearWarReferences(UUID teamId) {
        teamLinks.clearWarReferences(teamId);
    }

    public int countIncomingWars(UUID targetTeamId) {
        return teamLinks.countIncomingWars(targetTeamId);
    }

    // ---------------- Marketplace ----------------

    /** Lists a claimed chunk for sale on the public marketplace, replacing any existing listing for it. */
    public void setMarketListing(String chunkKey, MarketListing listing) {
        marketListings.setMarketListing(chunkKey, listing);
    }

    @Nullable
    public MarketListing getMarketListing(String chunkKey) {
        return marketListings.getMarketListing(chunkKey);
    }

    /** Removes a listing (sold, cancelled, or the chunk was unclaimed/lost). Returns false if none existed. */
    public boolean removeMarketListing(String chunkKey) {
        return marketListings.removeMarketListing(chunkKey);
    }

    public Map<String, MarketListing> getAllMarketListings() {
        return marketListings.getAllMarketListings();
    }

    public List<MarketListing> getMarketListingsBySeller(UUID sellerTeamId) {
        return marketListings.getMarketListingsBySeller(sellerTeamId);
    }

    // ---------------- Player warps ----------------

    public Map<String, WarpEntry> getWarps(UUID ownerId) {
        return warps.getWarps(ownerId);
    }

    @Nullable
    public WarpEntry getWarp(UUID ownerId, String nameLower) {
        return warps.getWarp(ownerId, nameLower);
    }

    /** Looks up a warp by its primary (lowercased) name first, then by alias, both scoped to this one owner. */
    @Nullable
    public WarpEntry resolveWarp(UUID ownerId, String nameOrAliasLower) {
        return warps.resolveWarp(ownerId, nameOrAliasLower);
    }

    public int countWarps(UUID ownerId) {
        return warps.countWarps(ownerId);
    }

    /** Stores (or replaces) a warp, keyed by its owner and lowercased name. */
    public void setWarp(WarpEntry entry) {
        warps.setWarp(entry);
    }

    public boolean removeWarp(UUID ownerId, String nameLower) {
        return warps.removeWarp(ownerId, nameLower);
    }

    /** Every warp (any owner) currently marked public, for browsing/teleporting to other players' warps. */
    public List<WarpEntry> getAllPublicWarps() {
        return warps.getAllPublicWarps();
    }

    /** Removes any warp (any owner) sitting on this chunk, e.g. after the chunk is unclaimed. */
    public boolean clearWarpsInChunk(String chunkKey) {
        return warps.clearWarpsInChunk(chunkKey);
    }

    // ---------------- Nested record types ----------------
    // Kept here (not moved into the managers above) since several other classes reference
    // these exact names, e.g. LcClaimEconomySavedData.TeamLinkEntry.

    public record TeamLinkEntry(
            UUID ftbTeamId,
            long lcTeamId,
            @Nullable BankAccount legacyAccount,
            boolean protectionLocked,
            TeamQueuedChanges pendingState,
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

        TeamLinkEntry withPendingState(TeamQueuedChanges pending) {
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
