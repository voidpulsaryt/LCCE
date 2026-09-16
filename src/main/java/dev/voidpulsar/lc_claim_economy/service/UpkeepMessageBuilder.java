package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.util.DurationFormat;
import dev.voidpulsar.lc_claim_economy.util.MoneyMessageUtil;
import dev.voidpulsar.lc_claim_economy.util.MoneyUtil;
import dev.voidpulsar.lc_claim_economy.util.UpkeepPeriodFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

public final class UpkeepMessageBuilder {
    private static final String DETAILS_COMMAND = "/" + LcClaimEconomy.MOD_ID + " upkeep_details";

    private UpkeepMessageBuilder() {
    }

    public static Component buildUnaffordableRestorationMessage(List<TeamProperty<?>> unaffordable) {
        MutableComponent msg = Component.literal("⌛ ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("message.lc_claim_economy.unaffordable_restoration_header", unaffordable.size())
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        appendProtectionList(msg, unaffordable, ChatFormatting.YELLOW);
        msg.append("\n");
        msg.append(Component.translatable("message.lc_claim_economy.unaffordable_restoration_hint").withStyle(ChatFormatting.GRAY));
        return msg;
    }

    public static Component buildRestorationSummary(List<TeamProperty<?>> restored, List<String> restoredWarNames) {
        MutableComponent msg = Component.empty();
        boolean wroteAnything = false;

        if (!restored.isEmpty()) {
            msg.append(Component.translatable("message.lc_claim_economy.restoration_header", restored.size())
                    .withStyle(ChatFormatting.GREEN));
            appendProtectionList(msg, restored, ChatFormatting.WHITE);
            wroteAnything = true;
        }

        for (String warName : restoredWarNames) {
            if (wroteAnything) msg.append("\n");
            msg.append(Component.translatable("message.lc_claim_economy.war_active", warName)
                    .withStyle(ChatFormatting.GRAY));
            wroteAnything = true;
        }

        return msg;
    }

    public static Component buildSuspensionSummary(List<TeamProperty<?>> suspended, boolean warsSuspended) {
        MutableComponent msg = Component.empty();
        boolean wroteAnything = false;

        if (!suspended.isEmpty()) {
            msg.append(Component.translatable("message.lc_claim_economy.suspension_header", suspended.size())
                    .withStyle(ChatFormatting.YELLOW));
            appendProtectionList(msg, suspended, ChatFormatting.WHITE);
            wroteAnything = true;
        }

        if (warsSuspended) {
            if (wroteAnything) msg.append("\n");
            msg.append(Component.translatable(
                    wroteAnything ? "message.lc_claim_economy.suspension_wars" : "message.lc_claim_economy.suspension_wars_header"
            ).withStyle(ChatFormatting.YELLOW));
            wroteAnything = true;
        }

        if (wroteAnything) {
            msg.append("\n");
            msg.append(Component.translatable("message.lc_claim_economy.suspension_hint").withStyle(ChatFormatting.GRAY));
        }

        return msg;
    }

    private static void appendProtectionList(MutableComponent msg, List<TeamProperty<?>> properties, ChatFormatting color) {
        for (TeamProperty<?> property : properties) {
            msg.append("\n");
            msg.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            String labelKey = "message.lc_claim_economy.upkeep_priority.protection."
                    + ProtectionPricing.propertyKey(property);
            msg.append(Component.translatable(labelKey).withStyle(color));
        }
    }

    public static Component buildSummary(UpkeepBreakdown breakdown) {
        Component amount = MoneyMessageUtil.formatValue(breakdown.totalCost());
        Component period = UpkeepPeriodFormat.format(breakdown.periodMinutes());

        MutableComponent message = Component.translatable("message.lc_claim_economy.upkeep_paid", amount, period)
                .withStyle(ChatFormatting.WHITE);
        message.append(Component.literal(" "));
        message.append(buildSeeMoreButton());
        return message;
    }

    /** Countdown line for {@code /lcce upkeep_details} and its OP&C equivalent - reports when the next charge fires. */
    public static Component buildNextChargeLine(long nextUpkeepTick, long gameTime) {
        if (nextUpkeepTick < 0L) {
            return Component.translatable("message.lc_claim_economy.upkeep_detail.next_charge_unknown")
                    .withStyle(ChatFormatting.GRAY);
        }
        long ticksRemaining = Math.max(0L, nextUpkeepTick - gameTime);
        Component eta = ticksRemaining <= 0L
                ? Component.translatable("message.lc_claim_economy.upkeep_detail.next_charge_imminent").withStyle(ChatFormatting.GOLD)
                : Component.literal(DurationFormat.ticksToShortString(ticksRemaining)).withStyle(ChatFormatting.AQUA);
        return Component.translatable("message.lc_claim_economy.upkeep_detail.next_charge_in", eta)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component buildDetails(UpkeepBreakdown breakdown) {
        MutableComponent message = Component.empty();

        message.append(Component.translatable("message.lc_claim_economy.upkeep_detail.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        message.append("\n");

        appendLine(message, "message.lc_claim_economy.upkeep_detail.period",
                styled(UpkeepPeriodFormat.format(breakdown.periodMinutes()), ChatFormatting.AQUA));
        message.append("\n");

        if (breakdown.chunkCount() > 0) {
            appendLine(message, "message.lc_claim_economy.upkeep_detail.chunks",
                    Component.literal(String.valueOf(breakdown.chunkCount())).withStyle(ChatFormatting.GREEN));
        }

        if (breakdown.forceLoadCount() > 0) {
            appendLine(message, "message.lc_claim_economy.upkeep_detail.forceloads",
                    Component.literal(String.valueOf(breakdown.forceLoadCount())).withStyle(ChatFormatting.GREEN));
        }

        appendProtectionSection(message,
                "message.lc_claim_economy.upkeep_detail.build_heading",
                breakdown.buildProtectionLines(),
                breakdown.buildBasePrice(),
                breakdown.buildUnits(),
                breakdown.buildProtectionCopper(),
                "message.lc_claim_economy.upkeep_detail.build_formula",
                false,
                1);

        appendProtectionSection(message,
                "message.lc_claim_economy.upkeep_detail.land_heading",
                breakdown.landProtectionLines(),
                breakdown.landBasePrice(),
                breakdown.landUnits(),
                breakdown.landProtectionCopper(),
                "message.lc_claim_economy.upkeep_detail.land_formula",
                true,
                ProtectionPricing.landChunkGroupSize());

        if (breakdown.forceLoadCount() > 0 && breakdown.forceLoadCopper() > 0) {
            message.append("\n");
            message.append(formatFormula(
                    "message.lc_claim_economy.upkeep_detail.forceload_formula",
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.forceLoadUnitPrice())),
                    breakdown.forceLoadCount(),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.forceLoadCopper()))
            ));
            message.append("\n");
        }

        appendWarSection(message, breakdown);

        appendPendingSection(message, breakdown);

        message.append("\n");
        appendLine(message, "message.lc_claim_economy.upkeep_detail.total",
                MoneyMessageUtil.formatValue(breakdown.totalCost()).copy().withStyle(ChatFormatting.GREEN));

        return message;
    }

