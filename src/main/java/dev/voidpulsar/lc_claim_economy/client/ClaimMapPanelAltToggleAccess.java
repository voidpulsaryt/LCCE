package dev.voidpulsar.lc_claim_economy.client;

import dev.ftb.mods.ftblibrary.math.XZ;

/** Duck interface for alt-drag chunk type toggling; must live outside mixin packages. */
public interface ClaimMapPanelAltToggleAccess {
    void lcClaimEconomy$selectForAltToggle(XZ chunkPos);

    void lcClaimEconomy$releaseAltToggleSelection();
}
