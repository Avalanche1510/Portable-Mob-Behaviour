package com.pmb.mixin.client;

import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.entity.EvokerRenderer$1")
public class PmbEvokerItemInHandLayerShieldMixin {
	@Redirect(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EvokerRenderState;FF)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/EvokerRenderState;isCastingSpell:Z"))
	private boolean pmb$renderHeldItemsWhileShielding(EvokerRenderState state) {
		return state.isCastingSpell || pmb$isUsingBlockingItem(state);
	}

	private static boolean pmb$isUsingBlockingItem(EvokerRenderState state) {
		if (!state.isUsingItem && state.ticksUsingItem <= 0.0F) {
			return false;
		}

		return pmb$isBlockingItem(state.leftHandItemStack) || pmb$isBlockingItem(state.rightHandItemStack);
	}

	private static boolean pmb$isBlockingItem(ItemStack stack) {
		return !stack.isEmpty() && stack.get(DataComponents.BLOCKS_ATTACKS) != null;
	}
}
