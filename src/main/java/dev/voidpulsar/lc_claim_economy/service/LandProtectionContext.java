package dev.voidpulsar.lc_claim_economy.service;

/**
 * A one-shot, per-thread relay for the land/build classification of whatever chunk
 * {@code shouldPreventInteraction} is currently evaluating. It exists purely because
 * {@code canPlayerUse} runs deeper in the same call and has no chunk parameter of its
 * own to inspect, so the caller stashes the answer here first. Always paired: a
 * {@link #set} before the protection check, a {@link #clear} once it returns.
 */
public final class LandProtectionContext {
    private static final ThreadLocal<Boolean> LAND_FLAG = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private LandProtectionContext() {
    }

    public static void set(boolean land) {
        LAND_FLAG.set(land);
    }

    public static boolean isLand() {
        return LAND_FLAG.get();
    }

    public static void clear() {
        LAND_FLAG.remove();
    }
}
