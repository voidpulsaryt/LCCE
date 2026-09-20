package dev.voidpulsar.lc_claim_economy.opc;

import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigManagerAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Resolves the OP&C {@link IPlayerConfigAPI} config behind an owner ID
 * (solo player, or a party via the party owner's "party claims" config -
 * see {@link IPlayerConfigManagerAPI#getPartyOwnerConfig(UUID)}), and
 * toggles the {@link PlayerConfigOptions#FORCELOAD} option on it.
 * <p>
 * OP&C has no team-level "protection locked" switch like FTB Chunks does
 * (see {@code SafeguardEnforcementService} on the FTB side); {@code FORCELOAD} is the
 * closest per-owner equivalent exposed by its public config API, so
 * {@link OpcBillingCycleService} toggles it off in place of a real protection
 * lock when upkeep can't be paid, and restores it once the owner's balance
 * recovers. {@link OpcSafeguardPricing} reads the same resolved config to
 * price protection upkeep.
 */
final class OpcPlayerConfigAccess {
    private OpcPlayerConfigAccess() {
    }

    @Nullable
    static IPlayerConfigAPI resolveConfig(MinecraftServer server, UUID owner, boolean partyOwned) {
        IPlayerConfigManagerAPI configManager = OpenPACServerAPI.get(server).getPlayerConfigManager();
        if (!partyOwned) {
            return configManager.getLoadedConfig(owner);
        }
        IServerPartyAPI party = OpcAccountResolver.resolveParty(server, owner);
        if (party == null) {
            return null;
        }
        return configManager.getPartyOwnerConfig(party.getOwner().getUUID());
    }

    /** @return true if the option was found and set (or already at the target value). */
    static boolean setForceloadEnabled(MinecraftServer server, UUID owner, boolean partyOwned, boolean enabled) {
        IPlayerConfigAPI config = resolveConfig(server, owner, partyOwned);
        if (config == null) {
            return false;
        }
        IPlayerConfigAPI.SetResult result = config.tryToSet(PlayerConfigOptions.FORCELOAD, enabled);
        return result == IPlayerConfigAPI.SetResult.SUCCESS || result == IPlayerConfigAPI.SetResult.DEFAULTED;
    }
}
