package com.pmb.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.tags.FluidTags;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Bounded, loaded-chunk-only local connector search. This is not a global path graph. */
public final class PmbLocalEdgePlanner {
	private PmbLocalEdgePlanner() {}
	enum EndpointKind { LANDING, AIR_CONTACT }
	public record Edge(Vec3 takeoff, Vec3 landing, EndpointKind kind, boolean rim,
			int flightTicks, Vec3 safeDescent, int steeringDelay) {
		public Edge(Vec3 takeoff, Vec3 landing) {
			this(takeoff, landing, EndpointKind.LANDING, false, 0, landing, 0);
		}
		public Edge(Vec3 takeoff, Vec3 landing, boolean airIntercept) {
			this(takeoff, landing, airIntercept ? EndpointKind.AIR_CONTACT : EndpointKind.LANDING,
					false, 0, landing, 0);
		}
		public Edge(Vec3 takeoff, Vec3 landing, boolean airIntercept, boolean rim) {
			this(takeoff, landing, airIntercept ? EndpointKind.AIR_CONTACT : EndpointKind.LANDING,
					rim, 0, landing, 0);
		}
		Edge(Vec3 takeoff, Vec3 contact, Vec3 safeDescent) {
			this(takeoff, contact, EndpointKind.AIR_CONTACT, false, 0, safeDescent, 0);
		}
		public boolean airIntercept() { return kind == EndpointKind.AIR_CONTACT; }
		Edge withFlight(FlightResult result) {
			return new Edge(takeoff, landing, kind, rim, result.ticks(), safeDescent, result.delay());
		}
	}
	private record FlightResult(int ticks, int delay, List<Vec3> trace) {}
	public interface WindTraceSink { void record(List<Vec3> simulated, boolean selected); }
	/** Only cursors survive retries; terrain and candidates are always read again. */
	static final class WindSearchProgress {
		private final int[] cursors = new int[2];
		int cursor(boolean rim, int size) { return size == 0 ? 0 : Math.floorMod(cursors[rim ? 1 : 0], size); }
		int next(boolean rim, int size) {
			int index = cursor(rim, size);
			cursors[rim ? 1 : 0] = size == 0 ? 0 : (index + 1) % size;
			return index;
		}
		void reset() { cursors[0] = cursors[1] = 0; }
	}
	static final class WindCandidateGate {
		private final Predicate<Edge> accepted;
		private final Consumer<String> diagnostic;
		private int trajectoryChecks;
		private int candidateIndex = -1;
		WindCandidateGate(Predicate<Edge> accepted, Consumer<String> diagnostic) {
			this.accepted = accepted; this.diagnostic = diagnostic;
		}
		WindCandidateGate(Predicate<Edge> accepted) { this(accepted, unused -> {}); }
		boolean reserveTrajectory() {
			if (++trajectoryChecks <= 10) return true;
			diagnostic.accept("trajectory_budget"); return false;
		}
		boolean reserveFastTrajectory() { return trajectoryChecks < 6 && reserveTrajectory(); }
		boolean fastExhausted() { return trajectoryChecks >= 6; }
		Edge admit(Edge edge) { return accepted.test(edge) ? edge : null; }
		void report(String reason) { diagnostic.accept(reason); }
	}
	static record RimCandidate(Edge edge, double score, int ray) {
		RimCandidate(Edge edge, double score) { this(edge, score, -1); }
	}
	private static final int[][] RIM_DIRECTIONS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1},
			{-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
	static final int[][] LOW_LANDING_OFFSETS = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
			{2, 0}, {-2, 0}, {0, 2}, {0, -2}};
	static final double[] TAKEOFF_BACKTRACKS = {0, .5, 1, 1.5, 2, 3};
	static List<BlockPos> rimOffsets() {
		List<BlockPos> offsets = new ArrayList<>(48);
		for (int radius = 1; radius <= 6; radius++)
			for (int[] direction : RIM_DIRECTIONS)
				offsets.add(new BlockPos(radius * direction[0], 0, radius * direction[1]));
		return offsets;
	}

	public static Edge findWind(ServerLevel level, Mob mob, Vec3 destination, float horizon,
			float maximumDistance, float maximumHeight, float minimumHorizontalDistance, int postJumpTicks, Vec3 partialAnchor,
			LivingEntity attackTarget, PmbAirTrackingAiData tracking,
			Predicate<Edge> accepted, Supplier<String> policyReason, Consumer<String> diagnostic,
			WindTraceSink traceSink, WindSearchProgress progress, Consumer<String> details) {
		WindCandidateGate gate = new WindCandidateGate(accepted, diagnostic);
		Vec3 flat = destination.subtract(mob.position()).multiply(1, 0, 1);
		Vec3 perpendicular = flat.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0)
				: new Vec3(-flat.z(), 0, flat.x()).normalize();
		Vec3 along = flat.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : flat.normalize();
		// Small, ordered lateral probes can reach a canopy rim without scanning the world.
		Vec3[] probes = {Vec3.ZERO, perpendicular, perpendicular.scale(-1), along, along.scale(-1),
				perpendicular.scale(2), perpendicular.scale(-2), along.scale(2), along.scale(-2)};
		List<RimCandidate> candidates = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<Vec3> airDescents = attackTarget == null ? List.of() : safeAirDescents(level, mob, destination);
		if (attackTarget != null && airDescents.isEmpty()) gate.report("air_contact_no_safe_descent");
		for (int ray = 0; ray < probes.length; ray++)
			findWindRay(level, mob, destination.add(probes[ray]), destination, horizon, maximumDistance,
					maximumHeight, minimumHorizontalDistance, airDescents, ray, candidates, seen);
		List<RimCandidate> ordered = prioritizeWindCandidates(candidates);
		int fastCount = Math.min(6, ordered.size());
		for (int checked = 0; checked < fastCount; checked++) {
			gate.candidateIndex = progress.next(false, ordered.size());
			RimCandidate candidate = ordered.get(gate.candidateIndex);
			gate.reserveFastTrajectory();
			Edge edge = candidate.edge();
			String[] failure = {"physics_unknown"};
			FlightResult flight = reachableWindEnvelope(level, mob, edge.takeoff(), postJumpTicks,
					edge.safeDescent(), attackTarget, edge.airIntercept(), tracking, -1,
					reason -> failure[0] = reason, traceSink != null);
			if (flight == null) { reportCandidate(gate, details, edge, "physics", failure[0]); continue; }
			if (mob.distanceToSqr(edge.takeoff()) > 4) {
				var path = mob.getNavigation().createPath(BlockPos.containing(edge.takeoff()), 0);
				if (path == null || !path.canReach()) { reportCandidate(gate, details, edge, "approach", "approach_path_unknown");
					if (traceSink != null) traceSink.record(flight.trace(), false);
					continue; }
			}
			Edge selected = gate.admit(edge.withFlight(flight));
			reportCandidate(gate, details, edge, selected == null ? "policy" : "selected", policyReason.get());
			if (traceSink != null) traceSink.record(flight.trace(), selected != null);
			if (selected != null) { reportBudget(details, "fast", ordered.size(), checked + 1, progress.cursor(false, ordered.size())); return selected; }
		}
		reportBudget(details, "fast", ordered.size(), fastCount, progress.cursor(false, ordered.size()));
		if (candidates.isEmpty()) gate.report("no_safe_endpoint");
		return findWindRim(level, mob, destination, horizon, maximumDistance, maximumHeight,
				minimumHorizontalDistance, postJumpTicks, partialAnchor, attackTarget, airDescents, tracking, gate, traceSink,
				progress, details, policyReason);
	}
	static void reportCandidate(WindCandidateGate gate, Consumer<String> details, Edge edge,
			String phase, String reason) {
		gate.report(reason + " phase=" + phase + " " + edge.takeoff() + "->" + edge.landing());
		if (details != null) details.accept("candidate=" + gate.trajectoryChecks + " "
				+ (edge.rim() ? "rim" : "fast") + " index=" + gate.candidateIndex + " phase=" + phase
				+ " takeoff=" + edge.takeoff() + " landing=" + edge.landing()
				+ " descent=" + edge.safeDescent() + " kind=" + edge.kind() + " reason=" + reason);
	}
	static void reportBudget(Consumer<String> details, String phase, int total, int checked, int cursor) {
		if (details != null) details.accept("budget phase=" + phase + " total=" + total
				+ " checked=" + checked + " unchecked=" + (total - checked) + " nextCursor=" + cursor);
	}
	private static void findWindRay(ServerLevel level, Mob mob, Vec3 aim, Vec3 destination, float horizon,
			float maximumDistance, float maximumHeight, float minimumHorizontalDistance,
			List<Vec3> airDescents, int ray, List<RimCandidate> candidates, Set<String> seen) {
		Vec3 flat = aim.subtract(mob.position()).multiply(1, 0, 1);
		double length = Math.min(flat.length(), Math.min(horizon, maximumDistance));
		Vec3 direction = flat.lengthSqr() < 1.0E-6 ? Vec3.ZERO : flat.normalize();
		Vec3 lastWalkable = standableNear(level, mob, mob.getX(), mob.getZ(), mob.getY(), 1);
		if (lastWalkable == null) return;
		Vec3 takeoff = null;
		boolean lowerLandingsAdded = false;
		for (double distance = .5; distance <= length + .5; distance += .5) {
			Vec3 sample = mob.position().add(direction.scale(Math.min(distance, length)));
			Vec3 walkable = standableNear(level, mob, sample.x(), sample.z(), lastWalkable.y(), 2);
			if (takeoff == null && ordinaryStep(level, mob, lastWalkable, walkable)) {
				lastWalkable = walkable;
				if (distance < length) continue;
			}
			if (takeoff == null) takeoff = lastWalkable;
			Vec3 landing = standableNear(level, mob, sample.x(), sample.z(), destination.y(),
					Math.max(1, (int) Math.ceil(maximumHeight)));
			if (landing != null) addBacktrackedCandidates(level, mob, takeoff, landing, destination,
					direction, horizon, maximumDistance, maximumHeight, minimumHorizontalDistance,
					false, landing, ray, candidates, seen);
			// A target-occupied narrow ledge is not a safe standing cell. Contact is
			// a separate endpoint, but only after this ray encountered an unwalkable edge.
			if (!airDescents.isEmpty() && airInterceptEndpoint(sample, destination, maximumHeight) != null
					&& !ordinaryStep(level, mob, takeoff, standableNear(level, mob,
							destination.x(), destination.z(), destination.y(), 1)))
				for (Vec3 descent : airDescents)
					addBacktrackedCandidates(level, mob, takeoff, destination, destination,
							direction, horizon, maximumDistance, maximumHeight, minimumHorizontalDistance,
							true, descent, ray, candidates, seen);
			if (!lowerLandingsAdded && destination.y() < takeoff.y() - 1
					&& horizontalDistanceSqr(sample, destination) <= 4) {
				lowerLandingsAdded = true;
				for (int[] offset : LOW_LANDING_OFFSETS) {
					Vec3 lower = standableNear(level, mob, destination.x() + offset[0],
							destination.z() + offset[1], destination.y(), 1);
					if (lower == null) continue;
					addBacktrackedCandidates(level, mob, takeoff, lower, destination,
							direction, horizon, maximumDistance, maximumHeight, minimumHorizontalDistance,
							false, lower, ray, candidates, seen);
				}
			}
		}
	}
	static List<RimCandidate> prioritizeWindCandidates(List<RimCandidate> candidates) {
		candidates = new ArrayList<>(candidates);
		candidates.sort(Comparator.comparingDouble(RimCandidate::score));
		List<RimCandidate> ordered = new ArrayList<>(candidates.size());
		RimCandidate firstContact = candidates.stream().filter(candidate -> candidate.edge().airIntercept())
				.findFirst().orElse(null);
		if (firstContact != null) {
			ordered.add(firstContact);
			candidates.remove(firstContact);
		}
		Map<String, List<RimCandidate>> byLanding = new LinkedHashMap<>();
		for (RimCandidate candidate : candidates)
			byLanding.computeIfAbsent((candidate.ray() < 0
					? BlockPos.containing(candidate.edge().landing()).toString() : "ray" + candidate.ray())
					+ ":" + candidate.edge().kind(), unused -> new ArrayList<>())
					.add(candidate);
		List<List<RimCandidate>> groups = new ArrayList<>(byLanding.values());
		int firstPass = Math.min(6 - ordered.size(), groups.size());
		for (int group = 0; group < firstPass; group++) ordered.add(groups.get(group).getFirst());
		// Use the remaining fast checks on meaningful retreats, not six nearly
		// identical launches into the nearest (possibly occupied) landing.
		int[] retreatOrder = {3, 1, 4, 2, 5};
		for (int depth : retreatOrder)
			for (int group = 0; group < firstPass; group++)
				if (depth < groups.get(group).size()) ordered.add(groups.get(group).get(depth));
		for (int group = 0; group < firstPass; group++)
			for (int depth = 6; depth < groups.get(group).size(); depth++) ordered.add(groups.get(group).get(depth));
		for (int group = firstPass; group < groups.size(); group++) ordered.addAll(groups.get(group));
		return ordered;
	}
	private static void addBacktrackedCandidates(ServerLevel level, Mob mob, Vec3 baseTakeoff, Vec3 landing,
			Vec3 destination, Vec3 direction, float horizon, float maximumDistance, float maximumHeight,
			float minimumHorizontalDistance, boolean intercept, Vec3 safeDescent, int ray,
			List<RimCandidate> candidates, Set<String> seen) {
		if (Math.abs(landing.y() - destination.y()) > 1) return;
		for (double backtrack : TAKEOFF_BACKTRACKS) {
			Vec3 behind = baseTakeoff.subtract(direction.scale(backtrack));
			Vec3 takeoff = standableNear(level, mob, behind.x(), behind.z(), baseTakeoff.y(), 1);
			if (takeoff == null || horizontalDistanceSqr(mob.position(), takeoff) > horizon * horizon
					|| horizontalDistanceSqr(mob.position(), takeoff) > maximumDistance * maximumDistance
					|| !withinConnectionHeight(takeoff.y(), landing.y(), maximumHeight)
					|| horizontalDistanceSqr(takeoff, landing) < minimumHorizontalDistance * minimumHorizontalDistance
					|| horizontalDistanceSqr(takeoff, landing) > maximumDistance * maximumDistance) continue;
			// Half-block backtracks can be the difference between clearing a wall
			// and striking it; block-position deduplication would discard them.
			String key = takeoff + ":" + landing + ":" + safeDescent + ":" + intercept;
			if (seen.add(key)) candidates.add(new RimCandidate(intercept
					? new Edge(takeoff, landing, safeDescent) : new Edge(takeoff, landing),
					horizontalDistanceSqr(landing, destination) * 10 + backtrack * .1
							+ horizontalDistanceSqr(safeDescent, destination) * .1, ray));
		}
	}
	private static List<Vec3> safeAirDescents(ServerLevel level, Mob mob, Vec3 target) {
		List<Vec3> result = new ArrayList<>(9);
		for (int[] offset : LOW_LANDING_OFFSETS) {
			if (Math.abs(offset[0]) + Math.abs(offset[1]) > 2) continue;
			Vec3 safe = standableNear(level, mob, target.x() + offset[0], target.z() + offset[1],
					target.y(), 2);
			if (safe != null && stableAirDescent(level, mob, safe) && safe.y() <= target.y()
					&& horizontalDistanceSqr(safe, target) <= 4) result.add(safe);
		}
		return result;
	}
	private static Edge findWindRim(ServerLevel level, Mob mob, Vec3 destination, float horizon,
			float maximumDistance, float maximumHeight, float minimumHorizontalDistance,
			int postJumpTicks, Vec3 partialAnchor, LivingEntity attackTarget, List<Vec3> airDescents,
			PmbAirTrackingAiData tracking, WindCandidateGate gate, WindTraceSink traceSink,
			WindSearchProgress progress, Consumer<String> details, Supplier<String> policyReason) {
		List<RimCandidate> candidates = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		List<Vec3> rimStarts = new ArrayList<>(49);
		if (partialAnchor != null && horizontalDistanceSqr(mob.position(), partialAnchor) <= horizon * horizon
				&& horizontalDistanceSqr(mob.position(), partialAnchor) <= maximumDistance * maximumDistance) {
			Vec3 anchor = standableNear(level, mob, partialAnchor.x(), partialAnchor.z(), partialAnchor.y(), 1);
			if (anchor != null) rimStarts.add(anchor);
		}
		for (BlockPos offset : rimOffsets()) {
			Vec3 start = standableNear(level, mob, destination.x() + offset.getX(),
					destination.z() + offset.getZ(), mob.getY(), 2);
			if (start != null) rimStarts.add(start);
		}
		for (Vec3 takeoff : rimStarts) {
			if (takeoff == null || horizontalDistanceSqr(mob.position(), takeoff) > horizon * horizon
					|| horizontalDistanceSqr(mob.position(), takeoff) > maximumDistance * maximumDistance)
				continue;
			Vec3 toward = destination.subtract(takeoff).multiply(1, 0, 1);
			if (toward.lengthSqr() < 1.0E-6) continue;
			Vec3 direction = toward.normalize();
			int inwardLimit = partialAnchor != null && takeoff.distanceToSqr(partialAnchor) <= 2 ? 8 : 4;
			for (int inward = 1; inward <= inwardLimit && inward <= toward.length(); inward++) {
				Vec3 sample = takeoff.add(direction.scale(inward));
				Vec3 landing = standableNear(level, mob, sample.x(), sample.z(), destination.y(), 1);
				if (landing == null || Math.abs(landing.y() - destination.y()) > 1
						|| horizontalDistanceSqr(takeoff, landing) < minimumHorizontalDistance * minimumHorizontalDistance
						|| horizontalDistanceSqr(takeoff, landing) > maximumDistance * maximumDistance
						|| !withinConnectionHeight(takeoff.y(), landing.y(), maximumHeight)
						|| !clearFlightSegment(level, mob, takeoff.add(0, .1, 0),
								takeoff.add(0, Math.max(2, landing.y() - takeoff.y() + 1), 0))
						|| !connectedUpperSurface(level, mob, landing, destination)) continue;
				String key = BlockPos.containing(takeoff) + ":" + BlockPos.containing(landing);
				if (seen.add(key)) candidates.add(new RimCandidate(new Edge(takeoff, landing, false, true),
						horizontalDistanceSqr(mob.position(), takeoff)
								+ horizontalDistanceSqr(landing, destination)));
				break;
			}
			if (attackTarget != null && !airDescents.isEmpty()
					&& withinConnectionHeight(takeoff.y(), destination.y(), maximumHeight)
					&& horizontalDistanceSqr(takeoff, destination) <= maximumDistance * maximumDistance
					&& blockedOrdinaryApproach(level, mob, takeoff, destination))
				for (Vec3 descent : airDescents) {
					String key = takeoff + ":air:" + descent;
					if (seen.add(key)) candidates.add(new RimCandidate(new Edge(takeoff, destination,
							EndpointKind.AIR_CONTACT, true, 0, descent, 0),
							horizontalDistanceSqr(mob.position(), takeoff)
									+ horizontalDistanceSqr(descent, destination)));
				}
		}
		List<RimCandidate> ordered = prioritizeWindCandidates(candidates);
		int count = Math.min(4, ordered.size());
		for (int checked = 0; checked < count; checked++) {
			gate.candidateIndex = progress.next(true, ordered.size());
			RimCandidate candidate = ordered.get(gate.candidateIndex);
			gate.reserveTrajectory();
			Edge edge = candidate.edge();
			if (mob.distanceToSqr(edge.takeoff()) > 4) {
				var path = mob.getNavigation().createPath(BlockPos.containing(edge.takeoff()), 0);
				if (path == null || !path.canReach()) { reportCandidate(gate, details, edge, "approach", "approach_path_unknown"); continue; }
			}
			String[] failure = {"physics_unknown"};
			FlightResult flight = reachableWindEnvelope(level, mob, edge.takeoff(), postJumpTicks,
					edge.safeDescent(), attackTarget, edge.airIntercept(), tracking, -1,
					reason -> failure[0] = reason, traceSink != null);
			if (flight == null) { reportCandidate(gate, details, edge, "physics", failure[0]); continue; }
			Edge selected = gate.admit(edge.withFlight(flight));
			reportCandidate(gate, details, edge, selected == null ? "policy" : "selected", policyReason.get());
			if (traceSink != null) traceSink.record(flight.trace(), selected != null);
			if (selected != null) { reportBudget(details, "rim", ordered.size(), checked + 1, progress.cursor(true, ordered.size())); return selected; }
		}
		reportBudget(details, "rim", ordered.size(), count, progress.cursor(true, ordered.size()));
		if (ordered.isEmpty() && gate.trajectoryChecks == 0) gate.report("no_rim_endpoint");
		return null;
	}
	private static boolean connectedUpperSurface(ServerLevel level, Mob mob, Vec3 landing, Vec3 destination) {
		Vec3 previous = landing;
		double length = Math.sqrt(horizontalDistanceSqr(landing, destination));
		for (double step = .5; step <= length + .5; step += .5) {
			Vec3 sample = landing.lerp(destination, Math.min(step / Math.max(.5, length), 1));
			Vec3 next = standableNear(level, mob, sample.x(), sample.z(), previous.y(), 1);
			if (!ordinaryStep(level, mob, previous, next)) return false;
			previous = next;
		}
		return true;
	}
	static Vec3 airInterceptEndpoint(Vec3 sample, Vec3 target, float maximumHeight) {
		return Math.abs(target.y() - sample.y()) <= maximumHeight
				&& horizontalDistanceSqr(sample, target) <= 2.25 ? target : null;
	}
	/** An exposed, non-zero current must lie on the dry-to-dry connection itself. */
	static boolean crossesFlowingWater(ServerLevel level, Vec3 takeoff, Vec3 landing) {
		if (!dryFoot(level, takeoff) || !dryFoot(level, landing)) return false;
		return flowingWaterOnConnection(takeoff, landing, at -> {
			if (!level.hasChunkAt(at) || !level.hasChunkAt(at.above())
					|| !level.hasChunkAt(at.below()) || !level.hasChunkAt(at.north())
					|| !level.hasChunkAt(at.south()) || !level.hasChunkAt(at.east())
					|| !level.hasChunkAt(at.west())) return false;
			var fluid = level.getFluidState(at);
			return fluid.is(FluidTags.WATER) && fluid.getFlow(level, at).lengthSqr() > 1.0E-8
					&& level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
		});
	}
	static boolean flowingWaterOnConnection(Vec3 takeoff, Vec3 landing, Predicate<BlockPos> flowing) {
		double length = Math.sqrt(horizontalDistanceSqr(takeoff, landing));
		if (length < 1) return false;
		int samples = Math.max(2, (int) Math.ceil(length * 4));
		for (int index = 1; index < samples; index++) {
			Vec3 point = takeoff.lerp(landing, index / (double) samples);
			int footY = (int) Math.floor(point.y());
			for (int y = footY - 1; y <= footY; y++) {
				BlockPos at = BlockPos.containing(point.x(), y, point.z());
				if (flowing.test(at)) return true;
			}
		}
		return false;
	}
	private static boolean dryFoot(ServerLevel level, Vec3 point) {
		BlockPos at = BlockPos.containing(point);
		return level.hasChunkAt(at) && !level.getFluidState(at).is(FluidTags.WATER);
	}
	/** Forward, loaded-only continuation proof; never treats an exhausted search as success. */
	static boolean boundedDryContinuation(ServerLevel level, Mob mob, Vec3 landing, Vec3 destination,
			LivingEntity attackTarget) {
		BlockPos origin = BlockPos.containing(landing);
		if (!level.hasChunkAt(origin) || !safeStanding(level, mob, landing)) return false;
		ArrayDeque<Vec3> open = new ArrayDeque<>();
		Set<BlockPos> visited = new HashSet<>();
		open.add(landing);
		visited.add(origin);
		while (!open.isEmpty() && visited.size() <= 64) {
			Vec3 current = open.removeFirst();
			if (continuationGoal(level, mob, current, destination, attackTarget)) return true;
			BlockPos at = BlockPos.containing(current);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				if (dx == 0 && dz == 0) continue;
				BlockPos nextBlock = at.offset(dx, 0, dz);
				if (Math.max(Math.abs(nextBlock.getX() - origin.getX()),
						Math.abs(nextBlock.getZ() - origin.getZ())) > 8) continue;
				if (!level.hasChunkAt(nextBlock) || !level.hasChunkAt(nextBlock.below())) continue;
				Vec3 next = standableNear(level, mob, nextBlock.getX() + .5,
						nextBlock.getZ() + .5, current.y(), 1);
				if (next == null || !dryFoot(level, next) || !ordinaryStep(level, mob, current, next)) continue;
				if (visited.add(BlockPos.containing(next))) open.addLast(next);
			}
		}
		return false;
	}
	/** A local straight ground approach must encounter real terrain, not merely the target's body. */
	static boolean blockedOrdinaryApproach(ServerLevel level, Mob mob, Vec3 takeoff, Vec3 target) {
		if (!canOrdinaryRise(target.y() - takeoff.y(), mob.maxUpStep())) return true;
		Vec3 flat = target.subtract(takeoff).multiply(1, 0, 1);
		double length = flat.length();
		Vec3 direction = length < 1.0E-6 ? Vec3.ZERO : flat.normalize();
		Vec3 previous = takeoff;
		int samples = ordinaryApproachSamples(length);
		for (int sample = 1; sample <= samples; sample++) {
			double distance = length * sample / samples;
			Vec3 point = takeoff.add(direction.scale(distance));
			BlockPos at = BlockPos.containing(point);
			if (!level.hasChunkAt(at)) return false;
			Vec3 next = standableNear(point.x(), point.z(), previous.y(), 1,
					position -> safeStandingForGroundConnection(level, mob, position));
			if (!ordinaryStep(level, mob, previous, next, true)) return true;
			previous = next;
		}
		return false;
	}
	static int ordinaryApproachSamples(double length) { return Math.max(1, (int) Math.ceil(length * 2)); }
	private static boolean continuationGoal(ServerLevel level, Mob mob, Vec3 current,
			Vec3 destination, LivingEntity attackTarget) {
		if (Math.abs(current.y() - destination.y()) > 1
				|| horizontalDistanceSqr(current, destination) > (attackTarget == null ? .75 * .75 : 1.5 * 1.5))
			return false;
		if (attackTarget == null) return ordinaryStep(level, mob, current, destination);
		return attackTarget.isAlive() && mob.canAttack(attackTarget)
				&& clearFlightSegment(level, mob, current.add(0, .5, 0),
						destination.add(0, .5, 0), true);
	}
	private static boolean safeStandingForGroundConnection(ServerLevel level, Mob mob, Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)) return false;
		AABB moved = mob.getBoundingBox().move(position.subtract(mob.position()));
		if (!safeFlightBox(level, mob, moved)
				|| level.getBlockCollisions(mob, moved).iterator().hasNext()) return false;
		BlockPos support = BlockPos.containing(position.x(), position.y() - .08, position.z());
		return level.hasChunkAt(support)
				&& !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
	}

	public static Edge findDangerousDrop(ServerLevel level, Mob mob, Vec3 destination, float horizon) {
		Vec3 flat = destination.subtract(mob.position()).multiply(1, 0, 1);
		double length = Math.min(flat.length(), horizon);
		if (length < 1.5 || flat.lengthSqr() < 1.0E-6 || mob.getY() - destination.y() <= 4) return null;
		Vec3 direction = flat.normalize();
		Vec3 lastWalkable = mob.position();
		for (double distance = .5; distance <= length; distance += .5) {
			Vec3 sample = mob.position().add(direction.scale(distance));
			Vec3 walkable = standableNear(level, mob, sample.x(), sample.z(), lastWalkable.y(), 1);
			if (walkable != null) { lastWalkable = walkable; continue; }
			Vec3 landing = standableNear(level, mob, destination.x(), destination.z(), destination.y(), 2);
			if (landing == null || landing.y() >= lastWalkable.y() - 4) return null;
			if (mob.distanceToSqr(lastWalkable) > 4) {
				var path = mob.getNavigation().createPath(BlockPos.containing(lastWalkable), 0);
				if (path == null || !path.canReach()) return null;
			}
			return new Edge(lastWalkable, landing);
		}
		return null;
	}

	public static boolean safeStanding(ServerLevel level, Mob mob, Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)) return false;
		AABB moved = mob.getBoundingBox().move(position.subtract(mob.position()));
		if (!safeFlightBox(level, mob, moved) || !level.noCollision(mob, moved)) return false;
		if (level.getFluidState(block).is(FluidTags.WATER)
				|| level.getBlockState(block).is(Blocks.COBWEB)) return true;
		if (level.getFluidState(block).is(FluidTags.LAVA)) return mob.fireImmune();
		BlockPos support = BlockPos.containing(position.x(), position.y() - .08, position.z());
		return level.hasChunkAt(support)
				&& !level.getBlockState(support).getCollisionShape(level, support).isEmpty();
	}
	static boolean stableAirDescent(ServerLevel level, Mob mob, Vec3 position) {
		if (!safeStanding(level, mob, position)) return false;
		if (level.getFluidState(BlockPos.containing(position)).is(FluidTags.WATER)) return true;
		AABB box = mob.getBoundingBox().move(position.subtract(mob.position()));
		for (double x : new double[] {box.minX + .05, box.maxX - .05})
			for (double z : new double[] {box.minZ + .05, box.maxZ - .05}) {
				BlockPos support = BlockPos.containing(x, position.y() - .08, z);
				if (!level.hasChunkAt(support)
						|| level.getBlockState(support).getCollisionShape(level, support).isEmpty())
					return false;
			}
		return true;
	}

	private static boolean ordinaryStep(ServerLevel level, Mob mob, Vec3 previous, Vec3 next) {
		return ordinaryStep(level, mob, previous, next, false);
	}
	private static boolean ordinaryStep(ServerLevel level, Mob mob, Vec3 previous, Vec3 next, boolean terrainOnly) {
		if (next == null || !canOrdinaryRise(next.y() - previous.y(), mob.maxUpStep())
				|| previous.y() - next.y() > 3) return false;
		BlockPos block = BlockPos.containing(next);
		if (level.getBlockState(block).is(Blocks.COBWEB)
				|| level.getFluidState(block).is(FluidTags.WATER)
				|| level.getFluidState(block).is(FluidTags.LAVA)) return false;
		// A support at the destination does not imply that the body can walk through
		// the intervening shape (fences and closed doors are common examples).
		Vec3 raised = previous;
		if (next.y() - previous.y() > mob.maxUpStep()) {
			// Vanilla ground navigation can jump one full block even when the
			// entity's passive step height is lower; require real headroom for it.
			raised = previous.add(0, next.y() - previous.y(), 0);
			if (!clearFlightSegment(level, mob, previous, raised, terrainOnly)) return false;
		}
		for (int step = 1; step <= 4; step++) {
			Vec3 point = raised.lerp(next, step / 4.0);
			BlockPos at = BlockPos.containing(point);
			if (!level.hasChunkAt(at)) return false;
			if (level.getBlockState(at).getBlock() instanceof DoorBlock door
					&& door.type().canOpenByHand()
					&& mob.getNavigation().getNodeEvaluator() != null
					&& mob.getNavigation().getNodeEvaluator().canOpenDoors()) continue;
			AABB box = mob.getBoundingBox().move(point.subtract(mob.position()));
			if (terrainOnly ? level.getBlockCollisions(mob, box).iterator().hasNext() : !level.noCollision(mob, box)) return false;
		}
		return true;
	}
	static boolean walkableConnection(ServerLevel level, Mob mob, Vec3 from, Vec3 to) {
		return safeStandingForGroundConnection(level, mob, from) && safeStandingForGroundConnection(level, mob, to)
				&& ordinaryStep(level, mob, from, to);
	}
	static boolean canOrdinaryRise(double rise, double maxUpStep) {
		return rise <= Math.max(1.125, maxUpStep);
	}

	private static Vec3 standableNear(ServerLevel level, Mob mob, double x, double z, double preferredY, int range) {
		return standableNear(x, z, preferredY, range, position -> safeStanding(level, mob, position));
	}
	private static Vec3 standableNear(double x, double z, double preferredY, int range, Predicate<Vec3> standing) {
		int center = (int) Math.floor(preferredY);
		for (int offset = 0; offset <= range; offset++) {
			for (int sign : offset == 0 ? new int[] {1} : new int[] {1, -1}) {
				Vec3 candidate = new Vec3(x, center + offset * sign, z);
				if (standing.test(candidate)) return candidate;
			}
		}
		return null;
	}

	private static FlightResult reachableWindEnvelope(ServerLevel level, Mob mob, Vec3 start, int postJumpTicks,
			Vec3 safeDescent, LivingEntity attackTarget, boolean intercept,
			PmbAirTrackingAiData tracking, int fixedDelay, Consumer<String> diagnostic, boolean captureTrace) {
		PmbWindLaunchPhysics.Launch launch = postJumpTicks < 0
				? PmbWindLaunchPhysics.current(level, mob, diagnostic)
				: PmbWindLaunchPhysics.predict(level, mob, start, postJumpTicks);
		if (launch == null) {
			if (postJumpTicks >= 0) diagnostic.accept("wind_impulse_unproven");
			return null;
		}
		if (tracking.trackAcceleration() <= 0 || tracking.trackMaxHorizontalSpeed() <= 0) {
			diagnostic.accept("flight_tracking_disabled"); return null;
		}
		int duration = tracking.trackDurationTicks() < 0 ? 80 : Math.min(80, tracking.trackDurationTicks());
		duration = Math.max(0, duration - launch.impactTicks());
		// Mob.tick TAIL queues the modifier for the following serverAiStep, so
		// post-impact displacement zero is never steered. The selected delay is
		// additional to that handoff, using runtime's same target and steering.
		boolean anyContact = false, anyCollision = false, anyVehicleCollision = false;
		for (int delay = 0; delay <= 20; delay += 4) {
			if (fixedDelay >= 0 && delay != fixedDelay) continue;
			Vec3 position = launch.position();
			Vec3 velocity = launch.velocity();
			List<Vec3> trace = captureTrace ? new ArrayList<>(Math.min(80, duration + 1)) : null;
			if (trace != null) trace.add(position);
			boolean contacted = false;
			for (int tick = 0; tick < duration; tick++) {
				if (tick >= PmbWindLaunchPhysics.firstSteeredPostImpactDisplacement(delay))
					velocity = PmbAirTrackingController.steeredVelocity(
						position, velocity, safeDescent, tracking, false);
				Vec3 next = position.add(velocity);
				if (level.isOutsideBuildHeight(BlockPos.containing(next))) break;
				double crossing = descendingLandingFraction(position.y(), next.y(), safeDescent.y());
				boolean landed = velocity.y() < 0 && crossing >= 0;
				Vec3 segmentEnd = landed ? position.lerp(next, crossing) : next;
				if (!clearFlightSegment(level, mob, position, segmentEnd, intercept)) {
					anyCollision = true; break;
				}
				if (intercept && attackTarget != null && attackTarget.getVehicle() != null
						&& sweptBoxOverlap(mob.getBoundingBox().move(position.subtract(mob.position())),
								attackTarget.getVehicle().getBoundingBox(), position, segmentEnd, 0)) {
					anyVehicleCollision = true; break;
				}
				if (intercept && attackTarget != null && sweptContact(
						mob.getBoundingBox().move(position.subtract(mob.position())),
						attackTarget.getBoundingBox(), position, segmentEnd)) {
					contacted = true; anyContact = true;
				}
				if (trace != null) trace.add(segmentEnd);
				if (landed) {
					if ((!intercept || contacted) && horizontalDistanceSqr(segmentEnd, safeDescent) <= .35 * .35
								&& (intercept ? stableAirDescent(level, mob, safeDescent)
									: safeStanding(level, mob, safeDescent)))
							return new FlightResult(tick + 1 + launch.impactTicks()
									+ Math.max(0, postJumpTicks), delay,
									trace == null ? List.of() : List.copyOf(trace));
					break;
				}
				position = next;
				velocity = new Vec3(velocity.x() * .91, (velocity.y() - .08) * .98,
						velocity.z() * .91);
			}
		}
		diagnostic.accept(anyVehicleCollision ? "vehicle_collision"
				: intercept && !anyContact ? (anyCollision ? "air_contact_collision" : "air_contact_not_reached")
				: anyCollision ? "flight_collision" : "no_safe_descent");
		return null;
	}
	static boolean revalidateAirContact(ServerLevel level, Mob mob, Edge edge,
			LivingEntity target, PmbAirTrackingAiData tracking) {
		return edge.airIntercept() && target != null && target.isAlive() && mob.canAttack(target)
				&& reachableWindEnvelope(level, mob, mob.position(), -1,
						edge.safeDescent(), target, true, tracking, edge.steeringDelay(), unused -> {}, false) != null;
	}
	static boolean revalidateWindFlight(ServerLevel level, Mob mob, Edge edge,
			LivingEntity target, PmbAirTrackingAiData tracking) {
		return revalidateWindFlight(level, mob, edge, target, tracking, unused -> {});
	}
	static boolean revalidateWindFlight(ServerLevel level, Mob mob, Edge edge,
			LivingEntity target, PmbAirTrackingAiData tracking, Consumer<String> diagnostic) {
		if (mob.getDeltaMovement().horizontalDistanceSqr() > .025 * .025) {
			diagnostic.accept("horizontal_motion"); return false;
		}
		return reachableWindEnvelope(level, mob, mob.position(), -1,
				edge.safeDescent(), target, edge.airIntercept(), tracking,
				edge.steeringDelay(), diagnostic, false) != null;
	}
	static boolean preflightWindDeparture(ServerLevel level, Mob mob, Edge edge, int postJumpTicks,
			LivingEntity target, PmbAirTrackingAiData tracking, Consumer<String> diagnostic) {
		return reachableWindEnvelope(level, mob, mob.position(), postJumpTicks == 0 ? -1 : postJumpTicks,
				edge.safeDescent(), target, edge.airIntercept(), tracking,
				edge.steeringDelay(), diagnostic, false) != null;
	}
	static boolean clearTakeoffApproach(ServerLevel level, Mob mob, Vec3 takeoff) {
		return clearFlightSegment(level, mob, mob.position(), takeoff);
	}
	static boolean sweptContact(AABB movingAtFrom, AABB target, Vec3 from, Vec3 to) {
		return sweptBoxOverlap(movingAtFrom, target, from, to, .15);
	}
	static boolean sweptBoxOverlap(AABB movingAtFrom, AABB target, Vec3 from, Vec3 to,
			double tolerance) {
		int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .2));
		for (int index = 1; index <= samples; index++) {
			Vec3 point = from.lerp(to, index / (double) samples);
			if (movingAtFrom.move(point.subtract(from)).intersects(target.inflate(tolerance))) return true;
		}
		return false;
	}
	static double descendingLandingFraction(double fromY, double toY, double landingY) {
		if (fromY < landingY || toY > landingY || fromY <= toY) return -1;
		return (fromY - landingY) / (fromY - toY);
	}
	static boolean withinConnectionHeight(double takeoffY, double landingY, float maximumHeight) {
		return Math.abs(landingY - takeoffY) <= maximumHeight;
	}
	private static boolean clearFlightSegment(ServerLevel level, Mob mob, Vec3 from, Vec3 to) {
		return clearFlightSegment(level, mob, from, to, false);
	}
	private static boolean clearFlightSegment(ServerLevel level, Mob mob, Vec3 from, Vec3 to,
			boolean ignoreEntityCollision) {
		int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .2));
		for (int index = 1; index <= samples; index++) {
			Vec3 point = from.lerp(to, index / (double) samples);
			BlockPos block = BlockPos.containing(point);
			AABB moved = mob.getBoundingBox().move(point.subtract(mob.position()));
			if (!level.hasChunkAt(block) || !level.getWorldBorder().isWithinBounds(block)
					|| !safeFlightBox(level, mob, moved)
					|| (ignoreEntityCollision ? level.getBlockCollisions(mob, moved).iterator().hasNext()
							: !level.noCollision(mob, moved))) return false;
		}
		return true;
	}
	private static boolean safeFlightBox(ServerLevel level, Mob mob, AABB box) {
		int minX = (int) Math.floor(box.minX + 1.0E-6), maxX = (int) Math.floor(box.maxX - 1.0E-6);
		int minY = (int) Math.floor(box.minY + 1.0E-6), maxY = (int) Math.floor(box.maxY - 1.0E-6);
		int minZ = (int) Math.floor(box.minZ + 1.0E-6), maxZ = (int) Math.floor(box.maxZ - 1.0E-6);
		for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++)
			for (int z = minZ; z <= maxZ; z++) {
				BlockPos occupied = new BlockPos(x, y, z);
				if (!level.hasChunkAt(occupied) || level.isOutsideBuildHeight(occupied)
						|| !mob.fireImmune() && level.getFluidState(occupied).is(FluidTags.LAVA)) return false;
			}
		return true;
	}

	private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
		double x = first.x() - second.x(), z = first.z() - second.z();
		return x * x + z * z;
	}
}
