package dev.voidpulsar.lc_claim_economy.web;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Ties {@link EmbeddedWebServer} to the dedicated server's lifecycle. Registered as
 * a NeoForge event listener rather than started from mod init because the HTTP
 * server needs a live {@link net.minecraft.server.MinecraftServer} reference to
 * serve any data - that only exists once the world has actually finished loading.
 */
public final class WebServerLifecycle {
    private final EmbeddedWebServer embeddedServer = new EmbeddedWebServer();

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        embeddedServer.start(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        embeddedServer.stop();
    }
}
