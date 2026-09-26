package dev.voidpulsaryt.lcce.client.xaero;

import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.Team;
import dev.voidpulsaryt.lcce.client.ClientNationCache;
import dev.voidpulsaryt.lcce.client.ClientRegionCache;
import dev.voidpulsaryt.lcce.client.ClientTeamBalanceCache;
import dev.voidpulsaryt.lcce.config.LCCEConfig;
import dev.voidpulsaryt.lcce.integration.currency.CurrencyBridge;
import dev.voidpulsaryt.lcce.network.RegionSyncPayload.RegionEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;

/**
 * The small info panel in the World Map's top-left corner, showing whatever's claimed under the
 * mouse cursor: the claiming team's name, that team's balance and nation (both only if the local
 * player is actually a member - {@link ClientTeamBalanceCache}/{@link ClientNationCache} only ever
 * hold entries the server chose to share with this client, which is only ever its own team), and
 * this specific chunk's force-load status and price. Drawn from
 * {@code dev.voidpulsaryt.lcce.client.xaero.mixin.GuiMapMixin}'s
 * injection at the very end of {@code GuiMap#render()} - after that method's own
 * {@code matrixStack.popPose()}, so the pose stack is back to plain logical-pixel space, the same
 * as any other on-screen text (confirmed by decompiling the real jar: unlike the mid-method
 * element-rendering section, which runs inside a physical-pixel transform, {@code render()} has no
 * early-return path, so this always runs exactly once per frame in that same restored state).
 */
public final class WorldMapInfoPanel {

    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;

    private WorldMapInfoPanel() {}

    public static void render(GuiGraphics graphics, int mouseBlockX, int mouseBlockZ) {
        var dimOpt = MapDimension.getCurrent();
        if (dimOpt.isEmpty()) {
            return;
        }
        int chunkX = mouseBlockX >> 4;
        int chunkZ = mouseBlockZ >> 4;
        MapChunk chunk = dimOpt.get().getRegion(XZ.regionFromChunk(chunkX, chunkZ)).getChunkForAbsoluteChunkPos(XZ.of(chunkX, chunkZ));
        var teamOpt = chunk.getTeam();
        if (teamOpt.isEmpty()) {
            return;
        }
        Team team = teamOpt.get();

        Font font = Minecraft.getInstance().font;
        java.util.List<Component> lines = new java.util.ArrayList<>();
        lines.add(team.getColoredName());

        Long balance = ClientTeamBalanceCache.get(team.getId());
        if (balance != null) {
            lines.add(Component.translatable("lcce.map.info_balance", CurrencyBridge.formatValue(balance)));
        }

        String nationName = ClientNationCache.get(team.getId());
        if (nationName != null && !nationName.isEmpty()) {
            lines.add(Component.translatable("lcce.map.info_nation", nationName));
        }

        long forceLoadPrice = LCCEConfig.UPKEEP_FORCE_LOAD_PRICE.get();
        if (forceLoadPrice > 0) {
            boolean forceLoaded = chunk.getForceLoadedDate().isPresent();
            lines.add(Component.translatable(
                    forceLoaded ? "lcce.map.info_forceloaded" : "lcce.map.info_not_forceloaded", CurrencyBridge.formatValue(forceLoadPrice)
            ));
        }

        RegionEntry region = ClientRegionCache.regionAt(new ChunkDimPos(dimOpt.get().dimension, new ChunkPos(chunkX, chunkZ)));
        if (region != null) {
            lines.add(Component.translatable("lcce.map.info_region", region.name()));
            lines.add(Component.translatable("lcce.map.info_mobgriefing", region.mobGriefing()));
            lines.add(Component.translatable("lcce.map.info_explosions", region.explosions()));
            lines.add(Component.translatable("lcce.map.info_pvp", region.pvp()));
            lines.add(Component.translatable("lcce.map.info_interact", region.interactTier()));
            lines.add(Component.translatable("lcce.map.info_edit", region.editTier()));
        }

        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int height = lines.size() * LINE_HEIGHT;

        int left = PADDING;
        int top = PADDING;
        graphics.fill(left, top, left + width + PADDING * 2, top + height + PADDING * 2, 0x90000000);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), left + PADDING, top + PADDING + i * LINE_HEIGHT, 0xFFFFFF);
        }
    }
}
