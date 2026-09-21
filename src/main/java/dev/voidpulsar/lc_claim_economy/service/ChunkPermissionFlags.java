package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.Protection;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;

public final class ChunkPermissionFlags {
    public static final int BLOCK_EDIT = 1;
    public static final int BLOCK_INTERACT = 1 << 1;
    public static final int ENTITY_INTERACT = 1 << 2;
    public static final int PVP = 1 << 3;

    public static final int ALL = BLOCK_EDIT | BLOCK_INTERACT | ENTITY_INTERACT | PVP;

    // Keyed by identity, not equals()/hashCode() - FTB's Protection constants are
    // effectively an enum-like set of singletons, and IdentityHashMap avoids relying
    // on whatever equals() contract (if any) that type happens to implement.
    private static final Map<Protection, Integer> KNOWN_PROTECTION_FLAGS = new IdentityHashMap<>();

    static {
        mapProtection("EDIT_BLOCK", BLOCK_EDIT);
        mapProtection("INTERACT_BLOCK", BLOCK_INTERACT);
        mapProtection("EDIT_AND_INTERACT_BLOCK", BLOCK_EDIT | BLOCK_INTERACT);
        mapProtection("EDIT_FLUID", BLOCK_EDIT);
        mapProtection("INTERACT_ENTITY", ENTITY_INTERACT);
        mapProtection("ATTACK_NONLIVING_ENTITY", PVP);
    }

    private ChunkPermissionFlags() {
    }

    public static int sanitize(int flags) {
        return flags & ALL;
    }

    /**
     * Maps an FTB {@link Protection} constant to our own flag bits. Known constants
     * resolve via the static table above; anything else (future FTB additions, or
     * constants this mod hasn't explicitly mapped) falls back to keyword-sniffing the
     * constant's field/enum name, since new Protection values tend to follow the same
     * EDIT/INTERACT/BLOCK/ENTITY/PVP naming convention as the ones we already know.
     */
    public static int fromProtection(Protection protection) {
        if (protection == null) {
            return 0;
        }

        Integer mapped = KNOWN_PROTECTION_FLAGS.get(protection);
        if (mapped != null) {
            return mapped;
        }

        String declaredFieldName = lookupDeclaredFieldName(protection);
        if (declaredFieldName != null) {
            int guessed = guessFlagsFromKeywords(declaredFieldName.toUpperCase(Locale.ROOT));
            if (guessed != 0) {
                return guessed;
            }
        }

        return guessFlagsFromKeywords(String.valueOf(protection).toUpperCase(Locale.ROOT));
    }

    private static int guessFlagsFromKeywords(String id) {
        if (id.contains("EDIT") && id.contains("INTERACT") && id.contains("BLOCK")) {
            return BLOCK_EDIT | BLOCK_INTERACT;
        }
        if (id.contains("BLOCK") && id.contains("INTERACT")) {
            return BLOCK_INTERACT;
        }
        boolean editVerb = id.contains("EDIT") || id.contains("BREAK") || id.contains("PLACE");
        boolean editNoun = id.contains("BLOCK") || id.contains("FLUID");
        if (editVerb && editNoun) {
            return BLOCK_EDIT;
        }
        if (id.contains("ENTITY") && id.contains("INTERACT")) {
            return ENTITY_INTERACT;
        }
        if (id.contains("PVP") || id.contains("ATTACK")) {
            return PVP;
        }
        return 0;
    }

    private static void mapProtection(String fieldName, int flags) {
        try {
            Field field = Protection.class.getField(fieldName);
            Object value = field.get(null);
            if (value instanceof Protection protection) {
                KNOWN_PROTECTION_FLAGS.put(protection, flags);
            }
        } catch (Throwable ignored) {
            // Field renamed/removed upstream - just skip it rather than crash at class-init time.
        }
    }

    private static String lookupDeclaredFieldName(Protection protection) {
        try {
            for (Field field : Protection.class.getFields()) {
                if (field.getType() != Protection.class || !Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (field.get(null) == protection) {
                    return field.getName();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
