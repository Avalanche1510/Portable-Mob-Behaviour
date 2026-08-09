package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbEnderPearlAiData;
import com.pmb.ai.PmbShieldAiData;
import com.pmb.faction.PmbFactionMobState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mob.class)
public abstract class PmbMobEnderPearlMixin extends LivingEntity {
	@Unique private static final float PMB_VANILLA_PEARL_SPEED = 1.5F;
	@Unique private static final double PMB_PEARL_DRAG = 0.99D;
	@Unique private static final double PMB_PEARL_GRAVITY = 0.03D;
	@Unique private static final int PMB_PEARL_SIMULATION_TICKS = 300;
	@Unique private static final int PMB_PEARL_LOOK_TICKS = 4;
	@Unique private static final double PMB_MIN_SOLVE_SPEED = 0.01D;

	@Unique private Vec3 pmb$pearlLookPoint;
	@Unique private int pmb$pearlLookTicks;

	protected PmbMobEnderPearlMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void pmb$tickEnderPearlAi(CallbackInfo info) {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Mob mob = (Mob) (Object) this;
		PmbEnderPearlAiData pearlAi = ((PmbAiHolder) this).pmb$getAiData().enderPearl();
		if (!isAlive()) {
			pmb$clearPearlLook();
			return;
		}
		pearlAi.tickCooldown();
		pmb$tickPearlLook(mob);

		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!pearlAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable()) {
			pmb$clearPearlLook();
			return;
		}
		InteractionHand hand = pmb$enderPearlHand();
		if (hand == null || pearlAi.throwChance() <= 0.0F) {
			return;
		}

		LivingEntity evasiveTarget = ((PmbFactionMobState) mob).pmb$getFactionAvoidTarget();
		boolean evasive = evasiveTarget != null && evasiveTarget.isAlive()
				&& evasiveTarget.level() == level();
		LivingEntity target = evasive ? evasiveTarget : mob.getTarget();
		if (target == null || !target.isAlive() || target.level() != level()) {
			return;
		}
		double distanceSquared = distanceToSqr(target);
		boolean inThrowRange = distanceSquared <= pearlAi.maxThrowRange() * pearlAi.maxThrowRange()
				&& (evasive || distanceSquared >= pearlAi.minThrowRange() * pearlAi.minThrowRange());
		if (!inThrowRange || !hasLineOfSight(target) || !pearlAi.canCheckThrow()) {
			return;
		}

