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
 * Italic-gray placeholder row ("no entries", "loading...", etc.), shared by
 * ChunkUserPermissionsScreen and WarpListScreen - their prior separate
 * {@code MessageRow} classes were byte-identical. Not used by ConflictScreen,
 * whose own {@code EmptyMessage} positions text differently and suppresses
 * the default hover background, which are real (if small) differences.
 */
public final class EmptyMessageRow extends Button {
    public EmptyMessageRow(Panel panel, Component title) {
        super(panel, title, Color4I.empty());
    }

    @Override
    public void onClicked(MouseButton button) {
    }

    @Override
    public void draw(GuiGraphics graphics, Theme theme, int x, int y, int w, int h) {
        theme.drawString(graphics, getTitle(), x + 6, y + 6, NordColors.SNOW_STORM_2, 0);
    }
}
