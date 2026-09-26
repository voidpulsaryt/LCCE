package dev.voidpulsaryt.lcce.client.xaero;

import dev.ftb.mods.ftblibrary.math.XZ;
import dev.voidpulsaryt.lcce.network.CreateRegionFromSelectionPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Set;

/**
 * A minimal "name this region" prompt, opened by the World Map's "New Region from Selection..."
 * right-click option. Plain vanilla {@code Screen} - Xaero's own {@code RightClickOption} can
 * only run a fixed action, not open its own text input, so this is a completely ordinary
 * Minecraft screen laid on top rather than anything Xaero- or Mixin-related.
 */
public final class CreateRegionScreen extends Screen {

    private final Screen previousScreen;
    private final Set<XZ> chunks;
    private EditBox nameBox;

    public CreateRegionScreen(Screen previousScreen, Set<XZ> chunks) {
        super(Component.translatable("lcce.map.create_region_title"));
        this.previousScreen = previousScreen;
        this.chunks = chunks;
    }

    @Override
    protected void init() {
        this.nameBox = new EditBox(this.font, this.width / 2 - 100, this.height / 2 - 24, 200, 20,
                Component.translatable("lcce.map.region_name"));
        this.addRenderableWidget(this.nameBox);
        this.setInitialFocus(this.nameBox);

        this.addRenderableWidget(Button.builder(Component.translatable("lcce.map.create"), b -> this.confirm())
                .bounds(this.width / 2 - 100, this.height / 2, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(this.width / 2 + 5, this.height / 2, 95, 20).build());
    }

    private void confirm() {
        String name = this.nameBox.getValue().trim();
        if (!name.isEmpty()) {
            PacketDistributor.sendToServer(new CreateRegionFromSelectionPayload(name, this.chunks));
        }
        this.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter / numpad Enter
            this.confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
