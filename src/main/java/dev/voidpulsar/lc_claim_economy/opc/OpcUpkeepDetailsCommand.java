package dev.voidpulsar.lc_claim_economy.opc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.service.UpkeepMessageBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.UUID;

/**
 * OP&C equivalent of the FTB side's {@code UpkeepDetailsCommand} "next charge" line. OP&C has
 * no per-owner cost breakdown store like the FTB side's {@code UpkeepBreakdownStore}, so this
 * only reports the countdown to the next charge, not a full cost breakdown.
 */
public final class OpcUpkeepDetailsCommand {
    private OpcUpkeepDetailsCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("opc_upkeep_details").executes(OpcUpkeepDetailsCommand::showDetails)));
    }

    private static int showDetails(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) {
            return 0;
        }
        MinecraftServer server = player.server;

        IServerPartyAPI party = OpenPACServerAPI.get(server).getPartyManager().getPartyByMember(player.getUUID());
        UUID owner = party != null ? party.getId() : player.getUUID();

        long gameTime = server.overworld().getGameTime();
        long nextUpkeepTick = LcClaimEconomySavedData.get(server).getNextOpcUpkeepTick(owner);
        player.displayClientMessage(UpkeepMessageBuilder.buildNextChargeLine(nextUpkeepTick, gameTime), false);
        return 1;
    }
}
