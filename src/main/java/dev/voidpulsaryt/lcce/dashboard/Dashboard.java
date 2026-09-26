package dev.voidpulsaryt.lcce.dashboard;

import com.sun.net.httpserver.HttpServer;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * A small read-only HTTP server showing team balances, regions, wars, and marketplace listings -
 * built on the JDK's own {@code com.sun.net.httpserver}, so no extra dependency is needed. Off by
 * default ({@code dashboard.enabled=false}) since it opens a network port.
 * <p>
 * HTTP handlers run on their own thread pool, never the server's main thread, so every handler in
 * {@link DashboardHttpHandler} reads Minecraft state via {@code server.submit(...)} and blocks on
 * the resulting future rather than touching server-owned objects directly from a request thread.
 */
public final class Dashboard {

    private static final Logger LOGGER = LoggerFactory.getLogger("LCCE/Dashboard");

    private static HttpServer httpServer;

    private Dashboard() {}

    public static void start(MinecraftServer server) {
        if (!LCCEConfig.DASHBOARD_ENABLED.get()) {
            return;
        }
        int port = LCCEConfig.DASHBOARD_PORT.get();
        try {
            httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            httpServer.createContext("/", new DashboardHttpHandler(server));
            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.start();
            LOGGER.info("LCCE dashboard listening on port {}", port);
        } catch (IOException e) {
            LOGGER.error("Failed to start LCCE dashboard on port {}", port, e);
            httpServer = null;
        }
    }

    public static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }
}
