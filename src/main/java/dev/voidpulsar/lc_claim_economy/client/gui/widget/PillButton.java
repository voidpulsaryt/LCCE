package dev.voidpulsar.lc_claim_economy.client.gui.widget;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Compact row-action button: flat color fill, centered label, single-pixel
 * bottom divider. This is the common shape behind WarpListScreen's
 * teleport/toggle-public/delete buttons and ChunkUserPermissionsScreen's
 * remove-player button - those four were once separate copy-pasted classes
 * that only differed in fill color and alpha pair, so the shared shape lives
 * here now. Two alpha pairs cover every current caller: 220/170 for regular
 * actions, 210/150 for destructive ones.
 * <p>
 * ChunkUserPermissionsScreen's per-flag toggle buttons are a different beast
 * (fill color depends on on/off state, plus a disabled-alpha override, and
 * no hover response) and were intentionally left as their own class rather
 * than forced into this shape.
 */
public abstract class PillButton extends Button {
    private final int hoverFillAlpha;
    private final int idleFillAlpha;

    protected PillButton(Panel panel, Component label, int hoverFillAlpha, int idleFillAlpha) {
        super(panel, label, Color4I.empty());
        this.hoverFillAlpha = hoverFillAlpha;
        this.idleFillAlpha = idleFillAlpha;
    }

    /** Base fill color for the pill, before the hover/idle alpha is mixed in. */
    protected abstract Color4I fillColor();

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        int alpha = isMouseOver() ? hoverFillAlpha : idleFillAlpha;
        fillColor().withAlpha(alpha).draw(graphics, x, y, w, h);
        NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
    }

    @Override
    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.draw(graphics, theme, x, y, w, h);
        theme.drawString(graphics, getTitle(), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
    }
}
