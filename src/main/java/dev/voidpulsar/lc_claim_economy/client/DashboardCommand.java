package dev.voidpulsar.lc_claim_economy.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.client.gui.BankDashboardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Client-only {@code /lcce dashboard} - opens {@link BankDashboardScreen}
 * directly (the screen requests its own data via {@code
 * RequestClaimPricesPayload} once open, so this never touches the server).
 * A more discoverable alternative to {@link ClientDashboardKeybind}'s
 * default-B keybind, since it sits alongside this mod's other {@code /lcce
 * ...} commands instead of requiring a player to find it in Controls.
 */
public final class DashboardCommand {
    private DashboardCommand() {
    }

    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("dashboard").executes(DashboardCommand::open)));
    }

    private static int open(CommandContext<CommandSourceStack> context) {
        if (Minecraft.getInstance().screen == null) {
            Minecraft.getInstance().setScreen(new BankDashboardScreen());
        }
        return 1;
    }
}
