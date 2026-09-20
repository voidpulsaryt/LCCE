package dev.voidpulsar.lc_claim_economy.client;

import dev.voidpulsar.lc_claim_economy.network.ConflictStateBroadcastPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictTeamEntry;

import java.util.List;

public final class ClientConflictState {
    private static long baseUpkeepCopper;
    private static long incomingWarCopper;
    private static long outgoingWarCopper;
    private static double warCostMultiplier = 1.2D;
    private static List<ConflictTeamEntry> incoming = List.of();
    private static List<ConflictTeamEntry> outgoing = List.of();
    private static List<ConflictTeamEntry> availableTargets = List.of();
    private static boolean canManageWar;
    private static boolean warModuleEnabled = true;
    private static boolean warDeclarationWindowOpen = true;
    private static String warDeclarationWindowDescription = "";

    private ClientConflictState() {
    }

    public static void setWarModuleEnabled(boolean enabled) {
        warModuleEnabled = enabled;
    }

    public static boolean warModuleEnabled() {
        return warModuleEnabled;
    }

    public static void update(ConflictStateBroadcastPayload payload) {
        baseUpkeepCopper = payload.baseUpkeepCopper();
        incomingWarCopper = payload.incomingWarCopper();
        outgoingWarCopper = payload.outgoingWarCopper();
        warCostMultiplier = payload.warCostMultiplier();
        incoming = List.copyOf(payload.incoming());
        outgoing = List.copyOf(payload.outgoing());
        availableTargets = List.copyOf(payload.availableTargets());
        canManageWar = payload.canManageWar();
        warDeclarationWindowOpen = payload.warDeclarationWindowOpen();
        warDeclarationWindowDescription = payload.warDeclarationWindowDescription();
    }

    public static long baseUpkeepCopper() {
        return baseUpkeepCopper;
    }

    public static long incomingWarCopper() {
        return incomingWarCopper;
    }

    public static long outgoingWarCopper() {
        return outgoingWarCopper;
    }

    public static long totalWarCopper() {
        return incomingWarCopper + outgoingWarCopper;
    }

    public static double warCostMultiplier() {
        return warCostMultiplier;
    }

    public static List<ConflictTeamEntry> incoming() {
        return incoming;
    }

    public static List<ConflictTeamEntry> outgoing() {
        return outgoing;
    }

    public static List<ConflictTeamEntry> availableTargets() {
        return availableTargets;
    }

    public static boolean canManageWar() {
        return canManageWar;
    }

    public static boolean warDeclarationWindowOpen() {
        return warDeclarationWindowOpen;
    }

    public static String warDeclarationWindowDescription() {
        return warDeclarationWindowDescription;
    }
}