    private static void appendProtectionSection(
            MutableComponent message,
            String headingKey,
            java.util.List<UpkeepBreakdown.ProtectionLine> lines,
            long basePrice,
            int units,
            long protectionCopper,
            String formulaKey,
            boolean landPricing,
            int chunkGroupSize
    ) {
        if (lines.isEmpty() || protectionCopper <= 0 || units <= 0) {
            return;
        }

        message.append("\n");
        if (landPricing) {
            message.append(Component.translatable(headingKey, chunkGroupSize).withStyle(ChatFormatting.YELLOW));
        } else {
            message.append(Component.translatable(headingKey).withStyle(ChatFormatting.YELLOW));
        }
        message.append("\n");

        for (UpkeepBreakdown.ProtectionLine line : lines) {
            Component label = line.extraArg() == null
                    ? Component.translatable(line.labelKey())
                    : Component.translatable(line.labelKey(), line.extraArg());
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(styled(label, ChatFormatting.YELLOW));
            message.append(Component.literal(" +").withStyle(ChatFormatting.GRAY));
            message.append(MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(line.pricePerChunk()))
                    .copy().withStyle(ChatFormatting.GOLD));
            if (landPricing) {
                message.append(Component.translatable(
                        "gui.lc_claim_economy.protection_price_per_n_chunks_suffix",
                        chunkGroupSize
                ).withStyle(ChatFormatting.GOLD));
            } else {
                message.append(Component.translatable("gui.lc_claim_economy.protection_price_per_chunk_suffix")
                        .withStyle(ChatFormatting.GOLD));
            }
            message.append("\n");
        }

