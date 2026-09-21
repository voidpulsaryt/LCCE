package dev.voidpulsar.lc_claim_economy.teams;

/**
 * A mixin on {@code TeamDataCache.removeTeam} cancels any deletion of an LC team that is
 * still bound to a live FTB/OP&C party, so a player can't disband the underlying LC team out
 * from under an active link (see {@code TeamCacheEvictionMixin}). This mod's own link-cleanup
 * code (party dissolution, reconcile, duplicate pruning) needs to remove that same LC team
 * deliberately, so it flags the current thread here first to slip past its own guard. The flag
 * is thread-local rather than a simple static boolean because upkeep/tick processing and player
 * commands can both be removing teams around the same time, and one thread's cleanup must not
 * accidentally waive the guard for another.
 */
public final class CurrencyTeamPurgeGuard {
    private static final ThreadLocal<Boolean> BYPASS_ACTIVE = ThreadLocal.withInitial(() -> false);

    private CurrencyTeamPurgeGuard() {
    }

    public static boolean isAllowed() {
        return Boolean.TRUE.equals(BYPASS_ACTIVE.get());
    }

    public static void runAllowed(Runnable action) {
        BYPASS_ACTIVE.set(true);
        try {
            action.run();
        } finally {
            BYPASS_ACTIVE.remove();
        }
    }
}
