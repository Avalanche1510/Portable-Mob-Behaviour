package com.pmb.ai;

import java.util.List;
import java.util.UUID;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Runtime-only airborne steering session selected from the configured source list. */
public final class PmbAirTrackingController {
	private LivingEntity windTarget;
	private boolean windEvasive;
	private boolean windLatched;
	private boolean windWasAirborne;
	private String source;
	private LivingEntity target;
	private boolean evasive;
	private int remaining;
	private boolean sightEstablished;
	private boolean lookDenied;
	private String clearReason = "inactive";

	public void latchWindBounce(Mob mob, LivingEntity target, boolean evasive) {
		PmbAirTrackingAiData ai = ((PmbAiHolder) mob).pmb$getAiData().airTracking();
		if (!ai.isEnabled() || !ai.activationSkills().contains("wind_charge") || ai.trackDurationTicks() == 0) return;
		// A new successful bounce replaces the former latch before the next control tick. The
		// controller deliberately waits for the Mob to leave the ground: the bounce action is
		// committed after this tick's tracking pass, and clearing here would lose its ascent.
		windTarget = target; windEvasive = evasive; windLatched = true; windWasAirborne = false;
		source = null; this.target = null; remaining = 0; sightEstablished = false; lookDenied = false;
		clearReason = "wind_latched_waiting_next_tick";
	}
	public void clear(String reason) {
		windTarget = null; windLatched = false; windWasAirborne = false; source = null; target = null; remaining = 0;
		sightEstablished = false; lookDenied = false; clearReason = reason;
	}
	public int remaining() { return source == null ? 0 : remaining; }
	public String source() { return source == null ? windLatched ? "wind_charge" : "inactive" : source; }
	public String clearReason() { return clearReason; }
	public boolean isWindLatched() { return windLatched; }
	public boolean isLookDenied() { return lookDenied; }
	public String trackingState() {
		if (source == null) return windLatched ? "wind_latched_waiting_next_tick" : "inactive";
		String duration = remaining < 0 ? "infinite" : "remaining=" + remaining;
		return lookDenied ? duration + ",look_optional_denied" : duration;
	}

