package com.pmb.mixin;

import com.pmb.ai.PmbSkillItemAccess;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RangedBowAttackGoal.class)
public class PmbRangedBowAttackGoalMixin {
	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/Monster;isUsingItem()Z"))
	private boolean pmb$hidePmbBowChargeFromVanillaGoal(Monster mob) {
		return pmb$usesPmbBowAi(mob) ? false : mob.isUsingItem();
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/Monster;startUsingItem(Lnet/minecraft/world/InteractionHand;)V"))
	private void pmb$preventVanillaBowCharge(Monster mob, InteractionHand hand) {
		if (!pmb$usesPmbBowAi(mob)) mob.startUsingItem(hand);
	}

	@Redirect(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/monster/RangedAttackMob;performRangedAttack(Lnet/minecraft/world/entity/LivingEntity;F)V"))
	private void pmb$preventVanillaBowShot(RangedAttackMob rangedMob, LivingEntity target, float power) {
		if (!(rangedMob instanceof LivingEntity living) || !pmb$usesPmbBowAi(living)) {
			rangedMob.performRangedAttack(target, power);
		}
	}

	private static boolean pmb$usesPmbBowAi(LivingEntity entity) {
		return entity instanceof Mob mob && PmbSkillItemAccess.canManageBow(mob);
	}
}
