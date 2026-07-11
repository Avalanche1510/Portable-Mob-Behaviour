package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbMaceAiData;
import com.pmb.ai.PmbShieldAiData;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class PmbMobMaceMixin extends LivingEntity {
	@Unique
	private boolean pmb$performingMaceSmash;

	protected PmbMobMaceMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void pmb$preventUnscheduledFallingMaceAttack(ServerLevel level, Entity target,
			CallbackInfoReturnable<Boolean> info) {
		PmbMaceAiData maceAi = ((PmbAiHolder) this).pmb$getAiData().mace();
		if (!pmb$performingMaceSmash && maceAi.isEnabled() && getMainHandItem().is(Items.MACE)
				&& pmb$isFallingForMaceSmash()) {
			info.setReturnValue(false);
		}
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void pmb$tickMaceAi(CallbackInfo info) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Mob mob = (Mob) (Object) this;
		PmbMaceAiData maceAi = ((PmbAiHolder) this).pmb$getAiData().mace();
		maceAi.tickCooldown();
		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!maceAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable()
				|| !getMainHandItem().is(Items.MACE) || !pmb$isFallingForMaceSmash()) {
			return;
		}

		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive() || !mob.canAttack(target) || !hasLineOfSight(target)) {
			return;
		}

		float range = maceAi.smashRange();
		if (!maceAi.canCheckSmash() || distanceToSqr(target) > range * range) {
			return;
		}

		maceAi.resetSmashCooldown();
		if (getRandom().nextFloat() > maceAi.hitChance()) {
			return;
		}

		pmb$faceMaceTarget(mob, target);
		swing(InteractionHand.MAIN_HAND, true);
		pmb$performingMaceSmash = true;
		try {
			mob.doHurtTarget(serverLevel, target);
		} finally {
			pmb$performingMaceSmash = false;
		}
	}

	@Unique
	private boolean pmb$isFallingForMaceSmash() {
		return !onGround() && getDeltaMovement().y() < 0.0D && MaceItem.canSmashAttack(this);
	}

	@Unique
	private void pmb$faceMaceTarget(Mob mob, LivingEntity target) {
		lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		float yaw = getYRot();
		setYHeadRot(yaw);
		setYBodyRot(yaw);
		mob.getLookControl().setLookAt(target, 180.0F, 180.0F);
	}

	@ModifyArg(method = "doHurtTarget", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"),
			index = 2)
	private float pmb$reduceMaceSmashDamage(float damage) {
		if (!pmb$performingMaceSmash) {
			return damage;
		}
		PmbMaceAiData maceAi = ((PmbAiHolder) this).pmb$getAiData().mace();
		return damage * (1.0F - maceAi.damageReduction());
	}
}
