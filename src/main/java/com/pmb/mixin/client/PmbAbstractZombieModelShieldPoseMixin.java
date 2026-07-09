package com.pmb.mixin.client;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractZombieModel.class)
public class PmbAbstractZombieModelShieldPoseMixin {
	@Redirect(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/AnimationUtils;animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZLnet/minecraft/client/renderer/entity/state/UndeadRenderState;)V"))
	private void pmb$keepBlockingArmPose(ModelPart leftArm, ModelPart rightArm, boolean isAggressive,
			UndeadRenderState state) {
		if (!pmb$isUsingBlockingItem(state)) {
			AnimationUtils.animateZombieArms(leftArm, rightArm, isAggressive, state);
			return;
		}

		ModelPart blockingArm = pmb$usedItemArm(state) == HumanoidArm.RIGHT ? rightArm : leftArm;
		float xRot = blockingArm.xRot;
		float yRot = blockingArm.yRot;
		float zRot = blockingArm.zRot;
		float x = blockingArm.x;
		float y = blockingArm.y;
		float z = blockingArm.z;

		AnimationUtils.animateZombieArms(leftArm, rightArm, isAggressive, state);

		blockingArm.xRot = xRot;
		blockingArm.yRot = yRot;
		blockingArm.zRot = zRot;
		blockingArm.x = x;
		blockingArm.y = y;
		blockingArm.z = z;
	}

	private static boolean pmb$isUsingBlockingItem(UndeadRenderState state) {
		if (!state.isUsingItem) {
			return false;
		}

		ItemStack stack = pmb$getItemStackForArm(state, pmb$usedItemArm(state));
		return !stack.isEmpty() && stack.get(DataComponents.BLOCKS_ATTACKS) != null;
	}

	private static HumanoidArm pmb$usedItemArm(UndeadRenderState state) {
		if (state.useItemHand == InteractionHand.MAIN_HAND) {
			return state.mainArm;
		}

		return state.mainArm.getOpposite();
	}

	@Unique
	private static ItemStack pmb$getItemStackForArm(UndeadRenderState state, HumanoidArm arm) {
		return arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
	}
}
