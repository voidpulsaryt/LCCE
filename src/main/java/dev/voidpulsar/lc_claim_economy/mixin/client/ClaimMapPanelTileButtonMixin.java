package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.icon.ImageIcon;
import dev.ftb.mods.ftblibrary.ui.BaseScreen;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.voidpulsar.lc_claim_economy.client.ClientLandChunks;
import dev.voidpulsar.lc_claim_economy.client.ClientMarket;
import dev.voidpulsar.lc_claim_economy.client.ClientQueuedChanges;
import dev.voidpulsar.lc_claim_economy.client.gui.ChunkUserPermissionsScreen;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.client.ClaimMapPanelAltToggleAccess;
import dev.voidpulsar.lc_claim_economy.network.MarketListingDto;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import dev.voidpulsar.lc_claim_economy.util.CurrencyAmounts;
import dev.voidpulsar.lc_claim_economy.util.CurrencyTextFormat;
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

import java.util.List;

/**
 * Everything this mod adds to a single claim-map tile: alt-click starts/extends a
 * multi-select for the land/build bulk-toggle in {@link ClaimMapPanelMixin},
 * shift-middle-click opens {@link ChunkUserPermissionsScreen} directly from the map,
 * the tooltip gains a land/build indicator plus a line for whichever queued change (if
 * any) is pending on this chunk, a checkered overlay in that same change's color
 * repeats the queued-state signal visually so it reads at a glance across a whole
 * claim without hovering every tile, and - independently of all of the above - a chunk
 * currently listed on the market gets its own overlay color plus a price/seller tooltip
 * line, sourced from {@link ClientMarket} (kept fresh by {@code ClaimMapScreenMixin}
 * requesting it whenever this screen opens).
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
    // Deliberately a different hue family from every PENDING_* color above so a for-sale
    // chunk never reads as a queued change at a glance.
    private static final int FOR_SALE_COLOR = 0xFFD600;
    private static final int FOR_SALE_OVERLAY_ALPHA = 170;

    @FunctionalInterface
    private interface PendingCheck {
        boolean test(ResourceKey<Level> dimension, int chunkX, int chunkZ);
    }

    private record PendingKind(PendingCheck check, String tooltipKey, int overlayColor) {
    }

    /** Cascading priority order for both the tooltip line and the tile overlay - first match wins in each. */
    private static final List<PendingKind> PENDING_KINDS = List.of(
            new PendingKind(ClientQueuedChanges::isPendingForceLoad, "gui.lc_claim_economy.chunk_pending_forceload", PENDING_FORCELOAD_COLOR),
            new PendingKind(ClientQueuedChanges::isPendingForceUnload, "gui.lc_claim_economy.chunk_pending_forceunload", PENDING_FORCEUNLOAD_COLOR),
            new PendingKind(ClientQueuedChanges::isPendingLandChunk, "gui.lc_claim_economy.chunk_pending_land", PENDING_LAND_COLOR),
            new PendingKind(ClientQueuedChanges::isPendingBuildChunk, "gui.lc_claim_economy.chunk_pending_build", PENDING_BUILD_COLOR)
    );

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

        MarketListingDto listing = ClientMarket.listingFor(ChunkCoordKey.encode(dimension.location(), chunkX, chunkZ));
        if (listing != null) {
            list.blankLine();
            list.add(Component.translatable(
                    "gui.lc_claim_economy.market.map_tooltip_price",
                    CurrencyTextFormat.formatValue(CurrencyAmounts.fromCopper(listing.priceCopper()))
            ).withStyle(ChatFormatting.GOLD));
            list.add(Component.translatable(
                    "gui.lc_claim_economy.market.map_tooltip_seller",
                    listing.sellerName()
            ).withStyle(ChatFormatting.DARK_GRAY));
        }

        for (PendingKind kind : PENDING_KINDS) {
            if (kind.check().test(dimension, chunkX, chunkZ)) {
                list.blankLine();
                list.add(Component.translatable(kind.tooltipKey(), SafeguardPriceDisplay.upkeepPeriodLabel())
                        .withStyle(ChatFormatting.GOLD));
                return;
            }
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

        for (PendingKind kind : PENDING_KINDS) {
            if (kind.check().test(dimension, chunkX, chunkZ)) {
                drawPattern(graphics, x, y, w, h, Color4I.rgb(kind.overlayColor()).withAlpha(PENDING_OVERLAY_ALPHA));
                return;
            }
        }

        // Checked last and separately from the pending-change patterns above: a chunk can be
        // both queued for a type change AND listed for sale at once, and the queued-change
        // signal is the more actionable one for the claim's own owner, so it takes priority
        // when both would otherwise want the tile.
        if (ClientMarket.listingFor(ChunkCoordKey.encode(dimension.location(), chunkX, chunkZ)) != null) {
            drawPattern(graphics, x, y, w, h, Color4I.rgb(FOR_SALE_COLOR).withAlpha(FOR_SALE_OVERLAY_ALPHA));
        }
    }

    private static void drawPattern(GuiGraphics graphics, int x, int y, int w, int h, Color4I color) {
        CHECKERED.withColor(color).draw(graphics, x, y, w, h);
    }
}
