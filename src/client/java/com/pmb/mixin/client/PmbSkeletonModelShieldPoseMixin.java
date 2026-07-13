package com.pmb.mixin.client;

import com.pmb.client.PmbBowPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.skeleton.SkeletonModel;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkeletonModel.class)
public class PmbSkeletonModelShieldPoseMixin {
	@Unique
	private boolean pmb$hasBlockingArmPose;
	@Unique
	private boolean pmb$blockingArmRight;
	@Unique
	private float pmb$blockingArmXRot;
	@Unique
	private float pmb$blockingArmYRot;
	@Unique
	private float pmb$blockingArmZRot;
	@Unique
	private float pmb$blockingArmX;
	@Unique
	private float pmb$blockingArmY;
	@Unique
	private float pmb$blockingArmZ;

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/HumanoidModel;setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", shift = At.Shift.AFTER))
	private void pmb$captureBlockingArmPose(SkeletonRenderState state, CallbackInfo info) {
		pmb$hasBlockingArmPose = false;
		if (!pmb$isUsingBlockingItem(state)) {
			return;
		}

		HumanoidArm usedArm = pmb$usedItemArm(state);
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		ModelPart arm = usedArm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
		pmb$blockingArmRight = usedArm == HumanoidArm.RIGHT;
		pmb$blockingArmXRot = arm.xRot;
		pmb$blockingArmYRot = arm.yRot;
		pmb$blockingArmZRot = arm.zRot;
		pmb$blockingArmX = arm.x;
		pmb$blockingArmY = arm.y;
		pmb$blockingArmZ = arm.z;
		pmb$hasBlockingArmPose = true;
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/SkeletonRenderState;)V", at = @At("TAIL"))
	private void pmb$restoreBlockingArmPose(SkeletonRenderState state, CallbackInfo info) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		if (PmbBowPose.pose(state, model.head, model.rightArm, model.leftArm)) {
			pmb$hasBlockingArmPose = false;
			return;
		}
		if (!pmb$hasBlockingArmPose) {
			return;
		}

		ModelPart arm = pmb$blockingArmRight ? model.rightArm : model.leftArm;
		arm.xRot = pmb$blockingArmXRot;
		arm.yRot = pmb$blockingArmYRot;
		arm.zRot = pmb$blockingArmZRot;
		arm.x = pmb$blockingArmX;
		arm.y = pmb$blockingArmY;
		arm.z = pmb$blockingArmZ;
		pmb$hasBlockingArmPose = false;
	}

	@Unique
	private static boolean pmb$isUsingBlockingItem(SkeletonRenderState state) {
		if (!state.isUsingItem) {
			return false;
		}

		ItemStack stack = pmb$getItemStackForArm(state, pmb$usedItemArm(state));
		return !stack.isEmpty() && stack.get(DataComponents.BLOCKS_ATTACKS) != null;
	}

	@Unique
	private static HumanoidArm pmb$usedItemArm(SkeletonRenderState state) {
		if (state.useItemHand == InteractionHand.MAIN_HAND) {
			return state.mainArm;
		}

		return state.mainArm.getOpposite();
	}

	@Unique
	private static ItemStack pmb$getItemStackForArm(SkeletonRenderState state, HumanoidArm arm) {
		return arm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
	}
}
