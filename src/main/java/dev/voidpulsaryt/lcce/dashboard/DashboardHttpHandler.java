package dev.voidpulsaryt.lcce.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.bounty.BountyManager;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.integration.ftb.TeamBalance;
import dev.voidpulsaryt.lcce.marketplace.ChunkListing;
import dev.voidpulsaryt.lcce.marketplace.CountryListing;
import dev.voidpulsaryt.lcce.marketplace.MarketplaceManager;
import dev.voidpulsaryt.lcce.nation.Nation;
import dev.voidpulsaryt.lcce.nation.NationManager;
import dev.voidpulsaryt.lcce.region.ProtectionLineItem;
import dev.voidpulsaryt.lcce.region.Region;
import dev.voidpulsaryt.lcce.region.RegionManager;
import dev.voidpulsaryt.lcce.economy.UpkeepPricing;
import dev.voidpulsaryt.lcce.war.War;
import dev.voidpulsaryt.lcce.war.WarManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/**
 * Renders the dashboard's pages. Every method here that touches Minecraft/FTB/LCCE state runs
 * inside {@code server.submit(...)} so it executes on the main server thread - this class's own
 * {@code handle} method runs on an HTTP worker thread and only ever sees the finished HTML string.
 */
final class DashboardHttpHandler implements HttpHandler {

    private final MinecraftServer server;
    private final byte[] bannerPng;

    DashboardHttpHandler(MinecraftServer server) {
        this.server = server;
        this.bannerPng = readClasspathResource("/assets/lcce/banner.png");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();

        if (path.equals("/banner.png") && bannerPng != null) {
            exchange.getResponseHeaders().add("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, bannerPng.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bannerPng);
            }
            return;
        }

        String html;
        try {
            if (path.startsWith("/team/")) {
                String teamName = URLDecoder.decode(path.substring("/team/".length()), StandardCharsets.UTF_8);
                html = server.submit(() -> renderTeam(teamName)).get();
            } else if (path.equals("/market")) {
                html = server.submit(this::renderMarket).get();
            } else {
                html = server.submit(this::renderHome).get();
            }
        } catch (InterruptedException | ExecutionException e) {
            html = page("Error", "<p>Something went wrong rendering this page.</p>");
        }

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static byte[] readClasspathResource(String path) {
        try (InputStream in = DashboardHttpHandler.class.getResourceAsStream(path)) {
            return in != null ? in.readAllBytes() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private String renderHome() {
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return page("LCCE Dashboard", "<p>Team manager isn't loaded yet.</p>");
        }
        StringBuilder rows = new StringBuilder();
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            rows.append("<tr><td><a href=\"/team/").append(urlEncode(team.getShortName())).append("\">")
                    .append(escape(team.getName().getString())).append("</a></td><td>")
                    .append(escape(CurrencyBridge.formatValue(TeamBalance.get(team)).getString())).append("</td></tr>");
        }
        StringBuilder bountyRows = new StringBuilder();
        for (Map.Entry<java.util.UUID, BigInteger> entry : BountyManager.get(server).getAllBounties().entrySet()) {
            var online = server.getPlayerList().getPlayer(entry.getKey());
            String name = online != null ? online.getGameProfile().getName() : entry.getKey().toString();
            bountyRows.append("<tr><td>").append(escape(name)).append("</td><td>")
                    .append(escape(CurrencyBridge.formatValue(entry.getValue()).getString())).append("</td></tr>");
        }
        String bountySection = bountyRows.isEmpty() ? "" :
                "<h2>Active bounties</h2><table><tr><th>Target</th><th>Reward</th></tr>" + bountyRows + "</table>";

        StringBuilder nationRows = new StringBuilder();
        for (Nation nation : NationManager.get(server).getAllNations()) {
            nationRows.append("<tr><td>").append(escape(nation.name())).append("</td><td>")
                    .append(escape(nameOf(nation.capitalTeamId()))).append("</td><td>")
                    .append(nation.memberTeamIds().size()).append("</td><td>")
                    .append(escape(CurrencyBridge.formatValue(nation.balance()).getString())).append("</td></tr>");
        }
        String nationSection = nationRows.isEmpty() ? "" :
                "<h2>Nations</h2><table><tr><th>Nation</th><th>Capital</th><th>Members</th><th>Balance</th></tr>"
                        + nationRows + "</table>";

        String body = "<h1>Teams</h1><table><tr><th>Team</th><th>Balance</th></tr>" + rows + "</table>"
                + nationSection
                + bountySection
                + "<p><a href=\"/market\">Marketplace listings &rarr;</a></p>";
        return page("LCCE Dashboard", body);
    }

    private String renderTeam(String teamShortName) {
        Optional<Team> teamOpt = FTBTeamsAPI.api().getManager().getTeamByName(teamShortName);
        if (teamOpt.isEmpty()) {
            return page("Team not found", "<p>No team named '" + escape(teamShortName) + "'.</p>");
        }
        Team team = teamOpt.get();
        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(escape(team.getName().getString())).append("</h1>");
        body.append("<p><strong>Balance:</strong> ").append(escape(CurrencyBridge.formatValue(TeamBalance.get(team)).getString())).append("</p>");

        int forceLoadedCount = FTBChunksAPI.api().getManager().getOrCreateData(team).getForceLoadedChunks().size();
        body.append("<p><strong>Force-loaded chunks:</strong> ").append(forceLoadedCount).append("</p>");

        Nation nation = NationManager.get(server).getNationForTeam(team.getId());
        if (nation != null) {
            body.append("<p><strong>Nation:</strong> ").append(escape(nation.name()));
            if (nation.capitalTeamId().equals(team.getId())) {
                body.append(" (capital)");
            }
            body.append("</p>");
        }

        body.append("<h2>Regions</h2><table><tr><th>Name</th><th>Chunks</th><th>Upkeep</th></tr>");
        List<Region> regions = RegionManager.get(server).getRegionsForTeam(team.getId());
        for (Region region : regions) {
            BigInteger regionUpkeep = BigInteger.ZERO;
            for (ProtectionLineItem item : ProtectionLineItem.values()) {
                if (UpkeepPricing.isIntended(region, item)) {
                    regionUpkeep = regionUpkeep.add(UpkeepPricing.costOf(region, item));
                }
            }
            body.append("<tr><td>").append(escape(region.name())).append("</td><td>")
                    .append(region.chunks().size()).append("</td><td>")
                    .append(escape(CurrencyBridge.formatValue(regionUpkeep).getString())).append("/period</td></tr>");
        }
        body.append("</table>");

        WarManager warManager = WarManager.get(server);
        body.append("<h2>Wars</h2><ul>");
        for (War war : warManager.getOutgoingWars(team.getId())) {
            body.append("<li>Attacking ").append(escape(nameOf(war.defenderTeamId())))
                    .append(war.isSiege() ? " (siege)" : "").append("</li>");
        }
        for (War war : warManager.getIncomingWars(team.getId())) {
            body.append("<li>Defending against ").append(escape(nameOf(war.attackerTeamId())))
                    .append(war.isSiege() ? " (siege)" : "").append("</li>");
        }
        body.append("</ul>");

        return page(team.getName().getString(), body.toString());
    }

    private String renderMarket() {
        StringBuilder rows = new StringBuilder();
        // Listings are keyed by chunk in MarketplaceManager, which doesn't expose a flat list
        // directly, so this walks every team's claimed chunks looking for one. Fine at dashboard
        // scale; would need an index if this grew to a very large server.
        if (FTBTeamsAPI.api().isManagerLoaded()) {
            for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
                for (var claimed : FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks()) {
                    ChunkListing listing = MarketplaceManager.get(server).getListing(claimed.getPos());
                    if (listing == null) {
                        continue;
                    }
                    rows.append("<tr><td>").append(escape(team.getName().getString())).append("</td><td>")
                            .append(escape(CurrencyBridge.formatValue(listing.price()).getString())).append("</td><td>")
                            .append(escape(listing.buyerRule().name())).append("</td></tr>");
                }
            }
        }

        StringBuilder countryRows = new StringBuilder();
        for (CountryListing listing : allCountryListings()) {
            countryRows.append("<tr><td>").append(escape(listing.label())).append("</td><td>")
                    .append(listing.chunks().size()).append("</td><td>")
                    .append(escape(CurrencyBridge.formatValue(listing.price()).getString())).append("</td><td>")
                    .append(escape(listing.buyerRule().name())).append("</td></tr>");
        }
        String countrySection = countryRows.isEmpty() ? "" :
                "<h2>Country listings</h2><table><tr><th>Name</th><th>Chunks</th><th>Price</th><th>Buyers allowed</th></tr>"
                        + countryRows + "</table>";

        String body = "<h1>Marketplace</h1><table><tr><th>Claiming team</th><th>Price</th><th>Buyers allowed</th></tr>"
                + rows + "</table>" + countrySection + "<p><a href=\"/\">&larr; Teams</a></p>";
        return page("Marketplace", body);
    }

