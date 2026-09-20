package dev.voidpulsar.lc_claim_economy.teams;

import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import io.github.lightman314.lightmanscurrency.api.misc.player.PlayerReference;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.teams.ITeam;
import io.github.lightman314.lightmanscurrency.api.teams.TeamAPI;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.parties.party.member.PartyMemberRank;
import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * OP&C equivalent of {@link CurrencyTeamLinkService}. Reuses the same
 * {@link LcClaimEconomySavedData} link storage as FTB parties, since it is
 * already keyed purely by a UUID - here, the OP&C party's own ID rather
 * than an FTB team ID. FTB and OP&C party UUIDs share no meaningful
 * relationship, so collisions are not a practical concern.
 * <p>
 * Covers bank account creation and owner/admin/member role sync; periodic
 * protection (force-load + land/build) upkeep billing against the linked
 * LC team account is handled separately by
 * {@code dev.voidpulsar.lc_claim_economy.opc.OpcBillingCycleService}. OP&C
 * parties never participate in wars - out of scope by design, not just
 * missing.
 */
public final class OpcPartySyncService {
    private static final ConcurrentHashMap<UUID, Object> LINK_LOCKS = new ConcurrentHashMap<>();

    private OpcPartySyncService() {
    }

    public static void ensureLinked(MinecraftServer server, IServerPartyAPI party) {
        if (CurrencyTeamAccess.cache() == null) {
            return;
        }

        synchronized (linkLock(party.getId())) {
            LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
            LcClaimEconomySavedData.TeamLinkEntry entry = data.getOrCreateLink(party.getId());
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam = resolveLcTeam(entry);
            if (lcTeam == null) {
                lcTeam = createLcTeam(server, party);
                if (lcTeam == null) {
                    return;
                }
                data.setLcTeamId(party.getId(), lcTeam.getID());
                LcClaimEconomy.LOGGER.info(
                        "Created LC team {} for OP&C party {}",
                        lcTeam.getID(),
                        party.getId()
                );
            }

            syncTeamState(server, party, lcTeam);
        }
    }

    private static Object linkLock(UUID partyId) {
        return LINK_LOCKS.computeIfAbsent(partyId, id -> new Object());
    }

    @Nullable
    public static IBankAccount getBankAccount(MinecraftServer server, IServerPartyAPI party) {
        ensureLinked(server, party);
        LcClaimEconomySavedData.TeamLinkEntry entry = LcClaimEconomySavedData.get(server).get(party.getId());
        if (entry == null || entry.lcTeamId() <= 0) {
            return null;
        }
        ITeam lcTeam = TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
        return lcTeam == null ? null : lcTeam.getBankAccount();
    }

    @Nullable
    public static IBankAccount getLinkedBankAccount(MinecraftServer server, UUID partyId) {
        if (server == null || partyId == null) {
            return null;
        }
        LcClaimEconomySavedData.TeamLinkEntry entry = LcClaimEconomySavedData.get(server).get(partyId);
        if (entry == null || entry.lcTeamId() <= 0) {
            return null;
        }
        ITeam lcTeam = TeamAPI.getApi().GetTeam(false, entry.lcTeamId());
        return lcTeam == null ? null : lcTeam.getBankAccount();
    }

    public static void onPartyRemoved(MinecraftServer server, UUID partyId) {
        LcClaimEconomySavedData data = LcClaimEconomySavedData.get(server);
        LcClaimEconomySavedData.TeamLinkEntry entry = data.get(partyId);
        if (entry == null || entry.lcTeamId() <= 0) {
            return;
        }

        io.github.lightman314.lightmanscurrency.common.data.types.TeamDataCache cache = CurrencyTeamAccess.cache();
        if (cache != null) {
            long lcTeamId = entry.lcTeamId();
            CurrencyTeamPurgeGuard.runAllowed(() -> cache.removeTeam(lcTeamId));
        }
        data.removeLink(partyId);
        LcClaimEconomy.LOGGER.info("Removed LC team {} for deleted OP&C party {}", entry.lcTeamId(), partyId);
    }

