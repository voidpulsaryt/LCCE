package dev.voidpulsar.lc_claim_economy.service;

import dev.ftb.mods.ftbchunks.api.ChunkTeamData;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsar.lc_claim_economy.LcClaimEconomy;
import dev.voidpulsar.lc_claim_economy.data.LcClaimEconomySavedData;
import dev.voidpulsar.lc_claim_economy.teams.TeamRegistry;
import dev.voidpulsar.lc_claim_economy.teams.CurrencyTeamLinkService;
import dev.voidpulsar.lc_claim_economy.service.ConflictSyncCoordinator;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PartyDissolutionSettlement {
    private static final Set<UUID> SETTLING = ConcurrentHashMap.newKeySet();

    private PartyDissolutionSettlement() {
    }

    public static void settle(MinecraftServer server, Team team) {
        if (!team.isPartyTeam()) {
            return;
        }

        UUID teamId = team.getId();
        if (!SETTLING.add(teamId)) {
            return;
        }

        try {
            // War cleanup and pending state must be cleared regardless of whether
            // account transfer succeeds — otherwise disbanded teams leave orphaned
            // war references and pending protection entries in SavedData.
            LcClaimEconomySavedData savedData = LcClaimEconomySavedData.get(server);
            TeamRegistry.dissolveWarLinks(server, teamId);
            savedData.setPendingState(teamId, savedData.getPendingState(teamId).cleared());

            UUID ownerId = team.getOwner();
            if (ownerId == null) {
                return;
            }

            IBankAccount teamAccount = CurrencyTeamLinkService.getLinkedBankAccount(server, teamId);
            if (teamAccount == null) {
                LcClaimEconomy.LOGGER.warn("No linked LC bank account found for disbanding party {}", teamId);
                return;
            }

            IBankAccount ownerAccount = PlayerBankReference.of(ownerId).get();
            if (ownerAccount == null) {
                LcClaimEconomy.LOGGER.warn(
                        "Could not transfer party funds for {}: missing personal account for owner {}",
                        teamId,
                        ownerId
                );
                return;
            }

            Component accountBalance = CurrencyTextFormat.formatBalance(teamAccount);
            int claimedChunks = 0;
            int unclaimedChunks = 0;
            long refundCopper = 0L;

            if (FTBChunksAPI.api().isManagerLoaded()) {
                QueuedChangeService.removeAllForceLoads(server, team);
                ChunkTeamData chunkData = FTBChunksAPI.api().getManager().getOrCreateData(team);
                CommandSourceStack source = server.createCommandSourceStack().withSuppressedOutput();
                claimedChunks = chunkData.getClaimedChunks().size();
                unclaimedChunks = ClaimSettlementSupport.unclaimAll(chunkData, source);
                refundCopper = (long) ComplimentaryChunkAllotment.billableChunkCount(claimedChunks) * ClaimSettlementSupport.refundPerChunk();
            }

            if (teamAccount.getMoneyStorage().isEmpty() && unclaimedChunks == 0) {
                return;
            }

            Component totalReceived = CurrencyTextFormat.formatBalance(teamAccount);
            transferAllFunds(teamAccount, ownerAccount);
            notifyOwner(server, ownerId, accountBalance, totalReceived, unclaimedChunks, refundCopper);
            ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
            if (owner != null) {
                ConflictSyncCoordinator.syncToPlayer(owner);
            }
            LcClaimEconomy.LOGGER.info(
                    "Settled disbanded party {} for owner {} ({} chunks, {} refund copper)",
                    teamId,
                    ownerId,
                    unclaimedChunks,
                    refundCopper
            );
        } catch (Exception exception) {
            LcClaimEconomy.LOGGER.error("Failed to settle disbanded party {}", teamId, exception);
        } finally {
            SETTLING.remove(teamId);
        }
    }

    private static void transferAllFunds(IBankAccount teamAccount, IBankAccount ownerAccount) {
        for (MoneyValue value : teamAccount.getMoneyStorage().allValues()) {
            if (value.isEmpty()) {
                continue;
            }
            teamAccount.withdrawMoney(value);
            ownerAccount.depositMoney(value);
        }
    }

    private static void notifyOwner(
            MinecraftServer server,
            UUID ownerId,
            Component accountBalance,
            Component totalReceived,
            int soldChunks,
            long refundCopper
    ) {
        ServerPlayer owner = server.getPlayerList().getPlayer(ownerId);
        if (owner == null) {
            return;
        }

        Component chunkRefunds = CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(refundCopper));
        owner.displayClientMessage(
                Component.translatable(
                        "message.lc_claim_economy.party_disband_settlement",
                        accountBalance,
                        totalReceived,
                        soldChunks,
                        chunkRefunds
                ),
                false
        );
    }
}