		pearlAi.resetThrowCooldown();
		if (getRandom().nextFloat() > pearlAi.throwChance()) {
			return;
		}
		pmb$throwEnderPearl(serverLevel, mob, hand, target, pearlAi, evasive);
	}

	@Unique
	private InteractionHand pmb$enderPearlHand() {
		if (getMainHandItem().is(Items.ENDER_PEARL)) {
			return InteractionHand.MAIN_HAND;
		}
		if (getOffhandItem().is(Items.ENDER_PEARL)) {
			return InteractionHand.OFF_HAND;
		}
		return null;
	}

	@Unique
	private void pmb$throwEnderPearl(ServerLevel level, Mob mob, InteractionHand hand, LivingEntity target,
			PmbEnderPearlAiData pearlAi, boolean evasive) {
		ItemStack heldStack = getItemInHand(hand);
		if (!heldStack.is(Items.ENDER_PEARL)) {
			return;
		}
		ItemStack projectileStack = heldStack.copyWithCount(1);
		Vec3 start = new Vec3(getX(), getEyeY() - 0.1D, getZ());
		Vec3 targetPoint = evasive
				? pmb$evasivePearlDestination(mob, target, pearlAi.maxThrowRange())
				: target.getEyePosition();
		float maxSpeed = PMB_VANILLA_PEARL_SPEED * pearlAi.maxThrowPower();
		double angle = Math.toRadians(pearlAi.throwAngle());
		Vec3 velocity = pmb$pearlVelocity(start, targetPoint, evasive ? null : target, angle, maxSpeed);
		pmb$facePearlVelocity(mob, velocity);

		ThrownEnderpearl pearl = new ThrownEnderpearl(level, this, projectileStack);
		float inaccuracy = (1.0F - pearlAi.throwAccuracy()) * 12.0F;
		float speed = (float) velocity.length();
		Projectile.spawnProjectileUsingShoot(pearl, level, projectileStack,
				velocity.x(), velocity.y(), velocity.z(), speed, inaccuracy);
		swing(hand, true);
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDER_PEARL_THROW,
				SoundSource.NEUTRAL, 0.5F, 0.4F / (getRandom().nextFloat() * 0.4F + 0.8F));
		if (pearlAi.doConsume()) {
			heldStack.consume(1, this);
			setItemInHand(hand, heldStack.isEmpty() ? ItemStack.EMPTY : heldStack.copy());
		}
	}

	@Unique
	private Vec3 pmb$evasivePearlDestination(Mob mob, LivingEntity threat, float maxThrowRange) {
		Vec3 destination = null;
		BlockPos navigationTarget = mob.getNavigation().getTargetPos();
		if (!mob.getNavigation().isDone() && navigationTarget != null) {
			Vec3 candidate = Vec3.atCenterOf(navigationTarget);
			if (candidate.distanceToSqr(threat.position()) > distanceToSqr(threat)) {
				destination = candidate;
			}
		}
		if (destination == null) {
			Vec3 away = position().subtract(threat.position()).multiply(1.0D, 0.0D, 1.0D);
			if (away.lengthSqr() < 1.0E-6D) {
				double angle = Math.toRadians(Math.floorMod(getId() * 137, 360));
				away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
			}
			destination = position().add(away.normalize().scale(Math.max(1.0D, maxThrowRange)));
		}

		Vec3 offset = destination.subtract(position());
		double distance = offset.length();
		if (distance > maxThrowRange && maxThrowRange > 0.0F) {
			destination = position().add(offset.scale(maxThrowRange / distance));
		}
		return destination;
	}

	@Unique
	private Vec3 pmb$pearlVelocity(Vec3 start, Vec3 initialTargetPoint, LivingEntity movingTarget,
			double angle, float maxSpeed) {
		Vec3 targetPoint = initialTargetPoint;
		double[] solution = pmb$solvePearlSpeed(start, targetPoint, angle, maxSpeed);
		if (movingTarget != null) {
			for (int i = 0; i < 3; i++) {
				targetPoint = movingTarget.getEyePosition()
						.add(movingTarget.getDeltaMovement().scale(solution[1]));
				solution = pmb$solvePearlSpeed(start, targetPoint, angle, maxSpeed);
			}
		}

		Vec3 horizontal = targetPoint.subtract(start).multiply(1.0D, 0.0D, 1.0D);
		Vec3 horizontalDirection = horizontal.lengthSqr() > 1.0E-8D
				? horizontal.normalize()
				: getLookAngle().multiply(1.0D, 0.0D, 1.0D).normalize();
		if (horizontalDirection.lengthSqr() < 1.0E-8D) {
			horizontalDirection = new Vec3(0.0D, 0.0D, 1.0D);
		}
		double speed = solution[0];
		return horizontalDirection.scale(Math.cos(angle) * speed)
				.add(0.0D, Math.sin(angle) * speed, 0.0D);
	}

	@Unique
	private double[] pmb$solvePearlSpeed(Vec3 start, Vec3 targetPoint, double angle, double maxSpeed) {
		double horizontalDistance = pmb$horizontalDistance(start, targetPoint);
		double targetHeight = targetPoint.y() - start.y();
		if (horizontalDistance < 1.0E-6D) {
			return new double[] {maxSpeed, Math.max(1.0D, Math.abs(targetHeight) / maxSpeed)};
		}

		double[] maximum = pmb$simulatePearl(horizontalDistance, targetHeight, angle, maxSpeed);
		if (maximum[2] == 0.0D || maximum[0] < targetHeight) {
			return new double[] {maxSpeed, maximum[1]};
		}

		double low = PMB_MIN_SOLVE_SPEED;
		double high = maxSpeed;
		for (int i = 0; i < 20; i++) {
			double speed = (low + high) * 0.5D;
			double[] result = pmb$simulatePearl(horizontalDistance, targetHeight, angle, speed);
			if (result[2] != 0.0D && result[0] >= targetHeight) {
				high = speed;
			} else {
				low = speed;
			}
		}
		double[] solved = pmb$simulatePearl(horizontalDistance, targetHeight, angle, high);
		return new double[] {high, solved[1]};
	}

	@Unique
	private double[] pmb$simulatePearl(double distance, double targetHeight, double angle, double speed) {
		double x = 0.0D;
		double y = 0.0D;
		double velocityX = Math.cos(angle) * speed;
		double velocityY = Math.sin(angle) * speed;
		double bestMiss = distance * distance + targetHeight * targetHeight;
		double bestTime = 1.0D;
		for (int tick = 1; tick <= PMB_PEARL_SIMULATION_TICKS; tick++) {
			double previousX = x;
			double previousY = y;
			velocityY -= PMB_PEARL_GRAVITY;
			velocityX *= PMB_PEARL_DRAG;
			velocityY *= PMB_PEARL_DRAG;
			x += velocityX;
			y += velocityY;
			double miss = (distance - x) * (distance - x) + (targetHeight - y) * (targetHeight - y);
			if (miss < bestMiss) {
				bestMiss = miss;
				bestTime = tick;
			}
			if (x >= distance && x > previousX) {
				double fraction = (distance - previousX) / (x - previousX);
				double crossingY = previousY + (y - previousY) * fraction;
				double crossingTime = tick - 1.0D + fraction;
				double verticalMiss = crossingY - targetHeight;
				return new double[] {crossingY, crossingTime, 1.0D, verticalMiss * verticalMiss};
			}
		}
		return new double[] {y, bestTime, 0.0D, bestMiss};
	}

	@Unique
	private double pmb$horizontalDistance(Vec3 first, Vec3 second) {
		double x = second.x() - first.x();
		double z = second.z() - first.z();
		return Math.sqrt(x * x + z * z);
	}

	@Unique
	private void pmb$facePearlVelocity(Mob mob, Vec3 velocity) {
		double horizontal = Math.sqrt(velocity.x() * velocity.x() + velocity.z() * velocity.z());
		float yaw = (float) (Math.toDegrees(Math.atan2(velocity.z(), velocity.x())) - 90.0D);
		float pitch = (float) -Math.toDegrees(Math.atan2(velocity.y(), horizontal));
		setYRot(yaw);
		setXRot(pitch);
		setYHeadRot(yaw);
		setYBodyRot(yaw);
		pmb$pearlLookPoint = getEyePosition().add(velocity.normalize().scale(16.0D));
		pmb$pearlLookTicks = PMB_PEARL_LOOK_TICKS;
		mob.getLookControl().setLookAt(pmb$pearlLookPoint.x(), pmb$pearlLookPoint.y(),
				pmb$pearlLookPoint.z(), 180.0F, 180.0F);
	}

	@Unique
	private void pmb$tickPearlLook(Mob mob) {
		if (pmb$pearlLookTicks <= 0 || pmb$pearlLookPoint == null) {
			pmb$clearPearlLook();
			return;
		}
		pmb$pearlLookTicks--;
		mob.getLookControl().setLookAt(pmb$pearlLookPoint.x(), pmb$pearlLookPoint.y(),
				pmb$pearlLookPoint.z(), 180.0F, 180.0F);
	}

	@Unique
	private void pmb$clearPearlLook() {
		pmb$pearlLookPoint = null;
		pmb$pearlLookTicks = 0;
	}
}
