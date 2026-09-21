package dev.voidpulsar.lc_claim_economy.teams;

import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.TeamRank;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import io.github.lightman314.lightmanscurrency.common.bank.BankAccount;
import io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Keeps a Lightman's Currency team mirrored onto whichever FTB party it's linked to -
 * owner, name, roster, and bank account all flow one-way from the FTB party into its LC
 * counterpart, never the other direction, since FTB party membership is the actual
 * source of truth for who belongs to the group.
 */
public final class CurrencyTeamLinkService {
    private static final ConcurrentHashMap<UUID, Object> PARTY_LOCKS = new ConcurrentHashMap<>();

    private CurrencyTeamLinkService() {
    }

    public static void ensureLinked(MinecraftServer server, Team ftbTeam) {
        if (!isEligibleForLink(server, ftbTeam)) {
            return;
        }

        synchronized (lockFor(ftbTeam.getId())) {
            LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
            LcClaimEconomySavedData.TeamLinkEntry entry = data.getOrCreateLink(ftbTeam.getId());
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam = findLinkedLcTeam(entry);
            if (lcTeam == null) {
                lcTeam = provisionLcTeam(server, ftbTeam);
                if (lcTeam == null) {
                    return;
                }
                data.setLcTeamId(ftbTeam.getId(), lcTeam.getID());
                LcClaimEconomy.LOGGER.info(
                        "Created LC team {} for FTB party {}",
                        lcTeam.getID(),
                        ftbTeam.getId()
                );
            }

            pruneDuplicateLcTeams(ftbTeam, data, lcTeam.getID());
            applyPartyStateToLcTeam(server, ftbTeam, lcTeam, entry);
        }
    }

    /**
     * Evaluated as an ordered rule list rather than one compound expression so each
     * precondition reads as a named, independent gate; still short-circuits on the first
     * failing gate exactly like the equivalent {@code &&} chain would.
     */
    private static boolean isEligibleForLink(MinecraftServer server, Team ftbTeam) {
        List<BooleanSupplier> gates = List.of(
                ftbTeam::isPartyTeam,
                ftbTeam::isValid,
                () -> TeamBankLinkRegistry.isFtbPartyInUse(server, ftbTeam),
                () -> CurrencyTeamAccess.cache() != null
        );
        return gates.stream().allMatch(BooleanSupplier::getAsBoolean);
    }

    private static Object lockFor(UUID ftbTeamId) {
        return PARTY_LOCKS.computeIfAbsent(ftbTeamId, id -> new Object());
    }

    public static void onTeamDeleted(MinecraftServer server, Team ftbTeam) {
        if (!ftbTeam.isPartyTeam()) {
            return;
        }
        TeamDataCache cache = CurrencyTeamAccess.cache();
        if (cache == null) {
            return;
        }

        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        LcClaimEconomySavedData.TeamLinkEntry entry = data.get(ftbTeam.getId());
        if (entry == null || entry.lcTeamId() <= 0) {
            return;
        }

        long linkedLcTeamId = entry.lcTeamId();
        CurrencyTeamPurgeGuard.runAllowed(() -> cache.removeTeam(linkedLcTeamId));
        if (data.removeLink(ftbTeam.getId()) != null) {
            LcClaimEconomy.LOGGER.info("Removed hook data for deleted FTB party {}", ftbTeam.getId());
        }
        LcClaimEconomy.LOGGER.info("Removed LC team {} for deleted FTB party {}", linkedLcTeamId, ftbTeam.getId());
    }

    @Nullable
    public static IBankAccount getLinkedBankAccount(MinecraftServer server, UUID ftbTeamId) {
        if (server == null || ftbTeamId == null) {
            return null;
        }

        LcClaimEconomySavedData.TeamLinkEntry entry = LcClaimEconomySavedData.get(server).get(ftbTeamId);
        if (entry == null) {
            return null;
        }

        if (entry.lcTeamId() > 0) {
            ITeam lcTeam = TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
            if (lcTeam != null && lcTeam.hasBankAccount()) {
                return lcTeam.getBankAccount();
            }
        }

        return entry.legacyAccount();
    }