    /**
     * {@code MarketplaceManager} only exposes country listings via a per-chunk lookup, not a flat
     * list - this walks every claimed chunk looking for one, same tradeoff as the single-chunk
     * listings above, deduplicated since a listing's chunks would otherwise each surface it once.
     */
    private java.util.List<CountryListing> allCountryListings() {
        java.util.Set<java.util.UUID> seen = new java.util.HashSet<>();
        java.util.List<CountryListing> result = new java.util.ArrayList<>();
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            return result;
        }
        MarketplaceManager market = MarketplaceManager.get(server);
        for (Team team : FTBTeamsAPI.api().getManager().getTeams()) {
            for (var claimed : FTBChunksAPI.api().getManager().getOrCreateData(team).getClaimedChunks()) {
                CountryListing listing = market.getCountryListingAt(claimed.getPos());
                if (listing != null && seen.add(listing.id())) {
                    result.add(listing);
                }
            }
        }
        return result;
    }

    private String nameOf(java.util.UUID teamId) {
        return FTBTeamsAPI.api().getManager().getTeamByID(teamId).map(t -> t.getName().getString()).orElse(teamId.toString());
    }

    private static String page(String title, String body) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>" + escape(title) + "</title>"
                + "<style>body{font-family:sans-serif;margin:2em;background:#1c1c1c;color:#e7ded0}"
                + "header{margin-bottom:1em}header img{max-width:100%;height:auto;image-rendering:pixelated}"
                + "table{border-collapse:collapse;width:100%;margin:1em 0}"
                + "th,td{border:1px solid #444;padding:6px 10px;text-align:left}"
                + "a{color:#f2c94c}</style></head><body>"
                + "<header><a href=\"/\"><img src=\"/banner.png\" alt=\"LCCE\"></a></header>"
                + body + "</body></html>";
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
