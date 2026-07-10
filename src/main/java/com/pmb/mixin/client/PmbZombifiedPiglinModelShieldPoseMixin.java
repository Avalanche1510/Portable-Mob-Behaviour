package com.pmb.mixin.client;

import com.pmb.client.PmbZombieShieldPose;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.monster.piglin.ZombifiedPiglinModel;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ZombifiedPiglinModel.class)
public class PmbZombifiedPiglinModelShieldPoseMixin {
	@Redirect(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/ZombifiedPiglinRenderState;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/AnimationUtils;animateZombieArms(Lnet/minecraft/client/model/geom/ModelPart;Lnet/minecraft/client/model/geom/ModelPart;ZLnet/minecraft/client/renderer/entity/state/UndeadRenderState;)V"))
	private void pmb$keepBlockingArmPose(ModelPart leftArm, ModelPart rightArm, boolean isAggressive,
			UndeadRenderState state) {
		PmbZombieShieldPose.keepBlockingArmPose(leftArm, rightArm, isAggressive, state);
	}
}
