package dev.voidpulsar.lc_claim_economy.teams;

import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache;
import io.github.lightman314.lightmanscurrency.common.teams.Team;
import io.github.lightman314.lightmanscurrency.common.teams.TeamBankAccount;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Lightman's Currency's {@code Team} exposes almost none of its mutable state through public
 * setters (owner/name/admins/members/bank account are all package-private fields with no
 * accessor), because normal gameplay only ever changes them through LC's own team-management
 * screens. This mod needs to push FTB/OP&C party state onto an LC team programmatically instead,
 * so these fields are reached through reflection rather than forking or wrapping LC's team class.
 * All lookups are done once in the static initializer and fail fast (as an
 * {@link ExceptionInInitializerError}) if LC ever renames one of these fields, rather than
 * failing silently mid-sync.
 */
public final class CurrencyTeamAccess {

    /**
     * One reflected {@link Team} field bundled with a typed, labeled read/write pair -
     * a single generic accessor shape shared by every field below, instead of a raw
     * {@link Field} constant plus a separately-labeled call into a generic helper method
     * per site. The label feeds directly into this field's own failure messages, so error
     * text stays identical to what a dedicated per-field method would have produced.
     */
    private record FieldAccessor<T>(Field field, String label) {
        static <T> FieldAccessor<T> of(Class<?> owner, String fieldName, String label) throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return new FieldAccessor<>(field, label);
        }

        @SuppressWarnings("unchecked")
        T get(Object target) {
            try {
                return (T) field.get(target);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to read LC team " + label, exception);
            }
        }

        void set(Object target, T value) {
            try {
                field.set(target, value);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Failed to set LC team " + label, exception);
            }
        }
    }

    private static final FieldAccessor<PlayerReference> OWNER;
    private static final FieldAccessor<String> NAME;
    private static final FieldAccessor<List<PlayerReference>> ADMINS;
    private static final FieldAccessor<List<PlayerReference>> MEMBERS;
    private static final FieldAccessor<TeamBankAccount> BANK_ACCOUNT;
    private static final Field CACHE_TEAM_MAP_FIELD;
    private static final Method CACHE_NEXT_ID_METHOD;

    static {
        try {
            OWNER = FieldAccessor.of(Team.class, "owner", "owner");
            NAME = FieldAccessor.of(Team.class, "teamName", "name");
            ADMINS = FieldAccessor.of(Team.class, "admins", "admins");
            MEMBERS = FieldAccessor.of(Team.class, "members", "members");
            BANK_ACCOUNT = FieldAccessor.of(Team.class, "bankAccount", "bank account");
            CACHE_TEAM_MAP_FIELD = reflectField(TeamDataCache.class, "teams");
            CACHE_NEXT_ID_METHOD = TeamDataCache.class.getDeclaredMethod("getNextID");
            CACHE_NEXT_ID_METHOD.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Field reflectField(Class<?> owner, String fieldName) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private CurrencyTeamAccess() {
    }

    @Nullable
    static TeamDataCache cache() {
        if (!TeamDataCache.TYPE.isLoaded(false)) {
            return null;
        }
        return TeamDataCache.TYPE.get(false);
    }

    static Team registerTeam(PlayerReference owner, String name) {
        TeamDataCache dataCache = cache();
        if (dataCache == null) {
            throw new IllegalStateException("LC team data is not loaded yet");
        }
        try {
            long newId = (long) CACHE_NEXT_ID_METHOD.invoke(dataCache);
            Team newTeam = Team.of(newId, owner, name).initialize();
            @SuppressWarnings("unchecked")
            Map<Long, Team> teamMap = (Map<Long, Team>) CACHE_TEAM_MAP_FIELD.get(dataCache);
            teamMap.put(newId, newTeam);
            dataCache.markTeamDirty(newId);
            return newTeam;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to register LC team", exception);
        }
    }

    static void setOwner(Team team, PlayerReference owner) {
        OWNER.set(team, owner);
    }

    static void setName(Team team, String name) {
        NAME.set(team, name);
        TeamBankAccount linkedAccount = BANK_ACCOUNT.get(team);
        if (linkedAccount != null) {
            linkedAccount.updateOwnersName(name);
        }
    }

    static List<PlayerReference> admins(Team team) {
        return ADMINS.get(team);
    }

    static List<PlayerReference> members(Team team) {
        return MEMBERS.get(team);
    }

    static void createBankAccount(Team team) {
        if (team.hasBankAccount()) {
            return;
        }
        try {
            TeamBankAccount newAccount = new TeamBankAccount(team, team::markDirty);
            newAccount.updateOwnersName(team.getName());
            BANK_ACCOUNT.field().set(team, newAccount);
            team.markDirty();
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to create LC team bank account", exception);
        }
    }

    static void replacePlayerReference(List<PlayerReference> list, int index, PlayerReference replacement) {
        list.set(index, replacement);
    }

    static PlayerReference ensureNamed(PlayerReference reference, String name) {
        String existingName = reference.getName(false);
        if (existingName != null && !existingName.isBlank()) {
            return reference;
        }
        return reference.copyWithName(name);
    }
}
