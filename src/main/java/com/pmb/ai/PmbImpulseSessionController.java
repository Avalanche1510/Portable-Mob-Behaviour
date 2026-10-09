package com.pmb.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Owns only PMB-created impulse contexts and never clears a later external replacement. */
public final class PmbImpulseSessionController {
	public enum State { INACTIVE, ARMED, AIRBORNE }
	private String source = "inactive";
	private State state = State.INACTIVE;
	private int armTick = Integer.MIN_VALUE;
	private int deadline = Integer.MIN_VALUE;
	private boolean wasAirborne;
	private Vec3 ownedImpactPos;
	private String lastReason = "inactive";
	private int landedTick = Integer.MIN_VALUE;
	private String landedSource = "fall_rescue";

	public void armAppliedImpulse(Mob mob, String source, int deadlineTicks) {
		this.source = source;
		state = State.ARMED;
		armTick = mob.tickCount;
		deadline = mob.tickCount + Math.max(1, deadlineTicks);
		wasAirborne = !mob.onGround();
		ownedImpactPos = mob.currentImpulseImpactPos;
		lastReason = "armed";
	}

	public void tick(Mob mob) {
		if (state == State.INACTIVE) return;
		if (!mob.onGround()) {
			wasAirborne = true;
			state = State.AIRBORNE;
			return;
		}
		if (wasAirborne) {
			landedTick = mob.tickCount;
			landedSource = source;
			boolean owned = ownsCurrent(ownedImpactPos, mob.currentImpulseImpactPos);
			if (owned) mob.resetCurrentImpulseContext();
			clear(owned ? "landed" : "landed_external_context_preserved");
			return;
		}
		if (mob.tickCount > deadline) {
			if (ownsCurrent(ownedImpactPos, mob.currentImpulseImpactPos)) mob.resetCurrentImpulseContext();
			clear("launch_failed");
		}
	}

	public void clear(String reason) {
		source = "inactive";
		state = State.INACTIVE;
		armTick = Integer.MIN_VALUE;
		deadline = Integer.MIN_VALUE;
		wasAirborne = false;
		ownedImpactPos = null;
		lastReason = reason;
	}

	public State state() { return state; }
	public String source() { return source; }
	public int armTick() { return armTick; }
	public int deadline() { return deadline; }
	public boolean wasAirborne() { return wasAirborne; }
	public Vec3 ownedImpactPos() { return ownedImpactPos; }
	public String lastReason() { return lastReason; }
	public boolean landedThisTick(Mob mob) { return landedTick == mob.tickCount; }
	public String landedSource() { return landedSource; }

	static boolean ownsCurrent(Vec3 owned, Vec3 current) {
		return owned != null && owned == current;
	}
}
