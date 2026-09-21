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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        if (!ftbTeam.isPartyTeam() || !ftbTeam.isValid()) {
            return;
        }
        if (!TeamBankLinkRegistry.isFtbPartyInUse(server, ftbTeam)) {
            return;
        }
        if (CurrencyTeamAccess.cache() == null) {
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
     * actually linked to.
     */
    private static void pruneDuplicateLcTeams(Team ftbTeam, LcClaimEconomySavedData data, long linkedLcTeamId) {
        TeamDataCache cache = CurrencyTeamAccess.cache();
        if (cache == null) {
            return;
        }

        String expectedName = clampToMaxNameLength(ftbTeam.getShortName());
        UUID ownerId = ftbTeam.getOwner();
        Set<Long> linkedIds = data.getLinkedLcTeamIds();

        for (ITeam candidate : cache.getAllTeams()) {
            if (candidate.getID() == linkedLcTeamId || linkedIds.contains(candidate.getID())) {
                continue;
            }
            if (!ownerId.equals(candidate.getOwner().id) || !expectedName.equals(candidate.getName())) {
                continue;
            }

            long duplicateId = candidate.getID();
            CurrencyTeamPurgeGuard.runAllowed(() -> cache.removeTeam(duplicateId));
            LcClaimEconomy.LOGGER.info(
                    "Removed duplicate LC team {} for FTB party {} (linked team is {})",
                    duplicateId,
                    ftbTeam.getId(),
                    linkedLcTeamId
            );
        }
    }

    private static void applyPartyStateToLcTeam(
            MinecraftServer server,
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam,
            LcClaimEconomySavedData.TeamLinkEntry entry
    ) {
        UUID ownerId = ftbTeam.getOwner();
        PlayerReference ownerRef = toPlayerReference(server, ownerId);
        if (!ownerRef.is(lcTeam.getOwner())) {
            CurrencyTeamAccess.setOwner(lcTeam, ownerRef);
        }

        String clampedName = clampToMaxNameLength(ftbTeam.getShortName());
        if (!clampedName.equals(lcTeam.getName())) {
            CurrencyTeamAccess.setName(lcTeam, clampedName);
        }

        reconcileRoster(server, ftbTeam, lcTeam, ownerId);
        provisionBankAccount(ftbTeam, lcTeam);
        migrateLegacyFunds(server, entry, lcTeam);
        lcTeam.markDirty();
    }

    /** Adds/removes/reclassifies admins and members so the LC roster matches the FTB party's current one exactly. */
    private static void reconcileRoster(
            MinecraftServer server,
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam,
            UUID ownerId
    ) {
        Set<UUID> wantedAdmins = new HashSet<>();
        Set<UUID> wantedMembers = new HashSet<>();

        for (UUID playerId : ftbTeam.getMembers()) {
            if (playerId.equals(ownerId)) {
                continue;
            }
            TeamRank rank = ftbTeam.getRankForPlayer(playerId);
            if (!TeamRankBridge.isTrackedMember(rank)) {
                continue;
            }
            if (TeamRankBridge.isLcAdmin(rank, playerId, ownerId)) {
                wantedAdmins.add(playerId);
            } else if (TeamRankBridge.isLcMember(rank, playerId, ownerId)) {
                wantedMembers.add(playerId);
            }
        }

        List<PlayerReference> currentAdmins = CurrencyTeamAccess.admins(lcTeam);
        List<PlayerReference> currentMembers = CurrencyTeamAccess.members(lcTeam);

        for (PlayerReference admin : List.copyOf(currentAdmins)) {
            if (!wantedAdmins.contains(admin.id)) {
                PlayerReference.removeFromList(currentAdmins, admin);
            }
        }
        for (PlayerReference member : List.copyOf(currentMembers)) {
            if (!wantedMembers.contains(member.id)) {
                PlayerReference.removeFromList(currentMembers, member);
            }
        }

        for (UUID adminId : wantedAdmins) {
            PlayerReference ref = toPlayerReference(server, adminId);
            PlayerReference.removeFromList(currentMembers, ref);
            PlayerReference.addToList(currentAdmins, ref);
        }
        for (UUID memberId : wantedMembers) {
            PlayerReference ref = toPlayerReference(server, memberId);
            PlayerReference.removeFromList(currentAdmins, ref);
            PlayerReference.addToList(currentMembers, ref);
        }

        refreshCachedNames(server, lcTeam);
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

        boolean movedAny = false;
        for (MoneyValue value : legacyAccount.getMoneyStorage().allValues()) {
            if (!value.isEmpty()) {
                lcAccount.depositMoney(value);
                movedAny = true;
            }
        }
        if (movedAny) {
            legacyAccount.getMoneyStorage().clear();
            LcClaimEconomySavedData.get(server).clearLegacyAccount(entry.ftbTeamId());
            LcClaimEconomy.LOGGER.info(
                    "Migrated legacy FTB hook balance to LC team {} for FTB party {}",
                    lcTeam.getID(),
                    entry.ftbTeamId()
            );
        }
    }

    @Nullable
    private static ServerPlayer resolveOnlinePlayer(Team ftbTeam, UUID preferredId) {
        MinecraftServer server = ftbTeam.getOnlineMembers().stream()
                .findFirst()
                .map(ServerPlayer::getServer)
                .orElse(null);
        if (server == null) {
            return null;
        }
        ServerPlayer preferred = server.getPlayerList().getPlayer(preferredId);
        if (preferred != null) {
            return preferred;
        }
        return ftbTeam.getOnlineMembers().stream().findFirst().orElse(null);
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
