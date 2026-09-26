package dev.voidpulsaryt.lcce.client;

import dev.voidpulsaryt.lcce.client.xaero.XaeroClaimIntegration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Entry point for everything client-only. Only ever called from behind a
 * {@code FMLEnvironment.dist.isClient()} check in {@code LCCEMod}, so nothing in this class or
 * anything it references gets loaded on a dedicated server.
 */
public final class ClientSetup {

    private ClientSetup() {}

    public static void init(IEventBus modEventBus) {
        ClaimMapCache.init();

        modEventBus.addListener(ClaimKeyBindings::register);
        NeoForge.EVENT_BUS.addListener(ClaimKeyBindings::onClientTick);

        if (ModList.get().isLoaded("xaerominimap")) {
            NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> XaeroClaimIntegration.tryRegister());
        }
        // World Map claim visualization is handled by LcceClaimHighlighter (registered via
        // WorldMapSessionMixin) instead of a hand-rolled element renderer - Xaero's own tile
        // renderer positions it, so there's no client-tick registration step needed here.
    }
}
