package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbShieldAiData;
import com.pmb.ai.PmbWindChargeAiData;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class PmbMobWindChargeMixin extends LivingEntity {
	@Unique
	private static final int PMB_BOUNCE_FLIGHT_TICKS = 60;
	@Unique
	private static final int PMB_BOUNCE_POSE_TICKS = 4;
	@Unique
	private static final double PMB_MAX_TARGETWARD_SPEED = 0.3D;

	@Unique
	private LivingEntity pmb$bounceTarget;
	@Unique
	private int pmb$bounceFlightTicks;
	@Unique
	private int pmb$bouncePoseTicks;

	protected PmbMobWindChargeMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void pmb$tickWindChargeAi(CallbackInfo info) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Mob mob = (Mob) (Object) this;
		PmbWindChargeAiData windChargeAi = ((PmbAiHolder) this).pmb$getAiData().windCharge();
		windChargeAi.tickCooldowns();

		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!windChargeAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable() || pmb$windChargeHand() == null) {
			pmb$clearBounceState();
			return;
		}
		pmb$tickBounceFlight(mob, windChargeAi);

		LivingEntity target = mob.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}

		double distanceSquared = distanceToSqr(target);
		if (onGround() && distanceSquared <= windChargeAi.bounceRange() * windChargeAi.bounceRange()
				&& windChargeAi.canCheckBounce()) {
			windChargeAi.resetBounceCooldown();
			if (getRandom().nextFloat() <= windChargeAi.bounceChance()) {
				pmb$performWindChargeBounce(serverLevel, mob, target, windChargeAi);
				return;
			}
		}

		if (distanceSquared <= windChargeAi.throwRange() * windChargeAi.throwRange()
				&& hasLineOfSight(target) && windChargeAi.canCheckThrow()) {
			windChargeAi.resetThrowCooldown();
			if (getRandom().nextFloat() <= windChargeAi.throwChance()) {
				pmb$throwWindCharge(serverLevel, target, windChargeAi);
			}
		}
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"))
	private void pmb$faceBounceTargetWhenAttacking(ServerLevel level, Entity target,
			CallbackInfoReturnable<Boolean> info) {
		if (pmb$bounceFlightTicks > 0 && target instanceof LivingEntity livingTarget) {
			pmb$bounceTarget = livingTarget;
			pmb$bouncePoseTicks = 0;
			pmb$faceBounceTarget((Mob) (Object) this, livingTarget);
		}
	}

	@Unique
	private InteractionHand pmb$windChargeHand() {
		if (getMainHandItem().is(Items.WIND_CHARGE)) {
			return InteractionHand.MAIN_HAND;
		}
		if (getOffhandItem().is(Items.WIND_CHARGE)) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	@Unique
	private void pmb$throwWindCharge(ServerLevel level, LivingEntity target, PmbWindChargeAiData windChargeAi) {
		InteractionHand hand = pmb$windChargeHand();
		if (hand == null) {
			return;
		}

		Vec3 start = new Vec3(getX(), getEyeY() - 0.1D, getZ());
		double distance = Math.sqrt(distanceToSqr(target));
		double travelTicks = distance / 1.5D;
		Vec3 targetPoint = target.getEyePosition().add(target.getDeltaMovement().scale(travelTicks));
		Vec3 direction = targetPoint.subtract(start);
		WindCharge projectile = new WindCharge(level, start.x(), start.y(), start.z(), Vec3.ZERO);
		projectile.setOwner(this);
		float inaccuracy = (1.0F - windChargeAi.throwAccuracy()) * 12.0F;
		projectile.shoot(direction.x(), direction.y(), direction.z(), 1.5F, inaccuracy);
		level.addFreshEntity(projectile);
		swing(hand, true);
		pmb$consumeWindCharge(hand, windChargeAi);
		pmb$playWindChargeThrowSound(level);
	}

	@Unique
	private void pmb$performWindChargeBounce(ServerLevel level, Mob mob, LivingEntity target,
			PmbWindChargeAiData windChargeAi) {
		InteractionHand hand = pmb$windChargeHand();
		if (hand == null) {
			return;
		}

		pmb$bounceTarget = target;
		pmb$bounceFlightTicks = PMB_BOUNCE_FLIGHT_TICKS;
		pmb$bouncePoseTicks = PMB_BOUNCE_POSE_TICKS;
		pmb$lookStraightDown(mob);
		swing(hand, true);
		jumpFromGround();

		Vec3 start = new Vec3(getX(), getY() + 0.15D, getZ());
		setIgnoreFallDamageFromCurrentImpulse(true, start);
		WindCharge projectile = new WindCharge(level, start.x(), start.y(), start.z(), Vec3.ZERO);
		projectile.setOwner(this);
		projectile.shoot(0.0D, -1.0D, 0.0D, 1.5F, 0.0F);
		level.addFreshEntity(projectile);
		pmb$consumeWindCharge(hand, windChargeAi);
		pmb$playWindChargeThrowSound(level);
	}

	@Unique
	private void pmb$consumeWindCharge(InteractionHand hand, PmbWindChargeAiData windChargeAi) {
		if (windChargeAi.doConsume()) {
			ItemStack stack = getItemInHand(hand);
			stack.consume(1, this);
			setItemInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
	}

	@Unique
	private void pmb$tickBounceFlight(Mob mob, PmbWindChargeAiData windChargeAi) {
		if (pmb$bouncePoseTicks > 0) {
			pmb$bouncePoseTicks--;
			pmb$lookStraightDown(mob);
		}

		if (pmb$bounceFlightTicks <= 0) {
			pmb$clearBounceState();
			return;
		}

		pmb$bounceFlightTicks--;
		if (pmb$bounceFlightTicks < PMB_BOUNCE_FLIGHT_TICKS - 2 && onGround()) {
			pmb$clearBounceState();
			return;
		}

		LivingEntity target = pmb$bounceTarget;
		if (target == null || !target.isAlive() || target.level() != level()) {
			pmb$clearBounceState();
			return;
		}

		if (mob.getTarget() == null) {
			mob.setTarget(target);
		}
		if (onGround()) {
			return;
		}
		if (pmb$bouncePoseTicks <= 0) {
			pmb$faceBounceTarget(mob, target);
		}

		Vec3 horizontal = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
		if (horizontal.lengthSqr() < 1.0E-6D) {
			return;
		}

		Vec3 movement = getDeltaMovement();
		Vec3 targetDirection = horizontal.normalize();
		Vec3 horizontalMovement = new Vec3(movement.x(), 0.0D, movement.z());
		double targetwardSpeed = horizontalMovement.dot(targetDirection);
		double adjustment = Math.min(windChargeAi.inAirTrackStrength(),
				Math.max(0.0D, PMB_MAX_TARGETWARD_SPEED - targetwardSpeed));
		if (adjustment > 0.0D) {
			Vec3 steered = horizontalMovement.add(targetDirection.scale(adjustment));
			setDeltaMovement(steered.x(), movement.y(), steered.z());
		}
	}

	@Unique
	private void pmb$faceBounceTarget(Mob mob, LivingEntity target) {
		lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
		float yaw = getYRot();
		setYHeadRot(yaw);
		setYBodyRot(yaw);
		mob.getLookControl().setLookAt(target, 180.0F, 180.0F);
	}

	@Unique
	private void pmb$clearBounceState() {
		pmb$bounceTarget = null;
		pmb$bounceFlightTicks = 0;
		pmb$bouncePoseTicks = 0;
	}

	@Unique
	private void pmb$lookStraightDown(Mob mob) {
		mob.getLookControl().setLookAt(getX(), getY() - 2.0D, getZ(), 180.0F, 180.0F);
		setXRot(90.0F);
	}

	@Unique
	private void pmb$playWindChargeThrowSound(ServerLevel level) {
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.WIND_CHARGE_THROW, SoundSource.NEUTRAL,
				0.5F, 0.8F + getRandom().nextFloat() * 0.4F);
	}
}
