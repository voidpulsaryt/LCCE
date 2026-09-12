package dev.voidpulsar.lc_claim_economy.client;

import dev.voidpulsar.lc_claim_economy.network.SyncWarpsPayload;
import dev.voidpulsar.lc_claim_economy.network.WarpDto;

import java.util.List;

public final class ClientWarps {
    private static boolean enabled = true;
    private static int maxWarps;
    private static long createCostCopper;
    private static long teleportCostCopper;
    private static List<WarpDto> ownWarps = List.of();
    private static List<WarpDto> publicWarps = List.of();

    private ClientWarps() {
    }

    public static void update(SyncWarpsPayload payload) {
        enabled = payload.enabled();
        maxWarps = payload.maxWarps();
        createCostCopper = payload.createCostCopper();
        teleportCostCopper = payload.teleportCostCopper();
        ownWarps = payload.ownWarps();
        publicWarps = payload.publicWarps();
    }

    public static boolean enabled() {
        return enabled;
    }

    public static int maxWarps() {
        return maxWarps;
    }

    public static long createCostCopper() {
        return createCostCopper;
    }

    public static long teleportCostCopper() {
        return teleportCostCopper;
    }

    public static List<WarpDto> ownWarps() {
        return ownWarps;
    }

    public static List<WarpDto> publicWarps() {
        return publicWarps;
    }
}
