package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbEnderPearlAiData;
import com.pmb.ai.PmbEnderPearlBallistics;
import com.pmb.ai.PmbShieldAiData;
import com.pmb.ai.PmbSkillHooks;
import com.pmb.ai.PmbSkillItemAccess;
import com.pmb.ai.PmbSkillScheduler;
import com.pmb.ai.PmbSkillTiming;
import java.util.List;
import com.pmb.faction.PmbFactionMobState;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
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
public abstract class PmbMobEnderPearlMixin extends LivingEntity implements PmbSkillHooks.Pearl {
	@Unique private static final float PMB_VANILLA_PEARL_SPEED = 1.5F;
	@Unique private static final int PMB_PEARL_LOOK_TICKS = 4;

	@Unique private Vec3 pmb$pearlLookPoint;
	@Unique private int pmb$pearlLookTicks;

	protected PmbMobEnderPearlMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public void pmb$tickPearlSkill() {
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
		if (pearlAi.throwChance() <= 0.0F) {
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
		boolean hasRequiredEyeSight = !pearlAi.requireEyeSight() || (target != null && hasLineOfSight(target));
		if (!inThrowRange || !hasRequiredEyeSight || !pearlAi.canCheckThrow() || pmb$pearlLaunchPointInWater()) {
			return;
		}

		PmbSkillItemAccess.Resolved item = PmbSkillItemAccess.resolvePreferred(mob, pearlAi.fetchSource(),
				List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND), stack -> stack.is(Items.ENDER_PEARL),
				pearlAi.preferredHand(), "pearl");
		if (item == null) return;
		PmbSkillScheduler.of(mob).offerDynamic("pearl", "throw", PmbSkillScheduler.Category.THROW, 20,
				() -> {
					pearlAi.resetThrowCooldown(getRandom());
					return PmbSkillTiming.passesChance(getRandom(), pearlAi.throwChance());
				}, () -> {
					Vec3 velocity = pmb$planEnderPearlLaunch(mob, target, pearlAi, evasive);
					if (velocity == null) return;
					PmbSkillItemAccess.ActionBinding lease = PmbSkillItemAccess.acquire(mob, item);
					if (lease == null) return;
					pmb$throwEnderPearl(serverLevel, mob, lease.hand(), pearlAi, velocity);
					lease.authorizeAction(mob);
					PmbSkillScheduler.of(mob).markCurrentCandidateExecuted("pearl");
				}, () -> item.resources(PmbSkillScheduler.Resource.LOOK));
	}

	@Override
	public void pmb$cancelPearlSkill() { pmb$clearPearlLook(); }

	@Override
	public void pmb$claimPearlResources(PmbSkillScheduler scheduler) {
		if (pmb$pearlLookTicks > 0) scheduler.claim("pearl", PmbSkillScheduler.Resource.LOOK);
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
	private Vec3 pmb$planEnderPearlLaunch(Mob mob, LivingEntity target,
			PmbEnderPearlAiData pearlAi, boolean evasive) {
		if (!isAlive() || mob.isNoAi() || !pearlAi.isEnabled()
				|| ((PmbAiHolder) this).pmb$getAiData().shield().isVulnerable()
				|| !target.isAlive() || target.level() != level() || pmb$pearlLaunchPointInWater()
				|| (pearlAi.requireEyeSight() && !hasLineOfSight(target))) return null;
		LivingEntity currentTarget = evasive
				? ((PmbFactionMobState) mob).pmb$getFactionAvoidTarget() : mob.getTarget();
		double distance = distanceTo(target);
		if (currentTarget != target || distance > pearlAi.maxThrowRange()
				|| (!evasive && distance < pearlAi.minThrowRange())) return null;
		Vec3 start = new Vec3(getX(), getEyeY() - 0.1D, getZ());
		Vec3 targetPoint = evasive
				? pmb$evasivePearlDestination(mob, target, pearlAi.maxThrowRange())
				: target.getEyePosition();
		float maxSpeed = PMB_VANILLA_PEARL_SPEED * pearlAi.maxThrowPower();
		double angle = Math.toRadians(pearlAi.throwAngle());
		Vec3 velocity = pmb$pearlVelocity(start, targetPoint, evasive ? null : target, angle, maxSpeed);
		if (velocity == null || !Double.isFinite(velocity.lengthSqr()) || velocity.lengthSqr() <= 0.0D
				|| velocity.length() > maxSpeed * (1.0D + 1.0E-12D)) return null;
		return velocity;
	}

	@Unique
	private void pmb$throwEnderPearl(ServerLevel level, Mob mob, InteractionHand hand,
			PmbEnderPearlAiData pearlAi, Vec3 velocity) {
		ItemStack heldStack = getItemInHand(hand);
		ItemStack projectileStack = heldStack.copyWithCount(1);
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
	private boolean pmb$pearlLaunchPointInWater() {
		Vec3 point = new Vec3(getX(), getEyeY() - 0.1D, getZ());
		BlockPos pos = BlockPos.containing(point);
		var fluid = level().getFluidState(pos);
		return fluid.is(FluidTags.WATER) && point.y() < pos.getY() + fluid.getHeight(level(), pos);
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
		Vec3 movement = movingTarget == null ? Vec3.ZERO : movingTarget.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D);
		PmbEnderPearlBallistics.Solution solution = null;
		for (int i = 0; i <= 3; i++) {
			Vec3 offset = targetPoint.subtract(start);
			solution = PmbEnderPearlBallistics.solve(Math.hypot(offset.x(), offset.z()), offset.y(), angle, maxSpeed);
			if (solution.status() == PmbEnderPearlBallistics.Status.INVALID) return null;
			if (solution.status() == PmbEnderPearlBallistics.Status.POWER_LIMITED || movingTarget == null || i == 3) break;
			// Jump velocity must not be extrapolated as constant upward motion.
			targetPoint = initialTargetPoint.add(movement.scale(solution.time()));
		}
		Vec3 horizontal = targetPoint.subtract(start).multiply(1.0D, 0.0D, 1.0D);
		Vec3 horizontalDirection = horizontal.scale(1.0D / Math.hypot(horizontal.x(), horizontal.z()));
		return horizontalDirection.scale(Math.cos(angle) * solution.speed())
				.add(0.0D, Math.sin(angle) * solution.speed(), 0.0D);
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
