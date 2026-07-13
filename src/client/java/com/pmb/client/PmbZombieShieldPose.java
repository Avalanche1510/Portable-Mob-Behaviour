package com.pmb.client;

import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

public final class PmbZombieShieldPose {
	private PmbZombieShieldPose() {
	}

	public static void keepBlockingArmPose(ModelPart leftArm, ModelPart rightArm, boolean isAggressive,
			UndeadRenderState state) {
		if (!isUsingBlockingItem(state)) {
			AnimationUtils.animateZombieArms(leftArm, rightArm, isAggressive, state);
			return;
		}

		ModelPart blockingArm = usedItemArm(state) == HumanoidArm.RIGHT ? rightArm : leftArm;
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

	private static boolean isUsingBlockingItem(UndeadRenderState state) {
		if (!state.isUsingItem) {
			return false;
		}

		ItemStack stack = getItemStackForArm(state, usedItemArm(state));
		return !stack.isEmpty() && stack.get(DataComponents.BLOCKS_ATTACKS) != null;
	}

	private static HumanoidArm usedItemArm(UndeadRenderState state) {
		if (state.useItemHand == InteractionHand.MAIN_HAND) {
			return state.mainArm;
		}

		return state.mainArm.getOpposite();
	}

	private static ItemStack getItemStackForArm(UndeadRenderState state, HumanoidArm arm) {
		return arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
	}
}
