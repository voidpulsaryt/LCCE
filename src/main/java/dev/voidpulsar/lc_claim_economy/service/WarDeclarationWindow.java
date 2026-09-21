package dev.voidpulsar.lc_claim_economy.service;

import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Gates only the act of declaring a brand-new war to a recurring weekly window (e.g.
 * "Friday 22:00 UTC to Sunday 22:00 UTC"). Ending an existing war, and the automatic
 * suspend/restore cycle that {@link BillingSettlementService} runs at upkeep, are
 * unaffected - this window only ever blocks new declarations.
 *
 * <p>Note: {@code isOpen}/{@code parseDay}/{@code describe} keep package-private
 * visibility and their exact signatures on purpose - {@code WarDeclarationWindowTest}
 * drives the date-math directly through them rather than mocking a clock.
 */
public final class WarDeclarationWindow {
    private static final int MINUTES_PER_HOUR = 60;
    private static final int MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;

    private WarDeclarationWindow() {
    }

    public static boolean isEnabled() {
        return LcClaimEconomyConfig.SERVER.warDeclarationWindowEnabled.get();
    }

    public static boolean isOpenNow() {
        if (!isEnabled()) {
            return true;
        }
        return isOpen(
                configuredStartDay(),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowStartHourUtc.get(),
                configuredEndDay(),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowEndHourUtc.get(),
                ZonedDateTime.now(ZoneOffset.UTC)
        );
    }

    /**
     * Converts everything to a minute offset within a 7-day (10080-minute) cycle so the
     * open/closed check is just three integer comparisons - no calendar edge cases to
     * worry about once we're in that space.
     */
    static boolean isOpen(DayOfWeek startDay, int startHour, DayOfWeek endDay, int endHour, ZonedDateTime nowUtc) {
        int nowOffset = minuteOfWeek(DayOfWeek.from(nowUtc), nowUtc.getHour(), nowUtc.getMinute());
        int openOffset = minuteOfWeek(startDay, startHour, 0);
        int closeOffset = minuteOfWeek(endDay, endHour, 0);

        if (openOffset == closeOffset) {
            // A zero-width window is almost certainly a config mistake, not an intent to
            // block every declaration forever - fail open instead of locking the server out.
            return true;
        }
        if (openOffset < closeOffset) {
            return nowOffset >= openOffset && nowOffset < closeOffset;
        }
        // Window straddles the week boundary (e.g. opens Sunday, closes Friday).
        return nowOffset >= openOffset || nowOffset < closeOffset;
    }

    public static String describeWindow() {
        return describe(
                configuredStartDay(),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowStartHourUtc.get(),
                configuredEndDay(),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowEndHourUtc.get()
        );
    }

    static String describe(DayOfWeek startDay, int startHour, DayOfWeek endDay, int endHour) {
        return formatDayHour(startDay, startHour) + " - " + formatDayHour(endDay, endHour) + " UTC";
    }

    static DayOfWeek parseDay(String raw, DayOfWeek fallback) {
        if (raw == null) {
            return fallback;
        }
        try {
            return DayOfWeek.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private static DayOfWeek configuredStartDay() {
        return parseDay(LcClaimEconomyConfig.SERVER.warDeclarationWindowStartDay.get(), DayOfWeek.FRIDAY);
    }

    private static DayOfWeek configuredEndDay() {
        return parseDay(LcClaimEconomyConfig.SERVER.warDeclarationWindowEndDay.get(), DayOfWeek.SUNDAY);
    }

    private static String formatDayHour(DayOfWeek day, int hour) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + String.format(Locale.ROOT, " %02d:00", hour);
    }

    private static int minuteOfWeek(DayOfWeek day, int hour, int minute) {
        return (day.getValue() - 1) * MINUTES_PER_DAY + hour * MINUTES_PER_HOUR + minute;
    }
}
