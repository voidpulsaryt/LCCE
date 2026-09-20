package dev.voidpulsar.lc_claim_economy.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.ftb.mods.ftblibrary.config.ConfigValue;
import dev.ftb.mods.ftblibrary.icon.Color4I;
import dev.ftb.mods.ftblibrary.ui.Theme;
import dev.voidpulsar.lc_claim_economy.client.ClientQueuedChanges;
import dev.voidpulsar.lc_claim_economy.client.EditConfigScreenUiHelper;
import dev.voidpulsar.lc_claim_economy.service.ClaimVisibilityRules;
import dev.voidpulsar.lc_claim_economy.service.SafeguardPriceDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "dev.ftb.mods.ftblibrary.config.ui.EditConfigScreen$ConfigEntryButton", remap = false)
public class TeamSettingsEntryButtonMixin {
    @Shadow(remap = false)
    private ConfigValue<?> configValue;

    /**
     * Injected at the constructor's return rather than before a specific
     * {@code getCanEdit()} call: FTB Library 2101.1.34 moved that call out of
     * {@code <init>} (into a lazy key-text supplier and into {@code draw}/
     * {@code onClicked}), which broke the old before-the-call injection point.
     * {@code configValue} is always assigned by the time the constructor
     * returns, and every consumer of {@code getCanEdit()} reads it live
     * rather than caching it at construction, so locking it here works
     * across both the old and new FTB Library layouts.
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$lockClaimVisibility(CallbackInfo ci) {
        if (ClaimVisibilityRules.isClaimVisibilityConfigId(EditConfigScreenUiHelper.propertyKey(configValue))) {
            configValue.setCanEdit(false);
        }
    }

    /**
     * FTB truncates the value column after {@code getStringForGUI}. Land entries
     * with {@code /5 chunks} plus a pending tag are often wider than build
     * entries, so the pending suffix was clipped. Replace the value-column
     * {@code drawString} text (the Color4I overload) with the full formatted line.
     */
    @WrapOperation(
            method = "draw",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/ui/Theme;drawString(Lnet/minecraft/client/gui/GuiGraphics;Ljava/lang/Object;IILdev/ftb/mods/ftblibrary/icon/Color4I;I)I"
            ),
            remap = false
    )
    private int lcClaimEconomy$drawFullFormattedValue(
            Theme theme,
            GuiGraphics graphics,
            Object text,
            int x,
            int y,
            Color4I color,
            int flags,
            Operation<Integer> original
    ) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        ConfigValue raw = configValue;
        Component formatted = EditConfigScreenUiHelper.buildFormattedLine(configValue, configValue.getValue(), (config, value) ->
                raw.getStringForGUI(value));
        return original.call(theme, graphics, formatted, x, y, color, flags);
    }

    @WrapOperation(
            method = "getValueStr",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/ftb/mods/ftblibrary/config/ConfigValue;getStringForGUI(Ljava/lang/Object;)Lnet/minecraft/network/chat/Component;"
            ),
            remap = false
    )
    private Component lcClaimEconomy$renderValueStr(
            ConfigValue<?> config,
            Object value,
            Operation<Component> original
    ) {
        return EditConfigScreenUiHelper.buildFormattedLine(config, value, original::call);
    }

    @Inject(method = "addMouseOverText", at = @At("RETURN"), remap = false)
    private void lcClaimEconomy$appendEntryTooltip(
            dev.ftb.mods.ftblibrary.util.TooltipList list,
            CallbackInfo ci
    ) {
        String propertyKey = EditConfigScreenUiHelper.propertyKey(configValue);
        Long basePrice = SafeguardPriceDisplay.pricePerChunkForConfigId(propertyKey);
        if (basePrice != null) {
            list.blankLine();
            long displayPrice = SafeguardPriceDisplay.effectiveProtectionPrice(basePrice);
            if (SafeguardPriceDisplay.isLandProtectionPropertyKey(propertyKey)) {
                list.add(Component.translatable(
                        "gui.lc_claim_economy.protection_price_per_n_chunks",
                        SafeguardPriceDisplay.formatPricePerChunk(displayPrice),
                        SafeguardPriceDisplay.upkeepPeriodLabel(),
                        SafeguardPriceDisplay.landChunkGroupSize()
                ).withStyle(ChatFormatting.GRAY));
            } else {
                list.add(Component.translatable(
                        "gui.lc_claim_economy.protection_price_per_chunk",
                        SafeguardPriceDisplay.formatPricePerChunk(displayPrice),
                        SafeguardPriceDisplay.upkeepPeriodLabel()
                ).withStyle(ChatFormatting.GRAY));
            }
            if (SafeguardPriceDisplay.showsIncomingWarSurcharge()) {
                list.add(Component.translatable(
                        "gui.lc_claim_economy.protection_price_war_incoming",
                        SafeguardPriceDisplay.incomingWarCount(),
                        SafeguardPriceDisplay.incomingWarFactorLabel(),
                        SafeguardPriceDisplay.formatPricePerChunk(basePrice),
                        SafeguardPriceDisplay.formatPricePerChunk(displayPrice)
                ).withStyle(ChatFormatting.GOLD));
            }
        }

        if (ClientQueuedChanges.hasPendingProperty(propertyKey)) {
            list.blankLine();
            list.add(Component.translatable("message.lc_claim_economy.protection_change_pending")
                    .withStyle(ChatFormatting.GOLD));
            Object pendingValue = ClientQueuedChanges.getDisplayValue(propertyKey, configValue.getValue());
            @SuppressWarnings({"unchecked", "rawtypes"})
            Component pendingText = ((ConfigValue) configValue).getStringForGUI(pendingValue);
            list.add(Component.translatable("gui.lc_claim_economy.pending_value", pendingText)
                    .withStyle(ChatFormatting.GOLD));
        }
    }
}
