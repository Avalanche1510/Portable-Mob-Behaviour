package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbBowAiData;
import com.pmb.ai.PmbBowMoveControl;
import com.pmb.ai.PmbShieldAiData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;
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
public abstract class PmbMobBowMixin extends LivingEntity {
	@Unique private static final int PMB_BOW_NONE = 0;
	@Unique private static final int PMB_BOW_LINE = 1;
	@Unique private static final int PMB_BOW_ARC = 2;
	@Unique private static final double PMB_ARROW_DRAG = 0.99D;
	@Unique private static final double PMB_ARROW_GRAVITY = 0.05D;
	@Unique private static final float PMB_FULL_DRAW_BOW_SPEED = 3.0F;
	@Unique private static final int PMB_ARC_SIMULATION_TICKS = 300;

	@Unique private int pmb$bowChargeMode;
	@Unique private int pmb$bowChargeTicks;
	@Unique private LivingEntity pmb$bowTarget;
	@Unique private int pmb$bowStrafeTicks;
	@Unique private boolean pmb$bowStrafeClockwise;
	@Unique private boolean pmb$bowStrafeForward;

	protected PmbMobBowMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void pmb$disableMeleeWhileUsingBowAi(ServerLevel level, Entity target,
			CallbackInfoReturnable<Boolean> info) {
		PmbBowAiData bowAi = ((PmbAiHolder) this).pmb$getAiData().bow();
		if (bowAi.isEnabled() && getMainHandItem().is(Items.BOW)) info.setReturnValue(false);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void pmb$tickBowAi(CallbackInfo info) {
		if (!(level() instanceof ServerLevel serverLevel)) return;

		Mob mob = (Mob) (Object) this;
		PmbBowAiData bowAi = ((PmbAiHolder) this).pmb$getAiData().bow();
		if (!isAlive()) {
			pmb$cancelBowCharge();
			return;
		}
		bowAi.tickCooldowns();
		if (pmb$bowChargeMode != PMB_BOW_NONE) {
			pmb$tickBowCharge(serverLevel, mob, bowAi);
			return;
		}

		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!bowAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable() || !getMainHandItem().is(Items.BOW)) return;

		LivingEntity target = mob.getTarget();
		if (!pmb$isValidBowTarget(mob, target) || !hasLineOfSight(target) || !pmb$hasBowAmmo(bowAi)) return;

		double distance = distanceTo(target);
		int movementMode = pmb$movementMode(bowAi, distance);
		if (movementMode != PMB_BOW_NONE) {
			pmb$moveWhileShooting(mob, bowAi, target, movementMode);
		}
		boolean lineReady = bowAi.lineShootChance() > 0.0F && bowAi.canCheckLine()
				&& distance <= bowAi.lineRange();
		boolean arcReady = bowAi.arcShootChance() > 0.0F && bowAi.canCheckArc()
				&& distance >= bowAi.arcMinRange() && distance <= bowAi.arcMaxRange();
		int mode = pmb$selectBowMode(bowAi, lineReady, arcReady);
		if (mode == PMB_BOW_NONE) return;

		float chance;
		if (mode == PMB_BOW_LINE) {
			bowAi.resetLineCooldown();
			chance = bowAi.lineShootChance();
		} else {
			bowAi.resetArcCooldown();
			chance = bowAi.arcShootChance();
		}
		if (getRandom().nextFloat() > chance) return;

		pmb$bowChargeMode = mode;
		pmb$bowChargeTicks = mode == PMB_BOW_LINE ? bowAi.lineChargeTicks() : bowAi.arcChargeTicks();
		pmb$bowTarget = target;
		pmb$faceBowTrajectory(mob, target, bowAi, mode);
		startUsingItem(InteractionHand.MAIN_HAND);
		if (pmb$bowChargeTicks == 0) pmb$releaseBow(serverLevel, mob, bowAi);
	}

