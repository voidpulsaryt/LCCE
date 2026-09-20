package dev.voidpulsar.lc_claim_economy.service;

import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;

import java.time.DayOfWeek;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Restricts when new wars can be declared to a recurring weekly window (e.g. "Friday 22:00 UTC
 * to Sunday 22:00 UTC"), independent of ending wars or the automatic upkeep-suspension/restore
 * cycle in {@link BillingSettlementService}, which are never gated by this window.
 */
public final class WarDeclarationWindow {

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
                parseDay(LcClaimEconomyConfig.SERVER.warDeclarationWindowStartDay.get(), DayOfWeek.FRIDAY),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowStartHourUtc.get(),
                parseDay(LcClaimEconomyConfig.SERVER.warDeclarationWindowEndDay.get(), DayOfWeek.SUNDAY),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowEndHourUtc.get(),
                ZonedDateTime.now(ZoneOffset.UTC)
        );
    }

    /** Pure, testable window check. Minute-of-week arithmetic on a 7-day (10080-minute) cycle. */
    static boolean isOpen(DayOfWeek startDay, int startHour, DayOfWeek endDay, int endHour, ZonedDateTime nowUtc) {
        int nowMinutes = minuteOfWeek(DayOfWeek.from(nowUtc), nowUtc.getHour(), nowUtc.getMinute());
        int startMinutes = minuteOfWeek(startDay, startHour, 0);
        int endMinutes = minuteOfWeek(endDay, endHour, 0);

        if (startMinutes == endMinutes) {
            // Degenerate config (start == end): treat as always open rather than always closed,
            // so a misconfiguration can't silently lock every team out of declaring war.
            return true;
        }
        if (startMinutes < endMinutes) {
            return nowMinutes >= startMinutes && nowMinutes < endMinutes;
        }
        // Window wraps past the end of the week (e.g. start Sunday, end Friday).
        return nowMinutes >= startMinutes || nowMinutes < endMinutes;
    }

    public static String describeWindow() {
        return describe(
                parseDay(LcClaimEconomyConfig.SERVER.warDeclarationWindowStartDay.get(), DayOfWeek.FRIDAY),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowStartHourUtc.get(),
                parseDay(LcClaimEconomyConfig.SERVER.warDeclarationWindowEndDay.get(), DayOfWeek.SUNDAY),
                LcClaimEconomyConfig.SERVER.warDeclarationWindowEndHourUtc.get()
        );
    }

    static String describe(DayOfWeek startDay, int startHour, DayOfWeek endDay, int endHour) {
        return formatDayHour(startDay, startHour) + " - " + formatDayHour(endDay, endHour) + " UTC";
    }

    private static String formatDayHour(DayOfWeek day, int hour) {
        return day.getDisplayName(TextStyle.FULL, Locale.ENGLISH) + String.format(Locale.ROOT, " %02d:00", hour);
    }

    private static int minuteOfWeek(DayOfWeek day, int hour, int minute) {
        return (day.getValue() - 1) * 1440 + hour * 60 + minute;
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
}
