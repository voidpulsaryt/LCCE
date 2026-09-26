package dev.voidpulsaryt.lcce.client.xaero;

import dev.ftb.mods.ftblibrary.math.XZ;
import dev.voidpulsaryt.lcce.marketplace.BuyerRule;
import dev.voidpulsaryt.lcce.network.SellCountryPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.math.BigInteger;
import java.util.Set;

/**
 * "Sell this whole selection as one package" prompt, opened by the World Map's
 * "Sell as Country..." right-click option - the Towny-style "sell the whole town" counterpart to
 * a single-chunk marketplace listing. Plain vanilla {@code Screen}, same reasoning as
 * {@link CreateRegionScreen}: Xaero's {@code RightClickOption} can't open its own input, so this
 * is laid on top instead.
 */
public final class SellCountryScreen extends Screen {

    private final Screen previousScreen;
    private final Set<XZ> chunks;
    private EditBox labelBox;
    private EditBox priceBox;
    private BuyerRule buyerRule = BuyerRule.TEAM;
    private Button everyoneButton;
    private Button alliesButton;
    private Button teamButton;

    public SellCountryScreen(Screen previousScreen, Set<XZ> chunks) {
        super(Component.translatable("lcce.map.sell_country_title"));
        this.previousScreen = previousScreen;
        this.chunks = chunks;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int top = this.height / 2 - 50;

        this.labelBox = new EditBox(this.font, centerX - 100, top, 200, 20, Component.translatable("lcce.map.country_label"));
        this.addRenderableWidget(this.labelBox);
        this.setInitialFocus(this.labelBox);

        this.priceBox = new EditBox(this.font, centerX - 100, top + 26, 200, 20, Component.translatable("lcce.map.country_price"));
        this.addRenderableWidget(this.priceBox);

        this.everyoneButton = Button.builder(Component.translatable("lcce.market.buyer_everyone"), b -> this.setBuyerRule(BuyerRule.EVERYONE))
                .bounds(centerX - 100, top + 52, 64, 20).build();
        this.alliesButton = Button.builder(Component.translatable("lcce.market.buyer_allies"), b -> this.setBuyerRule(BuyerRule.ALLIES))
                .bounds(centerX - 32, top + 52, 64, 20).build();
        this.teamButton = Button.builder(Component.translatable("lcce.market.buyer_team"), b -> this.setBuyerRule(BuyerRule.TEAM))
                .bounds(centerX + 36, top + 52, 64, 20).build();
        this.addRenderableWidget(this.everyoneButton);
        this.addRenderableWidget(this.alliesButton);
        this.addRenderableWidget(this.teamButton);
        this.updateBuyerRuleButtons();

        this.addRenderableWidget(Button.builder(Component.translatable("lcce.map.sell"), b -> this.confirm())
                .bounds(centerX - 100, top + 80, 95, 20).build());
        this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> this.onClose())
                .bounds(centerX + 5, top + 80, 95, 20).build());
    }

    private void setBuyerRule(BuyerRule rule) {
        this.buyerRule = rule;
        this.updateBuyerRuleButtons();
    }

    private void updateBuyerRuleButtons() {
        this.everyoneButton.active = this.buyerRule != BuyerRule.EVERYONE;
        this.alliesButton.active = this.buyerRule != BuyerRule.ALLIES;
        this.teamButton.active = this.buyerRule != BuyerRule.TEAM;
    }

    private void confirm() {
        BigInteger price;
        try {
            price = new BigInteger(this.priceBox.getValue().trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (price.signum() <= 0) {
            return;
        }
        PacketDistributor.sendToServer(new SellCountryPayload(this.labelBox.getValue().trim(), price, this.buyerRule, this.chunks));
        this.onClose();
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
