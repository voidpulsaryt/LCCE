package dev.voidpulsaryt.lcce.client.xaero;

import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.module.MinimapSession;

/**
 * Registers {@link ClaimBorderRenderer} into Xaero's own minimap render pipeline via
 * {@code MinimapElementRendererHandler#add}. The minimap's session doesn't exist yet when the mod
 * loads, so this is retried (cheaply) once per client tick until it succeeds, then stops.
 */
public final class XaeroClaimIntegration {

    private static boolean registered = false;

    private XaeroClaimIntegration() {}

    public static void tryRegister() {
        if (registered) {
            return;
        }
        MinimapSession session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            return;
        }
        var minimapInterface = session.getProcessor().getMinimapInterface();
        if (minimapInterface == null) {
            return;
        }
        minimapInterface.getOverMapRendererHandler().add(new ClaimBorderRenderer());
        registered = true;
    }
}
