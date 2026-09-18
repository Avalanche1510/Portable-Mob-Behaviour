package com.pmb.mixin;

import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbShieldAiData;
import com.pmb.ai.PmbWindChargeAiData;
import com.pmb.ai.PmbSkillHooks;
import com.pmb.ai.PmbSkillItemAccess;
import com.pmb.ai.PmbSkillScheduler;
import com.pmb.ai.PmbSkillTiming;
import java.util.List;
import com.pmb.faction.PmbFactionMobState;
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
public abstract class PmbMobWindChargeMixin extends LivingEntity implements PmbSkillHooks.Wind {
	@Unique
	private static final int PMB_BOUNCE_POSE_TICKS = 4;
	@Unique
	private static final int PMB_THROW_LOOK_TICKS = 4;

	@Unique
	private LivingEntity pmb$bounceTarget;
	@Unique
	private int pmb$bouncePoseTicks;
	@Unique
	private boolean pmb$bounceEvasive;
	@Unique
	private LivingEntity pmb$throwLookTarget;
	@Unique
	private int pmb$throwLookTicks;
	@Unique
	private boolean pmb$bounceAwaitingLanding;
	@Unique
	private boolean pmb$bounceWasAirborne;

	protected PmbMobWindChargeMixin(EntityType<? extends LivingEntity> entityType, Level level) {
		super(entityType, level);
	}

	@Override
	public void pmb$tickWindSkill() {
		if (!(level() instanceof ServerLevel serverLevel)) {
			return;
		}

		Mob mob = (Mob) (Object) this;
		PmbWindChargeAiData windChargeAi = ((PmbAiHolder) this).pmb$getAiData().windCharge();
		if (!isAlive()) {
			pmb$clearBounceState("death");
			pmb$clearThrowLook();
			pmb$clearBounceLanding();
			return;
		}
		pmb$tickBounceLandingSound(serverLevel);
		windChargeAi.tickCooldowns();

		PmbShieldAiData shieldAi = ((PmbAiHolder) this).pmb$getAiData().shield();
		if (!windChargeAi.isEnabled() || mob.isNoAi() || shieldAi.isVulnerable()) {
			pmb$clearBounceState("disabled_or_blocked");
			pmb$clearThrowLook();
			return;
		}
		if (pmb$bouncePoseTicks > 0) {
			pmb$bouncePoseTicks--;
			PmbSkillScheduler scheduler = PmbSkillScheduler.of(mob);
			if (scheduler.ownsResource("wind", PmbSkillScheduler.Resource.LOOK)
					|| !scheduler.ownsResource("air_tracking", PmbSkillScheduler.Resource.LOOK))
				pmb$lookStraightDown(mob);
		}
		// A throw started during bounce owns LOOK for four ticks and is applied after the
		// bounce look, so it visibly overlays that look while bounce flight continues.
		pmb$tickThrowLook(mob);
		LivingEntity evasiveTarget = ((PmbFactionMobState) mob).pmb$getFactionAvoidTarget();
		boolean evasive = evasiveTarget != null && evasiveTarget.isAlive()
				&& evasiveTarget.level() == level();
		LivingEntity target = evasive ? evasiveTarget : mob.getTarget();
		if (target == null || !target.isAlive()) {
			return;
		}
		PmbSkillItemAccess.Resolved item = PmbSkillItemAccess.resolvePreferred(mob, windChargeAi.fetchSource(),
				List.of(InteractionHand.MAIN_HAND, InteractionHand.OFF_HAND), stack -> stack.is(Items.WIND_CHARGE),
				windChargeAi.preferredHand(), "wind");
		if (item == null) return;

		double distanceSquared = distanceToSqr(target);
		if (onGround() && distanceSquared <= windChargeAi.bounceRange() * windChargeAi.bounceRange()
				&& windChargeAi.canCheckBounce()) {
			boolean throwEligible = distanceSquared <= windChargeAi.throwRange() * windChargeAi.throwRange()
					&& hasLineOfSight(target) && windChargeAi.canCheckThrow();
			boolean[] bouncePassed = {false};
			PmbSkillScheduler.of(mob).offerDynamicPlannedResult("wind", "bounce", PmbSkillScheduler.Category.THROW, 10,
					() -> {
						windChargeAi.resetBounceCooldown(getRandom());
						bouncePassed[0] = PmbSkillTiming.passesChance(getRandom(), windChargeAi.bounceChance());
						return bouncePassed[0];
					}, () -> pmb$isCurrentWindTarget(mob, target, evasive, windChargeAi) && onGround()
							&& distanceToSqr(target) <= windChargeAi.bounceRange() * windChargeAi.bounceRange()
							&& PmbSkillItemAccess.stillMatches(mob, item),
					() -> pmb$performWindChargeBounce(serverLevel, mob, target, windChargeAi, evasive, item),
					() -> item.resources(PmbSkillScheduler.Resource.LOOK));
			if (throwEligible) {
				PmbSkillScheduler.of(mob).offerDynamicPlannedResult("wind", "throw", PmbSkillScheduler.Category.THROW, 10,
						() -> {
							if (bouncePassed[0]) return false;
							windChargeAi.resetThrowCooldown(getRandom());
							return PmbSkillTiming.passesChance(getRandom(), windChargeAi.throwChance());
						}, () -> pmb$isCurrentWindTarget(mob, target, evasive, windChargeAi) && hasLineOfSight(target)
								&& distanceToSqr(target) <= windChargeAi.throwRange() * windChargeAi.throwRange()
								&& PmbSkillItemAccess.stillMatches(mob, item),
						() -> pmb$throwWindCharge(serverLevel, target, windChargeAi, item),
						() -> item.resources(PmbSkillScheduler.Resource.LOOK));
			}
			return;
		}

		if (distanceSquared <= windChargeAi.throwRange() * windChargeAi.throwRange()
				&& hasLineOfSight(target) && windChargeAi.canCheckThrow()) {
			PmbSkillScheduler.of(mob).offerDynamicPlannedResult("wind", "throw", PmbSkillScheduler.Category.THROW, 10,
					() -> {
						windChargeAi.resetThrowCooldown(getRandom());
						return PmbSkillTiming.passesChance(getRandom(), windChargeAi.throwChance());
					}, () -> pmb$isCurrentWindTarget(mob, target, evasive, windChargeAi) && hasLineOfSight(target)
							&& distanceToSqr(target) <= windChargeAi.throwRange() * windChargeAi.throwRange()
							&& PmbSkillItemAccess.stillMatches(mob, item),
					() -> pmb$throwWindCharge(serverLevel, target, windChargeAi, item),
					() -> item.resources(PmbSkillScheduler.Resource.LOOK));
		}
	}

