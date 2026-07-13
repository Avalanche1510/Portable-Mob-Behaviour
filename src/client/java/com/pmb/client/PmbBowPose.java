package com.pmb.client;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class PmbBowPose {
	private static final float HALF_PI = (float) (Math.PI / 2.0D);

	private PmbBowPose() {}

	public static boolean pose(HumanoidRenderState state, ModelPart head, ModelPart rightArm, ModelPart leftArm) {
		return pose(state.isUsingItem, state.ticksUsingItem, state.useItemHand, state.mainArm,
				state.rightHandItemStack, state.leftHandItemStack, head, rightArm, leftArm);
	}

	public static boolean pose(IllagerRenderState state, ModelPart head, ModelPart rightArm, ModelPart leftArm) {
		return pose(state.isUsingItem, state.ticksUsingItem, state.useItemHand, state.mainArm,
				state.rightHandItemStack, state.leftHandItemStack, head, rightArm, leftArm);
	}

	private static boolean pose(boolean usingItem, float ticksUsingItem, InteractionHand useItemHand,
			HumanoidArm mainArm, ItemStack rightHand, ItemStack leftHand, ModelPart head,
			ModelPart rightArm, ModelPart leftArm) {
		if ((!usingItem && ticksUsingItem <= 0.0F) || useItemHand != InteractionHand.MAIN_HAND) return false;
		ItemStack mainHand = mainArm == HumanoidArm.RIGHT ? rightHand : leftHand;
		if (!mainHand.is(Items.BOW)) return false;

		rightArm.visible = true;
		leftArm.visible = true;
		rightArm.zRot = 0.0F;
		leftArm.zRot = 0.0F;
		rightArm.xRot = -HALF_PI + head.xRot;
		leftArm.xRot = -HALF_PI + head.xRot;
		if (mainArm == HumanoidArm.RIGHT) {
			rightArm.yRot = -0.1F + head.yRot;
			leftArm.yRot = 0.1F + head.yRot + 0.4F;
		} else {
			rightArm.yRot = -0.1F + head.yRot - 0.4F;
			leftArm.yRot = 0.1F + head.yRot;
		}
		return true;
	}
}
