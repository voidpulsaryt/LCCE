package dev.voidpulsar.lc_claim_economy.client;

import dev.voidpulsar.lc_claim_economy.network.ConflictStateBroadcastPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictTeamEntry;

import java.util.List;

/**
 * Client-side mirror of the standing-conflict state for the player's own team,
 * refreshed whenever a {@link ConflictStateBroadcastPayload} lands. Everything
 * here is read-only from the UI's perspective - ConflictScreen and the team-hub
 * war button just render whatever the last broadcast said.
 */
public final class ClientConflictState {
    private static boolean moduleEnabledFlag = true;
    private static boolean manageWarFlag;
    private static boolean declareWindowOpenFlag = true;
    private static String declareWindowDescriptionText = "";
    private static long baseUpkeepCopperAmount;
    private static long incomingWarCopperAmount;
    private static long outgoingWarCopperAmount;
    private static double warCostMultiplierValue = 1.2D;
    private static List<ConflictTeamEntry> incomingEntries = List.of();
    private static List<ConflictTeamEntry> outgoingEntries = List.of();
    private static List<ConflictTeamEntry> availableTargetEntries = List.of();

    private ClientConflictState() {
    }

    /** Set from the pricing broadcast rather than the conflict broadcast - the war feature toggle lives with the rest of the module's on/off switches. */
    public static void setWarModuleEnabled(boolean isEnabled) {
        moduleEnabledFlag = isEnabled;
    }

    public static boolean warModuleEnabled() {
        return moduleEnabledFlag;
    }

    public static void update(ConflictStateBroadcastPayload snapshot) {
        manageWarFlag = snapshot.canManageWar();
        declareWindowOpenFlag = snapshot.warDeclarationWindowOpen();
        declareWindowDescriptionText = snapshot.warDeclarationWindowDescription();
        baseUpkeepCopperAmount = snapshot.baseUpkeepCopper();
        incomingWarCopperAmount = snapshot.incomingWarCopper();
        outgoingWarCopperAmount = snapshot.outgoingWarCopper();
        warCostMultiplierValue = snapshot.warCostMultiplier();
        incomingEntries = List.copyOf(snapshot.incoming());
        outgoingEntries = List.copyOf(snapshot.outgoing());
        availableTargetEntries = List.copyOf(snapshot.availableTargets());
    }

    public static boolean canManageWar() {
        return manageWarFlag;
    }

    public static boolean warDeclarationWindowOpen() {
        return declareWindowOpenFlag;
    }

    public static String warDeclarationWindowDescription() {
        return declareWindowDescriptionText;
    }

    public static long baseUpkeepCopper() {
        return baseUpkeepCopperAmount;
    }

    public static long incomingWarCopper() {
        return incomingWarCopperAmount;
    }

    public static long outgoingWarCopper() {
        return outgoingWarCopperAmount;
    }

    public static long totalWarCopper() {
        return incomingWarCopperAmount + outgoingWarCopperAmount;
    }

    public static double warCostMultiplier() {
        return warCostMultiplierValue;
    }

    public static List<ConflictTeamEntry> incoming() {
        return incomingEntries;
    }

    public static List<ConflictTeamEntry> outgoing() {
        return outgoingEntries;
    }

    public static List<ConflictTeamEntry> availableTargets() {
        return availableTargetEntries;
    }
}
