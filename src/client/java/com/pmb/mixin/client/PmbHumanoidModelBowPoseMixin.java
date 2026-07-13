package com.pmb.mixin.client;

import com.pmb.client.PmbBowPose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public class PmbHumanoidModelBowPoseMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
	private void pmb$forceBowPose(HumanoidRenderState state, CallbackInfo info) {
		HumanoidModel<?> model = (HumanoidModel<?>) (Object) this;
		PmbBowPose.pose(state, model.head, model.rightArm, model.leftArm);
	}
}
