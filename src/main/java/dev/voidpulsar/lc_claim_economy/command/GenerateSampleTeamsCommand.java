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
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal(LcClaimEconomy.COMMAND_ROOT)
                .then(Commands.literal("seed_test_teams")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> seed(context, SampleTeamGenerationService.DEFAULT_COUNT))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> seed(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("clear_test_teams")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> clear(context, 0))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                .executes(context -> clear(context, IntegerArgumentType.getInteger(context, "count")))))
                .then(Commands.literal("count_test_teams")
                        .requires(source -> source.hasPermission(2))
                        .executes(GenerateSampleTeamsCommand::count)));
    }

    private static int count(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!testCommandsUnlocked()) {
            source.sendFailure(Component.translatable("message.lc_claim_economy.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            source.sendFailure(Component.translatable("message.lc_claim_economy.count_test_teams.unavailable"));
            return 0;
        }

        SampleTeamGenerationService.CountResult result = SampleTeamGenerationService.count(source.getServer());
        source.sendSuccess(
                () -> Component.translatable(
                        "message.lc_claim_economy.count_test_teams.done",
                        result.total(),
                        result.withClaims(),
                        SampleTeamGenerationService.DEFAULT_COUNT,
                        result.inDefaultRange()
                ),
                false
        );
        return result.total();
    }

    private static int seed(CommandContext<CommandSourceStack> context, int count) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!testCommandsUnlocked()) {
            source.sendFailure(Component.translatable("message.lc_claim_economy.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded() || !FTBChunksAPI.api().isManagerLoaded()) {
            source.sendFailure(Component.translatable("message.lc_claim_economy.seed_test_teams.unavailable"));
            return 0;
        }

        SampleTeamGenerationService.SeedResult result = SampleTeamGenerationService.seed(source.getServer(), source, count);
        source.sendSuccess(
                () -> Component.translatable(
                        "message.lc_claim_economy.seed_test_teams.done",
                        result.created(),
                        result.skipped(),
                        result.failed(),
                        result.incomingWars(),
                        result.outgoingWars(),
                        result.availableTargets()
                ),
                true
        );
        return result.created() > 0 ? result.created() : (result.skipped() > 0 ? 1 : 0);
    }

    private static int clear(CommandContext<CommandSourceStack> context, int count) {
        CommandSourceStack source = context.getSource();
        if (!testCommandsUnlocked()) {
            source.sendFailure(Component.translatable("message.lc_claim_economy.test_teams.debug_disabled"));
            return 0;
        }
        if (!FTBTeamsAPI.api().isManagerLoaded()) {
            source.sendFailure(Component.translatable("message.lc_claim_economy.clear_test_teams.unavailable"));
            return 0;
        }

        SampleTeamGenerationService.ClearResult result = SampleTeamGenerationService.clear(source.getServer(), source, count);
        source.sendSuccess(
                () -> Component.translatable(
                        "message.lc_claim_economy.clear_test_teams.done",
                        result.deleted(),
                        result.skipped(),
                        result.failed()
                ),
                true
        );
        return result.deleted() > 0 ? result.deleted() : (result.failed() == 0 && result.skipped() == 0 ? 0 : 1);
    }
}
