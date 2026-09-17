package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbMovementController;
import com.pmb.ai.PmbBowAiData;
import com.pmb.ai.PmbBowMoveControl;
import com.pmb.ai.PmbShieldAiData;
import com.pmb.ai.PmbSkillHooks;
import com.pmb.ai.PmbSkillItemAccess;
import com.pmb.ai.PmbAmmoAccess;
import com.pmb.ai.PmbSkillScheduler;
import com.pmb.ai.PmbSkillTiming;
import java.util.List;
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
public abstract class PmbMobBowMixin extends LivingEntity implements PmbSkillHooks.Bow {
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
	@Unique private PmbSkillItemAccess.ActionBinding pmb$bowLease;
	@Unique private PmbAmmoAccess.Source pmb$bowAmmo;
	@Unique private InteractionHand pmb$bowHand;

	protected PmbMobBowMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"), cancellable = true)
	private void pmb$disableMeleeWhileUsingBowAi(ServerLevel level, Entity target,
			CallbackInfoReturnable<Boolean> info) {
		if (!((PmbSkillHooks.Mace) this).pmb$isPerformingMaceSmash()
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow((Mob) (Object) this)) info.setReturnValue(false);
	}

	@Override
	public void pmb$tickBowSkill() {
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
		PmbSkillScheduler scheduler = PmbSkillScheduler.of(mob);
		if (scheduler.hasBinding("bow")) {
			if (scheduler.release(mob, "bow")) pmb$bowLease = null;
			if (scheduler.hasBinding("bow")) return;
		}

		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!bowAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable()) return;
		PmbSkillItemAccess.Resolved bowItem = PmbSkillItemAccess.resolvePreferred(mob,
				bowAi.fetchSource(), List.of(InteractionHand.MAIN_HAND),
				stack -> stack.is(Items.BOW), bowAi.preferredHand(), "bow");
		if (bowItem == null) return;

		LivingEntity target = mob.getTarget();
		boolean hasRequiredEyeSight = !bowAi.requireEyeSight() || (target != null && hasLineOfSight(target));
		if (!pmb$isValidBowTarget(mob, target) || !hasRequiredEyeSight || !pmb$hasBowAmmo(bowAi)) return;

		double distance = distanceTo(target);
		boolean lineReady = bowAi.lineShootChance() > 0.0F && bowAi.canCheckLine()
				&& distance >= bowAi.lineMinRange() && distance <= bowAi.lineMaxRange();
		boolean arcReady = bowAi.arcShootChance() > 0.0F && bowAi.canCheckArc()
				&& distance >= bowAi.arcMinRange() && distance <= bowAi.arcMaxRange();
		int mode = pmb$selectBowMode(bowAi, lineReady, arcReady);
		if (mode == PMB_BOW_NONE) return;