        if (landPricing) {
            message.append(formatLandFormula(
                    formulaKey,
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(basePrice)),
                    units,
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(protectionCopper)),
                    chunkGroupSize
            ));
        } else {
            message.append(formatFormula(
                    formulaKey,
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(basePrice)),
                    units,
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(protectionCopper))
            ));
        }
        message.append("\n");
    }

    private static void appendWarSection(MutableComponent message, UpkeepBreakdown breakdown) {
        if (breakdown.totalWarCopper() <= 0) {
            return;
        }

        message.append("\n");
        message.append(Component.translatable("message.lc_claim_economy.upkeep_detail.war_heading").withStyle(ChatFormatting.YELLOW));
        message.append("\n");

        for (UpkeepBreakdown.WarLine line : breakdown.warLines()) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.literal(line.displayName()).withStyle(ChatFormatting.YELLOW));
            message.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY));
            message.append(Component.translatable(
                    line.incoming()
                            ? "message.lc_claim_economy.upkeep_detail.war_incoming_line"
                            : "message.lc_claim_economy.upkeep_detail.war_outgoing_line",
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(line.warCostCopper()))
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.incomingWarCopper() > 0) {
            int k = breakdown.incomingWarCount();
            double l = WarUpkeepMath.warExponent();
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.war_incoming_formula",
                    k,
                    trimExponent(l),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.baseUpkeepCopper())),
                    WarUpkeepMath.formatExtraTermSum(k, l),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.incomingWarCopper()))
            ).withStyle(ChatFormatting.GRAY));
            message.append("\n");
        }
        if (breakdown.outgoingWarCopper() > 0) {
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.war_outgoing_total",
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.outgoingWarCopper()))
            ).withStyle(ChatFormatting.GRAY));
            message.append("\n");
        }

        long expectedTotal = breakdown.baseUpkeepCopper() + breakdown.totalWarCopper();
        if (breakdown.baseUpkeepCopper() > 0 || breakdown.totalWarCopper() > 0) {
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.war_total_formula",
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.baseUpkeepCopper())),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.incomingWarCopper())),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(breakdown.outgoingWarCopper())),
                    MoneyMessageUtil.formatValue(MoneyUtil.fromCopper(expectedTotal))
            ).withStyle(ChatFormatting.DARK_GRAY));
            message.append("\n");
        }
    }

    private static String trimExponent(double l) {
        if (Math.rint(l) == l) {
            return String.valueOf((long) l);
        }
        return String.format("%.2f", l);
    }

    private static void appendPendingSection(MutableComponent message, UpkeepBreakdown breakdown) {
        if (!breakdown.hasPendingItems()) {
            return;
        }

        message.append("\n");
        message.append(Component.translatable("message.lc_claim_economy.upkeep_detail.pending_heading")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        message.append("\n");
        message.append(Component.translatable("message.lc_claim_economy.upkeep_detail.pending_hint")
                .withStyle(ChatFormatting.GRAY));
        message.append("\n");

        for (UpkeepBreakdown.PendingProtectionLine line : breakdown.pendingProtections()) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            Component label = Component.translatable(line.labelKey());
            String messageKey = line.dismantled()
                    ? "message.lc_claim_economy.upkeep_detail.pending_protection_dismantled"
                    : "message.lc_claim_economy.upkeep_detail.pending_protection_queued";
            message.append(Component.translatable(messageKey, label, line.desiredValue())
                    .withStyle(line.dismantled() ? ChatFormatting.RED : ChatFormatting.GOLD));
            message.append("\n");
        }

        for (UpkeepBreakdown.PendingWarLine line : breakdown.pendingWars()) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            String messageKey = line.endWar()
                    ? "message.lc_claim_economy.upkeep_detail.pending_war_end"
                    : "message.lc_claim_economy.upkeep_detail.pending_war_declare";
            message.append(Component.translatable(messageKey, line.displayName())
                    .withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.pendingForceLoadCount() > 0) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.pending_forceload",
                    breakdown.pendingForceLoadCount()
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.pendingForceUnloadCount() > 0) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.pending_forceunload",
                    breakdown.pendingForceUnloadCount()
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.pendingLandChunkCount() > 0) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.pending_land_chunks",
                    breakdown.pendingLandChunkCount()
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }

        if (breakdown.pendingBuildChunkCount() > 0) {
            message.append(Component.literal("  • ").withStyle(ChatFormatting.DARK_GRAY));
            message.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.pending_build_chunks",
                    breakdown.pendingBuildChunkCount()
            ).withStyle(ChatFormatting.GOLD));
            message.append("\n");
        }
    }

    private static Component buildSeeMoreButton() {
        return Component.translatable("message.lc_claim_economy.upkeep_see_more")
                .withStyle(Style.EMPTY
                        .withColor(ChatFormatting.AQUA)
                        .withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, DETAILS_COMMAND))
                        .withHoverEvent(new HoverEvent(
                                HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("message.lc_claim_economy.upkeep_see_more_hover")
                                        .withStyle(ChatFormatting.GRAY)
                        )));
    }

    private static void appendLine(MutableComponent message, String labelKey, Component value) {
        message.append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY));
        message.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY));
        message.append(value);
        message.append("\n");
    }

    private static Component formatFormula(String key, Component unitPrice, int count, Component subtotal) {
        return Component.translatable(key, unitPrice, count, subtotal)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component formatLandFormula(
            String key,
            Component unitPrice,
            int groups,
            Component subtotal,
            int chunkGroupSize
    ) {
        return Component.translatable(key, unitPrice, groups, subtotal, chunkGroupSize)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component styled(Component component, ChatFormatting... formats) {
        return component.copy().withStyle(formats);
    }
}
