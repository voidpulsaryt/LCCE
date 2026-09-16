package dev.voidpulsar.lc_claim_economy.util;

public final class DurationFormat {
    private DurationFormat() {
    }

    /** Formats a tick count as a short "1d 2h 3m 4s" style string, omitting any leading zero units. */
    public static String ticksToShortString(long ticks) {
        long totalSeconds = Math.max(0L, ticks) / 20L;
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            builder.append(hours).append("h ");
        }
        if (days > 0 || hours > 0 || minutes > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(seconds).append("s");
        return builder.toString();
    }
}