    @Nullable
    public static IBankAccount getBankAccount(MinecraftServer server, Team ftbTeam) {
        if (!ftbTeam.isPartyTeam()) {
            return null;
        }
        ensureLinked(server, ftbTeam);
        LcClaimEconomySavedData.TeamLinkEntry entry = LcClaimEconomySavedData.get(server).get(ftbTeam.getId());
        if (entry == null || entry.lcTeamId() <= 0) {
            return null;
        }
        ITeam lcTeam = TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
        return lcTeam == null ? null : lcTeam.getBankAccount();
    }

    public static long getLcTeamId(MinecraftServer server, UUID ftbTeamId) {
        LcClaimEconomySavedData.TeamLinkEntry entry = LcClaimEconomySavedData.get(server).get(ftbTeamId);
        return entry == null ? -1L : entry.lcTeamId();
    }

    @Nullable
    private static io.github.lightman314.lightmanscurrency.common.teams.Team findLinkedLcTeam(
            LcClaimEconomySavedData.TeamLinkEntry entry
    ) {
        if (entry.lcTeamId() <= 0) {
            return null;
        }
        ITeam candidate = TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
        return candidate instanceof io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam ? lcTeam : null;
    }

    @Nullable
    private static io.github.lightman314.lightmanscurrency.common.teams.Team provisionLcTeam(MinecraftServer server, Team ftbTeam) {
        String clampedName = clampToMaxNameLength(ftbTeam.getShortName());
        PlayerReference ownerRef = toPlayerReference(server, ftbTeam.getOwner());
        return CurrencyTeamAccess.registerTeam(ownerRef, clampedName);
    }

    /**
     * Between sessions a player can end up owning a second, orphaned LC team under the same
     * name (e.g. from an interrupted link, or manually created via LC's own team screen) -
     * sweeps those away so {@code /lctm} etc. only ever shows the one team this party is
     * actually linked to. Builds the full set of LC team ids that must survive this pass
     * (every currently linked id plus the one just resolved for this party) once, then
     * queries that set per-candidate rather than re-deriving "is this the linked team" per
     * iteration.
     */
    private static void pruneDuplicateLcTeams(Team ftbTeam, LcClaimEconomySavedData data, long linkedLcTeamId) {
        TeamDataCache cache = CurrencyTeamAccess.cache();
        if (cache == null) {
            return;
        }

        Set<Long> protectedIds = new HashSet<>(data.getLinkedLcTeamIds());
        protectedIds.add(linkedLcTeamId);

        String expectedName = clampToMaxNameLength(ftbTeam.getShortName());
        UUID ownerId = ftbTeam.getOwner();

        List<ITeam> duplicates = cache.getAllTeams().stream()
                .filter(candidate -> !protectedIds.contains(candidate.getID()))
                .filter(candidate -> ownerId.equals(candidate.getOwner().id))
                .filter(candidate -> expectedName.equals(candidate.getName()))
                .toList();

        for (ITeam duplicate : duplicates) {
            long duplicateId = duplicate.getID();
            CurrencyTeamPurgeGuard.runAllowed(() -> cache.removeTeam(duplicateId));
            LcClaimEconomy.LOGGER.info(
                    "Removed duplicate LC team {} for FTB party {} (linked team is {})",
                    duplicateId,
                    ftbTeam.getId(),
                    linkedLcTeamId
            );
        }
    }

    /**
     * Runs the sync as an ordered pipeline of independent stages rather than one long
     * imperative body - each stage is a self-contained unit of work over the same
     * {@code (server, ftbTeam, lcTeam, entry)} context, executed strictly in list order so
     * the observable effects (which fields get touched, in what sequence) are identical to
     * running the equivalent statements inline.
     */
    private static void applyPartyStateToLcTeam(
            MinecraftServer server,
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam,
            LcClaimEconomySavedData.TeamLinkEntry entry
    ) {
        UUID ownerId = ftbTeam.getOwner();
        List<Runnable> stages = List.of(
                () -> syncOwner(server, lcTeam, ownerId),
                () -> syncName(ftbTeam, lcTeam),
                () -> reconcileRoster(server, ftbTeam, lcTeam, ownerId),
                () -> provisionBankAccount(ftbTeam, lcTeam),
                () -> migrateLegacyFunds(server, entry, lcTeam)
        );
        stages.forEach(Runnable::run);
        lcTeam.markDirty();
    }

