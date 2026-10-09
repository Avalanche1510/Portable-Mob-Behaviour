package com.pmb.ai;

import com.pmb.PortableMobBehaviour;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Ephemeral, per-administrator visualization of already-computed paths. Never plans during drawing. */
public final class PmbPathParticleDebug {
	public static final int MAX_PARTICLES_PER_VIEWER_TICK = 144;
	public static final int MAX_OBSERVATIONS_PER_VIEWER = 4;
	public static final int MAX_VANILLA_NODES = 64;
	public static final int MAX_FLIGHT_POINTS = 80;
	private static final int[] COLORS = {0x30E0FF, 0xFF8B32, 0xB965FF, 0x6DED53, 0xFF5FAB, 0xF5D64F};
	private static final Map<UUID, Map<UUID, Observation>> OBSERVERS = new HashMap<>();
	private static final Map<UUID, Cache> CACHES = new HashMap<>();
	private PmbPathParticleDebug() {}

	private record Observation(UUID mobId, int entityId, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {}
	public enum ToggleResult { ADDED, REMOVED, FULL }
	private record Route(List<Vec3> points, int color, boolean selected) {}
	private static final class Cache {
		final List<Route> routes = new ArrayList<>(4);
		int rejected;
		int colorSequence;
		int colorOffset;
		int lastPlanTick;
		int releaseTick = Integer.MIN_VALUE;
	}

	public static void initialize() { ServerTickEvents.END_SERVER_TICK.register(PmbPathParticleDebug::tick); }
	public static ToggleResult toggle(ServerPlayer player, Mob mob) {
		return toggleSelection(player.getUUID(), mob.getUUID(), mob.getId(), mob.level().dimension());
	}
	static ToggleResult toggleSelection(UUID viewer, UUID mobId, int entityId,
			net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
		Map<UUID, Observation> selected = OBSERVERS.computeIfAbsent(viewer, ignored -> new LinkedHashMap<>());
		if (selected.remove(mobId) != null) {
			if (selected.isEmpty()) OBSERVERS.remove(viewer);
			prune(mobId);
			return ToggleResult.REMOVED;
		}
		if (selected.size() >= MAX_OBSERVATIONS_PER_VIEWER) return ToggleResult.FULL;
		selected.put(mobId, new Observation(mobId, entityId, dimension));
		return ToggleResult.ADDED;
	}
	static int subscriberCount(UUID mobId) {
		int count = 0;
		for (Map<UUID, Observation> observations : OBSERVERS.values()) if (observations.containsKey(mobId)) count++;
		return count;
	}
	static int observationCount(UUID viewer) { return OBSERVERS.getOrDefault(viewer, Map.of()).size(); }
	public static void disconnect(UUID player) {
		Map<UUID, Observation> old = OBSERVERS.remove(player);
		if (old != null) for (UUID mobId : old.keySet()) prune(mobId);
	}
	private static void prune(UUID mob) {
		if (subscriberCount(mob) == 0) CACHES.remove(mob);
	}
	private static boolean enabled(Mob mob) {
		return ((PmbAiHolder) mob).pmb$getAiData().pathfinding().planner().debugPathParticles();
	}
	public static boolean watched(Mob mob) {
		return enabled(mob) && observed(mob);
	}
	public static boolean observed(Mob mob) {
		if (OBSERVERS.isEmpty()) return false;
		UUID id = mob.getUUID();
		for (Map<UUID, Observation> observations : OBSERVERS.values()) if (observations.containsKey(id)) return true;
		return false;
	}
	public static void clear(Mob mob) { if (!CACHES.isEmpty()) CACHES.remove(mob.getUUID()); }
	public static void released(Mob mob) {
		Cache cache = CACHES.get(mob.getUUID());
		if (cache != null) cache.releaseTick = mob.tickCount;
	}
	public static void begin(Mob mob) {
		if (!watched(mob)) return;
		Cache cache = new Cache();
		cache.lastPlanTick = mob.tickCount;
		cache.colorOffset = Math.floorMod(mob.getUUID().hashCode() ^ (mob.tickCount * 31), COLORS.length);
		CACHES.put(mob.getUUID(), cache);
	}
	public static void vanilla(Mob mob, Path path, boolean selected) {
		if (!watched(mob)) return;
		Cache cache = CACHES.get(mob.getUUID());
		if (cache == null || path == null || path.getNodeCount() == 0) return;
		List<Vec3> points = new ArrayList<>(Math.min(MAX_VANILLA_NODES, path.getNodeCount()));
		for (int index : sampleIndices(path.getNodeCount(), MAX_VANILLA_NODES))
			points.add(Vec3.atBottomCenterOf(path.getNode(index).asBlockPos()).add(0, .15, 0));
		cache.routes.add(new Route(List.copyOf(points), nextColor(cache), selected));
	}
	public static void wind(Mob mob, List<Vec3> simulated, boolean selected) {
		if (!watched(mob)) return;
		Cache cache = CACHES.get(mob.getUUID());
		if (cache == null || !keepWindRoute(!simulated.isEmpty(), selected, cache.rejected)) return;
		if (!selected) cache.rejected++;
		List<Vec3> points = new ArrayList<>(Math.min(MAX_FLIGHT_POINTS, simulated.size()));
		for (int index : sampleIndices(simulated.size(), MAX_FLIGHT_POINTS)) points.add(simulated.get(index));
		cache.routes.add(new Route(List.copyOf(points), nextColor(cache), selected));
	}
	static boolean keepWindRoute(boolean simulated, boolean selected, int rejected) {
		return simulated && (selected || rejected < 2);
	}
	static boolean keepObservation(boolean administrator, boolean sameDimension, boolean loaded,
			boolean alive, boolean sameEntity) {
		return administrator && sameDimension && loaded && alive && sameEntity;
	}
	static boolean traceVisible(int planTick, int releaseTick, int now, boolean grounded) {
		return releaseTick == Integer.MIN_VALUE ? now - planTick <= 80
				: now - releaseTick <= 80 && (now <= releaseTick + 2 || !grounded);
	}
	public static void selectVanilla(Mob mob) {
		Cache cache = CACHES.get(mob.getUUID());
		if (cache == null || cache.routes.isEmpty()) return;
		Route route = cache.routes.getFirst();
		cache.routes.set(0, new Route(route.points(), route.color(), true));
	}
	private static int nextColor(Cache cache) {
		return colorFor(cache.colorOffset, cache.colorSequence++);
	}
	static int colorFor(int offset, int sequence) { return COLORS[(offset + sequence * 5) % COLORS.length]; }
	static int particleBudget(int[] routeSizes) {
		int remaining = MAX_PARTICLES_PER_VIEWER_TICK;
		for (int index = 0; index < routeSizes.length && remaining > 0; index++)
			remaining -= quota(remaining, routeSizes.length - index, routeSizes[index]);
		return MAX_PARTICLES_PER_VIEWER_TICK - remaining;
	}
	private static int quota(int remaining, int routesLeft, int routeSize) {
		return Math.min(routeSize, Math.max(1, remaining / routesLeft));
	}
	static List<Integer> sampleIndices(int count, int limit) {
		if (count <= 0 || limit <= 0) return List.of();
		int size = Math.min(count, limit);
		List<Integer> indices = new ArrayList<>(size);
		for (int i = 0; i < size; i++) indices.add(size == 1 ? 0 : i * (count - 1) / (size - 1));
		return indices;
	}
	private static void tick(MinecraftServer server) {
		if (OBSERVERS.isEmpty()) return;
		for (var viewers = OBSERVERS.entrySet().iterator(); viewers.hasNext();) {
			var entry = viewers.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			int remaining = MAX_PARTICLES_PER_VIEWER_TICK;
			int observationsLeft = entry.getValue().size();
			for (var observations = entry.getValue().values().iterator(); observations.hasNext();) {
				Observation observation = observations.next();
				int budget = remaining / observationsLeft--;
				Entity entity = player == null ? null : player.level().getEntity(observation.entityId());
				Mob mob = entity instanceof Mob found ? found : null;
				if (!keepObservation(player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER),
						player != null && player.level().dimension().equals(observation.dimension()), mob != null,
						mob != null && mob.isAlive(), mob != null && mob.getUUID().equals(observation.mobId()))) {
					observations.remove();
					continue;
				}
				PmbSkillDebugSnapshot snapshot = ((PmbSchedulerHolder) mob).pmb$getSkillScheduler().lastDebugSnapshot();
				String report = snapshot == null
						? PmbSkillDebugFormatter.formatUnavailableForLog(
								BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(), mob.getId(), mob.getUUID())
						: snapshot.formatForLog(mob.tickCount);
				var target = mob.getTarget();
				PortableMobBehaviour.LOGGER.info(
						"PMB observation viewer={} entity={} tick={} pos={} velocity={} onGround={} target={} targetPos={} targetVisible={} {}",
						player.getUUID(), mob.getUUID(), mob.tickCount, mob.position(), mob.getDeltaMovement(),
						mob.onGround(), target == null ? null : target.getUUID(),
						target == null ? null : target.position(),
						target != null && mob.getSensing().hasLineOfSight(target), report
								+ "\nWIND_PLANNER: " + PmbSkillScheduler.of(mob).traversalDiagnostics(mob));
				if (budget > 0) {
					int haloCount = halo((ServerLevel) player.level(), player, mob, budget);
					remaining -= haloCount;
					budget -= haloCount;
				}
				if (!enabled(mob)) CACHES.remove(observation.mobId());
				Cache cache = CACHES.get(observation.mobId());
				if (cache != null && !traceVisible(cache.lastPlanTick, cache.releaseTick,
						mob.tickCount, mob.onGround())) {
					CACHES.remove(observation.mobId());
					cache = null;
				}
				if (cache != null && budget > 0) remaining -= budget - draw(
						(ServerLevel) player.level(), player, cache, budget);
			}
			if (entry.getValue().isEmpty()) viewers.remove();
		}
		CACHES.keySet().removeIf(id -> subscriberCount(id) == 0);
	}
	private static int halo(ServerLevel level, ServerPlayer player, Mob mob, int budget) {
		int count = Math.min(8, budget);
		DustParticleOptions dust = new DustParticleOptions(0xFFE96B, 1.3F);
		for (int index = 0; index < count; index++) {
			double angle = index * Math.PI * 2.0D / 8.0D;
			level.sendParticles(player, dust, false, false,
					mob.getX() + Math.cos(angle) * (mob.getBbWidth() * .6D + .25D),
					mob.getY() + mob.getBbHeight() * .65D,
					mob.getZ() + Math.sin(angle) * (mob.getBbWidth() * .6D + .25D), 1, 0, 0, 0, 0);
		}
		return count;
	}
	private static int draw(ServerLevel level, ServerPlayer player, Cache cache, int remaining) {
		int routeCount = cache.routes.size();
		for (int routeIndex = 0; routeIndex < routeCount && remaining > 0; routeIndex++) {
			Route route = cache.routes.get(routeIndex);
			int quota = quota(remaining, routeCount - routeIndex, route.points().size());
			DustParticleOptions dust = new DustParticleOptions(route.color(), route.selected() ? 1.4F : .8F);
			for (int index : sampleIndices(route.points().size(), quota)) {
				Vec3 point = route.points().get(index);
				level.sendParticles(player, dust, false, false, point.x(), point.y(), point.z(),
						1, 0, 0, 0, 0);
				remaining--;
			}
		}
		return remaining;
	}
}
