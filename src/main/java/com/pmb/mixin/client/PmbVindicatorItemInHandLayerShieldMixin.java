package com.pmb.mixin.client;

import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.renderer.entity.VindicatorRenderer$1")
public class PmbVindicatorItemInHandLayerShieldMixin {
	@Redirect(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/IllagerRenderState;FF)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;isAggressive:Z"))
	private boolean pmb$renderHeldItemsWhileShielding(IllagerRenderState state) {
		return state.isAggressive || pmb$isUsingBlockingItem(state);
	}

	private static boolean pmb$isUsingBlockingItem(IllagerRenderState state) {
		if (!state.isUsingItem && state.ticksUsingItem <= 0.0F) {
			return false;
		}

		return pmb$isBlockingItem(state.leftHandItemStack) || pmb$isBlockingItem(state.rightHandItemStack);
	}

	private static boolean pmb$isBlockingItem(ItemStack stack) {
		return !stack.isEmpty() && stack.get(DataComponents.BLOCKS_ATTACKS) != null;
	}
}
