package dev.voidpulsaryt.lcce.client.xaero;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRendererProvider;
import xaero.hud.minimap.BuiltInHudModules;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderLocation;
import xaero.hud.minimap.element.render.MinimapElementRenderer;

/**
 * Draws a colored chunk-border outline over the minimap for each nearby claimed chunk.
 * <p>
 * The engine (verified by decompiling the real minimap jar with Vineflower, specifically
 * {@code MinimapElementOverMapRendererHandler#transformAndRenderForRenderer}/{@code translatePosition})
 * already computes the element's on-screen position itself - rotation and all - before calling
 * this method, and hands it over as an already-applied {@code PoseStack} translate plus a leftover
 * sub-pixel remainder in the {@code partialX}/{@code partialY} parameters (named {@code x}/{@code y}
 * below to match the SDK's own unclear parameter names, since those are the 6th/7th positional
 * arguments in the real {@code renderElement} signature - not raw x/z world coordinates). This
 * class used to redundantly (and incorrectly) recompute screen position itself from scratch using
 * {@code MinimapProcessor#getMinimapZoom()} and the minimap's transform - that math ignored map
 * rotation entirely and, worse, used the wrong reference point, which is why claim borders rendered
 * nowhere near the actual claimed chunks. Drawing at the local origin after applying the given
 * partial-pixel translate is what every one of Xaero's own built-in elements (waypoints, tracked
 * players) does, and is correct by construction.
 * <p>
 * One remaining simplification: the border box below is drawn axis-aligned rather than rotated to
 * match the minimap's own rotation (only its on-screen *position* accounts for rotation here, not
 * its shape) - correct for the common "north-up" minimap setting, but will look slightly off on a
 * minimap set to rotate with the player. Fixing that would mean drawing four manually-rotated
 * corners instead of a plain {@code GuiGraphics#fill}.
 */
public final class ClaimBorderRenderer extends MinimapElementRenderer<ClaimBorderElement, Object> {

    private static final int BORDER_THICKNESS = 1;
    private static final double HALF_CHUNK_BLOCKS = 8.0;

    public ClaimBorderRenderer() {
        super(new ClaimBorderReader(), new ClaimBorderProvider(), new Object());
    }

    @Override
    public boolean shouldRender(MinimapElementRenderLocation location) {
        return location == MinimapElementRenderLocation.OVER_MINIMAP;
    }

    @Override
    public void preRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffer, MultiTextureRenderTypeRendererProvider renderers) {
        // No extra GL/render state needed - GuiGraphics#fill manages its own.
    }

    @Override
    public void postRender(MinimapElementRenderInfo info, MultiBufferSource.BufferSource buffer, MultiTextureRenderTypeRendererProvider renderers) {
        // Nothing to tear down.
    }

    @Override
    public boolean renderElement(ClaimBorderElement element, boolean hovered, boolean outOfBounds, double optionalDepth, float optionalScale,
                                  double partialX, double partialY, MinimapElementRenderInfo info, GuiGraphics graphics, MultiBufferSource.BufferSource buffer) {
        if (outOfBounds) {
            return false;
        }
        var session = BuiltInHudModules.MINIMAP.getCurrentSession();
        if (session == null) {
            return false;
        }
        double zoom = session.getProcessor().getMinimapZoom();
        int half = (int) Math.round(HALF_CHUNK_BLOCKS * zoom);
        if (half <= 0) {
            return false;
        }

        PoseStack matrixStack = graphics.pose();
        matrixStack.pushPose();
        matrixStack.translate(partialX, partialY, 0.0);

        int color = element.color();
        graphics.fill(-half, -half, half, -half + BORDER_THICKNESS, color);
        graphics.fill(-half, half - BORDER_THICKNESS, half, half, color);
        graphics.fill(-half, -half, -half + BORDER_THICKNESS, half, color);
        graphics.fill(half - BORDER_THICKNESS, -half, half, half, color);

        matrixStack.popPose();
        return true;
    }
}
