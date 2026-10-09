package com.pmb.ai;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Fair server-level planning budget. Exhaustion postpones work and is never an unreachable result. */
public final class PmbPlanningBudget {
	private static final int UNITS_PER_TICK = 64;
	private static final int STALE_REQUEST_TICKS = 40;
	private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
	private PmbPlanningBudget() {}
	private record Request(int units, long lastSeenTick) {}
	private static final class State {
		long tick = Long.MIN_VALUE;
		int remaining;
		final java.util.Set<UUID> scheduled = new java.util.HashSet<>();
		final java.util.LinkedHashMap<UUID, Request> waiting = new java.util.LinkedHashMap<>();
		final java.util.Map<UUID, Integer> granted = new java.util.HashMap<>();
	}
	public static synchronized boolean acquire(ServerLevel level, Mob mob, int units) {
		if (units < 1 || units > UNITS_PER_TICK) throw new IllegalArgumentException("planning units out of range: " + units);
		State state = STATES.computeIfAbsent(level.getServer(), ignored -> new State());
		long tick = level.getServer().getTickCount();
		if (state.tick != tick) {
			state.tick = tick;
			state.remaining = UNITS_PER_TICK;
			state.scheduled.clear();
			state.granted.clear();
			state.waiting.entrySet().removeIf(entry -> tick - entry.getValue().lastSeenTick() > STALE_REQUEST_TICKS);
			var iterator = state.waiting.entrySet().iterator();
			while (iterator.hasNext()) {
				var entry = iterator.next();
				int requested = entry.getValue().units();
				if (requested > state.remaining) break;
				state.granted.put(entry.getKey(), requested);
				state.remaining -= requested;
				iterator.remove();
			}
		}
		UUID id = mob.getUUID();
		if (state.scheduled.contains(id)) return false;
		Integer granted = state.granted.remove(id);
		if (granted != null) {
			if (units > granted) {
				state.waiting.put(id, new Request(units, tick));
				return false;
			}
			state.scheduled.add(id);
			return true;
		}
		if (!state.waiting.isEmpty() || units > state.remaining) {
			state.waiting.put(id, new Request(units, tick));
			return false;
		}
		state.scheduled.add(id);
		state.remaining -= units;
		return true;
	}
}
