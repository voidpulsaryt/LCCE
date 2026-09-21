package dev.voidpulsar.lc_claim_economy.util;

import net.minecraft.network.chat.Component;

/** Renders the configured upkeep billing interval as the coarsest whole unit it evenly divides into, so a server owner's "1440" minute config reads as "1 day" rather than "1440 minutes". */
public final class BillingCycleFormat {
    private static final int MINUTES_PER_HOUR = 60;
    private static final int MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    private BillingCycleFormat() {
    }

    public static Component format(int minutes) {
        if (minutes % MINUTES_PER_DAY == 0) {
            return describeCount(minutes / MINUTES_PER_DAY, "message.lc_claim_economy.upkeep_period.one_day", "message.lc_claim_economy.upkeep_period.days");
        }
        if (minutes % MINUTES_PER_HOUR == 0) {
            return describeCount(minutes / MINUTES_PER_HOUR, "message.lc_claim_economy.upkeep_period.one_hour", "message.lc_claim_economy.upkeep_period.hours");
        }
        return describeCount(minutes, "message.lc_claim_economy.upkeep_period.one_minute", "message.lc_claim_economy.upkeep_period.minutes");
    }

    private static Component describeCount(int count, String singularKey, String pluralKey) {
        return count == 1 ? Component.translatable(singularKey) : Component.translatable(pluralKey, count);
    }
}