    private static void syncOwner(MinecraftServer server, io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam, UUID ownerId) {
        PlayerReference ownerRef = toPlayerReference(server, ownerId);
        if (!ownerRef.is(lcTeam.getOwner())) {
            CurrencyTeamAccess.setOwner(lcTeam, ownerRef);
        }
    }

    private static void syncName(Team ftbTeam, io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam) {
        String clampedName = clampToMaxNameLength(ftbTeam.getShortName());
        if (!clampedName.equals(lcTeam.getName())) {
            CurrencyTeamAccess.setName(lcTeam, clampedName);
        }
    }

    /** Adds/removes/reclassifies admins and members so the LC roster matches the FTB party's current one exactly. */
    private static void reconcileRoster(
            MinecraftServer server,
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam,
            UUID ownerId
    ) {
        Map<Boolean, Set<UUID>> desired = classifyDesiredRoster(ftbTeam, ownerId);
        Set<UUID> wantedAdmins = desired.get(Boolean.TRUE);
        Set<UUID> wantedMembers = desired.get(Boolean.FALSE);

        List<PlayerReference> currentAdmins = CurrencyTeamAccess.admins(lcTeam);
        List<PlayerReference> currentMembers = CurrencyTeamAccess.members(lcTeam);

        dropUnwanted(currentAdmins, wantedAdmins);
        dropUnwanted(currentMembers, wantedMembers);

        relocateAll(server, wantedAdmins, currentMembers, currentAdmins);
        relocateAll(server, wantedMembers, currentAdmins, currentMembers);

        refreshCachedNames(server, lcTeam);
    }

    /** One party member paired with their resolved rank, so the rank lookup happens exactly once per member. */
    private record RankedMember(UUID id, TeamRank rank) {
    }

    /**
     * Classifies every tracked, non-owner party member into the admin bucket ({@code true})
     * or the member bucket ({@code false}) via a declarative filter+partition pipeline,
     * instead of an imperative loop with nested if/else branches. {@link TeamRankBridge}'s
     * admin/member checks are mutually exclusive for any rank that passes
     * {@code isTrackedMember}, so partitioning on "is admin" is equivalent to the original
     * if-admin-else-if-member branching.
     */
    private static Map<Boolean, Set<UUID>> classifyDesiredRoster(Team ftbTeam, UUID ownerId) {
        return ftbTeam.getMembers().stream()
                .filter(id -> !id.equals(ownerId))
                .map(id -> new RankedMember(id, ftbTeam.getRankForPlayer(id)))
                .filter(member -> TeamRankBridge.isTrackedMember(member.rank()))
                .filter(member -> TeamRankBridge.isLcAdmin(member.rank(), member.id(), ownerId)
                        || TeamRankBridge.isLcMember(member.rank(), member.id(), ownerId))
                .collect(Collectors.partitioningBy(
                        member -> TeamRankBridge.isLcAdmin(member.rank(), member.id(), ownerId),
                        Collectors.mapping(RankedMember::id, Collectors.toCollection(HashSet::new))
                ));
    }

    /** Removes any roster entry whose id fell out of the wanted set, without touching entries still wanted. */
    private static void dropUnwanted(List<PlayerReference> current, Set<UUID> wantedIds) {
        for (PlayerReference ref : List.copyOf(current)) {
            if (!wantedIds.contains(ref.id)) {
                PlayerReference.removeFromList(current, ref);
            }
        }
    }

    /** Moves each wanted id from {@code sourceList} into {@code destinationList}, resolving a fresh reference along the way. */
    private static void relocateAll(MinecraftServer server, Set<UUID> ids, List<PlayerReference> sourceList, List<PlayerReference> destinationList) {
        for (UUID id : ids) {
            PlayerReference ref = toPlayerReference(server, id);
            PlayerReference.removeFromList(sourceList, ref);
            PlayerReference.addToList(destinationList, ref);
        }
    }

