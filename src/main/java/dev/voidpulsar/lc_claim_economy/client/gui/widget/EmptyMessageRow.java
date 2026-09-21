package dev.voidpulsar.lc_claim_economy.client.gui.widget;

import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Button;
import dev.ftb.mods.ftblibrary.ui.Panel;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.ftb.mods.ftblibrary.ui.input.MouseButton;
import dev.ftb.mods.ftblibrary.ui.misc.NordColors;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Italic-gray placeholder row ("no entries", "loading...", etc.) reused by
 * ChunkUserPermissionsScreen and WarpListScreen so their empty-state rows
 * render identically. ConflictScreen deliberately doesn't use this - its rows
 * are narrower and its own placeholder (PlaceholderRow, nested in that
 * screen) drops the hover fill and shifts the text position to match, which
 * would look wrong applied here.
 */
public final class EmptyMessageRow extends Button {
    public EmptyMessageRow(Panel panel, Component title) {
        super(panel, title, Color4I.empty());
    }

    @Override
    public void onClicked(MouseButton button) {
        // Purely informational row; clicks are swallowed rather than passed through.
    }

    @Override
    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        theme.drawString(graphics, getTitle(), x + 6, y + 6, NordColors.SNOW_STORM_2, 0);
    }
}