	@Unique
	private void pmb$tickBowCharge(ServerLevel level, Mob mob, PmbBowAiData bowAi) {
		if (!pmb$canContinueBowCharge(mob, bowAi)) {
			pmb$cancelBowCharge();
			return;
		}
		pmb$moveWhileShooting(mob, bowAi, pmb$bowTarget, pmb$bowChargeMode);
		pmb$faceBowTrajectory(mob, pmb$bowTarget, bowAi, pmb$bowChargeMode);
		if (!isUsingItem() || getUsedItemHand() != InteractionHand.MAIN_HAND) {
			startUsingItem(InteractionHand.MAIN_HAND);
		}
		if (pmb$bowChargeTicks > 0) pmb$bowChargeTicks--;
		if (pmb$bowChargeTicks == 0) pmb$releaseBow(level, mob, bowAi);
	}

	@Unique
	private boolean pmb$canContinueBowCharge(Mob mob, PmbBowAiData bowAi) {
		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!bowAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable() || !getMainHandItem().is(Items.BOW)
				|| !pmb$isValidBowTarget(mob, pmb$bowTarget) || !hasLineOfSight(pmb$bowTarget)
				|| !pmb$hasBowAmmo(bowAi) || mob.getTarget() != pmb$bowTarget) return false;
		double distance = distanceTo(pmb$bowTarget);
		return pmb$bowChargeMode == PMB_BOW_LINE
				? bowAi.lineShootChance() > 0.0F && distance <= bowAi.lineRange()
				: bowAi.arcShootChance() > 0.0F && distance >= bowAi.arcMinRange() && distance <= bowAi.arcMaxRange();
	}

	@Unique
	private boolean pmb$isValidBowTarget(Mob mob, LivingEntity target) {
		return target != null && target.isAlive() && target.level() == level() && mob.canAttack(target);
	}

	@Unique
	private int pmb$selectBowMode(PmbBowAiData bowAi, boolean lineReady, boolean arcReady) {
		if (lineReady && arcReady) return PmbBowAiData.MODE_ARC.equals(bowAi.modePriority()) ? PMB_BOW_ARC : PMB_BOW_LINE;
		if (lineReady) return PMB_BOW_LINE;
		return arcReady ? PMB_BOW_ARC : PMB_BOW_NONE;
	}

	@Unique
	private int pmb$movementMode(PmbBowAiData bowAi, double distance) {
		boolean line = bowAi.lineShootChance() > 0.0F && distance <= bowAi.lineRange();
		boolean arc = bowAi.arcShootChance() > 0.0F && distance <= bowAi.arcMaxRange()
				&& (distance >= bowAi.arcMinRange() || distance < bowAi.arcSafeDistance());
		return pmb$selectBowMode(bowAi, line, arc);
	}

	@Unique
	private void pmb$moveWhileShooting(Mob mob, PmbBowAiData bowAi, LivingEntity target, int mode) {
		double distance = distanceTo(target);
		float safeDistance = mode == PMB_BOW_LINE ? bowAi.lineSafeDistance() : bowAi.arcSafeDistance();
		boolean retreat = distance < safeDistance;
		mob.getNavigation().stop();
		if (!retreat && !bowAi.mobileWhileShooting()) {
			mob.getMoveControl().setWait();
			return;
		}

		if (++pmb$bowStrafeTicks >= 20) {
			pmb$bowStrafeTicks = 0;
			if (getRandom().nextFloat() < 0.3F) pmb$bowStrafeClockwise = !pmb$bowStrafeClockwise;
			if (getRandom().nextFloat() < 0.3F) pmb$bowStrafeForward = !pmb$bowStrafeForward;
		}
		float forward = retreat ? -0.7F : (pmb$bowStrafeForward ? 0.2F : -0.2F);
		float sideways = pmb$bowStrafeClockwise ? 0.5F : -0.5F;
		((PmbBowMoveControl) mob.getMoveControl()).pmb$strafeForBow(forward, sideways);
	}

	@Unique
	private boolean pmb$hasBowAmmo(PmbBowAiData bowAi) {
		return !bowAi.doConsume() || pmb$isSupportedArrow(getOffhandItem());
	}

	@Unique
	private boolean pmb$isSupportedArrow(ItemStack stack) {
		return getMainHandItem().getItem() instanceof BowItem bow && bow.getAllSupportedProjectiles().test(stack);
	}

	@Unique
	private ItemStack pmb$arrowForShot() {
		ItemStack offhand = getOffhandItem();
		return pmb$isSupportedArrow(offhand) ? offhand.copyWithCount(1) : new ItemStack(Items.ARROW);
	}

	@Unique
	private void pmb$releaseBow(ServerLevel level, Mob mob, PmbBowAiData bowAi) {
		LivingEntity target = pmb$bowTarget;
		int mode = pmb$bowChargeMode;
		if (!pmb$canContinueBowCharge(mob, bowAi)) {
			pmb$cancelBowCharge();
			return;
		}

		ItemStack bowStack = getMainHandItem();
		ItemStack arrowStack = pmb$arrowForShot();
		AbstractArrow arrow = ProjectileUtil.getMobArrow(this, arrowStack, 1.0F, bowStack);
		Vec3 velocity;
		float accuracy;
		if (mode == PMB_BOW_LINE) {
			velocity = pmb$lineVelocity(target, pmb$bowSpeed(bowAi.linePower()));
			accuracy = bowAi.lineShootAccuracy();
		} else {
			velocity = pmb$arcVelocity(target, bowAi.arcAngle(), pmb$bowSpeed(bowAi.arcMaxPower()));
			accuracy = bowAi.arcShootAccuracy();
		}
		pmb$faceBowVelocity(mob, velocity);
		float power = (float) Math.max(0.1D, velocity.length());
		float inaccuracy = (1.0F - accuracy) * 14.0F;
		stopUsingItem();
		Projectile.spawnProjectileUsingShoot(arrow, level, arrowStack, velocity.x(), velocity.y(), velocity.z(),
				power, inaccuracy);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ARROW_SHOOT, getSoundSource(), 1.0F,
				1.0F / (getRandom().nextFloat() * 0.4F + 0.8F));
		if (bowAi.doConsume()) pmb$consumeArrow();
		pmb$clearBowCharge();
	}

	@Unique
	private Vec3 pmb$lineVelocity(LivingEntity target, float power) {
		Vec3 start = new Vec3(getX(), getEyeY() - 0.1D, getZ());
		Vec3 targetPoint = target.getEyePosition();
		double horizontalDistance = pmb$horizontalDistance(start, targetPoint);
		double flightTicks = horizontalDistance / Math.max(0.1D, power);
		targetPoint = targetPoint.add(target.getDeltaMovement().scale(flightTicks));
		horizontalDistance = pmb$horizontalDistance(start, targetPoint);
		flightTicks = horizontalDistance / Math.max(0.1D, power);
		double gravityCompensation = 0.5D * PMB_ARROW_GRAVITY * flightTicks * flightTicks;
		return targetPoint.subtract(start).add(0.0D, gravityCompensation, 0.0D).normalize().scale(power);
	}

	@Unique
	private Vec3 pmb$arcVelocity(LivingEntity target, float angleDegrees, float maxPower) {
		Vec3 start = new Vec3(getX(), getEyeY() - 0.1D, getZ());
		Vec3 targetPoint = target.getEyePosition();
		double angle = Math.toRadians(angleDegrees);
		double power = maxPower;
		for (int i = 0; i < 3; i++) {
			double[] solution = pmb$solveArc(start, targetPoint, angle, maxPower);
			power = solution[0];
			targetPoint = target.getEyePosition().add(target.getDeltaMovement().scale(solution[1]));
		}
		power = pmb$solveArc(start, targetPoint, angle, maxPower)[0];
		Vec3 horizontal = targetPoint.subtract(start).multiply(1.0D, 0.0D, 1.0D);
		Vec3 horizontalDirection = horizontal.lengthSqr() > 1.0E-8D ? horizontal.normalize() : getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
		return horizontalDirection.scale(Math.cos(angle) * power).add(0.0D, Math.sin(angle) * power, 0.0D);
	}

	@Unique
	private double[] pmb$solveArc(Vec3 start, Vec3 targetPoint, double angle, double maxPower) {
		double distance = pmb$horizontalDistance(start, targetPoint);
		double targetHeight = targetPoint.y() - start.y();
		double[] maximum = pmb$simulateArc(distance, angle, maxPower);
		if (maximum[2] == 0.0D || maximum[0] < targetHeight) return new double[] {maxPower, maximum[1]};
		double low = 0.1D;
		double high = maxPower;
		for (int i = 0; i < 18; i++) {
			double mid = (low + high) * 0.5D;
			double[] result = pmb$simulateArc(distance, angle, mid);
			if (result[2] != 0.0D && result[0] >= targetHeight) high = mid;
			else low = mid;
		}
		double[] solved = pmb$simulateArc(distance, angle, high);
		return new double[] {high, solved[1]};
	}

	@Unique
	private double[] pmb$simulateArc(double distance, double angle, double power) {
		if (distance < 1.0E-6D) return new double[] {0.0D, 1.0D, 1.0D};
		double x = 0.0D;
		double y = 0.0D;
		double velocityX = Math.cos(angle) * power;
		double velocityY = Math.sin(angle) * power;
		for (int tick = 1; tick <= PMB_ARC_SIMULATION_TICKS; tick++) {
			double previousX = x;
			double previousY = y;
			x += velocityX;
			y += velocityY;
			if (x >= distance && x > previousX) {
				double fraction = (distance - previousX) / (x - previousX);
				return new double[] {previousY + (y - previousY) * fraction, tick - 1.0D + fraction, 1.0D};
			}
			velocityX *= PMB_ARROW_DRAG;
			velocityY = velocityY * PMB_ARROW_DRAG - PMB_ARROW_GRAVITY;
		}
		double estimatedTicks = Math.min(100.0D,
				distance / Math.max(0.01D, Math.cos(angle) * power));
		return new double[] {Double.NEGATIVE_INFINITY, estimatedTicks, 0.0D};
	}

	@Unique
	private double pmb$horizontalDistance(Vec3 first, Vec3 second) {
		double x = second.x() - first.x();
		double z = second.z() - first.z();
		return Math.sqrt(x * x + z * z);
	}

	@Unique
	private void pmb$consumeArrow() {
		ItemStack stack = getOffhandItem();
		stack.consume(1, this);
		setItemInHand(InteractionHand.OFF_HAND, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
	}

	@Unique
	private void pmb$faceBowTrajectory(Mob mob, LivingEntity target, PmbBowAiData bowAi, int mode) {
		Vec3 velocity = mode == PMB_BOW_LINE
				? pmb$lineVelocity(target, pmb$bowSpeed(bowAi.linePower()))
				: pmb$arcVelocity(target, bowAi.arcAngle(), pmb$bowSpeed(bowAi.arcMaxPower()));
		pmb$faceBowVelocity(mob, velocity);
	}

	@Unique
	private float pmb$bowSpeed(float multiplier) {
		return PMB_FULL_DRAW_BOW_SPEED * multiplier;
	}

	@Unique
	private void pmb$faceBowVelocity(Mob mob, Vec3 velocity) {
		double horizontal = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
		float yaw = (float) (Math.toDegrees(Math.atan2(velocity.z(), velocity.x())) - 90.0D);
		float pitch = (float) -Math.toDegrees(Math.atan2(velocity.y(), horizontal));
		setYRot(yaw);
		setXRot(pitch);
		setYHeadRot(yaw);
		setYBodyRot(yaw);
		Vec3 aimPoint = getEyePosition().add(velocity.normalize().scale(16.0D));
		mob.getLookControl().setLookAt(aimPoint.x(), aimPoint.y(), aimPoint.z(), 180.0F, 180.0F);
	}

	@Unique
	private void pmb$cancelBowCharge() {
		if (isUsingItem() && getUsedItemHand() == InteractionHand.MAIN_HAND) stopUsingItem();
		pmb$clearBowCharge();
	}

	@Unique
	private void pmb$clearBowCharge() {
		pmb$bowChargeMode = PMB_BOW_NONE;
		pmb$bowChargeTicks = 0;
		pmb$bowTarget = null;
	}
}
