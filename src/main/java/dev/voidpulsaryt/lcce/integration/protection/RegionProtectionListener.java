package dev.voidpulsaryt.lcce.integration.protection;

import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.economy.UpkeepPricing;
import dev.voidpulsaryt.lcce.marketplace.ChunkOwnership;
import dev.voidpulsaryt.lcce.marketplace.MarketplaceManager;
import dev.voidpulsaryt.lcce.region.PermissionTier;
import dev.voidpulsaryt.lcce.region.ProtectionLineItem;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import dev.voidpulsaryt.lcce.trust.ChunkTrustManager;
import dev.voidpulsaryt.lcce.trust.TrustLevel;
import dev.voidpulsaryt.lcce.war.WarManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Enforces per-chunk protection, checked in this order of precedence:
 * <ol>
 *   <li>Explicit per-player trust grants ({@code ChunkTrustManager}) - if an officer (or a private
 *   owner) has explicitly trusted a specific outsider on this specific chunk, that wins outright,
 *   overriding everything below it, including FTB Chunks' own team-membership check.</li>
 *   <li>Private marketplace ownership - if a chunk's been bought out to an individual player, only
 *   their whitelist/blacklist matters for interact/edit access; regions and FTB Chunks' own
 *   protection are bypassed entirely for that chunk.</li>
 *   <li>Per-region protection (mob griefing / explosions / PvP toggles, and interact/edit tiers)
 *   for any chunk that's been assigned to a {@link Region}.</li>
 *   <li>Neither: FTB Chunks' own team-wide protection (and vanilla behaviour on unclaimed land)
 *   keeps applying as if this mod didn't exist.</li>
 * </ol>
 * <p>
 * Trust grants need a second pass at {@link EventPriority#LOWEST}: FTB Chunks registers its own
 * listener on these same vanilla events to enforce team-membership, and since that's a completely
 * separate listener, our normal-priority checks below can't stop it from cancelling the event in
 * the first place. Registering again at LOWEST with {@code receiveCanceled = true} lets us inspect
 * the event <em>after</em> FTB Chunks has already denied it and explicitly un-cancel it for a
 * trusted player - which is what "wins outright" above actually requires.
 */
public final class RegionProtectionListener {

    private RegionProtectionListener() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RegionProtectionListener::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(RegionProtectionListener::onEntityPlace);
        NeoForge.EVENT_BUS.addListener(RegionProtectionListener::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(RegionProtectionListener::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(RegionProtectionListener::onMobGriefing);
        NeoForge.EVENT_BUS.addListener(RegionProtectionListener::onExplosionDetonate);
        NeoForge.EVENT_BUS.addListener(RegionProtectionListener::onIncomingDamage);

        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RegionProtectionListener::onBlockBreakTrustOverride);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RegionProtectionListener::onEntityPlaceTrustOverride);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RegionProtectionListener::onRightClickBlockTrustOverride);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, RegionProtectionListener::onEntityInteractTrustOverride);
    }

    private static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!isAllowed(level, event.getPos(), ProtectionLineItem.EDIT_TIER, RegionSettingsAccessor.EDIT, player.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!isAllowed(level, event.getPos(), ProtectionLineItem.EDIT_TIER, RegionSettingsAccessor.EDIT, player.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (!isAllowed(level, event.getPos(), ProtectionLineItem.INTERACT_TIER, RegionSettingsAccessor.INTERACT, player.getUUID())) {
            event.setCanceled(true);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getTarget().blockPosition();
        if (!isAllowed(level, pos, ProtectionLineItem.INTERACT_TIER, RegionSettingsAccessor.INTERACT, player.getUUID())) {
            event.setCanceled(true);
        }
    }

    // --- LOWEST-priority trust overrides, to undo FTB Chunks' own denial for a trusted player ---

    private static void onBlockBreakTrustOverride(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (isTrusted(level, event.getPos(), player.getUUID(), TrustLevel.BUILD)) {
            event.setCanceled(false);
        }
    }

    private static void onEntityPlaceTrustOverride(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isTrusted(level, event.getPos(), player.getUUID(), TrustLevel.BUILD)) {
            event.setCanceled(false);
        }
    }

    private static void onRightClickBlockTrustOverride(PlayerInteractEvent.RightClickBlock event) {
        if (!event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (isTrusted(level, event.getPos(), player.getUUID(), TrustLevel.INTERACT)) {
            event.setCanceled(false);
        }
    }

    private static void onEntityInteractTrustOverride(PlayerInteractEvent.EntityInteract event) {
        if (!event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (isTrusted(level, event.getTarget().blockPosition(), player.getUUID(), TrustLevel.INTERACT)) {
            event.setCanceled(false);
        }
    }

    private static boolean isTrusted(ServerLevel level, BlockPos pos, UUID playerId, TrustLevel required) {
        ChunkDimPos chunkPos = new ChunkDimPos(level, pos);
        return ChunkTrustManager.get(level.getServer()).isTrustedFor(chunkPos, playerId, required);
    }

    private static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity entity = event.getEntity();
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        Region region = regionAt(level, entity.blockPosition());
        if (region != null && UpkeepPricing.isActive(region, ProtectionLineItem.MOB_GRIEFING)) {
            event.setCanGrief(false);
        }
    }

    private static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> {
            Region region = regionAt(level, pos);
            return region != null && UpkeepPricing.isActive(region, ProtectionLineItem.EXPLOSIONS);
        });
        event.getAffectedEntities().removeIf(entity -> {
            Region region = regionAt(level, entity.blockPosition());
            return region != null && UpkeepPricing.isActive(region, ProtectionLineItem.EXPLOSIONS);
        });
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(victim.level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof Player attacker) || attacker.getUUID().equals(victim.getUUID())) {
            return;
        }
        Region region = regionAt(level, victim.blockPosition());
        if (region == null) {
            return;
        }
        if (isSiegingAttacker(level.getServer(), attacker.getUUID(), region.teamId())) {
            return;
        }
        if (UpkeepPricing.isActive(region, ProtectionLineItem.PVP)) {
            event.setCanceled(true);
        }
    }

    private enum RegionSettingsAccessor {
        INTERACT, EDIT
    }

    /**
     * Explicit trust (if any) always wins outright. Otherwise private ownership (if any) always
     * wins. Otherwise falls back to the region tier check, and if there's no region either, the
     * chunk is left alone (FTB Chunks' own protection applies as normal).
     */
    private static boolean isAllowed(ServerLevel level, BlockPos pos, ProtectionLineItem lineItem,
                                      RegionSettingsAccessor accessor, UUID playerId) {
        ChunkDimPos chunkPos = new ChunkDimPos(level, pos);
        TrustLevel required = accessor == RegionSettingsAccessor.EDIT ? TrustLevel.BUILD : TrustLevel.INTERACT;
        if (ChunkTrustManager.get(level.getServer()).isTrustedFor(chunkPos, playerId, required)) {
            return true;
        }

        ChunkOwnership ownership = MarketplaceManager.get(level.getServer()).getOwnership(chunkPos);
        if (ownership != null) {
            return ownership.isAllowed(playerId);
        }

        Region region = RegionManager.get(level.getServer()).getRegionAt(chunkPos);
        if (region == null) {
            return true;
        }
        if (isSiegingAttacker(level.getServer(), playerId, region.teamId())) {
            return true;
        }
        PermissionTier tier = accessor == RegionSettingsAccessor.EDIT
                ? region.settings().editTier()
                : region.settings().interactTier();
        return isAllowedTier(region, lineItem, tier, playerId);
    }

    /** Whether {@code playerId}'s team is sieging {@code defenderTeamId} - see {@code War#isSiege()}. */
    private static boolean isSiegingAttacker(MinecraftServer server, UUID playerId, UUID defenderTeamId) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return false;
        }
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(playerId)
                .map(attackerTeam -> WarManager.get(server).getOutgoingWars(attackerTeam.getId()).stream()
                        .anyMatch(w -> w.isSiege() && w.defenderTeamId().equals(defenderTeamId)))
                .orElse(false);
    }

    @Nullable
    private static Region regionAt(ServerLevel level, BlockPos pos) {
        return RegionManager.get(level.getServer()).getRegionAt(new ChunkDimPos(level, pos));
    }

    /**
     * A tier restriction only applies while its upkeep line item is active - if it's been
     * dismantled for non-payment, the region falls back to PUBLIC (no restriction) for that check
     * rather than silently locking people out of land the team stopped paying to restrict.
     */
    private static boolean isAllowedTier(Region region, ProtectionLineItem lineItem, PermissionTier tier, UUID playerId) {
        if (!UpkeepPricing.isActive(region, lineItem)) {
            return true;
        }
        Team team = ownerTeamOf(region);
        // If the owning team can no longer be resolved (edge case, e.g. mid-disband), fail open
        // rather than locking everyone out of an orphaned region.
        return team == null || tier.isAllowed(team, playerId, region.settings());
    }

    @Nullable
    private static Team ownerTeamOf(Region region) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return null;
        }
        return FTBTeamsAPI.api().getManager().getTeamByID(region.teamId()).orElse(null);
    }
}
