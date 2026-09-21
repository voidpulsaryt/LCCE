package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbteams.api.property.TeamProperty;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.util.DurationFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import dev.voidpulsar.lc_claim_economy.util.BillingCycleFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.List;

/**
 * Turns already-computed billing figures into the chat {@link Component} trees the
 * mod sends players. Nothing in here derives a price or a quantity - every number
 * shown was decided elsewhere (mostly {@link BillingBreakdown} and
 * {@link ConflictBillingMath}); this class only decides how it reads.
 */
public final class BillingMessageComposer {
    private static final String DETAILS_COMMAND = "/" + LcClaimEconomy.MOD_ID + " upkeep_details";
    private static final String BULLET = "  • ";

    private BillingMessageComposer() {
    }

    public static Component buildUnaffordableRestorationMessage(List<TeamProperty<?>> unaffordable) {
        MutableComponent out = Component.literal("⌛ ")
                .withStyle(ChatFormatting.YELLOW)
                .append(Component.translatable("message.lc_claim_economy.unaffordable_restoration_header", unaffordable.size())
                        .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        appendBulletedProtections(out, unaffordable, ChatFormatting.YELLOW);
        out.append("\n");
        out.append(Component.translatable("message.lc_claim_economy.unaffordable_restoration_hint").withStyle(ChatFormatting.GRAY));
        return out;
    }

    public static Component buildRestorationSummary(List<TeamProperty<?>> restored, List<String> restoredWarNames) {
        MutableComponent out = Component.empty();
        boolean hasContent = false;

        if (!restored.isEmpty()) {
            out.append(Component.translatable("message.lc_claim_economy.restoration_header", restored.size())
                    .withStyle(ChatFormatting.GREEN));
            appendBulletedProtections(out, restored, ChatFormatting.WHITE);
            hasContent = true;
        }

        for (String warName : restoredWarNames) {
            if (hasContent) {
                out.append("\n");
            }
            out.append(Component.translatable("message.lc_claim_economy.war_active", warName)
                    .withStyle(ChatFormatting.GRAY));
            hasContent = true;
        }

        return out;
    }

    public static Component buildSuspensionSummary(List<TeamProperty<?>> suspended, boolean warsSuspended) {
        MutableComponent out = Component.empty();
        boolean hasContent = false;

        if (!suspended.isEmpty()) {
            out.append(Component.translatable("message.lc_claim_economy.suspension_header", suspended.size())
                    .withStyle(ChatFormatting.YELLOW));
            appendBulletedProtections(out, suspended, ChatFormatting.WHITE);
            hasContent = true;
        }

        if (warsSuspended) {
            if (hasContent) {
                out.append("\n");
            }
            out.append(Component.translatable(
                    hasContent ? "message.lc_claim_economy.suspension_wars" : "message.lc_claim_economy.suspension_wars_header"
            ).withStyle(ChatFormatting.YELLOW));
            hasContent = true;
        }

        if (hasContent) {
            out.append("\n");
            out.append(Component.translatable("message.lc_claim_economy.suspension_hint").withStyle(ChatFormatting.GRAY));
        }

        return out;
    }

    private static void appendBulletedProtections(MutableComponent out, List<TeamProperty<?>> properties, ChatFormatting color) {
        for (TeamProperty<?> property : properties) {
            String labelKey = "message.lc_claim_economy.upkeep_priority.protection." + SafeguardPricing.propertyKey(property);
            out.append("\n");
            out.append(Component.literal(BULLET).withStyle(ChatFormatting.DARK_GRAY));
            out.append(Component.translatable(labelKey).withStyle(color));
        }
    }

    public static Component buildSummary(BillingBreakdown breakdown) {
        Component amount = CurrencyTextFormat.formatValue(breakdown.totalCost());
        Component period = BillingCycleFormat.format(breakdown.periodMinutes());

        return Component.translatable("message.lc_claim_economy.upkeep_paid", amount, period)
                .withStyle(ChatFormatting.WHITE)
                .append(Component.literal(" "))
                .append(seeMoreLink());
    }

    /** Countdown line shared by {@code /lcce upkeep_details} and its OP&C equivalent - reports when the next charge fires. */
    public static Component buildNextChargeLine(long nextUpkeepTick, long gameTime) {
        if (nextUpkeepTick < 0L) {
            return Component.translatable("message.lc_claim_economy.upkeep_detail.next_charge_unknown")
                    .withStyle(ChatFormatting.GRAY);
        }
        long ticksLeft = Math.max(0L, nextUpkeepTick - gameTime);
        Component eta = ticksLeft <= 0L
                ? Component.translatable("message.lc_claim_economy.upkeep_detail.next_charge_imminent").withStyle(ChatFormatting.GOLD)
                : Component.literal(DurationFormat.ticksToShortString(ticksLeft)).withStyle(ChatFormatting.AQUA);
        return Component.translatable("message.lc_claim_economy.upkeep_detail.next_charge_in", eta)
                .withStyle(ChatFormatting.GRAY);
    }

    public static Component buildDetails(BillingBreakdown breakdown) {
        MutableComponent out = Component.empty();

        out.append(Component.translatable("message.lc_claim_economy.upkeep_detail.header")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        out.append("\n");

        appendLabeledLine(out, "message.lc_claim_economy.upkeep_detail.period",
                withColors(BillingCycleFormat.format(breakdown.periodMinutes()), ChatFormatting.AQUA));
        out.append("\n");

        if (breakdown.chunkCount() > 0) {
            appendLabeledLine(out, "message.lc_claim_economy.upkeep_detail.chunks",
                    Component.literal(String.valueOf(breakdown.chunkCount())).withStyle(ChatFormatting.GREEN));
        }

        if (breakdown.forceLoadCount() > 0) {
            appendLabeledLine(out, "message.lc_claim_economy.upkeep_detail.forceloads",
                    Component.literal(String.valueOf(breakdown.forceLoadCount())).withStyle(ChatFormatting.GREEN));
        }

        appendPricedSection(out, new PricedSectionSpec(
                "message.lc_claim_economy.upkeep_detail.build_heading",
                breakdown.buildProtectionLines(),
                breakdown.buildBasePrice(),
                breakdown.buildUnits(),
                breakdown.buildProtectionCopper(),
                "message.lc_claim_economy.upkeep_detail.build_formula",
                false,
                1
        ));

        appendPricedSection(out, new PricedSectionSpec(
                "message.lc_claim_economy.upkeep_detail.land_heading",
                breakdown.landProtectionLines(),
                breakdown.landBasePrice(),
                breakdown.landUnits(),
                breakdown.landProtectionCopper(),
                "message.lc_claim_economy.upkeep_detail.land_formula",
                true,
                SafeguardPricing.landChunkGroupSize()
        ));

        if (breakdown.forceLoadCount() > 0 && breakdown.forceLoadCopper() > 0) {
            out.append("\n");
            out.append(formulaText(
                    "message.lc_claim_economy.upkeep_detail.forceload_formula",
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.forceLoadUnitPrice())),
                    breakdown.forceLoadCount(),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.forceLoadCopper()))
            ));
            out.append("\n");
        }

        appendConflictSection(out, breakdown);
        appendQueuedChangesSection(out, breakdown);

        out.append("\n");
        appendLabeledLine(out, "message.lc_claim_economy.upkeep_detail.total",
                CurrencyTextFormat.formatValue(breakdown.totalCost()).copy().withStyle(ChatFormatting.GREEN));

        return out;
    }

    /** Grouping for the two near-identical "heading + bulleted lines + formula" blocks (build protections, land protections). */
    private record PricedSectionSpec(
            String headingKey,
            List<BillingBreakdown.ProtectionLine> lines,
            long basePrice,
            int units,
            long protectionCopper,
            String formulaKey,
            boolean landPricing,
            int groupSize
    ) {
    }

    private static void appendPricedSection(MutableComponent out, PricedSectionSpec spec) {
        if (spec.lines().isEmpty() || spec.protectionCopper() <= 0 || spec.units() <= 0) {
            return;
        }

        out.append("\n");
        out.append(spec.landPricing()
                ? Component.translatable(spec.headingKey(), spec.groupSize()).withStyle(ChatFormatting.YELLOW)
                : Component.translatable(spec.headingKey()).withStyle(ChatFormatting.YELLOW));
        out.append("\n");

        for (BillingBreakdown.ProtectionLine line : spec.lines()) {
            appendPricedLine(out, line, spec.landPricing(), spec.groupSize());
        }

        out.append(spec.landPricing()
                ? landFormulaText(
                        spec.formulaKey(),
                        CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(spec.basePrice())),
                        spec.units(),
                        CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(spec.protectionCopper())),
                        spec.groupSize())
                : formulaText(
                        spec.formulaKey(),
                        CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(spec.basePrice())),
                        spec.units(),
                        CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(spec.protectionCopper()))));
        out.append("\n");
    }

    private static void appendPricedLine(MutableComponent out, BillingBreakdown.ProtectionLine line, boolean landPricing, int groupSize) {
        Component label = line.extraArg() == null
                ? Component.translatable(line.labelKey())
                : Component.translatable(line.labelKey(), line.extraArg());

        out.append(Component.literal(BULLET).withStyle(ChatFormatting.DARK_GRAY));
        out.append(withColors(label, ChatFormatting.YELLOW));
        out.append(Component.literal(" +").withStyle(ChatFormatting.GRAY));
        out.append(CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(line.pricePerChunk())).copy().withStyle(ChatFormatting.GOLD));
        out.append(landPricing
                ? Component.translatable("gui.lc_claim_economy.protection_price_per_n_chunks_suffix", groupSize).withStyle(ChatFormatting.GOLD)
                : Component.translatable("gui.lc_claim_economy.protection_price_per_chunk_suffix").withStyle(ChatFormatting.GOLD));
        out.append("\n");
    }

    private static void appendConflictSection(MutableComponent out, BillingBreakdown breakdown) {
        if (breakdown.totalWarCopper() <= 0) {
            return;
        }

        out.append("\n");
        out.append(Component.translatable("message.lc_claim_economy.upkeep_detail.war_heading").withStyle(ChatFormatting.YELLOW));
        out.append("\n");

        for (BillingBreakdown.WarLine line : breakdown.warLines()) {
            String lineKey = line.incoming()
                    ? "message.lc_claim_economy.upkeep_detail.war_incoming_line"
                    : "message.lc_claim_economy.upkeep_detail.war_outgoing_line";
            out.append(Component.literal(BULLET).withStyle(ChatFormatting.DARK_GRAY));
            out.append(Component.literal(line.displayName()).withStyle(ChatFormatting.YELLOW));
            out.append(Component.literal(" — ").withStyle(ChatFormatting.GRAY));
            out.append(Component.translatable(lineKey, CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(line.warCostCopper())))
                    .withStyle(ChatFormatting.GOLD));
            out.append("\n");
        }

        if (breakdown.incomingWarCopper() > 0) {
            int incomingCount = breakdown.incomingWarCount();
            double exponent = ConflictBillingMath.warExponent();
            out.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.war_incoming_formula",
                    incomingCount,
                    formatExponent(exponent),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.baseUpkeepCopper())),
                    ConflictBillingMath.formatExtraTermSum(incomingCount, exponent),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.incomingWarCopper()))
            ).withStyle(ChatFormatting.GRAY));
            out.append("\n");
        }
        if (breakdown.outgoingWarCopper() > 0) {
            out.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.war_outgoing_total",
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.outgoingWarCopper()))
            ).withStyle(ChatFormatting.GRAY));
            out.append("\n");
        }

        if (breakdown.baseUpkeepCopper() > 0 || breakdown.totalWarCopper() > 0) {
            long expectedTotal = breakdown.baseUpkeepCopper() + breakdown.totalWarCopper();
            out.append(Component.translatable(
                    "message.lc_claim_economy.upkeep_detail.war_total_formula",
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.baseUpkeepCopper())),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.incomingWarCopper())),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(breakdown.outgoingWarCopper())),
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(expectedTotal))
            ).withStyle(ChatFormatting.DARK_GRAY));
            out.append("\n");
        }
    }

    private static String formatExponent(double exponent) {
        if (Math.rint(exponent) == exponent) {
            return String.valueOf((long) exponent);
        }
        return String.format("%.2f", exponent);
    }

    private record PendingCountLine(int count, String translationKey) {
    }

    private static void appendQueuedChangesSection(MutableComponent out, BillingBreakdown breakdown) {
        if (!breakdown.hasPendingItems()) {
            return;
        }

        out.append("\n");
        out.append(Component.translatable("message.lc_claim_economy.upkeep_detail.pending_heading")
                .withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD));
        out.append("\n");
        out.append(Component.translatable("message.lc_claim_economy.upkeep_detail.pending_hint")
                .withStyle(ChatFormatting.GRAY));
        out.append("\n");

        for (BillingBreakdown.PendingProtectionLine line : breakdown.pendingProtections()) {
            String messageKey = line.dismantled()
                    ? "message.lc_claim_economy.upkeep_detail.pending_protection_dismantled"
                    : "message.lc_claim_economy.upkeep_detail.pending_protection_queued";
            Component label = Component.translatable(line.labelKey());
            out.append(Component.literal(BULLET).withStyle(ChatFormatting.DARK_GRAY));
            out.append(Component.translatable(messageKey, label, line.desiredValue())
                    .withStyle(line.dismantled() ? ChatFormatting.RED : ChatFormatting.GOLD));
            out.append("\n");
        }

        for (BillingBreakdown.PendingWarLine line : breakdown.pendingWars()) {
            String messageKey = line.endWar()
                    ? "message.lc_claim_economy.upkeep_detail.pending_war_end"
                    : "message.lc_claim_economy.upkeep_detail.pending_war_declare";
            out.append(Component.literal(BULLET).withStyle(ChatFormatting.DARK_GRAY));
            out.append(Component.translatable(messageKey, line.displayName()).withStyle(ChatFormatting.GOLD));
            out.append("\n");
        }

        List<PendingCountLine> countLines = List.of(
                new PendingCountLine(breakdown.pendingForceLoadCount(), "message.lc_claim_economy.upkeep_detail.pending_forceload"),
                new PendingCountLine(breakdown.pendingForceUnloadCount(), "message.lc_claim_economy.upkeep_detail.pending_forceunload"),
                new PendingCountLine(breakdown.pendingLandChunkCount(), "message.lc_claim_economy.upkeep_detail.pending_land_chunks"),
                new PendingCountLine(breakdown.pendingBuildChunkCount(), "message.lc_claim_economy.upkeep_detail.pending_build_chunks")
        );
        for (PendingCountLine countLine : countLines) {
            if (countLine.count() <= 0) {
                continue;
            }
            out.append(Component.literal(BULLET).withStyle(ChatFormatting.DARK_GRAY));
            out.append(Component.translatable(countLine.translationKey(), countLine.count()).withStyle(ChatFormatting.GOLD));
            out.append("\n");
        }
    }

    private static Component seeMoreLink() {
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

    private static void appendLabeledLine(MutableComponent out, String labelKey, Component value) {
        out.append(Component.translatable(labelKey).withStyle(ChatFormatting.GRAY));
        out.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY));
        out.append(value);
        out.append("\n");
    }

    private static Component formulaText(String key, Component unitPrice, int count, Component subtotal) {
        return Component.translatable(key, unitPrice, count, subtotal)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component landFormulaText(String key, Component unitPrice, int groups, Component subtotal, int groupSize) {
        return Component.translatable(key, unitPrice, groups, subtotal, groupSize)
                .withStyle(ChatFormatting.GRAY);
    }

    private static Component withColors(Component component, ChatFormatting... formats) {
        return component.copy().withStyle(formats);
    }
}
