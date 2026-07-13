package com.pmb.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(IllagerRenderer.class)
public class PmbIllagerRendererShieldPoseMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/illager/AbstractIllager;Lnet/minecraft/client/renderer/entity/state/IllagerRenderState;F)V", at = @At("TAIL"))
	private void pmb$extractBlockingArmPose(AbstractIllager illager, IllagerRenderState state, float partialTick,
			CallbackInfo info) {
		if (!illager.isUsingItem()) {
			return;
		}
		if (illager.getUsedItemHand() == InteractionHand.MAIN_HAND && illager.getMainHandItem().is(Items.BOW)) {
			state.armPose = AbstractIllager.IllagerArmPose.BOW_AND_ARROW;
			return;
		}

		HumanoidArm usedArm = illager.getUsedItemHand() == InteractionHand.MAIN_HAND
				? illager.getMainArm()
				: illager.getMainArm().getOpposite();
		ItemStack stack = usedArm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
		if (stack.isEmpty() || stack.get(DataComponents.BLOCKS_ATTACKS) == null) {
			return;
		}

		state.armPose = AbstractIllager.IllagerArmPose.NEUTRAL;
		if (usedArm == HumanoidArm.RIGHT) {
			state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
		} else {
			state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
		}
	}
}