	@Override
	public void pmb$cancelWindSkill() { pmb$clearBounceState("preempt_or_strategy"); pmb$clearThrowLook(); }

	@Override
	public void pmb$claimWindResources(PmbSkillScheduler scheduler) {
		if (pmb$throwLookTicks > 0) scheduler.maintainPhase("wind", "throw_follow_through",
				this::pmb$clearThrowLook, new PmbSkillScheduler.Resource[0], PmbSkillScheduler.Resource.LOOK);
	}

	@Unique
	private boolean pmb$isCurrentWindTarget(Mob mob, LivingEntity target, boolean evasive,
			PmbWindChargeAiData windChargeAi) {
		if (!isAlive() || mob.isNoAi() || !windChargeAi.isEnabled()
				|| ((PmbAiHolder) this).pmb$getAiData().shield().isVulnerable()
				|| !target.isAlive() || target.level() != level()) return false;
		return evasive ? ((PmbFactionMobState) mob).pmb$getFactionAvoidTarget() == target : mob.getTarget() == target;
	}

	@Inject(method = "doHurtTarget", at = @At("HEAD"))
	private void pmb$faceBounceTargetWhenAttacking(ServerLevel level, Entity target,
			CallbackInfoReturnable<Boolean> info) {
		if (pmb$bounceTarget != null && !pmb$bounceEvasive && target instanceof LivingEntity livingTarget) {
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
	private PmbSkillScheduler.CommitResult pmb$throwWindCharge(ServerLevel level, LivingEntity target,
			PmbWindChargeAiData windChargeAi,
			PmbSkillItemAccess.Resolved item) {
		Mob mob = (Mob) (Object) this;
		PmbSkillItemAccess.ActionBinding lease = PmbSkillItemAccess.acquire(mob, item);
		if (lease == null) return PmbSkillScheduler.CommitResult.FAILED;
		InteractionHand hand = lease.hand();
		pmb$faceBounceTarget(mob, target);
		pmb$throwLookTarget = target;
		pmb$throwLookTicks = PMB_THROW_LOOK_TICKS;

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
		lease.authorizeAction(mob);
		PmbSkillScheduler.of(mob).markCurrentCandidateExecuted("wind");
		return PmbSkillScheduler.CommitResult.COMMITTED;
	}

	@Unique
	private PmbSkillScheduler.CommitResult pmb$performWindChargeBounce(ServerLevel level, Mob mob, LivingEntity target,
			PmbWindChargeAiData windChargeAi, boolean evasive, PmbSkillItemAccess.Resolved item) {
		PmbSkillItemAccess.ActionBinding lease = PmbSkillItemAccess.acquire(mob, item);
		if (lease == null) return PmbSkillScheduler.CommitResult.FAILED;
		InteractionHand hand = lease.hand();

		pmb$bounceTarget = target;
		pmb$bouncePoseTicks = PMB_BOUNCE_POSE_TICKS;
		pmb$bounceEvasive = evasive;
		pmb$clearThrowLook();
		pmb$bounceAwaitingLanding = true;
		pmb$bounceWasAirborne = false;
		PmbSkillScheduler.of(mob).airTracking().latchWindBounce(mob, target, evasive);
		pmb$lookStraightDown(mob);
		swing(hand, true);
		jumpFromGround();
		PmbSkillScheduler.of(mob).markCurrentCandidateExecuted("wind");

		Vec3 start = new Vec3(getX(), getY() + 0.15D, getZ());
		setIgnoreFallDamageFromCurrentImpulse(true, start);
		WindCharge projectile = new WindCharge(level, start.x(), start.y(), start.z(), Vec3.ZERO);
		projectile.setOwner(this);
		projectile.shoot(0.0D, -1.0D, 0.0D, 1.5F, 0.0F);
		level.addFreshEntity(projectile);
		pmb$consumeWindCharge(hand, windChargeAi);
		pmb$playWindChargeThrowSound(level);
		lease.authorizeAction(mob);
		return PmbSkillScheduler.CommitResult.COMMITTED;
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
	private void pmb$tickThrowLook(Mob mob) {
		if (pmb$throwLookTicks <= 0) {
			pmb$clearThrowLook();
			return;
		}
		LivingEntity target = pmb$throwLookTarget;
		if (target == null || !target.isAlive() || target.level() != level()) {
			pmb$clearThrowLook();
			return;
		}
		pmb$throwLookTicks--;
		if (PmbSkillScheduler.of(mob).ownsResource("wind", PmbSkillScheduler.Resource.LOOK))
			pmb$faceBounceTarget(mob, target);
	}

	@Unique
	private void pmb$tickBounceLandingSound(ServerLevel level) {
		if (!pmb$bounceAwaitingLanding) {
			return;
		}
		if (!onGround()) {
			pmb$bounceWasAirborne = true;
			return;
		}
		if (!pmb$bounceWasAirborne) {
			return;
		}
		level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_SMALL_FALL,
				SoundSource.NEUTRAL, 1.0F, 1.0F);
		pmb$clearBounceLanding();
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
	private void pmb$clearBounceState(String reason) {
		pmb$bounceTarget = null;
		pmb$bouncePoseTicks = 0;
		pmb$bounceEvasive = false;
	}

	@Unique
	private void pmb$clearThrowLook() {
		pmb$throwLookTarget = null;
		pmb$throwLookTicks = 0;
	}

	@Unique
	private void pmb$clearBounceLanding() {
		pmb$bounceAwaitingLanding = false;
		pmb$bounceWasAirborne = false;
		pmb$bounceTarget = null;
		pmb$bounceEvasive = false;
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
