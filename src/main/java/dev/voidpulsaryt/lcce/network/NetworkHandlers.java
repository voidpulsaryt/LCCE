package dev.voidpulsaryt.lcce.network;

import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.integration.ftb.RegionSync;
import dev.voidpulsaryt.lcce.marketplace.CountryListing;
import dev.voidpulsaryt.lcce.marketplace.MarketplaceManager;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Registers this mod's network payloads and their handling, client and server side. */
public final class NetworkHandlers {

    private NetworkHandlers() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(ClaimChunkPayload.TYPE, ClaimChunkPayload.STREAM_CODEC, (IPayloadHandler<ClaimChunkPayload>) NetworkHandlers::handleClaimChunk);
        registrar.playToServer(CreateRegionFromSelectionPayload.TYPE, CreateRegionFromSelectionPayload.STREAM_CODEC,
                (IPayloadHandler<CreateRegionFromSelectionPayload>) NetworkHandlers::handleCreateRegionFromSelection);
        registrar.playToServer(SellCountryPayload.TYPE, SellCountryPayload.STREAM_CODEC,
                (IPayloadHandler<SellCountryPayload>) NetworkHandlers::handleSellCountry);
        registrar.playToClient(TeamBalanceSyncPayload.TYPE, TeamBalanceSyncPayload.STREAM_CODEC,
                (IPayloadHandler<TeamBalanceSyncPayload>) NetworkHandlers::handleTeamBalanceSync);
        registrar.playToClient(RegionSyncPayload.TYPE, RegionSyncPayload.STREAM_CODEC,
                (IPayloadHandler<RegionSyncPayload>) NetworkHandlers::handleRegionSync);
        registrar.playToClient(NationSyncPayload.TYPE, NationSyncPayload.STREAM_CODEC,
                (IPayloadHandler<NationSyncPayload>) NetworkHandlers::handleNationSync);
    }

    private static void handleClaimChunk(ClaimChunkPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        FTBChunksAPI.api().claimAsPlayer(player, player.level().dimension(), player.chunkPosition(), false);
    }

    private static void handleCreateRegionFromSelection(CreateRegionFromSelectionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
        if (teamOpt.isEmpty()) {
            return;
        }
        Team team = teamOpt.get();
        // Never trust the client alone for an officer-only action - re-checked here exactly like
        // every other region mutation in RegionCommand.
        if (!player.hasPermissions(2) && !team.getRankForPlayer(player.getUUID()).isOfficerOrBetter()) {
            return;
        }
        String name = payload.name().trim();
        if (name.isEmpty()) {
            return;
        }

        MinecraftServer server = player.getServer();
        RegionManager manager = RegionManager.get(server);
        Region region = manager.findRegionByName(team.getId(), name).orElseGet(() -> manager.createRegion(team.getId(), name));

        int added = 0;
        for (XZ xz : payload.chunks()) {
            ChunkDimPos pos = xz.dim(player.level());
            ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
            if (claimed != null && claimed.getTeamData().getTeam().getId().equals(team.getId())) {
                manager.addChunkToRegion(region, pos);
                added++;
            }
        }
        RegionSync.pushToTeam(server, team);
        player.sendSystemMessage(Component.translatable("lcce.region.created_from_selection", name, added));
    }

    private static void handleSellCountry(SellCountryPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !FTBTeamsAPI.api().isManagerLoaded()) {
            return;
        }
        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamForPlayer(player);
        if (teamOpt.isEmpty()) {
            return;
        }
        Team team = teamOpt.get();
        if (!player.hasPermissions(2) && !team.getRankForPlayer(player.getUUID()).isOfficerOrBetter()) {
            return;
        }
        if (payload.price().signum() <= 0) {
            return;
        }

        MinecraftServer server = player.getServer();
        MarketplaceManager market = MarketplaceManager.get(server);
        long now = server.overworld().getGameTime();
        long cooldown = LCCEConfig.MARKET_LISTING_COOLDOWN_TICKS.get();

        Set<ChunkDimPos> validChunks = new HashSet<>();
        for (XZ xz : payload.chunks()) {
            ChunkDimPos pos = xz.dim(player.level());
            if (market.isPrivatelyOwned(pos) || market.getListing(pos) != null || market.getCountryListingAt(pos) != null) {
                continue;
            }
            ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
            if (claimed == null || !claimed.getTeamData().getTeam().getId().equals(team.getId())) {
                continue;
            }
            if (now - claimed.getTimeClaimed() < cooldown) {
                continue;
            }
            validChunks.add(pos);
        }

        if (validChunks.isEmpty()) {
            player.sendSystemMessage(Component.translatable("lcce.market.country_no_valid_chunks"));
            return;
        }

        String label = payload.label().trim();
        CountryListing listing = new CountryListing(
                UUID.randomUUID(), team.getId(), true, payload.price(), payload.buyerRule(), now,
                label.isEmpty() ? team.getName().getString() : label, validChunks
        );
        market.listCountryForSale(listing);
        player.sendSystemMessage(Component.translatable(
                "lcce.market.country_listed", listing.label(), validChunks.size(), CurrencyBridge.formatValue(payload.price())
        ));
    }

    private static void handleTeamBalanceSync(TeamBalanceSyncPayload payload, IPayloadContext context) {
        // Only ever reached on a client, since this payload is only ever sent playToClient - but
        // guarded the same way as every other client-only code path in this mod, for consistency.
        if (FMLEnvironment.dist.isClient()) {
            dev.voidpulsaryt.lcce.client.ClientTeamBalanceCache.update(payload.teamId(), payload.balance());
        }
    }

    private static void handleRegionSync(RegionSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            dev.voidpulsaryt.lcce.client.ClientRegionCache.update(payload);
        }
    }

    private static void handleNationSync(NationSyncPayload payload, IPayloadContext context) {
        if (FMLEnvironment.dist.isClient()) {
            dev.voidpulsaryt.lcce.client.ClientNationCache.update(payload.teamId(), payload.nationName());
        }
    }
}
