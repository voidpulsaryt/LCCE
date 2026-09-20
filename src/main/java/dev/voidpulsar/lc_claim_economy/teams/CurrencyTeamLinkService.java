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

public final class CurrencyTeamLinkService {
    private static final ConcurrentHashMap<UUID, Object> LINK_LOCKS = new ConcurrentHashMap<>();

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

        synchronized (linkLock(ftbTeam.getId())) {
            LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
            LcClaimEconomySavedData.TeamLinkEntry entry = data.getOrCreateLink(ftbTeam.getId());
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam = resolveLcTeam(entry, ftbTeam);
            if (lcTeam == null) {
                lcTeam = createLcTeam(server, ftbTeam);
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

            removeUnlinkedDuplicateTeams(server, data, ftbTeam, lcTeam.getID());
            syncTeamState(server, ftbTeam, lcTeam, entry);
        }
    }

    private static Object linkLock(UUID ftbTeamId) {
        return LINK_LOCKS.computeIfAbsent(ftbTeamId, id -> new Object());
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

        CurrencyTeamPurgeGuard.runAllowed(() -> cache.removeTeam(entry.lcTeamId()));
        if (LcClaimEconomySavedData.get(server).removeLink(ftbTeam.getId()) != null) {
            LcClaimEconomy.LOGGER.info("Removed hook data for deleted FTB party {}", ftbTeam.getId());
        }
        LcClaimEconomy.LOGGER.info("Removed LC team {} for deleted FTB party {}", entry.lcTeamId(), ftbTeam.getId());
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
    private static io.github.lightman314.lightmanscurrency.common.teams.Team resolveLcTeam(
            LcClaimEconomySavedData.TeamLinkEntry entry,
            Team ftbTeam
    ) {
        if (entry.lcTeamId() > 0) {
            ITeam existing = TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
            if (existing instanceof io.github.lightman314.lightmanscurrency.common.teams.Team team) {
                return team;
            }
        }
        return null;
    }

    @Nullable
    private static io.github.lightman314.lightmanscurrency.common.teams.Team createLcTeam(MinecraftServer server, Team ftbTeam) {
        String name = truncateName(ftbTeam.getShortName());
        PlayerReference ownerRef = playerRef(server, ftbTeam.getOwner());
        return CurrencyTeamAccess.registerTeam(ownerRef, name);
    }

    private static void removeUnlinkedDuplicateTeams(
            MinecraftServer server,
            LcClaimEconomySavedData data,
            Team ftbTeam,
            long linkedLcTeamId
    ) {
        TeamDataCache cache = CurrencyTeamAccess.cache();
        if (cache == null) {
            return;
        }

        String expectedName = truncateName(ftbTeam.getShortName());
        UUID ownerId = ftbTeam.getOwner();
        Set<Long> linkedIds = data.getLinkedLcTeamIds();

        for (ITeam candidate : cache.getAllTeams()) {
            if (candidate.getID() == linkedLcTeamId || linkedIds.contains(candidate.getID())) {
                continue;
            }
            if (!ownerId.equals(candidate.getOwner().id)) {
                continue;
            }
            if (!expectedName.equals(candidate.getName())) {
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

    private static void syncTeamState(
            MinecraftServer server,
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam,
            LcClaimEconomySavedData.TeamLinkEntry entry
    ) {
        UUID ownerId = ftbTeam.getOwner();
        PlayerReference ownerRef = playerRef(server, ownerId);
        if (!ownerRef.is(lcTeam.getOwner())) {
            CurrencyTeamAccess.setOwner(lcTeam, ownerRef);
        }

        String name = truncateName(ftbTeam.getShortName());
        if (!name.equals(lcTeam.getName())) {
            CurrencyTeamAccess.setName(lcTeam, name);
        }

        syncMembers(server, ftbTeam, lcTeam, ownerId);
        ensureBankAccount(ftbTeam, lcTeam);
        migrateLegacyBalance(server, entry, lcTeam);
        lcTeam.markDirty();
    }

    private static void syncMembers(
            MinecraftServer server,
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam,
            UUID ownerId
    ) {
        Set<UUID> targetAdmins = new HashSet<>();
        Set<UUID> targetMembers = new HashSet<>();

        for (UUID playerId : ftbTeam.getMembers()) {
            if (playerId.equals(ownerId)) {
                continue;
            }
            TeamRank rank = ftbTeam.getRankForPlayer(playerId);
            if (!TeamRankBridge.isTrackedMember(rank)) {
                continue;
            }
            if (TeamRankBridge.isLcAdmin(rank, playerId, ownerId)) {
                targetAdmins.add(playerId);
            } else if (TeamRankBridge.isLcMember(rank, playerId, ownerId)) {
                targetMembers.add(playerId);
            }
        }

        List<PlayerReference> admins = CurrencyTeamAccess.admins(lcTeam);
        List<PlayerReference> members = CurrencyTeamAccess.members(lcTeam);

        for (PlayerReference admin : List.copyOf(admins)) {
            if (!targetAdmins.contains(admin.id)) {
                PlayerReference.removeFromList(admins, admin);
            }
        }
        for (PlayerReference member : List.copyOf(members)) {
            if (!targetMembers.contains(member.id)) {
                PlayerReference.removeFromList(members, member);
            }
        }

        for (UUID adminId : targetAdmins) {
            PlayerReference ref = playerRef(server, adminId);
            PlayerReference.removeFromList(members, ref);
            PlayerReference.addToList(admins, ref);
        }
        for (UUID memberId : targetMembers) {
            PlayerReference ref = playerRef(server, memberId);
            PlayerReference.removeFromList(admins, ref);
            PlayerReference.addToList(members, ref);
        }

        refreshStoredNames(server, lcTeam);
    }

    private static void refreshStoredNames(MinecraftServer server, io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam) {
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

    private static void ensureBankAccount(
            Team ftbTeam,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam
    ) {
        if (lcTeam.hasBankAccount()) {
            return;
        }
        ServerPlayer ownerPlayer = findOnlinePlayer(ftbTeam, ftbTeam.getOwner());
        if (ownerPlayer != null) {
            lcTeam.createBankAccount(ownerPlayer);
        } else {
            CurrencyTeamAccess.createBankAccount(lcTeam);
        }
    }

    private static void migrateLegacyBalance(
            MinecraftServer server,
            LcClaimEconomySavedData.TeamLinkEntry entry,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam
    ) {
        BankAccount legacyAccount = entry.legacyAccount();
        if (legacyAccount == null || !lcTeam.hasBankAccount()) {
            return;
        }

        IBankAccount target = lcTeam.getBankAccount();
        if (target == null) {
            return;
        }

        boolean migrated = false;
        for (MoneyValue value : legacyAccount.getMoneyStorage().allValues()) {
            if (!value.isEmpty()) {
                target.depositMoney(value);
                migrated = true;
            }
        }
        if (migrated) {
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
    private static ServerPlayer findOnlinePlayer(Team ftbTeam, UUID preferredOwnerId) {
        MinecraftServer server = ftbTeam.getOnlineMembers().stream()
                .findFirst()
                .map(ServerPlayer::getServer)
                .orElse(null);
        if (server == null) {
            return null;
        }
        ServerPlayer owner = server.getPlayerList().getPlayer(preferredOwnerId);
        if (owner != null) {
            return owner;
        }
        return ftbTeam.getOnlineMembers().stream().findFirst().orElse(null);
    }

    private static PlayerReference playerRef(MinecraftServer server, UUID playerId) {
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                return PlayerReference.of(player);
            }
        }
        return PlayerReference.of(playerId, resolvePlayerName(server, playerId));
    }

    private static String resolvePlayerName(@Nullable MinecraftServer server, UUID playerId) {
        String name = PlayerReference.getPlayerName(playerId);
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (server != null && server.getProfileCache() != null) {
            return server.getProfileCache().get(playerId)
                    .map(profile -> profile.getName())
                    .filter(profileName -> profileName != null && !profileName.isBlank())
                    .orElse(playerId.toString());
        }
        return playerId.toString();
    }

    private static String truncateName(String name) {
        int maxLength = io.github.lightman314.lightmanscurrency.common.teams.Team.MAX_NAME_LENGTH;
        if (name.length() <= maxLength) {
            return name;
        }
        return name.substring(0, maxLength);
    }
}