    @Nullable
    private static io.github.lightman314.lightmanscurrency.common.teams.Team resolveLcTeam(
            LcClaimEconomySavedData.TeamLinkEntry entry
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
    private static io.github.lightman314.lightmanscurrency.common.teams.Team createLcTeam(
            MinecraftServer server,
            IServerPartyAPI party
    ) {
        String name = truncateName(party.getDefaultName());
        PlayerReference ownerRef = playerRef(server, party.getOwner().getUUID(), party.getOwner().getUsername());
        return CurrencyTeamAccess.registerTeam(ownerRef, name);
    }

    private static void syncTeamState(
            MinecraftServer server,
            IServerPartyAPI party,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam
    ) {
        IPartyMemberAPI owner = party.getOwner();
        PlayerReference ownerRef = playerRef(server, owner.getUUID(), owner.getUsername());
        if (!ownerRef.is(lcTeam.getOwner())) {
            CurrencyTeamAccess.setOwner(lcTeam, ownerRef);
        }

        String name = truncateName(party.getDefaultName());
        if (!name.equals(lcTeam.getName())) {
            CurrencyTeamAccess.setName(lcTeam, name);
        }

        syncMembers(server, party, lcTeam);
        ensureBankAccount(server, party, lcTeam);
        lcTeam.markDirty();
    }

    private static void syncMembers(
            MinecraftServer server,
            IServerPartyAPI party,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam
    ) {
        UUID ownerId = party.getOwner().getUUID();
        Set<UUID> targetAdmins = new HashSet<>();
        Set<UUID> targetMembers = new HashSet<>();

        try (Stream<IPartyMemberAPI> stream = party.getMemberInfoStream()) {
            for (IPartyMemberAPI member : stream.toList()) {
                UUID playerId = member.getUUID();
                if (playerId.equals(ownerId)) {
                    continue;
                }
                PartyMemberRank rank = member.getRank();
                boolean isOwner = member.isOwner();
                if (OpcRoleMapping.isLcAdmin(rank, isOwner)) {
                    targetAdmins.add(playerId);
                } else if (OpcRoleMapping.isLcMember(rank, isOwner)) {
                    targetMembers.add(playerId);
                }
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
            PlayerReference ref = playerRef(server, adminId, null);
            PlayerReference.removeFromList(members, ref);
            PlayerReference.addToList(admins, ref);
        }
        for (UUID memberId : targetMembers) {
            PlayerReference ref = playerRef(server, memberId, null);
            PlayerReference.removeFromList(admins, ref);
            PlayerReference.addToList(members, ref);
        }
    }

    private static void ensureBankAccount(
            MinecraftServer server,
            IServerPartyAPI party,
            io.github.lightman314.lightmanscurrency.common.teams.Team lcTeam
    ) {
        if (lcTeam.hasBankAccount()) {
            return;
        }
        ServerPlayer ownerPlayer = findOnlinePlayer(server, party);
        if (ownerPlayer != null) {
            lcTeam.createBankAccount(ownerPlayer);
        } else {
            CurrencyTeamAccess.createBankAccount(lcTeam);
        }
    }

    @Nullable
    private static ServerPlayer findOnlinePlayer(MinecraftServer server, IServerPartyAPI party) {
        ServerPlayer owner = server.getPlayerList().getPlayer(party.getOwner().getUUID());
        if (owner != null) {
            return owner;
        }
        try (Stream<ServerPlayer> stream = party.getOnlineMemberStream()) {
            return stream.findFirst().orElse(null);
        }
    }

    private static PlayerReference playerRef(MinecraftServer server, UUID playerId, @Nullable String knownName) {
        if (server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                return PlayerReference.of(player);
            }
        }
        String name = (knownName != null && !knownName.isBlank()) ? knownName : resolvePlayerName(server, playerId);
        return PlayerReference.of(playerId, name);
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
