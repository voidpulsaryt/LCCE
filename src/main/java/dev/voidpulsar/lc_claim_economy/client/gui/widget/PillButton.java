package dev.voidpulsar.lc_claim_economy.client.gui.widget;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Small action pill button (colored fill, centered label, bottom divider) used for
 * per-row actions like teleport/toggle-public/delete/remove. Was independently
 * hand-rolled in WarpListScreen's TpButton/TogglePublicButton/DeleteButton and
 * ChunkUserPermissionsScreen's RemovePlayerButton - all four share this exact
 * structure, using one of two consistent hover-alpha pairs (220/170 for regular
 * actions, 210/150 for delete-style ones). ChunkUserPermissionsScreen's
 * ToggleFlagButton is NOT one of these - it picks fill color from toggle state
 * plus a disabled-alpha override, with no hover-alpha at all, which is a
 * genuinely different pattern.
 */
public abstract class PillButton extends Button {
    private final int hoverAlpha;
    private final int normalAlpha;

    protected PillButton(Panel panel, Component label, int hoverAlpha, int normalAlpha) {
        super(panel, label, Color4I.empty());
        this.hoverAlpha = hoverAlpha;
        this.normalAlpha = normalAlpha;
    }

    /** The pill's fill color before hover/normal alpha is applied. */
    protected abstract Color4I fillColor();

    @Override
    public void drawBackground(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        fillColor().withAlpha(isMouseOver() ? hoverAlpha : normalAlpha).draw(graphics, x, y, w, h);
        NordColors.POLAR_NIGHT_3.draw(graphics, x, y + h - 1, w, 1);
    }

    @Override
    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        super.draw(graphics, theme, x, y, w, h);
        theme.drawString(graphics, getTitle(), x + w / 2, y + 5, NordColors.SNOW_STORM_0, Theme.CENTERED);
    }
}
