package com.pmb.mixin.client;

import com.pmb.client.PmbZombieShieldPose;
import com.pmb.client.PmbBowPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.zombie.AbstractZombieModel;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractZombieModel.class)
public class PmbAbstractZombieModelShieldPoseMixin {
	@Redirect(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/AnimationUtils;animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZLnet/minecraft/client/renderer/entity/state/UndeadRenderState;)V"))
	private void pmb$keepBlockingArmPose(ModelPart leftArm, ModelPart rightArm, boolean isAggressive,
			UndeadRenderState state) {
		PmbZombieShieldPose.keepBlockingArmPose(leftArm, rightArm, isAggressive, state);
	}

	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombieRenderState;)V", at = @At("TAIL"))
	private void pmb$forceBowPose(ZombieRenderState state, CallbackInfo info) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		PmbBowPose.pose(state, model.head, model.rightArm, model.leftArm);
	}
}
