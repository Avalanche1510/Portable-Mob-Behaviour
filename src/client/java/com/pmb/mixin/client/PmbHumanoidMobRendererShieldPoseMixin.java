package com.pmb.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HumanoidMobRenderer.class)
public class PmbHumanoidMobRendererShieldPoseMixin {
	private static final String GUARD_VILLAGERS_NAMESPACE = "guardvillagers";

	@Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
	private void pmb$getBlockingArmPose(Mob mob, HumanoidArm arm,
			CallbackInfoReturnable<HumanoidModel.ArmPose> info) {
		if (pmb$isGuardVillagersEntity(mob)) {
			return;
		}

		if (!mob.isUsingItem() || arm != pmb$usedItemArm(mob)) {
			return;
		}

		ItemStack stack = mob.getItemHeldByArm(arm);
		if (stack.is(Items.BOW)) {
			info.setReturnValue(HumanoidModel.ArmPose.BOW_AND_ARROW);
			return;
		}
		if (!stack.isEmpty() && stack.get(DataComponents.BLOCKS_ATTACKS) != null) {
			info.setReturnValue(HumanoidModel.ArmPose.BLOCK);
		}
	}

	private static HumanoidArm pmb$usedItemArm(Mob mob) {
		if (mob.getUsedItemHand() == InteractionHand.MAIN_HAND) {
			return mob.getMainArm();
		}

		return mob.getMainArm().getOpposite();
	}

	private static boolean pmb$isGuardVillagersEntity(Mob mob) {
		Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
		return GUARD_VILLAGERS_NAMESPACE.equals(id.getNamespace());
	}
}
