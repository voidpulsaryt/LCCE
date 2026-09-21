package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.voidpulsar.lc_claim_economy.client.ClientLandChunks;
import dev.voidpulsar.lc_claim_economy.client.ClientQueuedChanges;
import dev.voidpulsar.lc_claim_economy.client.gui.ChunkUserPermissionsScreen;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.client.ClaimMapPanelAltToggleAccess;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Everything this mod adds to a single claim-map tile: alt-click starts/extends a
 * multi-select for the land/build bulk-toggle in {@link ClaimMapPanelMixin},
 * shift-middle-click opens {@link ChunkUserPermissionsScreen} directly from the map,
 * the tooltip gains a land/build indicator plus a line for whichever queued change (if
 * any) is pending on this chunk, and a checkered overlay in that same change's color
 * repeats the queued-state signal visually so it reads at a glance across a whole
 * claim without hovering every tile.
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel$ChunkButton", remap = false)
public class ClaimMapPanelTileButtonMixin {
    private static final ImageIcon CHECKERED = new ImageIcon(
            ResourceLocation.fromNamespaceAndPath("ftbchunks", "textures/checkered.png")
    );

    // Pending-change overlay colors. Not NordColors constants - none of that palette's entries
    // are an exact hex match for these, and swapping in an "approximately right" Nord color
    // would be a real (if subtle) visual change rather than a pure rename.
    private static final int PENDING_FORCELOAD_COLOR = 0xFFB74D;
    private static final int PENDING_FORCEUNLOAD_COLOR = 0xEF5350;
    private static final int PENDING_LAND_COLOR = 0x81C784;
    private static final int PENDING_BUILD_COLOR = 0x64B5F6;
    private static final int PENDING_OVERLAY_ALPHA = 170;

    @Shadow(remap = false)
    private dev.ftb.mods.ftblibrary.math.XZ chunkPos;

    @Shadow(remap = false)
    private dev.ftb.mods.ftbchunks.client.map.MapChunk chunk;

    @Final
    @Shadow(remap = false)
    private dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel this$0;

    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$altClickToggleChunkType(MouseButton mouseButton, CallbackInfo ci) {
        if (!Screen.hasAltDown() || !mouseButton.isLeft() || chunk == null || chunkPos == null) {
            return;
        }
        ci.cancel();
        if (chunk.getClaimedDate().isEmpty()) {
            return;
        }
        ((ClaimMapPanelAltToggleAccess) this$0).lcClaimEconomy$selectForAltToggle(chunkPos);
    }

    @Inject(method = "onClicked", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$openUserPermsScreen(MouseButton mouseButton, CallbackInfo ci) {
        if (!Screen.hasShiftDown() || Screen.hasAltDown() || Screen.hasControlDown() || !mouseButton.isMiddle() || chunk == null || chunkPos == null) {
            return;
        }
        if (chunk.getClaimedDate().isEmpty()) {
            return;
        }

        ResourceKey<Level> dimension = ((ClaimMapPanelAccessor) this$0).lcClaimEconomy$getChunkScreen().getDimension().dimension;
        String chunkKey = ChunkCoordKey.encode(dimension.location(), chunkPos.x(), chunkPos.z());
        BaseScreen screen = (BaseScreen) ((ClaimMapPanelAccessor) this$0).lcClaimEconomy$getChunkScreen();
        new ChunkUserPermissionsScreen(screen, chunkKey).openGui();
        ci.cancel();
    }

    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$addPendingForceLoadTooltip(
            dev.ftb.mods.ftblibrary.util.TooltipList list,
            CallbackInfo ci
    ) {
        if (chunk == null || chunkPos == null) {
            return;
        }

        ResourceKey<Level> dimension = ((ClaimMapPanelAccessor) this$0).lcClaimEconomy$getChunkScreen().getDimension().dimension;
        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();

        if (chunk.getClaimedDate().isPresent()) {
            boolean land = ClientLandChunks.isLand(dimension, chunkX, chunkZ);
            list.add(Component.translatable(land
                    ? "gui.lc_claim_economy.chunk_type_land"
                    : "gui.lc_claim_economy.chunk_type_build").withStyle(ChatFormatting.AQUA));
            list.add(Component.translatable("gui.lc_claim_economy.chunk_type_hint").withStyle(ChatFormatting.DARK_GRAY));
            list.add(Component.translatable("gui.lc_claim_economy.chunk_user_perm.open_hint").withStyle(ChatFormatting.DARK_GRAY));
        }

        if (ClientQueuedChanges.isPendingForceLoad(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.chunk_pending_forceload",
                    SafeguardPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientQueuedChanges.isPendingForceUnload(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.chunk_pending_forceunload",
                    SafeguardPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientQueuedChanges.isPendingLandChunk(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.chunk_pending_land",
                    SafeguardPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
            return;
        }

        if (ClientQueuedChanges.isPendingBuildChunk(dimension, chunkX, chunkZ)) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.chunk_pending_build",
                    SafeguardPriceDisplay.upkeepPeriodLabel()
            ).withStyle(ChatFormatting.GOLD));
        }
    }

    @Inject(method = "drawBackground", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$drawPendingChunkPattern(
            GuiGraphics graphics,
            Theme theme,
            int x,
            int y,
            int w,
            int h,
            CallbackInfo ci
    ) {
        ResourceKey<Level> dimension = ((ClaimMapPanelAccessor) this$0).lcClaimEconomy$getChunkScreen().getDimension().dimension;
        int chunkX = chunkPos.x();
        int chunkZ = chunkPos.z();

        if (ClientQueuedChanges.isPendingForceLoad(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(PENDING_FORCELOAD_COLOR).withAlpha(PENDING_OVERLAY_ALPHA));
            return;
        }

        if (ClientQueuedChanges.isPendingForceUnload(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(PENDING_FORCEUNLOAD_COLOR).withAlpha(PENDING_OVERLAY_ALPHA));
            return;
        }

        if (ClientQueuedChanges.isPendingLandChunk(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(PENDING_LAND_COLOR).withAlpha(PENDING_OVERLAY_ALPHA));
            return;
        }

        if (ClientQueuedChanges.isPendingBuildChunk(dimension, chunkX, chunkZ)) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(PENDING_BUILD_COLOR).withAlpha(PENDING_OVERLAY_ALPHA));
        }
    }

    private static void drawPattern(GuiGraphics graphics, int x, int y, int w, int h, Color4I color) {
        CHECKERED.withColor(color).draw(graphics, x, y, w, h);
    }
}
