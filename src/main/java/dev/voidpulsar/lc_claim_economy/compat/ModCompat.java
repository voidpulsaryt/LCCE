package dev.voidpulsar.lc_claim_economy.compat;

import net.neoforged.fml.loading.LoadingModList;

/**
 * Central detection of which chunk-claiming backend is installed, so the mod
 * can support FTB Chunks/Teams and Open Parties and Claims (OP&C) as
 * either/or alternatives rather than hard dependencies.
 * <p>
 * All FTB-specific registrations (mixins, event handlers, commands) must be
 * gated behind {@link #isFtbAvailable()} before touching any
 * {@code dev.ftb.mods.*} class, and all OP&C-specific registrations gated
 * behind {@link #isOpcAvailable()} before touching any {@code xaero.pac.*}
 * class. This is required because merely classloading a class that
 * references a missing mod's types in method signatures or constructors can
 * throw {@link NoClassDefFoundError} even if that specific code path is
 * never reached.
 * <p>
 * If both backends are installed, FTB Chunks/Teams is preferred, since it is
 * this mod's original, full-featured integration (protections, wars, and
 * land/build chunk splitting are currently FTB-only).
 * <p>
 * This deliberately uses {@link LoadingModList} rather than
 * {@code net.neoforged.fml.ModList}. {@code ModList} is only populated
 * later, during actual mod construction - it is NOT safe to query from a
 * Mixin config plugin (see {@code LcFtbMixinPlugin}), which runs during the
 * much earlier game bootstrap/mod-discovery phase, well before {@code
 * ModList} exists. {@code LoadingModList} is populated during mod
 * discovery itself and is safe to use at any point, including from Mixin
 * plugins, which is why it's used here universally rather than only in the
 * Mixin-plugin-specific code path.
 */
public final class ModCompat {
    private static final String FTB_LIBRARY_MOD_ID = "ftblibrary";
    private static final String FTB_TEAMS_MOD_ID = "ftbteams";
    private static final String FTB_CHUNKS_MOD_ID = "ftbchunks";
    private static final String OPC_MOD_ID = "openpartiesandclaims";
    private static final String BLUEMAP_MOD_ID = "bluemap";

    private ModCompat() {
    }

    public static boolean isFtbAvailable() {
        return isLoaded(FTB_LIBRARY_MOD_ID) && isLoaded(FTB_TEAMS_MOD_ID) && isLoaded(FTB_CHUNKS_MOD_ID);
    }

    public static boolean isOpcAvailable() {
        return isLoaded(OPC_MOD_ID);
    }

    /** True if the (optional, soft-dependency) BlueMap mod is installed - see the {@code bluemap} package. */
    public static boolean isBlueMapAvailable() {
        return isLoaded(BLUEMAP_MOD_ID);
    }

    /** True if FTB is installed, or FTB is absent but OP&C is present (OP&C used as the active backend). */
    public static boolean isOpcActive() {
        return !isFtbAvailable() && isOpcAvailable();
    }

    private static boolean isLoaded(String modId) {
        return LoadingModList.get().getModFileById(modId) != null;
    }
}

