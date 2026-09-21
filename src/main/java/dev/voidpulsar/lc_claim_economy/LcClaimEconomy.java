package dev.voidpulsar.lc_claim_economy;

import com.mojang.logging.LogUtils;
import dev.voidpulsar.lc_claim_economy.config.LcClaimEconomyConfig;
import dev.voidpulsar.lc_claim_economy.command.ResetConflictsCommand;
import dev.voidpulsar.lc_claim_economy.command.GenerateSampleTeamsCommand;
import dev.voidpulsar.lc_claim_economy.command.BillingDetailsCommand;
import dev.voidpulsar.lc_claim_economy.command.BillingPriorityCommand;
import dev.voidpulsar.lc_claim_economy.handler.ChunkAcquisitionHandler;
import dev.voidpulsar.lc_claim_economy.handler.ChunkLoadPinHandler;
import dev.voidpulsar.lc_claim_economy.handler.LevyCollectorPlacementHandler;
import dev.voidpulsar.lc_claim_economy.handler.TeamLifecycleWatcher;
import dev.voidpulsar.lc_claim_economy.handler.TeamPropertyHandler;
import dev.voidpulsar.lc_claim_economy.network.PricingRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestChunkUserPermsPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestLandChunksPayload;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.PricingBroadcastPayload;
import dev.voidpulsar.lc_claim_economy.network.SyncChunkUserPermsPayload;
import dev.voidpulsar.lc_claim_economy.network.SyncLandChunksPayload;
import dev.voidpulsar.lc_claim_economy.network.QueuedStateBroadcastPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictStateRequestPayload;
import dev.voidpulsar.lc_claim_economy.network.SetChunkUserPermsPayload;
import dev.voidpulsar.lc_claim_economy.network.ConflictStateBroadcastPayload;
import dev.voidpulsar.lc_claim_economy.network.ToggleChunkTypeBatchPayload;
import dev.voidpulsar.lc_claim_economy.network.ToggleChunkTypePayload;
import dev.voidpulsar.lc_claim_economy.network.ToggleConflictPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestWarpsPayload;
import dev.voidpulsar.lc_claim_economy.network.SyncWarpsPayload;
import dev.voidpulsar.lc_claim_economy.network.WarpCreatePayload;
import dev.voidpulsar.lc_claim_economy.network.WarpDeletePayload;
import dev.voidpulsar.lc_claim_economy.network.WarpSetPublicPayload;
import dev.voidpulsar.lc_claim_economy.network.WarpTeleportOtherPayload;
import dev.voidpulsar.lc_claim_economy.network.WarpTeleportOwnPayload;
import dev.voidpulsar.lc_claim_economy.network.RequestMarketPayload;
import dev.voidpulsar.lc_claim_economy.network.SyncMarketPayload;
import dev.voidpulsar.lc_claim_economy.network.MarketSellPayload;
import dev.voidpulsar.lc_claim_economy.network.MarketCancelPayload;
import dev.voidpulsar.lc_claim_economy.network.MarketBuyPayload;
import dev.voidpulsar.lc_claim_economy.client.ClientQueuedStateRefreshHandler;
import dev.voidpulsar.lc_claim_economy.service.BillingCycleService;
import dev.voidpulsar.lc_claim_economy.teams.LandProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(LcClaimEconomy.MOD_ID)
public class LcClaimEconomy {
    public static final String MOD_ID = "lc_claim_economy";
    /** Typed command root ({@code /lcce ...}) - kept short-hand and separate from {@link #MOD_ID}, which stays the resource/network namespace. */
    public static final String COMMAND_ROOT = "lcce";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LcClaimEconomy(IEventBus modEventBus, ModContainer modContainer) {
        DependencyVersionGuard.validateOrThrow();

        modContainer.registerConfig(ModConfig.Type.SERVER, LcClaimEconomyConfig.SERVER_SPEC);

        modEventBus.addListener(this::registerPayloads);

        LandProperties.register();

        if (dev.voidpulsar.lc_claim_economy.compat.ModCompat.isFtbAvailable()) {
            NeoForge.EVENT_BUS.register(new BillingCycleService());
            NeoForge.EVENT_BUS.register(new TeamLifecycleWatcher());
            NeoForge.EVENT_BUS.register(new LevyCollectorPlacementHandler());

            new ChunkAcquisitionHandler();
            new TeamPropertyHandler();
            new ChunkLoadPinHandler();

            NeoForge.EVENT_BUS.addListener(BillingDetailsCommand::register);
            NeoForge.EVENT_BUS.addListener(BillingPriorityCommand::register);
            NeoForge.EVENT_BUS.addListener(GenerateSampleTeamsCommand::register);
            NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.command.QuestRewardCommand::register);
            NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.command.LeaderboardCommand::register);
            NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.command.BountyCommand::register);
            NeoForge.EVENT_BUS.register(new dev.voidpulsar.lc_claim_economy.handler.BountyKillHandler());
            NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.command.MarketCommand::register);
            NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.command.WarpCommand::register);
        } else {
            LOGGER.info("FTB Chunks/Teams not detected - FTB Chunks integration disabled.");
        }

        if (dev.voidpulsar.lc_claim_economy.compat.ModCompat.isFtbAvailable()
                && dev.voidpulsar.lc_claim_economy.compat.ModCompat.isBlueMapAvailable()) {
            NeoForge.EVENT_BUS.register(new dev.voidpulsar.lc_claim_economy.bluemap.BlueMapClaimIntegration());
            LOGGER.info("BlueMap detected - claim overlay integration enabled.");
        }

        if (dev.voidpulsar.lc_claim_economy.compat.ModCompat.isOpcAvailable()) {
            NeoForge.EVENT_BUS.register(new dev.voidpulsar.lc_claim_economy.opc.OpcIntegration());
            NeoForge.EVENT_BUS.register(new dev.voidpulsar.lc_claim_economy.opc.OpcBillingCycleService());
            NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.opc.OpcChunkTypeCommand::register);
            NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.opc.OpcBillingDetailsCommand::register);
            LOGGER.info("Open Parties and Claims detected - OP&C claim economy integration enabled.");
        }

        NeoForge.EVENT_BUS.addListener(ResetConflictsCommand::register);
        NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.command.WebLoginCommand::register);
        NeoForge.EVENT_BUS.addListener(dev.voidpulsar.lc_claim_economy.command.WarPeacefulCommand::register);
        NeoForge.EVENT_BUS.register(new dev.voidpulsar.lc_claim_economy.handler.CoinMintDisableHandler());
        NeoForge.EVENT_BUS.register(new dev.voidpulsar.lc_claim_economy.web.WebServerLifecycle());

        if (FMLEnvironment.dist == Dist.CLIENT && dev.voidpulsar.lc_claim_economy.compat.ModCompat.isFtbAvailable()) {
            new ClientQueuedStateRefreshHandler();
        }
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToClient(
                PricingBroadcastPayload.TYPE,
                PricingBroadcastPayload.STREAM_CODEC,
                PricingBroadcastPayload::handleClient
        );
        registrar.playToClient(
                QueuedStateBroadcastPayload.TYPE,
                QueuedStateBroadcastPayload.STREAM_CODEC,
                QueuedStateBroadcastPayload::handleClient
        );
        registrar.playToClient(
                SyncLandChunksPayload.TYPE,
                SyncLandChunksPayload.STREAM_CODEC,
                SyncLandChunksPayload::handleClient
        );
        registrar.playToClient(
                ConflictStateBroadcastPayload.TYPE,
                ConflictStateBroadcastPayload.STREAM_CODEC,
                ConflictStateBroadcastPayload::handleClient
        );
        registrar.playToClient(
                SyncChunkUserPermsPayload.TYPE,
                SyncChunkUserPermsPayload.STREAM_CODEC,
                SyncChunkUserPermsPayload::handleClient
        );
        registrar.playToClient(
                SyncWarpsPayload.TYPE,
                SyncWarpsPayload.STREAM_CODEC,
                SyncWarpsPayload::handleClient
        );
        registrar.playToServer(
                RequestWarpsPayload.TYPE,
                RequestWarpsPayload.STREAM_CODEC,
                RequestWarpsPayload::handleServer
        );
        registrar.playToServer(
                WarpCreatePayload.TYPE,
                WarpCreatePayload.STREAM_CODEC,
                WarpCreatePayload::handleServer
        );
        registrar.playToServer(
                WarpDeletePayload.TYPE,
                WarpDeletePayload.STREAM_CODEC,
                WarpDeletePayload::handleServer
        );
        registrar.playToServer(
                WarpSetPublicPayload.TYPE,
                WarpSetPublicPayload.STREAM_CODEC,
                WarpSetPublicPayload::handleServer
        );
        registrar.playToServer(
                WarpTeleportOwnPayload.TYPE,
                WarpTeleportOwnPayload.STREAM_CODEC,
                WarpTeleportOwnPayload::handleServer
        );
        registrar.playToServer(
                WarpTeleportOtherPayload.TYPE,
                WarpTeleportOtherPayload.STREAM_CODEC,
                WarpTeleportOtherPayload::handleServer
        );
        registrar.playToClient(
                SyncMarketPayload.TYPE,
                SyncMarketPayload.STREAM_CODEC,
                SyncMarketPayload::handleClient
        );
        registrar.playToServer(
                RequestMarketPayload.TYPE,
                RequestMarketPayload.STREAM_CODEC,
                RequestMarketPayload::handleServer
        );
        registrar.playToServer(
                MarketSellPayload.TYPE,
                MarketSellPayload.STREAM_CODEC,
                MarketSellPayload::handleServer
        );
        registrar.playToServer(
                MarketCancelPayload.TYPE,
                MarketCancelPayload.STREAM_CODEC,
                MarketCancelPayload::handleServer
        );
        registrar.playToServer(
                MarketBuyPayload.TYPE,
                MarketBuyPayload.STREAM_CODEC,
                MarketBuyPayload::handleServer
        );
        registrar.playToServer(
                PricingRequestPayload.TYPE,
                PricingRequestPayload.STREAM_CODEC,
                PricingRequestPayload::handleServer
        );
        registrar.playToServer(
                QueuedStateRequestPayload.TYPE,
                QueuedStateRequestPayload.STREAM_CODEC,
                QueuedStateRequestPayload::handleServer
        );
        registrar.playToServer(
                RequestLandChunksPayload.TYPE,
                RequestLandChunksPayload.STREAM_CODEC,
                RequestLandChunksPayload::handleServer
        );
        registrar.playToServer(
                RequestChunkUserPermsPayload.TYPE,
                RequestChunkUserPermsPayload.STREAM_CODEC,
                RequestChunkUserPermsPayload::handleServer
        );
        registrar.playToServer(
                SetChunkUserPermsPayload.TYPE,
                SetChunkUserPermsPayload.STREAM_CODEC,
                SetChunkUserPermsPayload::handleServer
        );
        registrar.playToServer(
                ToggleChunkTypePayload.TYPE,
                ToggleChunkTypePayload.STREAM_CODEC,
                ToggleChunkTypePayload::handleServer
        );
        registrar.playToServer(
                ToggleChunkTypeBatchPayload.TYPE,
                ToggleChunkTypeBatchPayload.STREAM_CODEC,
                ToggleChunkTypeBatchPayload::handleServer
        );
        registrar.playToServer(
                ConflictStateRequestPayload.TYPE,
                ConflictStateRequestPayload.STREAM_CODEC,
                ConflictStateRequestPayload::handleServer
        );
        registrar.playToServer(
                ToggleConflictPayload.TYPE,
                ToggleConflictPayload.STREAM_CODEC,
                ToggleConflictPayload::handleServer
        );
    }
}
