package dev.voidpulsaryt.lcce.integration.ftb;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.voidpulsaryt.lcce.bounty.BountyManager;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.math.BigInteger;
import java.util.Map;

import static com.mojang.brigadier.arguments.LongArgumentType.getLong;
import static com.mojang.brigadier.arguments.LongArgumentType.longArg;

/**
 * {@code /lcce bounty place|list} - a personal currency reward on a player's head, paid straight
 * out of your own wallet (not your team's balance, since this is a personal vendetta) and claimed
 * automatically by whoever kills them.
 */
public final class BountyCommand {

    private BountyCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("bounty")
                .then(Commands.literal("list").executes(BountyCommand::list))
                .then(Commands.literal("place")
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("amount", longArg(1))
                                        .executes(ctx -> place(ctx, EntityArgument.getPlayer(ctx, "target"), BigInteger.valueOf(getLong(ctx, "amount")))))));
    }

    private static int place(CommandContext<CommandSourceStack> ctx, ServerPlayer target, BigInteger amount) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer placer)) {
            return 0;
        }
        if (target.getUUID().equals(placer.getUUID())) {
            source.sendFailure(Component.translatable("lcce.bounty.cannot_place_on_self"));
            return 0;
        }
        BigInteger minimum = BigInteger.valueOf(LCCEConfig.BOUNTY_MINIMUM.get());
        if (amount.compareTo(minimum) < 0) {
            source.sendFailure(Component.translatable("lcce.bounty.below_minimum", CurrencyBridge.formatValue(minimum)));
            return 0;
        }
        if (!CurrencyBridge.withdrawFromPlayer(placer, amount)) {
            source.sendFailure(Component.translatable("lcce.bounty.cannot_afford", CurrencyBridge.formatValue(amount)));
            return 0;
        }

        BountyManager bounties = BountyManager.get(source.getServer());
        bounties.addBounty(target.getUUID(), amount);
        BigInteger newTotal = bounties.getBounty(target.getUUID());

        source.sendSuccess(() -> Component.translatable(
                "lcce.bounty.placed", target.getGameProfile().getName(), CurrencyBridge.formatValue(amount), CurrencyBridge.formatValue(newTotal)
        ), true);
        target.sendSystemMessage(Component.translatable("lcce.bounty.notice", CurrencyBridge.formatValue(newTotal)));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Map<java.util.UUID, BigInteger> bounties = BountyManager.get(source.getServer()).getAllBounties();
        if (bounties.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("lcce.bounty.none"), false);
            return 1;
        }
        bounties.forEach((targetId, amount) -> {
            ServerPlayer online = source.getServer().getPlayerList().getPlayer(targetId);
            String name = online != null ? online.getGameProfile().getName() : targetId.toString();
            source.sendSuccess(() -> Component.translatable("lcce.bounty.list_entry", name, CurrencyBridge.formatValue(amount)), false);
        });
        return bounties.size();
    }
}
