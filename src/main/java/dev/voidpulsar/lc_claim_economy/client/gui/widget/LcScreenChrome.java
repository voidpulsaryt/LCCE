package dev.voidpulsar.lc_claim_economy.client.gui.widget;

import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Common visual scaffolding for this mod's FTB-Library popups (ClaimBreakdownScreen,
 * ChunkUserPermissionsScreen, WarpListScreen, ConflictScreen). Pulling the header
 * metrics and the two-tone Polar Night panel background out here means every popup
 * shares one definition instead of four copies that could quietly drift apart.
 */
public final class LcScreenChrome {
    public static final int HEADER_HEIGHT = 22;
    public static final int HEADER_BUTTON_SIZE = 16;
    public static final int CONTENT_PAD = 8;
    public static final int SCROLLBAR_WIDTH = 8;

    private LcScreenChrome() {
    }

    /** Draws the dark content-panel fill plus its top divider line, directly under the header band; called from each screen's {@code drawBackground}. */
    public static void drawContentBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        NordColors.POLAR_NIGHT_0.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, h - HEADER_HEIGHT - 6);
        NordColors.POLAR_NIGHT_2.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, 1);
    }

    /**
     * Caps a popup's size to the given maximum while leaving a small margin against
     * the available screen dimension. ConflictScreen doesn't call this - it sizes
     * itself as a fraction of its parent screen instead of clamping to a fixed max.
     */
    public static int clamped(int available, int max) {
        return Math.min(available - 20, max);
    }
}
