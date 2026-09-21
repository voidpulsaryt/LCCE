package dev.voidpulsar.lc_claim_economy.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.service.SampleTeamGenerationService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /lcce seed_test_teams|clear_test_teams|count_test_teams} - generates
 * throwaway FTB Teams (with claims and war relationships) for exercising the
 * economy at scale without hand-creating dozens of teams. Gated behind
 * {@code debugTestTeamCommands} on top of the permission-2 requirement so it
 * can't be run by accident on a live world once testing is done.
 */
public final class GenerateSampleTeamsCommand {
    private GenerateSampleTeamsCommand() {
    }

    private static boolean testCommandsUnlocked() {
        return LcClaimEconomyConfig.SERVER.debugTestTeamCommands.get();
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> commandTree = event.getDispatcher();
        commandTree.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("seed_test_teams")
                        .requires(caller -> caller.hasPermission(2))
                        .executes(invocation -> seed(invocation, SampleTeamGenerationService.DEFAULT_COUNT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(invocation -> seed(invocation, IntegerArgumentType.getInteger(invocation, "count")))))
                .then(Commands.literal("clear_test_teams")
                        .requires(caller -> caller.hasPermission(2))
                        .executes(invocation -> clear(invocation, 0))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(invocation -> clear(invocation, IntegerArgumentType.getInteger(invocation, "count")))))
                .then(Commands.literal("count_test_teams")
                        .requires(caller -> caller.hasPermission(2))
                        .executes(GenerateSampleTeamsCommand::count)));
    }

    private static int count(CommandContext<CommandSourceStack> invocation) {
        CommandSourceStack issuer = invocation.getSource();
        if (!testCommandsUnlocked()) {
            issuer.sendFailure(Component.translatable("message.lc_claim_economy.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            issuer.sendFailure(Component.translatable("message.lc_claim_economy.count_test_teams.unavailable"));
            return 0;
        }

        SampleTeamGenerationService.CountResult tally = SampleTeamGenerationService.count(issuer.getServer());
        issuer.sendSuccess(
                () -> Component.translatable(
                        "message.lc_claim_economy.count_test_teams.done",
                        tally.total(),
                        tally.withClaims(),
                        SampleTeamGenerationService.DEFAULT_COUNT,
                        tally.inDefaultRange()
                ),
                false
        );
        return tally.total();
    }

    private static int seed(CommandContext<CommandSourceStack> invocation, int amount) throws CommandSyntaxException {
        CommandSourceStack issuer = invocation.getSource();
        if (!testCommandsUnlocked()) {
            issuer.sendFailure(Component.translatable("message.lc_claim_economy.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            issuer.sendFailure(Component.translatable("message.lc_claim_economy.seed_test_teams.unavailable"));
            return 0;
        }

        SampleTeamGenerationService.SeedResult outcome = SampleTeamGenerationService.seed(issuer.getServer(), issuer, amount);
        issuer.sendSuccess(
                () -> Component.translatable(
                        "message.lc_claim_economy.seed_test_teams.done",
                        outcome.created(),
                        outcome.skipped(),
                        outcome.failed(),
                        outcome.incomingWars(),
                        outcome.outgoingWars(),
                        outcome.availableTargets()
                ),
                true
        );
        return outcome.created() > 0 ? outcome.created() : (outcome.skipped() > 0 ? 1 : 0);
    }

    private static int clear(CommandContext<CommandSourceStack> invocation, int amount) {
        CommandSourceStack issuer = invocation.getSource();
        if (!testCommandsUnlocked()) {
            issuer.sendFailure(Component.translatable("message.lc_claim_economy.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            issuer.sendFailure(Component.translatable("message.lc_claim_economy.clear_test_teams.unavailable"));
            return 0;
        }

        SampleTeamGenerationService.ClearResult outcome = SampleTeamGenerationService.clear(issuer.getServer(), issuer, amount);
        issuer.sendSuccess(
                () -> Component.translatable(
                        "message.lc_claim_economy.clear_test_teams.done",
                        outcome.deleted(),
                        outcome.skipped(),
                        outcome.failed()
                ),
                true
        );
        return outcome.deleted() > 0 ? outcome.deleted() : (outcome.failed() == 0 && outcome.skipped() == 0 ? 0 : 1);
    }
}
