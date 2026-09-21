package dev.voidpulsar.lc_claim_economy.mixin.client;

import dev.ftb.mods.ftbchunks.client.gui.ChunkScreen;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.voidpulsar.lc_claim_economy.client.ClientPricingCache;
import dev.voidpulsar.lc_claim_economy.data.ChunkCoordKey;
import dev.voidpulsar.lc_claim_economy.client.ClaimMapPanelAltToggleAccess;
import dev.voidpulsar.lc_claim_economy.network.ToggleChunkTypeBatchPayload;
import dev.voidpulsar.lc_claim_economy.network.ToggleChunkTypePayload;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Set;

/**
 * Two unrelated pieces of behavior share this target because both need to reach into
 * the claim map panel's private selection/drag state:
 * <ul>
 *   <li>The three {@code @Redirect}s intercept FTB's own drag-claim result summary
 *   line as it's built (translate key → append count suffix) so a claim rejected by
 *   this mod's own economy (insufficient funds) shows this mod's message instead of
 *   FTB's generic "N chunks failed" text. The thread-local flag threads a decision
 *   made in the middle redirect (was this an LC rejection?) through to the third one,
 *   since they're three separate injected calls with no other way to share state.</li>
 *   <li>{@code mouseReleased}/the {@link ClaimMapPanelAltToggleAccess} methods add
 *   alt-click-drag as a way to toggle a whole selection between land/build at once,
 *   reusing the panel's existing left-drag chunk-selection machinery rather than
 *   adding a separate selection mode.</li>
 * </ul>
 */
@Mixin(targets = "dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel", remap = false)
public class ClaimMapPanelMixin implements ClaimMapPanelAltToggleAccess {
    @Shadow(remap = false)
    private ChunkScreen chunkScreen;

    @Shadow(remap = false)
    private XZ firstSelectedChunk;

    @Shadow(remap = false)
    private Set<XZ> selectedChunks;

    @Shadow(remap = false)
    private dev.ftb.mods.ftblibrary.ui.Button lastButtonDragged;

    @Unique
    private static final ThreadLocal<Boolean> lcClaimEconomy$suppressProblemSuffix = ThreadLocal.withInitial(() -> false);

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftbchunks/client/gui/ChunkScreenPanel$ChunkUpdateInfo;summary()Lnet/minecraft/network/chat/Component;"
            )
    )
    private Component lcClaimEconomy$hideChunkModifiedSummary(
            dev.ftb.mods.ftbchunks.client.gui.ChunkScreenPanel.ChunkUpdateInfo updateInfo
    ) {
        return Component.empty();
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent lcClaimEconomy$formatClaimProblem(String key) {
        if (ClientPricingCache.isLcClaimResult(key)) {
            lcClaimEconomy$suppressProblemSuffix.set(true);
            return ClientPricingCache.claimProblemLine(key);
        }
        lcClaimEconomy$suppressProblemSuffix.set(false);
        return Component.translatable(key);
    }

    @Redirect(
            method = "drawBackground",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/MutableComponent;append(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"
            )
    )
    private MutableComponent lcClaimEconomy$hideProblemChunkCount(MutableComponent component, String suffix) {
        if (Boolean.TRUE.equals(lcClaimEconomy$suppressProblemSuffix.get())) {
            lcClaimEconomy$suppressProblemSuffix.set(false);
            return component;
        }
        return component.append(suffix);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, remap = false)
    private void lcClaimEconomy$altToggleOnRelease(dev.ftb.mods.ftblibrary.ui.input.MouseButton button, CallbackInfo ci) {
        if (!Screen.hasAltDown() || !button.isLeft() || selectedChunks.isEmpty()) {
            return;
        }
        lcClaimEconomy$releaseAltToggleSelection();
        ci.cancel();
    }

    @Override
    public void lcClaimEconomy$selectForAltToggle(XZ chunkPos) {
        if (selectedChunks.isEmpty()) {
            firstSelectedChunk = chunkPos;
        }
        selectedChunks.add(chunkPos);
    }

    @Override
    public void lcClaimEconomy$releaseAltToggleSelection() {
        if (selectedChunks.isEmpty()) {
            return;
        }

        ResourceKey<Level> dimension = chunkScreen.getDimension().dimension;
        List<String> keys = Set.copyOf(selectedChunks).stream()
                .filter(this::lcClaimEconomy$hasClaimAt)
                .map(pos -> ChunkCoordKey.encode(dimension.location(), pos.x(), pos.z()))
                .toList();

        if (!keys.isEmpty()) {
            if (keys.size() == 1) {
                PacketDistributor.sendToServer(new ToggleChunkTypePayload(keys.getFirst()));
            } else {
                PacketDistributor.sendToServer(new ToggleChunkTypeBatchPayload(keys));
            }
        }

        selectedChunks.clear();
        firstSelectedChunk = null;
        lastButtonDragged = null;
    }

    @Unique
    private boolean lcClaimEconomy$hasClaimAt(XZ pos) {
        MapChunk mapChunk = lcClaimEconomy$chunkAt(pos);
        return mapChunk != null && mapChunk.getClaimedDate().isPresent();
    }

    @Unique
    private MapChunk lcClaimEconomy$chunkAt(XZ pos) {
        return chunkScreen.getDimension()
                .getRegion(XZ.regionFromChunk(pos.x(), pos.z()))
                .getDataBlocking()
                .getChunk(pos);
    }
}
