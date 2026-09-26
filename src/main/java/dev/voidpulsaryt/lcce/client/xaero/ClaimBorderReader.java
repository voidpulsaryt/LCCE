package dev.voidpulsaryt.lcce.client.xaero;

import dev.voidpulsaryt.lcce.client.ClaimMapCache;
import net.minecraft.client.Minecraft;
import xaero.hud.minimap.element.render.MinimapElementReader;

/**
 * Describes one claimed chunk to Xaero's element-rendering engine: where it sits in the world
 * (chunk center), and how big its box is (half a chunk each way, i.e. the whole 16x16 chunk) for
 * both rendering and right-click interaction purposes.
 */
public final class ClaimBorderReader extends MinimapElementReader<ClaimBorderElement, Object> {

    private static final int HALF_CHUNK = 8;

    @Override
    public boolean isHidden(ClaimBorderElement element, Object context) {
        return false;
    }

    @Override
    public double getRenderX(ClaimBorderElement element, Object context, float partialTicks) {
        return element.pos().x() * 16.0 + HALF_CHUNK;
    }

    @Override
    public double getRenderY(ClaimBorderElement element, Object context, float partialTicks) {
        var player = Minecraft.getInstance().player;
        return player != null ? player.getY() : 64.0;
    }

    @Override
    public double getRenderZ(ClaimBorderElement element, Object context, float partialTicks) {
        return element.pos().z() * 16.0 + HALF_CHUNK;
    }

    @Override
    public int getInteractionBoxLeft(ClaimBorderElement element, Object context, float partialTicks) {
        return -HALF_CHUNK;
    }

    @Override
    public int getInteractionBoxRight(ClaimBorderElement element, Object context, float partialTicks) {
        return HALF_CHUNK;
    }

    @Override
    public int getInteractionBoxTop(ClaimBorderElement element, Object context, float partialTicks) {
        return -HALF_CHUNK;
    }

    @Override
    public int getInteractionBoxBottom(ClaimBorderElement element, Object context, float partialTicks) {
        return HALF_CHUNK;
    }

    @Override
    public int getRenderBoxLeft(ClaimBorderElement element, Object context, float partialTicks) {
        return -HALF_CHUNK;
    }

    @Override
    public int getRenderBoxRight(ClaimBorderElement element, Object context, float partialTicks) {
        return HALF_CHUNK;
    }

    @Override
    public int getRenderBoxTop(ClaimBorderElement element, Object context, float partialTicks) {
        return -HALF_CHUNK;
    }

    @Override
    public int getRenderBoxBottom(ClaimBorderElement element, Object context, float partialTicks) {
        return HALF_CHUNK;
    }

    @Override
    public int getLeftSideLength(ClaimBorderElement element, Minecraft mc) {
        return HALF_CHUNK * 2;
    }

    @Override
    public String getMenuName(ClaimBorderElement element) {
        return ClaimMapCache.teamName(element.teamId());
    }

    @Override
    public String getFilterName(ClaimBorderElement element) {
        return getMenuName(element);
    }

    @Override
    public int getMenuTextFillLeftPadding(ClaimBorderElement element) {
        return 0;
    }

    @Override
    public int getRightClickTitleBackgroundColor(ClaimBorderElement element) {
        return 0x80000000;
    }

    @Override
    public boolean shouldScaleBoxWithOptionalScale() {
        return false;
    }
}
