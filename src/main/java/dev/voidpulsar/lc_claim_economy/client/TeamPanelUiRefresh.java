package dev.voidpulsar.lc_claim_economy.client;

import dev.ftb.mods.ftblibrary.util.client.ClientUtils;
import dev.ftb.mods.ftbteams.client.gui.MyTeamScreen;

public final class TeamPanelUiRefresh {
    private TeamPanelUiRefresh() {
    }

    public static void refreshMyTeamScreenIfOpen() {
        MyTeamScreen screen = ClientUtils.getCurrentGuiAs(MyTeamScreen.class);
        if (screen != null) {
            screen.refreshWidgets();
        }
    }
}
