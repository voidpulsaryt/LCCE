package dev.voidpulsar.lc_claim_economy.service;

import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.data.ChunkPosKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.data.WarpEntry;
import dev.voidpulsar.lc_claim_economy.network.WarpDto;
import dev.voidpulsar.lc_claim_economy.network.SyncWarpsPayload;
import dev.voidpulsar.lc_claim_economy.util.MoneyMessageUtil;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import dev.voidpulsar.lc_claim_economy.util.WorldDisplayNames;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player-owned warps: personal teleport points a player sets inside a chunk
 * their own team has claimed (see {@code warpRequireOwnClaim}), optionally
 * shared publicly for other players to teleport to (for a toll, see {@code
 * warpTeleportCostCopper}). Deleted automatically by {@link
 * LandChunkService#onChunkUnclaimed} when the underlying chunk is unclaimed,
 * so a warp can never point into land nobody protects anymore.
 */
public final class WarpService {
    private static final Map<UUID, Long> lastTeleportMillis = new ConcurrentHashMap<>();

    private WarpService() {
    }

    public static void createOrUpdateWarp(ServerPlayer player, String rawName) {
        if (!LcClaimEconomyConfig.SERVER.warpsEnabled.get()) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.disabled"), false);
            return;
        }

        String name = WarpNames.normalize(rawName);
        if (name == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.invalid_name"), false);
            return;
        }
        String key = name.toLowerCase(java.util.Locale.ROOT);

        MinecraftServer server = player.server;
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        WarpEntry existing = data.getWarp(player.getUUID(), key);

        if (existing == null && data.countWarps(player.getUUID()) >= LcClaimEconomyConfig.SERVER.maxWarpsPerPlayer.get()) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.limit_reached",
                    LcClaimEconomyConfig.SERVER.maxWarpsPerPlayer.get()), false);
            return;
        }

        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        String chunkKey = ChunkPosKey.encode(pos);

        if (LcClaimEconomyConfig.SERVER.warpRequireOwnClaim.get()) {
            if (!FTBChunksAPI.api().isManagerLoaded() || !FTBTeamsAPI.api().isManagerLoaded()) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.claims_unavailable"), false);
                return;
            }
            Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
            ClaimedChunk chunk = FTBChunksAPI.api().getManager().getChunk(pos);
            if (team == null || chunk == null || chunk.getTeamData().getTeam() == null
                    || !chunk.getTeamData().getTeam().getId().equals(team.getId())) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.not_your_claim"), false);
                return;
            }
        }

        long costCopper = existing == null ? LcClaimEconomyConfig.SERVER.warpCreateCostCopper.get() : 0L;
        if (costCopper > 0L) {
            MoneyValue cost = MoneyUtil.fromCopper(costCopper);
            IBankAccount account = BankAccountHelper.getAccountForPlayer(server, player);
            if (!account.getMoneyStorage().containsValue(cost)) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.insufficient_funds",
                        MoneyMessageUtil.formatValue(cost), MoneyMessageUtil.formatBalance(account)), false);
                return;
            }
            account.withdrawMoney(cost);
            BankAccountHelper.logTransaction(account, false, cost, Component.translatable("message.lc_claim_economy.ledger.warp_create"));
        }

        WarpEntry entry = new WarpEntry(
                name,
                player.getUUID(),
                player.getGameProfile().getName(),
                player.level().dimension().location(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(),
                chunkKey,
                existing != null && existing.isPublic(),
                existing != null ? existing.createdAtMillis() : System.currentTimeMillis(),
                existing != null ? existing.aliases() : Set.of()
        );
        data.setWarp(entry);

        player.displayClientMessage(Component.translatable(
                existing == null ? "message.lc_claim_economy.warp.created" : "message.lc_claim_economy.warp.updated", name), false);
    }

    public static void deleteWarp(ServerPlayer player, String rawName) {
        String key = WarpNames.normalizeKey(rawName);
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);
        WarpEntry entry = key == null ? null : data.resolveWarp(player.getUUID(), key);
        if (entry == null || !data.removeWarp(player.getUUID(), entry.name().toLowerCase(java.util.Locale.ROOT))) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.not_found", rawName), false);
            return;
        }
        player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.deleted", entry.name()), false);
    }

    public static void setPublic(ServerPlayer player, String rawName, boolean isPublic) {
        String key = WarpNames.normalizeKey(rawName);
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);
        WarpEntry existing = key == null ? null : data.resolveWarp(player.getUUID(), key);
        if (existing == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.not_found", rawName), false);
            return;
        }
        data.setWarp(existing.withPublic(isPublic));
        player.displayClientMessage(Component.translatable(
                isPublic ? "message.lc_claim_economy.warp.made_public" : "message.lc_claim_economy.warp.made_private", existing.name()), false);
    }

    public static void addAlias(ServerPlayer player, String rawName, String rawAlias) {
        String key = WarpNames.normalizeKey(rawName);
        String aliasKey = WarpNames.normalizeKey(rawAlias);
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);
        WarpEntry entry = key == null ? null : data.resolveWarp(player.getUUID(), key);
        if (entry == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.not_found", rawName), false);
            return;
        }
        if (aliasKey == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.invalid_name"), false);
            return;
        }
        if (data.resolveWarp(player.getUUID(), aliasKey) != null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.alias_taken", rawAlias), false);
            return;
        }

        Set<String> updated = new java.util.HashSet<>(entry.aliases());
        updated.add(aliasKey);
        data.setWarp(entry.withAliases(Set.copyOf(updated)));
        player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.alias_added", rawAlias, entry.name()), false);
    }

    public static void removeAlias(ServerPlayer player, String rawName, String rawAlias) {
        String key = WarpNames.normalizeKey(rawName);
        String aliasKey = WarpNames.normalizeKey(rawAlias);
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);
        WarpEntry entry = key == null ? null : data.resolveWarp(player.getUUID(), key);
        if (entry == null || aliasKey == null || !entry.aliases().contains(aliasKey)) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.alias_not_found", rawAlias), false);
            return;
        }

        Set<String> updated = new java.util.HashSet<>(entry.aliases());
        updated.remove(aliasKey);
        data.setWarp(entry.withAliases(Set.copyOf(updated)));
        player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.alias_removed", rawAlias, entry.name()), false);
    }

    public static void teleportToOwnWarp(ServerPlayer player, String rawName) {
        String key = WarpNames.normalizeKey(rawName);
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);
        WarpEntry entry = key == null ? null : data.resolveWarp(player.getUUID(), key);
        if (entry == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.not_found", rawName), false);
            return;
        }
        teleport(player, entry, false);
    }

    public static void teleportToOtherWarp(ServerPlayer player, String ownerRef, String rawName) {
        String key = WarpNames.normalizeKey(rawName);
        Optional<UUID> ownerId = key == null ? Optional.empty() : resolveOwner(player.server, ownerRef);
        if (ownerId.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.not_found", rawName), false);
            return;
        }
        teleportToOtherWarp(player, ownerId.get(), rawName);
    }

    public static void teleportToOtherWarp(ServerPlayer player, UUID ownerId, String rawName) {
        String key = WarpNames.normalizeKey(rawName);
        WarpEntry entry = key == null ? null : LcClaimEconomySavedData.get(player.server).resolveWarp(ownerId, key);

        if (entry == null || !entry.isPublic()) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.not_found", rawName), false);
            return;
        }
        if (entry.ownerId().equals(player.getUUID())) {
            teleportToOwnWarp(player, rawName);
            return;
        }
        teleport(player, entry, true);
    }

    public static void list(ServerPlayer player) {
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);
        List<WarpEntry> own = data.getWarps(player.getUUID()).values().stream()
                .sorted(Comparator.comparing(WarpEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<WarpEntry> publicWarps = data.getAllPublicWarps().stream()
                .filter(entry -> !entry.ownerId().equals(player.getUUID()))
                .sorted(Comparator.comparing(WarpEntry::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        if (own.isEmpty() && publicWarps.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.list_empty"), false);
            return;
        }

        MutableComponent message = Component.translatable("message.lc_claim_economy.warp.list_header").withStyle(ChatFormatting.YELLOW);
        if (!own.isEmpty()) {
            message.append("\n").append(Component.translatable("message.lc_claim_economy.warp.list_own_header"));
            for (WarpEntry entry : own) {
                message.append("\n").append(warpLine(entry, true));
            }
        }
        if (!publicWarps.isEmpty()) {
            message.append("\n").append(Component.translatable("message.lc_claim_economy.warp.list_public_header"));
            for (WarpEntry entry : publicWarps) {
                message.append("\n").append(warpLine(entry, false));
            }
        }
        player.displayClientMessage(message, false);
    }

    public static void onChunkUnclaimed(MinecraftServer server, String chunkKey) {
        LcClaimEconomySavedData.get(server).clearWarpsInChunk(chunkKey);
    }

    /** Pushes this player's own warps and every public warp (theirs excluded) to their client for the warp GUI. */
    public static void syncToPlayer(ServerPlayer player) {
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(player.server);

        List<WarpDto> own = data.getWarps(player.getUUID()).values().stream()
                .sorted(Comparator.comparing(WarpEntry::name, String.CASE_INSENSITIVE_ORDER))
                .map(WarpService::toDto)
                .toList();
        List<WarpDto> publicWarps = data.getAllPublicWarps().stream()
                .filter(entry -> !entry.ownerId().equals(player.getUUID()))
                .sorted(Comparator.comparing(WarpEntry::name, String.CASE_INSENSITIVE_ORDER))
                .map(WarpService::toDto)
                .toList();

        PacketDistributor.sendToPlayer(player, new SyncWarpsPayload(
                LcClaimEconomyConfig.SERVER.warpsEnabled.get(),
                LcClaimEconomyConfig.SERVER.maxWarpsPerPlayer.get(),
                LcClaimEconomyConfig.SERVER.warpCreateCostCopper.get(),
                LcClaimEconomyConfig.SERVER.warpTeleportCostCopper.get(),
                own,
                publicWarps
        ));
    }

    private static WarpDto toDto(WarpEntry entry) {
        return new WarpDto(
                entry.name(),
                entry.ownerId(),
                entry.ownerName(),
                WorldDisplayNames.resolve(entry.dimension()),
                (int) Math.floor(entry.x()),
                (int) Math.floor(entry.y()),
                (int) Math.floor(entry.z()),
                entry.isPublic(),
                new java.util.TreeSet<>(entry.aliases()).stream().toList()
        );
    }

    private static Component warpLine(WarpEntry entry, boolean own) {
        String command = own
                ? "/" + LcClaimEconomy.COMMAND_ROOT + " warp tp " + entry.name()
                : "/" + LcClaimEconomy.COMMAND_ROOT + " warp tpto " + entry.ownerName() + " " + entry.name();
        String dimensionDisplay = WorldDisplayNames.resolve(entry.dimension());
        Component label = own
                ? Component.translatable("message.lc_claim_economy.warp.list_line_own", entry.name(), dimensionDisplay)
                : Component.translatable("message.lc_claim_economy.warp.list_line_public", entry.name(), entry.ownerName(), dimensionDisplay);
        MutableComponent line = label.copy().withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.lc_claim_economy.warp.list_line_hover"))));
        if (!entry.aliases().isEmpty()) {
            String aliasList = String.join(", ", new java.util.TreeSet<>(entry.aliases()));
            line.append(Component.translatable("message.lc_claim_economy.warp.list_line_aliases", aliasList).withStyle(ChatFormatting.GRAY));
        }
        return line;
    }

    private static void teleport(ServerPlayer player, WarpEntry entry, boolean chargeToll) {
        MinecraftServer server = player.server;

        int cooldownSeconds = LcClaimEconomyConfig.SERVER.warpCooldownSeconds.get();
        if (cooldownSeconds > 0) {
            long now = System.currentTimeMillis();
            Long last = lastTeleportMillis.get(player.getUUID());
            if (last != null && now - last < cooldownSeconds * 1000L) {
                long remaining = (cooldownSeconds * 1000L - (now - last) + 999L) / 1000L;
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.cooldown", remaining), false);
                return;
            }
        }

        ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, entry.dimension());
        ServerLevel level = server.getLevel(dimensionKey);
        if (level == null) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.dimension_missing"), false);
            return;
        }

        long tollCopper = chargeToll ? LcClaimEconomyConfig.SERVER.warpTeleportCostCopper.get() : 0L;
        if (tollCopper > 0L) {
            MoneyValue toll = MoneyUtil.fromCopper(tollCopper);
            IBankAccount payerAccount = BankAccountHelper.getAccountForPlayer(server, player);
            if (!payerAccount.getMoneyStorage().containsValue(toll)) {
                player.displayClientMessage(Component.translatable("message.lc_claim_economy.insufficient_funds",
                        MoneyMessageUtil.formatValue(toll), MoneyMessageUtil.formatBalance(payerAccount)), false);
                return;
            }
            IBankAccount ownerAccount = resolveOwnerAccount(server, entry.ownerId());
            payerAccount.withdrawMoney(toll);
            BankAccountHelper.logTransaction(payerAccount, false, toll, Component.translatable("message.lc_claim_economy.ledger.warp_toll_paid"));
            if (ownerAccount != null) {
                ownerAccount.depositMoney(toll);
                BankAccountHelper.logTransaction(ownerAccount, true, toll, Component.translatable("message.lc_claim_economy.ledger.warp_toll_received"));
            }
        }

        player.teleportTo(level, entry.x(), entry.y(), entry.z(), Set.of(), entry.yaw(), entry.pitch());
        lastTeleportMillis.put(player.getUUID(), System.currentTimeMillis());
        player.displayClientMessage(Component.translatable("message.lc_claim_economy.warp.teleported", entry.name()), false);
    }

    @Nullable
    private static IBankAccount resolveOwnerAccount(MinecraftServer server, UUID ownerId) {
        Optional<Team> team = FTBTeamsAPI.api().isManagerLoaded()
                ? FTBTeamsAPI.api().getManager().getTeamForPlayerID(ownerId)
                : Optional.empty();
        if (team.isPresent()) {
            BankAccountHelper.ensurePartyAccountExists(server, team.get());
            return BankAccountHelper.getAccountForTeam(server, team.get());
        }
        return PlayerBankReference.of(ownerId).get();
    }

    private static Optional<UUID> resolveOwner(MinecraftServer server, String ownerRef) {
        if (ownerRef == null || ownerRef.isBlank()) {
            return Optional.empty();
        }
        ServerPlayer online = server.getPlayerList().getPlayerByName(ownerRef);
        if (online != null) {
            return Optional.of(online.getUUID());
        }
        if (server.getProfileCache() != null) {
            return server.getProfileCache().get(ownerRef).map(GameProfile::getId);
        }
        return Optional.empty();
    }
}
