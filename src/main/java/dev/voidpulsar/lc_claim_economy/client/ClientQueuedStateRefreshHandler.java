package dev.voidpulsar.lc_claim_economy.client;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.voidpulsar.lc_claim_economy.network.PricingRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestLandChunksPayload;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictStateRequestPayload;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientQueuedStateRefreshHandler {
    public ClientQueuedStateRefreshHandler() {
        TeamEvent.CLIENT_PROPERTIES_CHANGED.register(event -> {
            PacketDistributor.sendToServer(new PricingRequestPayload());
            PacketDistributor.sendToServer(new QueuedStateRequestPayload());
            PacketDistributor.sendToServer(new RequestLandChunksPayload());
            PacketDistributor.sendToServer(new ConflictStateRequestPayload());

            // Only push values into an open properties screen when the change
            // concerns the player's own team; properties of other teams also
            // sync to all clients.
            if (isSelfTeam(event.getTeam())) {
                QueuedStateUiRefresh.syncOpenScreenValues(event.getTeam());
            }
        });
    }

    private static boolean isSelfTeam(Team team) {
        if (!FTBTeamsAPI.api().isClientManagerLoaded()) {
            return false;
        }
        Team selfTeam = FTBTeamsAPI.api().getClientManager().selfTeam();
        return selfTeam != null && selfTeam.getTeamId().equals(team.getTeamId());
    }
}
