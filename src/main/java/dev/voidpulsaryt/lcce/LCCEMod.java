package dev.voidpulsaryt.lcce;

import dev.voidpulsaryt.lcce.bounty.BountyListener;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.dashboard.Dashboard;
import dev.voidpulsaryt.lcce.economy.UpkeepScheduler;
import dev.voidpulsaryt.lcce.integration.ftb.ClaimChunkEconomyListener;
import dev.voidpulsaryt.lcce.integration.ftb.LCCECommand;
import dev.voidpulsaryt.lcce.integration.ftb.NationSync;
import dev.voidpulsaryt.lcce.integration.ftb.RegionSync;
import dev.voidpulsaryt.lcce.integration.ftb.TeamBalance;
import dev.voidpulsaryt.lcce.integration.protection.RegionProtectionListener;
import dev.voidpulsaryt.lcce.network.NetworkHandlers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@Mod(LCCEMod.MOD_ID)
public final class LCCEMod {

    public static final String MOD_ID = "lcce";

    public LCCEMod(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, LCCEConfig.SPEC);

        // FTB Teams' COLLECT_PROPERTIES and FTB Chunks' ClaimedChunkEvent are architectury events -
        // plain static registrations, no need to wait for a lifecycle event.
        TeamBalance.init();
        RegionSync.init();
        NationSync.init();
        ClaimChunkEconomyListener.init();
        RegionProtectionListener.init();
        UpkeepScheduler.init();
        BountyListener.init();

        modEventBus.addListener(NetworkHandlers::register);

        // Guarded so nothing under dev.voidpulsaryt.lcce.client (KeyMapping, Xaero's classes, etc.)
        // ever gets referenced - and therefore never gets classloaded - on a dedicated server.
        if (FMLEnvironment.dist.isClient()) {
            dev.voidpulsaryt.lcce.client.ClientSetup.init(modEventBus);
        }

        NeoForge.EVENT_BUS.addListener(this::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        LCCECommand.register(event.getDispatcher());
    }

    private void onServerStarted(ServerStartedEvent event) {
        Dashboard.start(event.getServer());

        // Guarded the same way as the client-side Xaero's integration: nothing under
        // dev.voidpulsaryt.lcce.integration.bluemap (which references de.bluecolored.bluemap.api.*)
        // gets referenced unless BlueMap is actually installed.
        if (ModList.get().isLoaded("bluemap")) {
            dev.voidpulsaryt.lcce.integration.bluemap.BlueMapIntegration.init(event.getServer());
        }
    }

    private void onServerStopping(ServerStoppingEvent event) {
        Dashboard.stop();
    }
}
