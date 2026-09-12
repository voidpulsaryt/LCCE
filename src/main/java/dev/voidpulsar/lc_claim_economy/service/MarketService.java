package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.bank.BankAccountHelper;
import dev.voidpulsar.lc_claim_economy.bank.ClaimBatchContext;
import dev.voidpulsar.lc_claim_economy.data.ChunkPosKey;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.teams.FtbTeamCatalog;
import dev.voidpulsar.lc_claim_economy.util.MoneyMessageUtil;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Player-to-player chunk sales: list the chunk you're standing in for a
 * price, another player buys it by standing in it and paying that price
 * directly to you. Ownership transfers via a suppressed claim/unclaim pair
 * (see {@link ClaimBatchContext#runAsInternalTransfer}) so {@link
 * dev.voidpulsar.lc_claim_economy.handler.ChunkClaimHandler} doesn't also
 * charge the buyer the normal claim price or pay the seller an unclaim
 * refund on top of the agreed sale price.
 * <p>
 * FTB Chunks only for now - this is layered on the same {@code
 * ChunkTeamData}/{@code ClaimedChunkManager} APIs the FTB claim economy
 * handler uses, with no OP&C equivalent yet.
 * <p>
 * Scope note: the buyer receives a freshly-claimed chunk with default
 * protection settings and force-load state - land/build classification,
 * force-loading, and per-player chunk permissions are NOT carried over from
 * the seller. That's a reasonable first cut, not an oversight; carrying all
 * of that over safely would need its own design pass.
 */
public final class MarketService {
    private MarketService() {
    }

    public static void list(CommandSourceStack source, long priceCopper) {
        ServerPlayer player = source.getPlayer();
        if (player == null || priceCopper <= 0) {
            return;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        if (team == null) {
            return;
        }
        if (!BankAccountHelper.canPurchaseForTeam(team, player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.market.rank_denied"), false);
            return;
        }

        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
        if (claimed == null || claimed.getTeamData().getTeam() == null
                || !claimed.getTeamData().getTeam().getId().equals(team.getId())) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.market.not_your_chunk"), false);
            return;
        }

        String chunkKey = ChunkPosKey.encode(pos);
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(source.getServer());
        savedData.setMarketListing(chunkKey, new LcClaimEconomySavedData.MarketListing(
                team.getId(), team.getName().getString(), priceCopper, System.currentTimeMillis()));

        player.displayClientMessage(Component.translatable("message.lc_claim_economy.market.listed",
                MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(priceCopper))), false);
    }

    public static void cancel(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            return;
        }

        Team team = FTBTeamsAPI.api().getManager().getTeamForPlayer(player).orElse(null);
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        String chunkKey = ChunkPosKey.encode(pos);

        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(source.getServer());
        LcClaimEconomySavedData.MarketListing listing = savedData.getMarketListing(chunkKey);
        if (listing == null || team == null || !listing.sellerTeamId().equals(team.getId())) {
            player.displayClientMessage(Component.translatable("message.lc_claim_economy.market.no_listing_here"), false);
            return;
        }

        savedData.removeMarketListing(chunkKey);
        player.displayClientMessage(Component.translatable("message.lc_claim_economy.market.cancelled"), false);
    }

    public static void buy(CommandSourceStack source) {
        ServerPlayer buyer = source.getPlayer();
        if (buyer == null) {
            return;
        }
        MinecraftServer server = source.getServer();

        ChunkDimPos pos = new ChunkDimPos(buyer.level(), buyer.blockPosition());
        String chunkKey = ChunkPosKey.encode(pos);
        LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
        LcClaimEconomySavedData.MarketListing listing = savedData.getMarketListing(chunkKey);
        if (listing == null) {
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.no_listing_here"), false);
            return;
        }

        Team sellerTeam = FtbTeamCatalog.resolve(server, listing.sellerTeamId());
        ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
        if (sellerTeam == null || claimed == null || claimed.getTeamData().getTeam() == null
                || !claimed.getTeamData().getTeam().getId().equals(sellerTeam.getId())) {
            // Stale listing - the chunk changed hands or was unclaimed some other way.
            savedData.removeMarketListing(chunkKey);
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.listing_stale"), false);
            return;
        }

        Team buyerTeam = FTBTeamsAPI.api().getManager().getTeamForPlayer(buyer).orElse(null);
        if (buyerTeam == null) {
            return;
        }
        if (buyerTeam.getId().equals(sellerTeam.getId())) {
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.no_self"), false);
            return;
        }
        if (!BankAccountHelper.canPurchaseForTeam(buyerTeam, buyer.getUUID())) {
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.rank_denied"), false);
            return;
        }

        ChunkTeamData buyerData = FTBChunksAPI.api().getManager().getOrCreateData(buyerTeam);
        if (!buyerTeam.isServerTeam() && buyerData.getClaimedChunks().size() >= buyerData.getMaxClaimChunks()) {
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.buyer_claim_limit"), false);
            return;
        }

        MoneyValue price = MoneyUtil.fromCopper(listing.priceCopper());
        BankAccountHelper.ensurePartyAccountExists(server, buyerTeam);
        IBankAccount buyerAccount = BankAccountHelper.getAccountForPlayer(server, buyer);
        if (!buyerAccount.getMoneyStorage().containsValue(price)) {
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.insufficient_funds",
                    MoneyMessageUtil.formatValue(price), MoneyMessageUtil.formatBalance(buyerAccount)), false);
            return;
        }

        // Transfer ownership *before* touching any money - if either step fails,
        // nothing has been charged yet.
        ChunkTeamData sellerData = claimed.getTeamData();
        CommandSourceStack serverSource = server.createCommandSourceStack().withSuppressedOutput();

        ClaimResult[] unclaimResult = new ClaimResult[1];
        ClaimBatchContext.runAsInternalTransfer(() -> unclaimResult[0] = sellerData.unclaim(serverSource, pos, true, true));
        if (unclaimResult[0] == null || !unclaimResult[0].isSuccess()) {
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.transfer_failed"), false);
            return;
        }

        ClaimResult[] claimResult = new ClaimResult[1];
        ClaimBatchContext.runAsInternalTransfer(() -> claimResult[0] = buyerData.claim(serverSource, pos, true));
        if (claimResult[0] == null || !claimResult[0].isSuccess()) {
            // Try to give the chunk back to the seller so it isn't left unclaimed.
            ClaimResult[] rollback = new ClaimResult[1];
            ClaimBatchContext.runAsInternalTransfer(() -> rollback[0] = sellerData.claim(serverSource, pos, true));
            if (rollback[0] == null || !rollback[0].isSuccess()) {
                LcClaimEconomy.LOGGER.error("Market transfer failed and rollback also failed - chunk {} may be left unclaimed", chunkKey);
            }
            buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.transfer_failed"), false);
            return;
        }

        BankAccountHelper.ensurePartyAccountExists(server, sellerTeam);
        IBankAccount sellerAccount = BankAccountHelper.getAccountForTeam(server, sellerTeam);
        buyerAccount.withdrawMoney(price);
        sellerAccount.depositMoney(price);

        savedData.removeMarketListing(chunkKey);
        savedData.recordMarketSale(listing.priceCopper());
        BankAccountHelper.logTransaction(sellerAccount, true, price, Component.translatable("message.lc_claim_economy.ledger.market_sale"));
        BankAccountHelper.logTransaction(buyerAccount, false, price, Component.translatable("message.lc_claim_economy.ledger.market_purchase"));

        Component priceText = MoneyMessageUtil.formatValue(price);
        buyer.displayClientMessage(Component.translatable("message.lc_claim_economy.market.bought", priceText), false);
        ClaimPriceSync.syncToPlayer(buyer);
        notifySeller(server, sellerTeam, Component.translatable("message.lc_claim_economy.market.sold", buyer.getDisplayName(), priceText));
    }

    public static void browse(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        Map<String, LcClaimEconomySavedData.MarketListing> listings = LcClaimEconomySavedData.get(server).getAllMarketListings();
        if (listings.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("message.lc_claim_economy.market.browse_empty"), false);
            return;
        }

        List<Map.Entry<String, LcClaimEconomySavedData.MarketListing>> sorted = new ArrayList<>(listings.entrySet());
        sorted.sort((a, b) -> Long.compare(a.getValue().priceCopper(), b.getValue().priceCopper()));

        MutableComponent message = Component.translatable("message.lc_claim_economy.market.browse_header", listings.size())
                .withStyle(ChatFormatting.YELLOW);
        int shown = 0;
        for (Map.Entry<String, LcClaimEconomySavedData.MarketListing> entry : sorted) {
            if (shown >= 20) {
                message.append("\n").append(Component.translatable("message.lc_claim_economy.market.browse_truncated", listings.size() - shown));
                break;
            }
            LcClaimEconomySavedData.MarketListing listing = entry.getValue();
            Team sellerTeam = FtbTeamCatalog.resolve(server, listing.sellerTeamId());
            Component sellerName = sellerTeam != null ? sellerTeam.getName() : Component.literal(listing.sellerName());
            var dimPos = ChunkPosKey.toChunkDimPos(entry.getKey());
            message.append("\n").append(Component.translatable(
                    "message.lc_claim_economy.market.browse_line",
                    dimPos.x(), dimPos.z(),
                    Component.literal(dimPos.dimension().location().getPath()),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(listing.priceCopper())),
                    sellerName.copy().withStyle(ChatFormatting.AQUA)
            ));
            shown++;
        }

        ServerPlayer player = source.getPlayer();
        if (player != null) {
            player.displayClientMessage(message, false);
        } else {
            source.sendSuccess(() -> message, false);
        }
    }

    private static void notifySeller(MinecraftServer server, Team sellerTeam, Component message) {
        for (ServerPlayer member : sellerTeam.getOnlineMembers()) {
            member.displayClientMessage(message, false);
            ClaimPriceSync.syncToPlayer(member);
        }
    }
}
