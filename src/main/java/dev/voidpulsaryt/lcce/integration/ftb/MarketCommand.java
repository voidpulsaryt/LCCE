package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.api.ClaimedChunk;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.marketplace.AccessListMode;
import dev.voidpulsaryt.lcce.marketplace.BuyerRule;
import dev.voidpulsaryt.lcce.marketplace.ChunkListing;
import dev.voidpulsaryt.lcce.marketplace.ChunkOwnership;
import dev.voidpulsaryt.lcce.marketplace.CountryListing;
import dev.voidpulsaryt.lcce.marketplace.MarketplaceManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.Optional;

import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;

/**
 * {@code /lcce market ...} - selling state-owned chunks ("sell as country") and private resales,
 * plus a private owner's own fine-grained access dial for their chunk. Money movement between
 * individual players routes through {@link CurrencyBridge}.
 */
public final class MarketCommand {

    private MarketCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("market")
                .then(Commands.literal("info").executes(MarketCommand::info))
                .then(Commands.literal("sell")
                        .then(Commands.argument("price", longArg(0))
                                .executes(ctx -> sell(ctx, BigInteger.valueOf(getLong(ctx, "price")), null))
                                .then(Commands.literal("everyone")
                                        .executes(ctx -> sell(ctx, BigInteger.valueOf(getLong(ctx, "price")), BuyerRule.EVERYONE)))
                                .then(Commands.literal("allies")
                                        .executes(ctx -> sell(ctx, BigInteger.valueOf(getLong(ctx, "price")), BuyerRule.ALLIES)))
                                .then(Commands.literal("team")
                                        .executes(ctx -> sell(ctx, BigInteger.valueOf(getLong(ctx, "price")), BuyerRule.TEAM)))))
                .then(Commands.literal("cancel").executes(MarketCommand::cancel))
                .then(Commands.literal("buy").executes(MarketCommand::buy))
                .then(Commands.literal("forcebuyback")
                        .then(Commands.argument("price", longArg(0))
                                .executes(ctx -> forceBuyBack(ctx, BigInteger.valueOf(getLong(ctx, "price"))))))
                .then(Commands.literal("label")
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(ctx -> setLabel(ctx, StringArgumentType.getString(ctx, "text")))))
                .then(Commands.literal("mode")
                        .then(Commands.literal("whitelist").executes(ctx -> setMode(ctx, AccessListMode.WHITELIST)))
                        .then(Commands.literal("blacklist").executes(ctx -> setMode(ctx, AccessListMode.BLACKLIST))))
                .then(Commands.literal("whitelist")
                        .then(Commands.literal("add")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> editAccessList(ctx, true))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("player", GameProfileArgument.gameProfile())
                                        .executes(ctx -> editAccessList(ctx, false)))));
    }

    private static int info(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        MarketplaceManager market = MarketplaceManager.get(server);

        ChunkOwnership ownership = market.getOwnership(pos);
        if (ownership == null) {
            source.sendSuccess(() -> Component.translatable("lcce.market.info_state_owned"), false);
        } else {
            String label = ownership.customLabel() != null ? ownership.customLabel() : "-";
            String ownerName = nameOf(server, ownership.ownerId());
            source.sendSuccess(() -> Component.translatable(
                    "lcce.market.info_privately_owned", ownerName, ownership.accessMode(), label
            ), false);
        }

        ChunkListing listing = market.getListing(pos);
        if (listing == null) {
            source.sendSuccess(() -> Component.translatable("lcce.market.info_not_listed"), false);
        } else {
            source.sendSuccess(() -> Component.translatable(
                    "lcce.market.info_listed", CurrencyBridge.formatValue(listing.price()), listing.buyerRule()
            ), false);
        }

        CountryListing countryListing = market.getCountryListingAt(pos);
        if (countryListing != null) {
            source.sendSuccess(() -> Component.translatable(
                    "lcce.market.info_country_listed", countryListing.label(),
                    countryListing.chunks().size(), CurrencyBridge.formatValue(countryListing.price()), countryListing.buyerRule()
            ), false);
        }
        return 1;
    }

    private static int sell(CommandContext<CommandSourceStack> ctx, BigInteger price, BuyerRule explicitRule) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
        if (claimed == null) {
            source.sendFailure(Component.translatable("lcce.market.not_claimed"));
            return 0;
        }

        MarketplaceManager market = MarketplaceManager.get(server);
        if (market.getListing(pos) != null) {
            source.sendFailure(Component.translatable("lcce.market.already_listed"));
            return 0;
        }

        long now = server.overworld().getGameTime();
        long cooldown = LCCEConfig.MARKET_LISTING_COOLDOWN_TICKS.get();

        ChunkOwnership ownership = market.getOwnership(pos);
        java.util.UUID sellerId;
        boolean sellerIsTeam;

        if (ownership != null) {
            if (!ownership.ownerId().equals(player.getUUID())) {
                source.sendFailure(Component.translatable("lcce.market.not_your_chunk"));
                return 0;
            }
            if (now - ownership.lastOwnershipChangeTime() < cooldown) {
                source.sendFailure(Component.translatable("lcce.market.cooldown"));
                return 0;
            }
            sellerId = player.getUUID();
            sellerIsTeam = false;
        } else {
            Team team = claimed.getTeamData().getTeam();
            if (!isOfficerOrBetter(source, team, player)) {
                source.sendFailure(Component.translatable("lcce.market.not_authorized"));
                return 0;
            }
            if (now - claimed.getTimeClaimed() < cooldown) {
                source.sendFailure(Component.translatable("lcce.market.cooldown"));
                return 0;
            }
            sellerId = team.getId();
            sellerIsTeam = true;
        }

        BuyerRule rule = explicitRule != null ? explicitRule : LCCEConfig.MARKET_DEFAULT_BUYER_RULE.get();
        market.listForSale(pos, new ChunkListing(sellerId, sellerIsTeam, price, rule, now));
        source.sendSuccess(() -> Component.translatable("lcce.market.listed", CurrencyBridge.formatValue(price), rule), true);
        return 1;
    }

    private static int cancel(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        MarketplaceManager market = MarketplaceManager.get(source.getServer());

        CountryListing countryListing = market.getCountryListingAt(pos);
        if (countryListing != null) {
            boolean authorized = FTBTeamsAPI.api().getManager().getTeamByID(countryListing.sellerId())
                    .map(team -> isOfficerOrBetter(source, team, player))
                    .orElse(false);
            if (!authorized) {
                source.sendFailure(Component.translatable("lcce.market.not_authorized"));
                return 0;
            }
            market.cancelCountryListing(countryListing.id());
            source.sendSuccess(() -> Component.translatable("lcce.market.country_cancelled", countryListing.label()), true);
            return 1;
        }

        ChunkListing listing = market.getListing(pos);
        if (listing == null) {
            source.sendFailure(Component.translatable("lcce.market.not_listed"));
            return 0;
        }

        boolean authorized;
        if (listing.sellerIsTeam()) {
            authorized = FTBTeamsAPI.api().getManager().getTeamByID(listing.sellerId())
                    .map(team -> isOfficerOrBetter(source, team, player))
                    .orElse(false);
        } else {
            authorized = listing.sellerId().equals(player.getUUID());
        }
        if (!authorized) {
            source.sendFailure(Component.translatable("lcce.market.not_authorized"));
            return 0;
        }

        market.cancelListing(pos);
        source.sendSuccess(() -> Component.translatable("lcce.market.cancelled"), true);
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer buyer)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        ChunkDimPos pos = new ChunkDimPos(buyer.level(), buyer.blockPosition());
        MarketplaceManager market = MarketplaceManager.get(server);

        CountryListing countryListing = market.getCountryListingAt(pos);
        if (countryListing != null) {
            return buyCountry(source, server, buyer, market, countryListing);
        }

        ChunkListing listing = market.getListing(pos);
        if (listing == null) {
            source.sendFailure(Component.translatable("lcce.market.not_listed"));
            return 0;
        }
        ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
        if (claimed == null) {
            // The land was unclaimed out from under the listing - clean it up rather than sell nothing.
            market.cancelListing(pos);
            source.sendFailure(Component.translatable("lcce.market.not_claimed"));
            return 0;
        }

        Team originalTeam = claimed.getTeamData().getTeam();
        if (!listing.buyerRule().isAllowed(originalTeam, buyer.getUUID())) {
            source.sendFailure(Component.translatable("lcce.market.not_allowed_to_buy"));
            return 0;
        }

        if (!CurrencyBridge.withdrawFromPlayer(buyer, listing.price())) {
            source.sendFailure(Component.translatable("lcce.market.insufficient_funds", CurrencyBridge.formatValue(listing.price())));
            return 0;
        }

        if (listing.sellerIsTeam()) {
            FTBTeamsAPI.api().getManager().getTeamByID(listing.sellerId())
                    .ifPresent(sellerTeam -> TeamBalance.refund(sellerTeam, listing.price()));
        } else {
            // If the private seller is offline, the sale still goes through without paying them out.
            ServerPlayer sellerPlayer = server.getPlayerList().getPlayer(listing.sellerId());
            if (sellerPlayer != null) {
                CurrencyBridge.depositToPlayer(sellerPlayer, listing.price());
            }
        }

        long now = server.overworld().getGameTime();
        market.setOwnership(pos, ChunkOwnership.boughtBy(buyer.getUUID(), now));
        market.cancelListing(pos);

        source.sendSuccess(() -> Component.translatable("lcce.market.bought", listing.price()), true);
        return 1;
    }

    /**
     * Buying a "sell as country" package: one price for every chunk in it at once, all
     * transferred to the buyer's private ownership together - the same result as buying each
     * chunk individually, just in a single transaction. Every chunk is re-verified as still
     * validly claimed by the selling team <em>before</em> any money changes hands, so a chunk
     * that got unclaimed out from under the listing fails the whole purchase up front rather than
     * charging the buyer for a package that can't be fully delivered.
     */
    private static int buyCountry(CommandSourceStack source, MinecraftServer server, ServerPlayer buyer,
                                    MarketplaceManager market, CountryListing listing) {
        Optional<Team> sellerTeamOpt = FTBTeamsAPI.api().getManager().getTeamByID(listing.sellerId());
        if (sellerTeamOpt.isEmpty()) {
            market.cancelCountryListing(listing.id());
            source.sendFailure(Component.translatable("lcce.market.not_claimed"));
            return 0;
        }
        Team sellerTeam = sellerTeamOpt.get();

        if (!listing.buyerRule().isAllowed(sellerTeam, buyer.getUUID())) {
            source.sendFailure(Component.translatable("lcce.market.not_allowed_to_buy"));
            return 0;
        }

        for (ChunkDimPos pos : listing.chunks()) {
            ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
            if (claimed == null || !claimed.getTeamData().getTeam().getId().equals(sellerTeam.getId())) {
                market.cancelCountryListing(listing.id());
                source.sendFailure(Component.translatable("lcce.market.country_no_longer_valid"));
                return 0;
            }
        }

        if (!CurrencyBridge.withdrawFromPlayer(buyer, listing.price())) {
            source.sendFailure(Component.translatable("lcce.market.insufficient_funds", CurrencyBridge.formatValue(listing.price())));
            return 0;
        }
        TeamBalance.refund(sellerTeam, listing.price());

        long now = server.overworld().getGameTime();
        for (ChunkDimPos pos : listing.chunks()) {
            market.setOwnership(pos, ChunkOwnership.boughtBy(buyer.getUUID(), now));
        }
        market.cancelCountryListing(listing.id());

        source.sendSuccess(() -> Component.translatable(
                "lcce.market.country_bought", listing.label(), listing.chunks().size(), CurrencyBridge.formatValue(listing.price())
        ), true);
        return 1;
    }

    private static int forceBuyBack(CommandContext<CommandSourceStack> ctx, BigInteger price) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        MinecraftServer server = source.getServer();
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        ClaimedChunk claimed = FTBChunksAPI.api().getManager().getChunk(pos);
        if (claimed == null) {
            source.sendFailure(Component.translatable("lcce.market.not_claimed"));
            return 0;
        }
        Team team = claimed.getTeamData().getTeam();
        if (!isOfficerOrBetter(source, team, player)) {
            source.sendFailure(Component.translatable("lcce.market.not_authorized"));
            return 0;
        }

        MarketplaceManager market = MarketplaceManager.get(server);
        ChunkOwnership ownership = market.getOwnership(pos);
        if (ownership == null) {
            source.sendFailure(Component.translatable("lcce.market.not_privately_owned"));
            return 0;
        }

        if (!TeamBalance.canAfford(team, price)) {
            source.sendFailure(Component.translatable("lcce.claim.insufficient_funds", CurrencyBridge.formatValue(price), CurrencyBridge.formatValue(TeamBalance.get(team))));
            return 0;
        }
        TeamBalance.charge(team, price);

        ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownership.ownerId());
        if (ownerPlayer != null) {
            CurrencyBridge.depositToPlayer(ownerPlayer, price);
        }

        market.removeOwnership(pos);
        market.cancelListing(pos);
        source.sendSuccess(() -> Component.translatable("lcce.market.forced_buyback", CurrencyBridge.formatValue(price)), true);
        return 1;
    }

    private static int setLabel(CommandContext<CommandSourceStack> ctx, String text) {
        return withOwnedChunk(ctx, ownership -> {
            ownership.setCustomLabel(text);
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.market.label_set", text), true);
            return 1;
        });
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx, AccessListMode mode) {
        return withOwnedChunk(ctx, ownership -> {
            ownership.setAccessMode(mode);
            ctx.getSource().sendSuccess(() -> Component.translatable("lcce.market.mode_set", mode), true);
            return 1;
        });
    }

    private static int editAccessList(CommandContext<CommandSourceStack> ctx, boolean add) {
        return withOwnedChunk(ctx, ownership -> {
            try {
                var profiles = GameProfileArgument.getGameProfiles(ctx, "player");
                for (var profile : profiles) {
                    if (add) {
                        ownership.accessList().add(profile.getId());
                    } else {
                        ownership.accessList().remove(profile.getId());
                    }
                }
                ctx.getSource().sendSuccess(() -> Component.translatable(
                        add ? "lcce.market.access_added" : "lcce.market.access_removed"
                ), true);
                return 1;
            } catch (CommandSyntaxException e) {
                ctx.getSource().sendFailure(Component.translatable("lcce.region.player_not_found"));
                return 0;
            }
        });
    }

    private interface OwnedChunkAction {
        int run(ChunkOwnership ownership);
    }

    private static int withOwnedChunk(CommandContext<CommandSourceStack> ctx, OwnedChunkAction action) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }
        ChunkDimPos pos = new ChunkDimPos(player.level(), player.blockPosition());
        MarketplaceManager market = MarketplaceManager.get(source.getServer());
        ChunkOwnership ownership = market.getOwnership(pos);
        if (ownership == null || !ownership.ownerId().equals(player.getUUID())) {
            source.sendFailure(Component.translatable("lcce.market.not_your_chunk"));
            return 0;
        }
        int result = action.run(ownership);
        market.setDirty();
        return result;
    }

    private static boolean isOfficerOrBetter(CommandSourceStack source, Team team, ServerPlayer player) {
        if (source.hasPermission(2)) {
            return true;
        }
        return team.getRankForPlayer(player.getUUID()).isOfficerOrBetter();
    }

    private static String nameOf(MinecraftServer server, java.util.UUID playerId) {
        ServerPlayer online = server.getPlayerList().getPlayer(playerId);
        return online != null ? online.getGameProfile().getName() : playerId.toString();
    }
}
