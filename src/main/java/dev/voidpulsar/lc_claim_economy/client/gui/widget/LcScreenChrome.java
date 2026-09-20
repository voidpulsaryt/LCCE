package dev.voidpulsar.lc_claim_economy.client.gui.widget;

import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Shared chrome for this mod's FTB-Library-based popup screens (ClaimBreakdownScreen,
 * ChunkUserPermissionsScreen, WarpListScreen, ConflictScreen): the header height/button
 * size/padding/scrollbar-width values and the two-tone Polar Night content-panel
 * background were independently redeclared identically in every one of them.
 */
public final class LcScreenChrome {
    public static final int HEADER_HEIGHT = 22;
    public static final int HEADER_BUTTON_SIZE = 16;
    public static final int CONTENT_PAD = 8;
    public static final int SCROLLBAR_WIDTH = 8;

    private LcScreenChrome() {
    }

    /** The content-panel background fill + top divider line drawn below the header, shared by every screen's {@code drawBackground}. */
    public static void drawContentBackground(GuiGraphics graphics, int x, int y, int w, int h) {
        NordColors.POLAR_NIGHT_0.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, h - HEADER_HEIGHT - 6);
        NordColors.POLAR_NIGHT_2.draw(graphics, x + 4, y + HEADER_HEIGHT + 2, w - 8, 1);
    }

    /** {@code Math.min(available - 20, max)} sizing shared by ClaimBreakdownScreen/ChunkUserPermissionsScreen/WarpListScreen (ConflictScreen sizes proportionally to its parent instead, so doesn't use this). */
    public static int clamped(int available, int max) {
        return Math.min(available - 20, max);
    }
}
