package dev.voidpulsar.lc_claim_economy.teams;

public final class CurrencyTeamPurgeGuard {
    private static final ThreadLocal<Boolean> ALLOW = ThreadLocal.withInitial(() -> false);

    private CurrencyTeamPurgeGuard() {
    }

    public static boolean isAllowed() {
        return Boolean.TRUE.equals(ALLOW.get());
    }

    public static void runAllowed(Runnable action) {
        ALLOW.set(true);
        try {
            action.run();
        } finally {
            ALLOW.remove();
        }
    }
}