    /** LC caches each member's display name on their {@link PlayerReference}; re-stamp it in case a player has changed their name since the reference was created. */
    private static void refreshCachedNames(MinecraftServer server, io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam) {
        PlayerReference owner = CurrencyTeamAccess.ensureNamed(lcTeam.getOwner(), resolvePlayerName(server, lcTeam.getOwner().id));
        CurrencyTeamAccess.setOwner(lcTeam, owner);

        List<PlayerReference> admins = CurrencyTeamAccess.admins(lcTeam);
        for (int i = 0; i < admins.size(); i++) {
            PlayerReference admin = admins.get(i);
            CurrencyTeamAccess.replacePlayerReference(admins, i, CurrencyTeamAccess.ensureNamed(admin, resolvePlayerName(server, admin.id)));
        }

        List<PlayerReference> members = CurrencyTeamAccess.members(lcTeam);
        for (int i = 0; i < members.size(); i++) {
            PlayerReference member = members.get(i);
            CurrencyTeamAccess.replacePlayerReference(members, i, CurrencyTeamAccess.ensureNamed(member, resolvePlayerName(server, member.id)));
        }
    }

    private static void provisionBankAccount(
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam
    ) {
        if (lcTeam.hasBankAccount()) {
            return;
        }
        ServerPlayer onlineOwner = resolveOnlinePlayer(ftbTeam, ftbTeam.getOwner());
        if (onlineOwner != null) {
            lcTeam.createBankAccount(onlineOwner);
        } else {
            CurrencyTeamAccess.createBankAccount(lcTeam);
        }
    }

    /**
     * Before this mod linked FTB parties to real LC teams, each party had its own
     * standalone {@link BankAccount} on {@code entry.legacyAccount()}. Once a proper LC
     * team account exists, whatever balance is still sitting in that legacy account is
     * swept over and the legacy account zeroed out, so old worlds don't lose money in the
     * transition.
     */
    private static void migrateLegacyFunds(
            MinecraftServer server,
            LcClaimEconomySavedData.TeamLinkEntry entry,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam
    ) {
        BankAccount legacyAccount = entry.legacyAccount();
        if (legacyAccount == null || !lcTeam.hasBankAccount()) {
            return;
        }

        IBankAccount lcAccount = lcTeam.getBankAccount();
        if (lcAccount == null) {
            return;
        }

        List<MoneyValue> heldValues = legacyAccount.getMoneyStorage().allValues().stream()
                .filter(value -> !value.isEmpty())
                .toList();
        if (heldValues.isEmpty()) {
            return;
        }

        heldValues.forEach(lcAccount::depositMoney);
        legacyAccount.getMoneyStorage().clear();
        LcClaimEconomySavedData.get(server).clearLegacyAccount(entry.ftbTeamId());
        LcClaimEconomy.LOGGER.info(
                "Migrated legacy FTB hook balance to LC team {} for FTB party {}",
                lcTeam.getID(),
                entry.ftbTeamId()
        );
    }

    @Nullable
    private static ServerPlayer resolveOnlinePlayer(Team ftbTeam, UUID preferredId) {
        ServerPlayer anyOnlineMember = null;
        for (ServerPlayer candidate : ftbTeam.getOnlineMembers()) {
            anyOnlineMember = candidate;
            break;
        }
        if (anyOnlineMember == null) {
            return null;
        }

        ServerPlayer preferred = anyOnlineMember.getServer().getPlayerList().getPlayer(preferredId);
        return preferred != null ? preferred : anyOnlineMember;
    }

    private static PlayerReference toPlayerReference(MinecraftServer server, UUID playerId) {
        if (server != null) {
            ServerPlayer onlinePlayer = server.getPlayerList().getPlayer(playerId);
            if (onlinePlayer != null) {
                return PlayerReference.of(onlinePlayer);
            }
        }
        return PlayerReference.of(playerId, resolvePlayerName(server, playerId));
    }

    private static String resolvePlayerName(@Nullable MinecraftServer server, UUID playerId) {
        String cachedName = PlayerReference.getPlayerName(playerId);
        if (cachedName != null && !cachedName.isBlank()) {
            return cachedName;
        }
        if (server != null && server.getProfileCache() != null) {
            return server.getProfileCache().get(playerId)
                    .map(profile -> profile.getName())
                    .filter(profileName -> profileName != null && !profileName.isBlank())
                    .orElse(playerId.toString());
        }
        return playerId.toString();
    }

    private static String clampToMaxNameLength(String name) {
        int maxLength = io.github.lightman314.lightmanscurrency.common.teams.Team.MAX_NAME_LENGTH;
        return name.length() <= maxLength ? name : name.substring(0, maxLength);
    }
}
