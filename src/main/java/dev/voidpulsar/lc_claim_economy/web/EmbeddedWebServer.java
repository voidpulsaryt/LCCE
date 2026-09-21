package dev.voidpulsar.lc_claim_economy.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Wraps the JDK's own {@link HttpServer} rather than pulling in a web framework -
 * the whole surface here is a dozen-odd routes serving small JSON payloads, which
 * doesn't justify a new dependency. Two independent things are served, each behind
 * its own config flag so a server owner can run either, both, or neither:
 * <ul>
 *   <li>{@code /} and {@code /api/data} - public, unauthenticated leaderboard/info
 *   page (gated by {@code webEnabled}). Nothing is cached; every hit re-reads live
 *   state from {@link WebDataService} since the underlying data changes constantly
 *   and there's no reason to serve it stale.</li>
 *   <li>{@code /dashboard} and {@code /api/*} - the login-gated per-player
 *   dashboard (gated by {@code webDashboardEnabled}), backed by {@link DashboardApi},
 *   {@link DashboardSessions}, and short-lived session cookies. Only works where FTB
 *   Chunks/Teams is the active backend.</li>
 * </ul>
 * This class owns the route table and decides what each endpoint's request/response
 * looks like; it deliberately doesn't do its own byte-pushing or cookie-parsing -
 * that's {@link WebResponses} and {@link WebSessionCookies} respectively, split out
 * so a change to, say, how bodies get read doesn't require touching route logic.
 */
public final class EmbeddedWebServer {
    private static final String INDEX_RESOURCE = "/web/index.html";
    private static final String DASHBOARD_RESOURCE = "/web/dashboard.html";
    private static final String SHARED_CSS_RESOURCE = "/web/shared.css";
    private static final String SHARED_JS_RESOURCE = "/web/shared.js";

    private HttpServer httpServer;
    private byte[] indexHtml;
    private byte[] dashboardHtml;
    private byte[] sharedCss;
    private byte[] sharedJs;

    public void start(MinecraftServer server) {
        if (!LcClaimEconomyConfig.SERVER.webEnabled.get()) {
            return;
        }

        indexHtml = WebResponses.loadResource(INDEX_RESOURCE);
        if (indexHtml == null) {
            LcClaimEconomy.LOGGER.error("Claim Economy web server: could not load {} from mod resources, not starting.", INDEX_RESOURCE);
            return;
        }

        sharedCss = WebResponses.loadResource(SHARED_CSS_RESOURCE);
        sharedJs = WebResponses.loadResource(SHARED_JS_RESOURCE);
        if (sharedCss == null || sharedJs == null) {
            LcClaimEconomy.LOGGER.error("Claim Economy web server: could not load {}/{} from mod resources, not starting.", SHARED_CSS_RESOURCE, SHARED_JS_RESOURCE);
            return;
        }

        boolean dashboardEnabled = LcClaimEconomyConfig.SERVER.webDashboardEnabled.get();
        if (dashboardEnabled) {
            dashboardHtml = WebResponses.loadResource(DASHBOARD_RESOURCE);
            if (dashboardHtml == null) {
                LcClaimEconomy.LOGGER.error("Claim Economy web server: could not load {}, dashboard disabled for this session.", DASHBOARD_RESOURCE);
                dashboardEnabled = false;
            }
        }

        String bindAddress = LcClaimEconomyConfig.SERVER.webBindAddress.get();
        int port = LcClaimEconomyConfig.SERVER.webPort.get();

        try {
            httpServer = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            httpServer.createContext("/", this::handleIndex);
            httpServer.createContext("/api/data", exchange -> handleData(exchange, server));
            httpServer.createContext("/api/theme", this::handleTheme);
            httpServer.createContext("/web/shared.css", exchange -> handleStaticResource(exchange, sharedCss, "text/css; charset=utf-8"));
            httpServer.createContext("/web/shared.js", exchange -> handleStaticResource(exchange, sharedJs, "application/javascript; charset=utf-8"));

            if (dashboardEnabled) {
                httpServer.createContext("/dashboard", this::handleDashboardPage);
                httpServer.createContext("/api/login", exchange -> handleLogin(exchange, server));
                httpServer.createContext("/api/logout", this::handleLogout);
                httpServer.createContext("/api/me", exchange -> handleMe(exchange, server));
                // JDK HttpServer dispatches by longest-matching-prefix, so registering the more
                // specific /api/dashboard/* action paths below doesn't steal requests away from
                // this exact path - it only ever sees the plain dashboard-data GET.
                httpServer.createContext("/api/dashboard", exchange -> handleDashboardData(exchange, server));
                httpServer.createContext("/api/dashboard/protection", exchange -> handleProtection(exchange, server));
                httpServer.createContext("/api/dashboard/peaceful", exchange -> handlePeaceful(exchange, server));
                httpServer.createContext("/api/dashboard/forceload", exchange -> handleForceLoad(exchange, server));
                httpServer.createContext("/api/dashboard/unclaim", exchange -> handleUnclaim(exchange, server));
                httpServer.createContext("/api/dashboard/war", exchange -> handleWar(exchange, server));
            }

            httpServer.setExecutor(Executors.newFixedThreadPool(4, daemonWebThreadFactory()));
            httpServer.start();
            LcClaimEconomy.LOGGER.info("Claim Economy web server started on {}:{} (dashboard: {})", bindAddress, port, dashboardEnabled ? "enabled" : "disabled");
        } catch (IOException e) {
            LcClaimEconomy.LOGGER.error("Claim Economy web server failed to start on {}:{} - is the port already in use?", bindAddress, port, e);
            httpServer = null;
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            LcClaimEconomy.LOGGER.info("Claim Economy web server stopped.");
        }
    }

    // ---------------- Static pages ----------------

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        WebResponses.sendResource(exchange, 200, indexHtml, "text/html; charset=utf-8");
    }

    private void handleDashboardPage(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        WebResponses.sendResource(exchange, 200, dashboardHtml, "text/html; charset=utf-8");
    }

    private void handleStaticResource(HttpExchange exchange, byte[] body, String contentType) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        WebResponses.sendResource(exchange, 200, body, contentType);
    }

    private void handleData(HttpExchange exchange, MinecraftServer server) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        try {
            WebResponses.sendJson(exchange, 200, WebApiJson.buildDataPayload(server));
        } catch (Exception e) {
            LcClaimEconomy.LOGGER.error("Claim Economy web server: failed to build /api/data response", e);
            WebResponses.sendPlain(exchange, 500, "Internal error building leaderboard data");
        }
    }

    private void handleTheme(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        var config = LcClaimEconomyConfig.SERVER;
        String json = JsonWriter.object()
                .field("siteName", config.webSiteName.get())
                .field("accentColor", config.webAccentColor.get())
                .field("logoUrl", config.webLogoUrl.get())
                .field("customCss", config.webCustomCss.get())
                .field("dashboardEnabled", config.webDashboardEnabled.get())
                .build();
        WebResponses.sendJson(exchange, 200, json);
    }

    // ---------------- Auth ----------------

    private void handleLogin(HttpExchange exchange, MinecraftServer server) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        JsonReader body = JsonReader.parse(WebResponses.readBody(exchange));
        Optional<UUID> playerId = DashboardSessions.LOGIN_CODES.redeem(body.getString("code"));
        if (playerId.isEmpty()) {
            WebResponses.sendJson(exchange, 401, WebResponses.resultJson(false, "Invalid or expired code."));
            return;
        }

        int ttlMinutes = LcClaimEconomyConfig.SERVER.webSessionMinutes.get();
        String token = DashboardSessions.SESSIONS.create(playerId.get(), ttlMinutes);
        exchange.getResponseHeaders().add("Set-Cookie",
                WebSessionCookies.SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + (ttlMinutes * 60));
        WebResponses.sendJson(exchange, 200, WebResponses.resultJson(true, "Logged in."));
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        String token = WebSessionCookies.sessionToken(exchange);
        DashboardSessions.SESSIONS.invalidate(token);
        exchange.getResponseHeaders().add("Set-Cookie", WebSessionCookies.SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
        WebResponses.sendJson(exchange, 200, WebResponses.resultJson(true, "Logged out."));
    }

    private void handleMe(HttpExchange exchange, MinecraftServer server) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        Optional<UUID> playerId = WebSessionCookies.resolveSession(exchange);
        if (playerId.isEmpty()) {
            WebResponses.sendJson(exchange, 401, WebResponses.resultJson(false, "Not logged in."));
            return;
        }
        String json = JsonWriter.object()
                .field("name", DashboardApi.playerName(server, playerId.get()))
                .field("uuid", playerId.get().toString())
                .build();
        WebResponses.sendJson(exchange, 200, json);
    }

    // ---------------- Dashboard data + actions ----------------

    private void handleDashboardData(HttpExchange exchange, MinecraftServer server) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            WebResponses.sendPlain(exchange, 405, "Method Not Allowed");
            return;
        }
        Optional<UUID> playerId = WebSessionCookies.resolveSession(exchange);
        if (playerId.isEmpty()) {
            WebResponses.sendJson(exchange, 401, WebResponses.resultJson(false, "Not logged in."));
            return;
        }
        String json;
        try {
            json = DashboardApi.buildDashboardJson(server, playerId.get());
        } catch (Exception e) {
            LcClaimEconomy.LOGGER.error("Claim Economy web server: failed to build dashboard data", e);
            WebResponses.sendJson(exchange, 500, WebResponses.resultJson(false, "Internal error building dashboard data."));
            return;
        }
        if (json == null) {
            WebResponses.sendJson(exchange, 404, WebResponses.resultJson(false, "No team found for this player."));
            return;
        }
        WebResponses.sendJson(exchange, 200, json);
    }

    private void handleProtection(HttpExchange exchange, MinecraftServer server) throws IOException {
        Optional<UUID> playerId = WebSessionCookies.requirePostSession(exchange);
        if (playerId.isEmpty()) {
            return;
        }
        JsonReader body = JsonReader.parse(WebResponses.readBody(exchange));
        ActionResult result = DashboardApi.applyProtection(server, playerId.get(), body.getString("key"), body.getBoolean("active", false));
        WebResponses.sendJson(exchange, result.success() ? 200 : 400, WebResponses.resultJson(result.success(), result.message()));
    }

    private void handlePeaceful(HttpExchange exchange, MinecraftServer server) throws IOException {
        Optional<UUID> playerId = WebSessionCookies.requirePostSession(exchange);
        if (playerId.isEmpty()) {
            return;
        }
        JsonReader body = JsonReader.parse(WebResponses.readBody(exchange));
        ActionResult result = DashboardApi.setPeaceful(server, playerId.get(), body.getBoolean("active", false));
        WebResponses.sendJson(exchange, result.success() ? 200 : 400, WebResponses.resultJson(result.success(), result.message()));
    }

    private void handleForceLoad(HttpExchange exchange, MinecraftServer server) throws IOException {
        Optional<UUID> playerId = WebSessionCookies.requirePostSession(exchange);
        if (playerId.isEmpty()) {
            return;
        }
        JsonReader body = JsonReader.parse(WebResponses.readBody(exchange));
        ActionResult result = DashboardApi.toggleForceLoad(server, playerId.get(), body.getString("key"), body.getBoolean("load", false));
        WebResponses.sendJson(exchange, result.success() ? 200 : 400, WebResponses.resultJson(result.success(), result.message()));
    }

    private void handleUnclaim(HttpExchange exchange, MinecraftServer server) throws IOException {
        Optional<UUID> playerId = WebSessionCookies.requirePostSession(exchange);
        if (playerId.isEmpty()) {
            return;
        }
        JsonReader body = JsonReader.parse(WebResponses.readBody(exchange));
        ActionResult result = DashboardApi.unclaimChunk(server, playerId.get(), body.getString("key"));
        WebResponses.sendJson(exchange, result.success() ? 200 : 400, WebResponses.resultJson(result.success(), result.message()));
    }

    private void handleWar(HttpExchange exchange, MinecraftServer server) throws IOException {
        Optional<UUID> playerId = WebSessionCookies.requirePostSession(exchange);
        if (playerId.isEmpty()) {
            return;
        }
        JsonReader body = JsonReader.parse(WebResponses.readBody(exchange));
        UUID targetTeamId;
        try {
            targetTeamId = UUID.fromString(body.getString("teamId"));
        } catch (Exception e) {
            WebResponses.sendJson(exchange, 400, WebResponses.resultJson(false, "Invalid team id."));
            return;
        }
        ActionResult result = DashboardApi.toggleWar(server, playerId.get(), targetTeamId);
        WebResponses.sendJson(exchange, result.success() ? 200 : 400, WebResponses.resultJson(result.success(), result.message()));
    }

    // ---------------- Helpers ----------------

    // Daemon so a lingering request thread can never keep the JVM alive past server shutdown -
    // stop() already calls httpServer.stop(0), this is just a belt-and-suspenders guarantee.
    private static ThreadFactory daemonWebThreadFactory() {
        AtomicInteger nextThreadNumber = new AtomicInteger(1);
        return task -> {
            Thread thread = new Thread(task, "lc-claim-economy-web-" + nextThreadNumber.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