	public void tick(Mob mob, PmbSkillScheduler scheduler) {
		PmbAirTrackingAiData ai = ((PmbAiHolder) mob).pmb$getAiData().airTracking();
		if (!ai.isEnabled() || !mob.isAlive() || mob.isNoAi()) {
			clear("disabled_or_blocked"); return;
		}
		if (mob.isInWater()) {
			clear("water"); return;
		}
		if (mob.onGround()) {
			String groundState = groundLifecycleState(windLatched, windWasAirborne, source != null, validWind(mob));
			if (groundState.equals("wind_latched_waiting_next_tick")) { clearReason = groundState; return; }
			clear(groundState); return;
		}
		if (windLatched) windWasAirborne = true;
		Source next = select(mob, scheduler, ai);
		if (next == null) { clear(windLatched && !validWind(mob) ? "target_invalid" : "source_invalid"); return; }
		if (!sameSession(next)) {
			source = next.id; target = next.target; evasive = next.evasive; remaining = ai.trackDurationTicks();
			sightEstablished = false; clearReason = "active";
		}
		if (remaining == 0) { clear("duration_elapsed"); return; }
		if (!sightEstablished && ai.requireEyeSight()) {
			if (!mob.getSensing().hasLineOfSight(target)) { clear("initial_sight_lost"); return; }
			sightEstablished = true;
		} else sightEstablished = true;
		if (scheduler.priorityTier("air_tracking") < 0) { clear("priority_omitted"); return; }
		if (!scheduler.mayOverrideVanillaNavigation("air_tracking")) { clear("navigation_denied"); return; }
		scheduler.maintainPhase("air_tracking", "airborne_tracking",
				() -> validCurrent(mob, scheduler, ai), () -> clear("preempt_or_invalid"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.NAVIGATION}, PmbSkillScheduler.Resource.LOOK);
		if (!scheduler.ownsResource("air_tracking", PmbSkillScheduler.Resource.NAVIGATION)) return;
		lookDenied = !scheduler.ownsResource("air_tracking", PmbSkillScheduler.Resource.LOOK);
		if (!lookDenied) {
			mob.lookAt(EntityAnchorArgument.Anchor.EYES, target.getEyePosition());
			mob.getLookControl().setLookAt(target, 180.0F, 180.0F);
		}
		scheduler.movement().submit(mob, "air_tracking", PmbMovementController.Type.VELOCITY_MODIFIER,
				PmbMovementController.Tier.ACTIVE, PmbSkillScheduler.Category.THROW, 0,
				() -> validCurrent(mob, scheduler, ai)
						&& scheduler.ownsResource("air_tracking", PmbSkillScheduler.Resource.NAVIGATION),
				() -> steer(mob, ai));
		if (remaining > 0) remaining--;
	}
	private boolean validCurrent(Mob mob, PmbSkillScheduler scheduler, PmbAirTrackingAiData ai) {
		// A positive timer is decremented after this tick's intent is submitted. Keep zero valid
		// until MoveControl consumes that last submitted intent; the next scheduler tick clears it.
		if (!ai.isEnabled() || mob.onGround() || mob.isInWater() || target == null || !target.isAlive()
				|| target.level() != mob.level() || remaining == 0) return false;
		Source selected = select(mob, scheduler, ai);
		return selected != null && sameSession(selected);
	}
	private Source select(Mob mob, PmbSkillScheduler scheduler, PmbAirTrackingAiData ai) {
		for (String id : ai.activationSkills()) {
			if (id.equals("wind_charge") && windLatched && validWind(mob)) return new Source(id, windTarget, windEvasive);
			if (id.equals("mace") && validMace(mob)) return new Source(id, mob.getTarget(), false);
			if (!id.equals("wind_charge") && !id.equals("mace") && generic(mob, scheduler, id))
				return new Source(id, mob.getTarget(), false);
		}
		return null;
	}
	private boolean validWind(Mob mob) {
		// Bounce ownership is intentionally independent of later vanilla/Faction target changes.
		// Only the captured LivingEntity's own lifecycle may invalidate this latch.
		return windTarget != null && windTarget.isAlive() && windTarget.level() == mob.level();
	}
	private boolean validMace(Mob mob) {
		PmbMaceAiData mace = ((PmbAiHolder) mob).pmb$getAiData().mace();
		LivingEntity current = mob.getTarget();
		if (!mace.isEnabled() || current == null || !current.isAlive() || !mob.canAttack(current)
				|| !PmbMovementController.hasCurrentAuthority(mob, current, false)
				|| mob.onGround() || mob.getDeltaMovement().y() >= 0.0D) return false;
		double distanceSquared = mob.distanceToSqr(current);
		double followRange = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
		if (!withinMaceAirRange(distanceSquared, followRange)) return false;
		return PmbSkillItemAccess.resolveForRequiredHand(mob, mace.fetchSource(), List.of(InteractionHand.MAIN_HAND),
				stack -> stack.is(Items.MACE), InteractionHand.MAIN_HAND) != null;
	}
	private boolean generic(Mob mob, PmbSkillScheduler scheduler, String id) {
		LivingEntity current = mob.getTarget();
		return current != null && current.isAlive() && PmbMovementController.hasCurrentAuthority(mob, current, false)
				&& scheduler.activePhases().values().stream()
				.anyMatch(phase -> PmbSkillScheduler.skillIdForOwner(phase.owner()).equals(id));
	}
	private void steer(Mob mob, PmbAirTrackingAiData ai) {
		Vec3 delta = mob.getDeltaMovement();
		Vec3 horizontal = target.position().subtract(mob.position()).multiply(1, 0, 1);
		if (horizontal.lengthSqr() < 1.0E-6D) return;
		Vec3 direction = horizontal.normalize();
		if (evasive) direction = direction.scale(-1);
		Vec3 next = new Vec3(delta.x() + direction.x() * ai.trackAcceleration(), delta.y(),
				delta.z() + direction.z() * ai.trackAcceleration());
		Vec3 nextHorizontal = new Vec3(next.x(), 0, next.z());
		if (nextHorizontal.length() > ai.trackMaxHorizontalSpeed())
			next = nextHorizontal.normalize().scale(ai.trackMaxHorizontalSpeed()).add(0, delta.y(), 0);
		mob.setDeltaMovement(next);
	}
	private boolean sameSession(Source next) {
		return target != null && sameSession(source, target.getUUID(), evasive,
				next.id, next.target.getUUID(), next.evasive);
	}
	static boolean sameSession(String activeSource, UUID activeTarget, boolean activeEvasive,
			String nextSource, UUID nextTarget, boolean nextEvasive) {
		return activeSource != null && activeSource.equals(nextSource) && activeTarget.equals(nextTarget)
				&& activeEvasive == nextEvasive;
	}
	static boolean withinMaceAirRange(double distanceSquared, double followRange) {
		return distanceSquared <= followRange * followRange;
	}
	/** Package-visible pure seam for the bounce-to-air lifecycle regression harness. */
	static String groundLifecycleState(boolean latchedWind, boolean windWasAirborne, boolean activeSource,
			boolean validWindTarget) {
		if (latchedWind && !windWasAirborne)
			return validWindTarget ? "wind_latched_waiting_next_tick" : "target_invalid";
		return activeSource || windWasAirborne ? "landed" : "not_airborne";
	}
	private record Source(String id, LivingEntity target, boolean evasive) {}
}