		float chance;
		chance = mode == PMB_BOW_LINE ? bowAi.lineShootChance() : bowAi.arcShootChance();
		PmbSkillScheduler.Resource[] actionResources = bowItem.resources(PmbSkillScheduler.Resource.USE_ITEM, PmbSkillScheduler.Resource.LOOK);
		PmbSkillScheduler.of(mob).offer("bow", mode == PMB_BOW_LINE ? "line" : "arc",
				PmbSkillScheduler.Category.MAIN, 10, () -> {
			if (mode == PMB_BOW_LINE) bowAi.resetLineCooldown(getRandom());
			else bowAi.resetArcCooldown(getRandom());
			return PmbSkillTiming.passesChance(getRandom(), chance);
		}, () -> {
			LivingEntity currentTarget = mob.getTarget();
			boolean currentEyeSight = !bowAi.requireEyeSight()
					|| currentTarget != null && hasLineOfSight(currentTarget);
			if (!bowAi.isEnabled() || mob.isNoAi()
					|| ((PmbAiHolder) this).pmb$getAiData().shield().isVulnerable()
					|| currentTarget != target || !pmb$isValidBowTarget(mob, target) || !currentEyeSight) return;
			double currentDistance = distanceTo(target);
			boolean stillInModeRange = mode == PMB_BOW_LINE
					? currentDistance >= bowAi.lineMinRange() && currentDistance <= bowAi.lineMaxRange()
					: currentDistance >= bowAi.arcMinRange() && currentDistance <= bowAi.arcMaxRange();
			if (!stillInModeRange || !pmb$hasBowAmmo(bowAi)) return;
			// AmmoSource locations are defined before an inventory bow lease displaces its use hand.
			PmbAmmoAccess.Source selectedAmmo = pmb$findArrow(bowAi);
			if (bowAi.doConsume() && selectedAmmo == null) return;
			pmb$bowLease = PmbSkillItemAccess.acquire(mob, bowItem);
			if (pmb$bowLease == null) return;
			if (selectedAmmo != null) selectedAmmo.followEquipmentSwap(pmb$bowLease);
			pmb$bowAmmo = selectedAmmo;
			if (pmb$bowAmmo != null && !pmb$bowAmmo.matches(mob)) { pmb$bowLease = null; pmb$bowAmmo = null; return;
			}
			if (!PmbSkillScheduler.of(mob).bind("bow", pmb$bowLease)) { pmb$bowLease = null; pmb$bowAmmo = null; return;
			}
			pmb$bowChargeMode = mode;
			pmb$bowChargeTicks = mode == PMB_BOW_LINE ? bowAi.lineChargeTicks() : bowAi.arcChargeTicks();
			pmb$bowTarget = target;
			pmb$bowHand = pmb$bowLease.hand();
			pmb$faceBowTrajectory(mob, target, bowAi, mode);
			startUsingItem(pmb$bowHand);
			pmb$offerBowMovement(mob, bowAi, target, mode);
			PmbSkillScheduler.of(mob).markCurrentCandidateExecuted("bow");
			if (pmb$bowChargeTicks == 0) pmb$releaseBow(serverLevel, mob, bowAi);
		}, actionResources);
	}

	@Override
	public void pmb$cancelBowSkill() { pmb$cancelBowCharge(); }

	@Override
	public void pmb$claimBowResources(PmbSkillScheduler scheduler) {
		if (pmb$bowChargeMode != PMB_BOW_NONE) {
			scheduler.claim("bow", PmbSkillItemAccess.resource(pmb$bowHand),
					PmbSkillScheduler.Resource.USE_ITEM, PmbSkillScheduler.Resource.LOOK);
		}
	}

	@Unique
	private void pmb$tickBowCharge(ServerLevel level, Mob mob, PmbBowAiData bowAi) {
		if (!pmb$canContinueBowCharge(mob, bowAi)) {
			pmb$cancelBowCharge();
			return;
		}
		pmb$offerBowMovement(mob, bowAi, pmb$bowTarget, pmb$bowChargeMode);
		pmb$faceBowTrajectory(mob, pmb$bowTarget, bowAi, pmb$bowChargeMode);
		if (!isUsingItem() || getUsedItemHand() != pmb$bowHand) {
			startUsingItem(pmb$bowHand);
		}
		if (pmb$bowChargeTicks > 0) pmb$bowChargeTicks--;
		if (pmb$bowChargeTicks == 0) pmb$releaseBow(level, mob, bowAi);
	}

	@Unique
	private boolean pmb$canContinueBowCharge(Mob mob, PmbBowAiData bowAi) {
		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		boolean hasRequiredEyeSight = !bowAi.requireEyeSight()
				|| (pmb$bowTarget != null && hasLineOfSight(pmb$bowTarget));
		if (!bowAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable() || pmb$bowHand == null
				|| !getItemInHand(pmb$bowHand).is(Items.BOW)
				|| pmb$bowLease == null || !pmb$bowLease.matches(mob)
				|| !pmb$isValidBowTarget(mob, pmb$bowTarget)
				|| !hasRequiredEyeSight
				|| (pmb$bowAmmo != null ? !pmb$bowAmmo.matches(mob) : bowAi.doConsume())
				|| mob.getTarget() != pmb$bowTarget) return false;
		double distance = distanceTo(pmb$bowTarget);
		return pmb$bowChargeMode == PMB_BOW_LINE
				? bowAi.lineShootChance() > 0.0F && distance >= bowAi.lineMinRange() && distance <= bowAi.lineMaxRange()
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
	private void pmb$offerBowMovement(Mob mob, PmbBowAiData bowAi, LivingEntity target, int mode) {
		PmbSkillScheduler.of(mob).movement().submit(mob, "bow", PmbMovementController.Type.LOCOMOTION,
				PmbMovementController.Tier.ACTIVE,
				PmbSkillScheduler.Category.MAIN, 10,
				() -> isAlive() && !mob.isNoAi() && bowAi.isEnabled() && mob.getTarget() == target
						&& pmb$isValidBowTarget(mob, target)
						&& ((com.pmb.faction.PmbFactionMobState) mob).pmb$getFactionAvoidTarget() == null
						&& (!bowAi.requireEyeSight() || hasLineOfSight(target))
						&& pmb$bowChargeMode != PMB_BOW_NONE && pmb$canContinueBowCharge(mob, bowAi)
						&& mode == pmb$bowChargeMode,
				() -> pmb$moveWhileShooting(mob, bowAi, target, mode));
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
		return !bowAi.doConsume() || pmb$findArrow(bowAi) != null;
	}

	@Unique
	private PmbAmmoAccess.Source pmb$findArrow(PmbBowAiData bowAi) {
		return PmbAmmoAccess.find((Mob) (Object) this, bowAi.ammoSource(),
				((BowItem) Items.BOW).getAllSupportedProjectiles());
	}

	@Unique
	private void pmb$releaseBow(ServerLevel level, Mob mob, PmbBowAiData bowAi) {
		LivingEntity target = pmb$bowTarget;
		int mode = pmb$bowChargeMode;
		if (!pmb$canContinueBowCharge(mob, bowAi)) {
			pmb$cancelBowCharge();
			return;
		}

		ItemStack bowStack = getItemInHand(pmb$bowHand);
		PmbAmmoAccess.Source ammo = pmb$bowAmmo;
		ItemStack arrowStack = ammo == null ? new ItemStack(Items.ARROW) : ammo.projectile();
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
		if (bowAi.doConsume() && ammo != null) {
			if (!ammo.consume(mob)) { arrow.discard(); pmb$cancelBowCharge(); return; }
		}
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
		PmbSkillScheduler.of((Mob) (Object) this).movement().cancel("bow");
		if (isUsingItem() && getUsedItemHand() == pmb$bowHand) stopUsingItem();
		pmb$clearBowCharge();
	}

	@Unique
	private void pmb$clearBowCharge() {
		pmb$bowChargeMode = PMB_BOW_NONE;
		pmb$bowChargeTicks = 0;
		pmb$bowTarget = null;
		pmb$bowAmmo = null;
		pmb$bowHand = null;
		if (pmb$bowLease != null && PmbSkillScheduler.of((Mob) (Object) this)
				.release((Mob) (Object) this, "bow")) pmb$bowLease = null;
	}
}
