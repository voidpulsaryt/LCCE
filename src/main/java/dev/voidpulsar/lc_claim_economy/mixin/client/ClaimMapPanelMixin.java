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

import java.util.Set;

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
    private static final ThreadLocal<Boolean> lcClaimEconomy$skipProblemCount = ThreadLocal.withInitial(() -> false);

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
            lcClaimEconomy$skipProblemCount.set(true);
            return ClientPricingCache.claimProblemLine(key);
        }
        lcClaimEconomy$skipProblemCount.set(false);
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
        if (Boolean.TRUE.equals(lcClaimEconomy$skipProblemCount.get())) {
            lcClaimEconomy$skipProblemCount.set(false);
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
        java.util.ArrayList<String> keys = new java.util.ArrayList<>();
        for (XZ pos : Set.copyOf(selectedChunks)) {
            MapChunk mapChunk = lcClaimEconomy$mapChunk(pos);
            if (mapChunk != null && mapChunk.getClaimedDate().isPresent()) {
                keys.add(ChunkCoordKey.encode(dimension.location(), pos.x(), pos.z()));
            }
        }

        if (keys.size() == 1) {
            PacketDistributor.sendToServer(new ToggleChunkTypePayload(keys.getFirst()));
        } else if (!keys.isEmpty()) {
            PacketDistributor.sendToServer(new ToggleChunkTypeBatchPayload(keys));
        }

        selectedChunks.clear();
        firstSelectedChunk = null;
        lastButtonDragged = null;
    }

    @Unique
    private MapChunk lcClaimEconomy$mapChunk(XZ pos) {
        return chunkScreen.getDimension()
                .getRegion(XZ.regionFromChunk(pos.x(), pos.z()))
                .getDataBlocking()
                .getChunk(pos);
    }
}
