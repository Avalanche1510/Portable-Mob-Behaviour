package com.pmb.ai;

import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Runtime-only pathfinding controller installed only by a successfully committed action. */
public final class PmbAirTrackingController {
	public enum TargetType { ENTITY, POSITION }
	public record AirTrackingContext(String owner, TargetType targetType, UUID entityTarget, Vec3 positionTarget,
			int validUntil, boolean stopOnGround, boolean evasive) {}
	private AirTrackingContext context;
	private LivingEntity entity;
	private String clearReason = "inactive";
	private int groundGraceUntil;
	private int steeringDelayRemaining;
	private boolean wasAirborne;

	public void commitEntity(Mob mob, String owner, LivingEntity target, boolean evasive) {
		if (target == null) return;
		PmbAirTrackingAiData data = ((PmbAiHolder) mob).pmb$getAiData().pathfinding().airTracking();
		if (data.trackDurationTicks() == 0) return;
		int until = data.trackDurationTicks() < 0 ? -1 : mob.tickCount + data.trackDurationTicks();
		context = new AirTrackingContext(owner, TargetType.ENTITY, target.getUUID(), null, until, true, evasive);
		entity = target;
		groundGraceUntil = mob.tickCount + 2;
		steeringDelayRemaining = 0;
		wasAirborne = !mob.onGround();
		clearReason = "committed";
	}
	public void commitPosition(Mob mob, String owner, Vec3 target) {
		commitPosition(mob, owner, target, 0);
	}
	public void commitPosition(Mob mob, String owner, Vec3 target, int steeringDelay) {
		if (target == null) return;
		PmbAirTrackingAiData data = ((PmbAiHolder) mob).pmb$getAiData().pathfinding().airTracking();
		if (data.trackDurationTicks() == 0) return;
		int until = data.trackDurationTicks() < 0 ? -1 : mob.tickCount + data.trackDurationTicks();
		context = new AirTrackingContext(owner, TargetType.POSITION, null, target, until, true, false);
		entity = null;
		groundGraceUntil = mob.tickCount + 2;
		steeringDelayRemaining = Math.max(0, steeringDelay);
		wasAirborne = !mob.onGround();
		clearReason = "committed";
	}
	public void latchWindBounce(Mob mob, LivingEntity target, boolean evasive) {
		commitEntity(mob, "wind_charge", target, evasive);
	}
	public void clear(String reason) {
		context = null; entity = null; wasAirborne = false;
		steeringDelayRemaining = 0; clearReason = reason;
	}
	public AirTrackingContext context() { return context; }
	public int remaining(Mob mob) { return context == null || context.validUntil() < 0 ? -1 : Math.max(0, context.validUntil() - mob.tickCount); }
	public int remaining() { return context == null ? 0 : context.validUntil(); }
	public String source() { return context == null ? "inactive" : context.owner(); }
	public String clearReason() { return clearReason; }
	public boolean isWindLatched() { return context != null && context.owner().equals("wind_charge"); }
	public boolean isLookDenied() { return false; }
	public String trackingState() { return context == null ? "inactive" : context.targetType().name().toLowerCase(); }

	public void tick(Mob mob, PmbSkillScheduler scheduler) {
		if (context == null) return;
		if (!mob.isAlive() || mob.isNoAi() || mob.isInWater()) { clear("blocked"); return; }
		if (!mob.onGround()) wasAirborne = true;
		if (context.stopOnGround() && mob.onGround() && wasAirborne) { clear("landed"); return; }
		if (context.stopOnGround() && mob.onGround() && mob.tickCount > groundGraceUntil) {
			clear("launch_failed");
			return;
		}
		if (context.validUntil() >= 0 && mob.tickCount >= context.validUntil()) { clear("duration_elapsed"); return; }
		Vec3 target = target(mob);
		if (target == null) { clear("target_invalid"); return; }
		PmbAirTrackingAiData data = ((PmbAiHolder) mob).pmb$getAiData().pathfinding().airTracking();
		Vec3 finalTarget = target;
		// A position charge is committed at Mob.tick TAIL. Count every following
		// tail, including grounded pre-impact tails; the resulting intent can only
		// be applied at the next serverAiStep before that tick's displacement.
		if (steeringDelayRemaining > 0) steeringDelayRemaining--;
		else scheduler.movement().submit(mob, "air_tracking", PmbMovementController.Type.VELOCITY_MODIFIER,
				PmbMovementController.Tier.ACTIVE, PmbSkillScheduler.Category.THROW, 0,
				() -> context != null && !mob.onGround(), () -> steer(mob, data, finalTarget));
		if (scheduler.canClaim("air_tracking", PmbSkillScheduler.Resource.LOOK)) {
			Vec3 eyes = context.targetType() == TargetType.ENTITY && entity != null
					? entity.getEyePosition() : target;
			mob.lookAt(EntityAnchorArgument.Anchor.EYES, eyes);
			mob.setYHeadRot(mob.getYRot());
			mob.setYBodyRot(mob.getYRot());
			if (context.targetType() == TargetType.ENTITY && entity != null)
				mob.getLookControl().setLookAt(entity, 180, 180);
			else mob.getLookControl().setLookAt(eyes.x(), eyes.y(), eyes.z(), 180, 180);
		}
	}
	private Vec3 target(Mob mob) {
		if (context.targetType() == TargetType.POSITION) return context.positionTarget();
		if (entity == null || !entity.isAlive() || entity.level() != mob.level()
				|| !entity.getUUID().equals(context.entityTarget())) return null;
		return entity.position();
	}
	private void steer(Mob mob, PmbAirTrackingAiData data, Vec3 target) {
		mob.setDeltaMovement(steeredVelocity(mob.position(), mob.getDeltaMovement(), target, data,
				context.evasive()));
	}
	/** Shared with wind-edge feasibility; runtime and proof use identical horizontal steering. */
	static Vec3 steeredVelocity(Vec3 position, Vec3 delta, Vec3 target,
			PmbAirTrackingAiData data, boolean evasive) {
		Vec3 horizontal = target.subtract(position).multiply(1, 0, 1);
		if (horizontal.lengthSqr() < 1.0E-6) return delta;
		Vec3 direction = horizontal.normalize();
		if (evasive) direction = direction.scale(-1);
		Vec3 next = new Vec3(delta.x() + direction.x() * data.trackAcceleration(), delta.y(),
				delta.z() + direction.z() * data.trackAcceleration());
		Vec3 flat = new Vec3(next.x(), 0, next.z());
		if (flat.length() > data.trackMaxHorizontalSpeed())
			next = flat.normalize().scale(data.trackMaxHorizontalSpeed()).add(0, delta.y(), 0);
		return next;
	}

	static boolean sameSession(String activeSource, UUID activeTarget, boolean activeEvasive,
			String nextSource, UUID nextTarget, boolean nextEvasive) {
		return activeSource != null && activeSource.equals(nextSource) && activeTarget.equals(nextTarget)
				&& activeEvasive == nextEvasive;
	}
	static boolean withinMaceAirRange(double distanceSquared, double followRange) {
		return distanceSquared <= followRange * followRange;
	}
	static String groundLifecycleState(boolean latchedWind, boolean windWasAirborne, boolean activeSource,
			boolean validWindTarget) {
		if (latchedWind && !windWasAirborne) return validWindTarget ? "wind_latched_waiting_next_tick" : "target_invalid";
		return activeSource || windWasAirborne ? "landed" : "not_airborne";
	}
}
