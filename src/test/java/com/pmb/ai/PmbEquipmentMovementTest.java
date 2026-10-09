package com.pmb.ai;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import sun.misc.Unsafe;

/**
 * Dependency-free regression executable. The allocated fixture bypasses world construction
 * and overrides only item storage; production resolution, exchanges and movement arbitration run unchanged.
 * This does not test Minecraft AI timing or Mixin application.
 */
public final class PmbEquipmentMovementTest {
	private static int checks;
	public static void main(String[] args) throws Exception {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		// Only these fixture items are used; no datapack/world component loading is involved.
		for (var item : List.of(Items.ARROW, Items.BOW, Items.STICK, Items.DIAMOND,
				Items.MACE, Items.SHIELD, Items.ENDER_PEARL))
			item.builtInRegistryHolder().bindComponents(net.minecraft.core.component.DataComponentMap.builder()
					.set(net.minecraft.core.component.DataComponents.MAX_STACK_SIZE, 64).build());
		testEquipment();
		testInventoryScatterDrops();
		testInventoryCapacityAndSources();
		testMovement();
		testDebugCaptureGate();
		testSchema();
		testDestructiveAiHierarchy();
		testSkillPriorities();
		testBowMeleeSuppressionRules();
		testSkillTimingAndPreClaim();
		testPriorityPreemptionHarness();
		testTransactionalItemPreemption();
		testBowTacticalFallPolicy();
		testFallRescueMethodOrder();
		testRescueVelocityAndLook();
		testSkillDebugDiagnostics();
		testFactionCompatCodec();
		testFactionBrainIntent();
		testFactionTargetRetention();
		testWindChargeNeutralFallbackPredicate();
		testWindLandingAndDisabledPlanning();
		testWindLocalCheckReuse();
		testWindObstacleProgress();
		testWindSearchProgress();
		testPathParticleBounds();
		testParameterSuggestions();
		testPriorityArgument();
		System.out.println("PASSED " + checks + " equipment/movement/schema assertions");
	}
	private static void testWindSearchProgress() {
		List<PmbLocalEdgePlanner.RimCandidate> input = new ArrayList<>();
		for (int ray = 0; ray < 8; ray++) for (int offset = 0; offset < 9; offset++)
			input.add(new PmbLocalEdgePlanner.RimCandidate(new PmbLocalEdgePlanner.Edge(
					new Vec3(offset, 0, ray), new Vec3(12, 3, ray)), ray * 10 + offset, ray));
		var original = List.copyOf(input);
		var ordered = PmbLocalEdgePlanner.prioritizeWindCandidates(input);
		check(input.equals(original) && ordered.size() == input.size()
				&& new java.util.HashSet<>(ordered).equals(new java.util.HashSet<>(input)),
				"candidate ordering preserves every candidate beyond six per group without mutating input");
		var oneGroup = PmbLocalEdgePlanner.prioritizeWindCandidates(input.subList(0, 9));
		check(oneGroup.size() == 9 && new java.util.HashSet<>(oneGroup).size() == 9,
				"a single long candidate group retains its tail exactly once");
		var progress = new PmbLocalEdgePlanner.WindSearchProgress();
		java.util.Set<Integer> covered = new java.util.HashSet<>();
		for (int round = 0; round < 12; round++) {
			java.util.Set<Integer> batch = new java.util.HashSet<>();
			for (int i = 0; i < 6; i++) batch.add(progress.next(false, ordered.size()));
			check(batch.size() == 6, "a fast planning batch never checks a candidate twice");
			covered.addAll(batch);
		}
		check(covered.size() == ordered.size() && covered.contains(ordered.size() - 1),
				"normal bounded retries reach a feasible candidate at the end of a stable search");
		progress.reset();
		for (int i = 0; i < 6; i++) progress.next(false, 9);
		check(progress.next(true, 7) == 0 && progress.cursor(false, 9) == 6,
				"rim search advances independently of the fast cursor");
		var small = new java.util.HashSet<Integer>();
		for (int i = 0; i < Math.min(6, 3); i++) small.add(progress.next(false, 3));
		check(small.size() == 3 && progress.cursor(false, 0) == 0,
				"shrinking and empty candidate lists normalize cursors without duplicate checks");
		var context = new PmbTraversalController.WindSearchContext();
		var objective = new PmbPathfindingController.TraversalObjective(
				PmbPathfindingController.IntentKind.PURSUIT, null, new Vec3(10, 2, 0), "test");
		context.update(objective, PmbSkillScheduler.Strategy.COMBAT, Vec3.ZERO, 2);
		context.progress.next(false, 20);
		context.update(objective, PmbSkillScheduler.Strategy.COMBAT, new Vec3(1, 0, 0), 2);
		context.update(objective, PmbSkillScheduler.Strategy.COMBAT, new Vec3(1.9, 0, 0), 2);
		check(context.progress.cursor(false, 20) == 1, "ordinary retries preserve search progress");
		context.methodAvailability(true, true);
		check(context.progress.cursor(false, 20) == 1, "available wind method preserves normal retry progress");
		context.update(objective, PmbSkillScheduler.Strategy.COMBAT, new Vec3(2.1, 0, 0), 2);
		check(context.progress.cursor(false, 20) == 0, "cumulative mob movement resets from fixed search origin");
		context.progress.next(false, 20);
		context.update(objective, PmbSkillScheduler.Strategy.RETREAT, new Vec3(2.1, 0, 0), 2);
		check(context.progress.cursor(false, 20) == 0, "strategy changes reset wind search");
		context.progress.next(false, 20);
		var moved = new PmbPathfindingController.TraversalObjective(objective.kind(), null, new Vec3(13, 2, 0), "test");
		context.update(moved, PmbSkillScheduler.Strategy.RETREAT, new Vec3(2.1, 0, 0), 2);
		check(context.progress.cursor(false, 20) == 0, "significant objective movement resets wind search");
		context.progress.next(false, 20);
		context.update(new PmbPathfindingController.TraversalObjective(moved.kind(), null, moved.destination(), "new_owner"),
				PmbSkillScheduler.Strategy.RETREAT, new Vec3(2.1, 0, 0), 2);
		check(context.progress.cursor(false, 20) == 0, "objective identity changes reset wind search");
		context.progress.next(true, 20);
		context.reset();
		check(context.progress.cursor(true, 20) == 0 && context.progress.cursor(false, 20) == 0,
				"disable or successful release can reset both search cursors");
		context.progress.next(false, 20);
		context.methodAvailability(false, true);
		check(context.progress.cursor(false, 20) == 0,
				"disabling only wind resets progress even when pearl keeps traversal enabled");
		context.progress.next(true, 20);
		context.methodAvailability(true, false);
		check(context.progress.cursor(true, 20) == 0,
				"removing wind from methods resets progress even when pearl remains listed");
		List<String> reasons = new ArrayList<>(), details = new ArrayList<>();
		var gate = new PmbLocalEdgePlanner.WindCandidateGate(edge -> false, reasons::add);
		var edge = new PmbLocalEdgePlanner.Edge(new Vec3(.25, 64, .75), new Vec3(5.5, 67, 3.25));
		PmbLocalEdgePlanner.reportCandidate(gate, details::add, edge, "physics", "launch_body_obstructed");
		PmbLocalEdgePlanner.reportBudget(details::add, "fast", 12, 6, 6);
		check(reasons.size() == 1 && reasons.getFirst().contains("launch_body_obstructed")
				&& reasons.getFirst().contains(edge.takeoff().toString())
				&& details.getFirst().contains(edge.landing().toString())
				&& details.getLast().contains("unchecked=6"),
				"budget summaries preserve the actual candidate rejection and precise endpoints");
	}
	private static void testPathParticleBounds() throws Exception {
		Fixture mob = fixture();
		check(!mob.ai.pathfinding().planner().debugPathParticles() && !PmbPathParticleDebug.watched(mob),
				"path particles are disabled by default and allocate no route cache");
		check(PmbPathParticleDebug.sampleIndices(200, 64).size() == 64
				&& PmbPathParticleDebug.sampleIndices(200, 64).getFirst() == 0
				&& PmbPathParticleDebug.sampleIndices(200, 64).getLast() == 199
				&& PmbPathParticleDebug.sampleIndices(200, 80).size() == 80,
				"vanilla nodes and wind flight samples have explicit bounded, endpoint-preserving caps");
		check(PmbPathParticleDebug.particleBudget(new int[]{64, 80, 80, 80}) <= 144
				&& PmbPathParticleDebug.particleBudget(new int[]{1, 80, 80, 80}) <= 144,
				"a viewer never exceeds the per-tick particle packet cap");
		check(PmbPathParticleDebug.colorFor(2, 0) == PmbPathParticleDebug.colorFor(2, 0)
				&& PmbPathParticleDebug.colorFor(2, 0) != PmbPathParticleDebug.colorFor(2, 1)
				&& PmbPathParticleDebug.colorFor(2, 1) != PmbPathParticleDebug.colorFor(2, 2),
				"cached routes retain distinct colors during one planning cycle");
		check(PmbPathParticleDebug.keepWindRoute(true, false, 0)
				&& PmbPathParticleDebug.keepWindRoute(true, false, 1)
				&& !PmbPathParticleDebug.keepWindRoute(true, false, 2)
				&& PmbPathParticleDebug.keepWindRoute(true, true, 2)
				&& !PmbPathParticleDebug.keepWindRoute(false, true, 0),
				"only two later-rejected simulated candidates and the adopted trajectory are cached");
		UUID adminA = UUID.randomUUID(), adminB = UUID.randomUUID();
		UUID first = UUID.randomUUID(), second = UUID.randomUUID();
		check(PmbPathParticleDebug.toggleSelection(adminA, first, 10,
				net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.ADDED
				&& PmbPathParticleDebug.toggleSelection(adminB, first, 10,
					net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.ADDED
				&& PmbPathParticleDebug.subscriberCount(first) == 2,
				"two administrators can independently observe the same mob");
		check(PmbPathParticleDebug.toggleSelection(adminA, second, 11,
				net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.ADDED
				&& PmbPathParticleDebug.subscriberCount(first) == 2
				&& PmbPathParticleDebug.subscriberCount(second) == 1,
				"K on a second mob adds rather than replaces that administrator's observation");
		check(PmbPathParticleDebug.toggleSelection(adminA, second, 11,
				net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.REMOVED
				&& PmbPathParticleDebug.subscriberCount(second) == 0,
				"K on the same mob cancels observation");
		UUID third = UUID.randomUUID(), fourth = UUID.randomUUID(), fifth = UUID.randomUUID();
		check(PmbPathParticleDebug.toggleSelection(adminA, second, 11,
				net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.ADDED
				&& PmbPathParticleDebug.toggleSelection(adminA, third, 12,
					net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.ADDED
				&& PmbPathParticleDebug.toggleSelection(adminA, fourth, 13,
					net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.ADDED
				&& PmbPathParticleDebug.toggleSelection(adminA, fifth, 14,
					net.minecraft.world.level.Level.OVERWORLD) == PmbPathParticleDebug.ToggleResult.FULL
				&& PmbPathParticleDebug.observationCount(adminA) == 4
				&& PmbPathParticleDebug.observationCount(adminB) == 1,
				"each administrator has an independent four-mob cap with no automatic replacement");
		check(PmbTraversalController.holdReleasedWind(100, 101, true)
				&& PmbTraversalController.holdReleasedWind(100, 102, false)
				&& PmbTraversalController.holdReleasedWind(100, 110, false)
				&& PmbTraversalController.holdReleasedWind(100, 180, false)
				&& !PmbTraversalController.holdReleasedWind(100, 181, false)
				&& !PmbTraversalController.holdReleasedWind(100, 111, true),
				"post-release traversal waits during the visible arc but resumes replanning at the 80-tick deadline");
		check(PmbPathParticleDebug.traceVisible(80, 100, 101, true)
				&& PmbPathParticleDebug.traceVisible(80, 100, 102, false)
				&& PmbPathParticleDebug.traceVisible(80, 100, 130, false)
				&& PmbPathParticleDebug.traceVisible(80, 100, 180, false)
				&& !PmbPathParticleDebug.traceVisible(80, 100, 103, true)
				&& !PmbPathParticleDebug.traceVisible(80, 100, 181, false),
				"accepted cached arc survives repeated airborne ticks then clears on landing or short timeout");
		check(PmbPathParticleDebug.keepObservation(true, true, true, true, true)
				&& !PmbPathParticleDebug.keepObservation(false, true, true, true, true)
				&& !PmbPathParticleDebug.keepObservation(true, false, true, true, true)
				&& !PmbPathParticleDebug.keepObservation(true, true, false, true, true)
				&& !PmbPathParticleDebug.keepObservation(true, true, true, false, true)
				&& !PmbPathParticleDebug.keepObservation(true, true, true, true, false),
				"lost GM permission, dimension, load, life, or identity stops observation");
		PmbPathParticleDebug.disconnect(adminA);
		PmbPathParticleDebug.disconnect(adminB);
		check(PmbPathParticleDebug.subscriberCount(first) == 0,
				"disconnect removes the remaining observation");
		PmbPathfindingData.Planner old = mob.ai.pathfinding().planner();
		mob.ai.pathfinding().setPlanner(new PmbPathfindingData.Planner(old.planningHorizon(),
				old.repathIntervalTicks(), old.objectiveMemoryTicks(), old.targetMoveThreshold(),
				old.conditionalWaitTicks(), true));
		check(mob.ai.pathfinding().planner().debugPathParticles(),
				"planner debug flag can be enabled independently of traversal policy");
	}
	private static void testWindLocalCheckReuse() {
		PmbTraversalController.LocalWindChecks cache = new PmbTraversalController.LocalWindChecks();
		int[] pathCalls = {0};
		var first = new net.minecraft.core.BlockPos(1, 2, 3);
		var nullPath = cache.path(first, 0, () -> { pathCalls[0]++; return null; });
		var repeated = cache.path(first, 0, () -> { pathCalls[0]++; return null; });
		check(nullPath.available() && repeated.available() && repeated.path() == null && pathCalls[0] == 1,
				"same-round null local path is cached across takeoff candidates");
		cache.path(first.east(), 0, () -> { pathCalls[0]++; return null; });
		cache.path(first.west(), 0, () -> { pathCalls[0]++; return null; });
		check(!cache.path(first.north(), 0, () -> { pathCalls[0]++; return null; }).available()
				&& cache.path(first, 0, () -> { pathCalls[0]++; return null; }).available() && pathCalls[0] == 3,
				"direct path budget counts unique cache misses and allows hits after exhaustion");
		check(cache.path(first.north(), 1, () -> { pathCalls[0]++; return null; }).available()
				&& cache.path(first.south(), 1, () -> { pathCalls[0]++; return null; }).available()
				&& !cache.path(first.above(), 1, () -> { pathCalls[0]++; return null; }).available()
				&& pathCalls[0] == 5,
				"rim candidates keep their independent two-miss budget");
		PmbTraversalController.LocalWindChecks nextRound = new PmbTraversalController.LocalWindChecks();
		check(nextRound.path(first, 0, () -> { pathCalls[0]++; return null; }).available() && pathCalls[0] == 6,
				"new planning round does not reuse stale terrain path results");
	}
	private static void testWindObstacleProgress() throws Exception {
		Vec3 start = new Vec3(0, 0, 0), target = new Vec3(0, 4, 0);
		check(PmbTraversalController.partialRouteReason(true, start, target, target).equals("partial_route_obstacle_progress"),
				"partial or computed-null routes permit obstacle-crossing progress straight up a pillar");
		check(PmbTraversalController.partialRouteReason(false, start, target, target).equals("no_local_obstacle"),
				"partial routes without an actual local obstacle do not waive savings");
		check(PmbTraversalController.partialRouteReason(true, start, new Vec3(0, -1, 0), target).equals("no_target_progress")
				&& PmbTraversalController.partialRouteReason(true, start, start, target).equals("no_target_progress"),
				"a blocked edge must improve three-dimensional objective distance");
		check(PmbLocalEdgePlanner.ordinaryApproachSamples(.2) == 1
				&& PmbLocalEdgePlanner.ordinaryApproachSamples(1.4) == 3
				&& !PmbLocalEdgePlanner.canOrdinaryRise(2, .6),
				"short obstacle segments are sampled and a close two-block rise remains non-walkable");
		Path full = new Path(List.of(new Node(0, 0, 0), new Node(0, 0, 1), new Node(1, 0, 1), new Node(2, 0, 1)),
				new net.minecraft.core.BlockPos(2, 0, 1), true);
		PmbTraversalController stalled = new PmbTraversalController();
		field(PmbTraversalController.class, "vanillaPath").set(stalled, full);
		field(PmbTraversalController.class, "fullVanilla").setBoolean(stalled, false);
		field(PmbTraversalController.class, "vanillaStallUntil").setInt(stalled, Integer.MAX_VALUE);
		check(stalled.globalWindAdmission(PmbPathfindingData.RouteMode.FALLBACK)
				== PmbTraversalController.GlobalWindAdmission.KEEP_VANILLA,
				"a complete global route during a stall still blocks fallback before partial/null local admission");
		check(stalled.globalWindAdmission(PmbPathfindingData.RouteMode.EFFICIENT)
				== PmbTraversalController.GlobalWindAdmission.COMPARE_FULL_ROUTE,
				"a complete global route during a stall still requires full-route savings rather than a local bypass");
		Vec3 landing = new Vec3(1.6, 0, 1.5), destination = new Vec3(2.5, 0, 1.5);
		check(PmbTraversalController.provenWalkableSuffixNode(full, landing, destination, point -> true) == 2
				&& PmbTraversalController.meetsSavings(false, 100, 10, 20, 40),
				"complete global route comparisons retain suffix and savings requirements");
		check(!PmbTraversalController.meetsSavings(false, 30, 5, 20, 40),
				"a complete local route around a lantern still fails insufficient savings");
		check(PmbTraversalController.provenWalkableSuffixNode(full, landing, destination, point -> false) < 0,
				"a complete route across a wall cannot supply a safe suffix");
		PmbTraversalController.LocalWindChecks checks = new PmbTraversalController.LocalWindChecks();
		for (int i = 0; i < 3; i++) checks.path(new net.minecraft.core.BlockPos(i, 0, 0), 0, () -> null);
		var untested = checks.path(new net.minecraft.core.BlockPos(4, 0, 0), 0, () -> { throw new AssertionError("budget"); });
		check(!untested.available() && checks.path(net.minecraft.core.BlockPos.ZERO, 0, () -> null).available(),
				"unexecuted budget-limited paths remain distinct from computed-null paths");
	}
	private static void testWindLandingAndDisabledPlanning() {
		for (double landing : List.of(-4.0, -2.0, 0.0, 2.0, 4.0)) {
			check(Math.abs(PmbLocalEdgePlanner.descendingLandingFraction(landing + .4, landing - .6, landing) - .4) < 1.0E-9,
					"wind landing detects the exact descending segment at height " + landing);
			check(PmbLocalEdgePlanner.descendingLandingFraction(landing - .1, landing - 1.0, landing) < 0,
					"wind landing rejects a segment already below height " + landing);
		}
		check(PmbLocalEdgePlanner.withinConnectionHeight(0, 0, 2),
				"two-block wind connection permits a same-height landing regardless of arc apex");
		check(PmbLocalEdgePlanner.canOrdinaryRise(1, .6)
				&& !PmbLocalEdgePlanner.canOrdinaryRise(2, .6),
				"vanilla one-block jump is ordinary navigation, but a two-block wall is not");
		check(PmbLocalEdgePlanner.withinConnectionHeight(0, 2, 2),
				"two-block wind connection permits a two-block rise");
		check(PmbWindLaunchPhysics.conservativeImpulse(.5, .8, 0)
					> PmbWindLaunchPhysics.conservativeImpulse(1.5, .8, 0)
					&& PmbWindLaunchPhysics.conservativeImpulse(2.4, 1, 0) == 0
					&& PmbWindLaunchPhysics.conservativeImpulse(.5, 1, 1) == 0,
				"26.2 wind impulse falls with distance and vanishes at radius or full resistance");
		var releaseBox = new net.minecraft.world.phys.AABB(-.3, 0, -.3, .3, 1.8, .3);
		var impactBox = releaseBox.move(0, 1, 0);
		double releaseExposure = PmbWindLaunchPhysics.sampleExposure(releaseBox,
				sample -> sample.y() < 1.8);
		double impactExposure = PmbWindLaunchPhysics.sampleExposure(impactBox,
				sample -> sample.y() < 1.8);
		check(releaseExposure > impactExposure
				&& PmbWindLaunchPhysics.windPush(new Vec3(0, 1, 0), new Vec3(0, .25, 0), 1.6,
						impactExposure, 0).y()
						< PmbWindLaunchPhysics.windPush(new Vec3(0, 1, 0), new Vec3(0, .25, 0), 1.6,
							releaseExposure, 0).y(),
				"moving into occlusion before impact reduces the predicted wind push");
		check(PmbWindLaunchPhysics.projectileTravelTicks(1.6) == 1
				&& PmbWindLaunchPhysics.projectileTravelTicks(1.61) == 2
				&& PmbWindLaunchPhysics.projectileTravelTicks(3.31) == 3,
				"projectile acceleration changes the floor-hit tick at each swept segment");
		check(PmbWindLaunchPhysics.runtimeSteeringDelay(2, 0) == 2
				&& PmbWindLaunchPhysics.runtimeSteeringDelay(2, 4) == 6
				&& PmbWindLaunchPhysics.firstSteeredPostImpactDisplacement(0) == 1
				&& PmbWindLaunchPhysics.firstSteeredPostImpactDisplacement(1) == 2,
				"tail submission and next serverAiStep reserve the first post-impact displacement");
		PmbAirTrackingAiData steeringData = new PmbAirTrackingAiData();
		steeringData.set(.1F, 1, -1);
		Vec3[] immediate = windPostImpactDisplacements(steeringData, 0, 3);
		Vec3[] delayed = windPostImpactDisplacements(steeringData, 1, 3);
		var narrowObstacle = new net.minecraft.world.phys.AABB(.08, 0, -.1, .12, 1, .1);
		var body = new net.minecraft.world.phys.AABB(-.01, 0, -.01, .01, 1, .01);
		check(immediate[1].x() == 0 && immediate[2].x() > .08
				&& !PmbLocalEdgePlanner.sweptBoxOverlap(body, narrowObstacle,
						immediate[0], immediate[1], 0)
				&& PmbLocalEdgePlanner.sweptBoxOverlap(body.move(immediate[1]), narrowObstacle,
						immediate[1], immediate[2], 0),
				"delay zero cannot claim a first-displacement contact or avoid its next-displacement collision");
		check(delayed[1].x() == 0 && delayed[2].x() == 0 && delayed[3].x() > .08
				&& !PmbLocalEdgePlanner.sweptBoxOverlap(body.move(delayed[1]), narrowObstacle,
						delayed[1], delayed[2], 0)
				&& PmbLocalEdgePlanner.sweptBoxOverlap(body.move(delayed[2]), narrowObstacle,
						delayed[2], delayed[3], 0),
				"delay one shifts the contact and collision threshold by one complete displacement");
		for (int impactTicks : new int[] {1, 2})
			for (int delay : new int[] {0, 1}) {
				Vec3[] planned = windPostImpactDisplacements(steeringData, delay, 4);
				Vec3[] runtime = windRuntimePostImpactDisplacements(steeringData, impactTicks, delay, 4);
				check(java.util.Arrays.equals(planned, runtime),
						"planned and tail-submitted runtime displacement agree for impact="
								+ impactTicks + " delay=" + delay);
			}
		check(!PmbLocalEdgePlanner.withinConnectionHeight(0, 4, 2),
				"connection height rejects an endpoint four blocks higher");
		Vec3 pillarTop = new Vec3(1.5, 2, .5);
		check(PmbLocalEdgePlanner.airInterceptEndpoint(new Vec3(.5, 0, .5), pillarTop, 2) == pillarTop
				&& PmbLocalEdgePlanner.airInterceptEndpoint(new Vec3(.5, 0, .5), pillarTop, 1) == null
				&& PmbLocalEdgePlanner.airInterceptEndpoint(new Vec3(.5, 2, .5),
					new Vec3(1.5, 0, .5), 2) != null
				&& PmbLocalEdgePlanner.airInterceptEndpoint(new Vec3(.5, 0, .5),
					new Vec3(1.5, 0, .5), 2) != null,
				"air contact may be same-height after a blocked approach, including an occupied narrow ledge");
		check(PmbLocalEdgePlanner.airInterceptEndpoint(new Vec3(1.5, 0, .5), pillarTop, 2) == pillarTop
				&& PmbLocalEdgePlanner.sweptContact(
					new net.minecraft.world.phys.AABB(1.2, 0, .2, 1.8, 1.8, .8),
					new net.minecraft.world.phys.AABB(1.2, 2, .2, 1.8, 3.8, .8),
					new Vec3(1.5, 0, .5), new Vec3(1.5, 2, .5)),
				"same-XZ vertical contact is a geometric candidate even when occupied footing is not");
		PmbLocalEdgePlanner.Edge occupiedPillar = new PmbLocalEdgePlanner.Edge(
				new Vec3(.5, 0, .5), pillarTop, new Vec3(2.5, 0, .5));
		check(occupiedPillar.airIntercept() && occupiedPillar.safeDescent().distanceToSqr(pillarTop) > 0,
				"occupied pillar contact keeps its separate bounded safe-descent waypoint");
		check(PmbLocalEdgePlanner.sweptBoxOverlap(
				new net.minecraft.world.phys.AABB(0, 0, 0, .6, 1.8, .6),
				new net.minecraft.world.phys.AABB(1, 0, 0, 1.8, .7, .8),
				new Vec3(0, 0, 0), new Vec3(1.5, 0, 0), 0)
				&& !PmbLocalEdgePlanner.sweptBoxOverlap(
					new net.minecraft.world.phys.AABB(0, 0, 0, .6, 1.8, .6),
					new net.minecraft.world.phys.AABB(1, 0, 2, 1.8, .7, 2.8),
					new Vec3(0, 0, 0), new Vec3(1.5, 0, 0), 0),
				"a target vehicle crossing the flight corridor differs from a side vehicle");
		Vec3 nearShore = new Vec3(.5, 0, .5), farShore = new Vec3(4.5, 0, .5);
		check(PmbLocalEdgePlanner.flowingWaterOnConnection(nearShore, farShore,
				at -> at.getX() == 2 && at.getZ() == 0),
				"non-zero flow on the actual shoreline connection can qualify");
		check(!PmbLocalEdgePlanner.flowingWaterOnConnection(nearShore, farShore,
				at -> at.getX() == 2 && at.getZ() == 1)
				&& !PmbLocalEdgePlanner.flowingWaterOnConnection(nearShore, farShore,
					at -> false),
				"side water and still water do not qualify as crossed flow");
		check(new PmbLocalEdgePlanner.Edge(nearShore, farShore).kind()
				== PmbLocalEdgePlanner.EndpointKind.LANDING
				&& new PmbLocalEdgePlanner.Edge(nearShore, farShore, true).kind()
				== PmbLocalEdgePlanner.EndpointKind.AIR_CONTACT,
				"standing landing and occupied-target air contact retain distinct endpoint semantics");
		PmbAirTrackingAiData steering = new PmbAirTrackingAiData();
		steering.set(.1F, .3F, -1);
		Vec3 towardWaypoint = PmbAirTrackingController.steeredVelocity(
				new Vec3(0, 0, 0), new Vec3(.25, 1, 0), new Vec3(2, 0, 0), steering, false);
		Vec3 afterWaypoint = PmbAirTrackingController.steeredVelocity(
				new Vec3(3, 0, 0), new Vec3(.25, 1, 0), new Vec3(2, 0, 0), steering, false);
		check(Math.abs(towardWaypoint.x() - .3) < 1.0E-6
				&& Math.abs(afterWaypoint.x() - .15) < 1.0E-6,
				"wind feasibility shares capped waypoint steering and post-overshoot reversal with runtime");
		List<PmbLocalEdgePlanner.RimCandidate> budgetChoices = new ArrayList<>();
		for (int ray = 0; ray < 7; ray++)
			budgetChoices.add(new PmbLocalEdgePlanner.RimCandidate(
					new PmbLocalEdgePlanner.Edge(new Vec3(ray, 0, 0), farShore), ray, ray));
		budgetChoices.add(new PmbLocalEdgePlanner.RimCandidate(
				new PmbLocalEdgePlanner.Edge(nearShore, farShore, true), 100, 6));
		List<PmbLocalEdgePlanner.RimCandidate> fairChoices =
				PmbLocalEdgePlanner.prioritizeWindCandidates(budgetChoices);
		check(fairChoices.getFirst().edge().airIntercept()
				&& fairChoices.stream().limit(6).map(PmbLocalEdgePlanner.RimCandidate::ray)
					.distinct().count() >= 5,
				"six fast checks reserve air contact and distribute other checks across rays");
		List<PmbLocalEdgePlanner.RimCandidate> lowerChoices = new ArrayList<>();
		for (int landing = 0; landing < 4; landing++)
			for (int backtrack = 0; backtrack < 6; backtrack++)
				lowerChoices.add(new PmbLocalEdgePlanner.RimCandidate(
						new PmbLocalEdgePlanner.Edge(new Vec3(-backtrack * .5, 2, 0),
								new Vec3(landing + .5, 0, .5)), landing * 10 + backtrack * .1));
		List<PmbLocalEdgePlanner.RimCandidate> scheduled = PmbLocalEdgePlanner.prioritizeWindCandidates(lowerChoices);
		check(scheduled.stream().limit(4).map(candidate -> candidate.edge().landing().x()).distinct().count() == 4
				&& scheduled.get(4).edge().takeoff().x() == -1.5,
				"wall-to-trench fast checks visit distinct lower landings before meaningful takeoff retreats");
		check(PmbLocalEdgePlanner.TAKEOFF_BACKTRACKS.length == 6
				&& PmbLocalEdgePlanner.TAKEOFF_BACKTRACKS[1] == .5
				&& PmbLocalEdgePlanner.TAKEOFF_BACKTRACKS[5] == 3
				&& PmbLocalEdgePlanner.LOW_LANDING_OFFSETS.length == 9,
				"wall-to-lower-trench search retains half-block takeoffs and nine target-level landing probes");
		check(PmbLocalEdgePlanner.sweptContact(
				new net.minecraft.world.phys.AABB(0, 2, 0, .6, 3.8, .6),
				new net.minecraft.world.phys.AABB(1, 0, 0, 1.6, 1.8, .6),
				new Vec3(0, 2, 0), new Vec3(2, 0, 0))
				&& !PmbLocalEdgePlanner.sweptContact(
					new net.minecraft.world.phys.AABB(0, 2, 0, .6, 3.8, .6),
					new net.minecraft.world.phys.AABB(1, 0, 2, 1.6, 1.8, 2.6),
					new Vec3(0, 2, 0), new Vec3(2, 0, 0)),
				"descending airborne interception requires actual swept entity contact");
		Path route = new Path(List.of(new Node(0, 0, 0), new Node(0, 0, 1),
				new Node(0, 0, 2), new Node(1, 0, 2), new Node(2, 0, 2)),
				new net.minecraft.core.BlockPos(2, 0, 2), true);
		Path localMiss = new Path(List.of(new Node(0, 0, 0), new Node(1, 0, 0)),
				new net.minecraft.core.BlockPos(2, 0, 0), false);
		check(PmbTraversalController.isLocalVanillaMiss(localMiss, new Vec3(2.5, 0, .5))
				&& !PmbTraversalController.isLocalVanillaMiss(localMiss, new Vec3(2.5, 2, .5)),
				"a nearby same-height path miss keeps the savings gate; a two-block pillar does not");
		check(!PmbTraversalController.hasVanillaProgress(true, 2, 2, 9, 16)
				&& PmbTraversalController.hasVanillaProgress(true, 2, 3, 9, 16)
				&& PmbTraversalController.hasVanillaProgress(false, 2, 0, 9, 8),
				"backward water drift is not navigation progress, but node advance or real goal approach is");
		check(PmbTraversalController.completedWindApproachFailed(true, 9, true)
				&& !PmbTraversalController.completedWindApproachFailed(true, 2, true)
				&& !PmbTraversalController.completedWindApproachFailed(true, 9, false)
				&& !PmbTraversalController.completedWindApproachFailed(false, 9, true),
				"completed partial path away from takeoff is blocked whenever submitted, even after retry deadline");
		check(!PmbTraversalController.atWindTakeoff(new Vec3(0, 64, 0), new Vec3(0, 63, 0)),
				"a mob one block above the planned departure must not jump from the wrong floor");
		check(!PmbTraversalController.atWindTakeoff(new Vec3(1.3, 67, 0), new Vec3(0, 67, 0))
				&& PmbTraversalController.atWindTakeoff(new Vec3(.2, 67, .1), new Vec3(0, 67, 0)),
				"coarse navigation arrival is not precise wind departure arrival");
		Vec3 drift = new Vec3(.078694055, .248135999, .056669832);
		check(!PmbTraversalController.settledWindMotion(drift)
				&& PmbTraversalController.settledWindMotion(new Vec3(.002, -.08, -.002))
				&& drift.y() == .248135999,
				"launch waits for horizontal inertia without treating gravity as motion to clear");
		check(PmbTraversalController.provenSuffixNode(route, new Vec3(0.5, 0, 1.5),
				new Vec3(2.5, 0, 2.5)) == 1,
				"efficient wind may join a demonstrated vanilla route suffix");
		check(PmbTraversalController.provenSuffixNode(route, new Vec3(1.5, 0, 0.5),
				new Vec3(2.5, 0, 2.5)) < 0,
				"efficient wind rejects a nearby landing with no proven onward route");
		check(PmbTraversalController.provenSuffixNode(localMiss, new Vec3(2.5, 0, .5),
				new Vec3(2.5, 0, .5)) < 0,
				"an incomplete path across an obstacle is not proof of a walkable suffix");
		PmbLocalEdgePlanner.Edge shortEdge = new PmbLocalEdgePlanner.Edge(Vec3.ZERO, new Vec3(2, 0, 0));
		PmbLocalEdgePlanner.Edge longerEdge = new PmbLocalEdgePlanner.Edge(Vec3.ZERO, new Vec3(5, 0, 0));
		PmbLocalEdgePlanner.WindCandidateGate savings = new PmbLocalEdgePlanner.WindCandidateGate(
				candidate -> candidate.landing().x() >= 5);
		check(savings.reserveTrajectory() && savings.admit(shortEdge) == null,
				"insufficient first wind edge leaves the scan open");
		check(savings.reserveTrajectory() && savings.admit(longerEdge) == longerEdge,
				"later wind edge can satisfy the same scan's savings policy");
		PmbLocalEdgePlanner.WindCandidateGate rejected = new PmbLocalEdgePlanner.WindCandidateGate(
				candidate -> false);
		for (int attempt = 0; attempt < 10; attempt++)
			check(rejected.reserveTrajectory() && rejected.admit(shortEdge) == null,
					"rejected wind candidates consume one of ten bounded checks");
		check(!rejected.reserveTrajectory(), "all rejected wind edges end after ten trajectory checks");
		var rimOffsets = PmbLocalEdgePlanner.rimOffsets();
		check(rimOffsets.size() == 48 && rimOffsets.contains(new net.minecraft.core.BlockPos(3, 0, 0))
				&& rimOffsets.contains(new net.minecraft.core.BlockPos(-6, 0, -6)),
				"bounded rim search includes canopy edges beyond the two-block fast probes");
		PmbLocalEdgePlanner.WindCandidateGate splitGate = new PmbLocalEdgePlanner.WindCandidateGate(
				candidate -> false);
		for (int attempt = 0; attempt < 6; attempt++) check(splitGate.reserveFastTrajectory(),
				"fast wind search may use six trajectories");
		check(!splitGate.reserveFastTrajectory() && splitGate.fastExhausted(),
				"fast wind search reserves four trajectory checks for canopy rim candidates");
		for (int attempt = 0; attempt < 4; attempt++) check(splitGate.reserveTrajectory(),
				"canopy rim search retains four swept-flight checks");
		check(PmbSkillSchema.byId("traversal.wind_charge").hasField("minHorizontalConnectionDistance")
				&& !PmbSkillSchema.byId("traversal.ender_pearl").hasField("minHorizontalConnectionDistance"),
				"minimum horizontal connection is a wind-only command field");
		check(PmbSkillSchema.byId("wind_charge").field("bouncePostJumpTicks").max() == 10
				&& PmbSkillSchema.byId("traversal.wind_charge").field("postJumpTicks").max() == 10
				&& !PmbSkillSchema.byId("traversal.ender_pearl").hasField("postJumpTicks")
				&& !PmbSkillSchema.byId("fall_rescue.wind_charge").hasField("postJumpTicks"),
				"post-jump timing belongs only to active bounce and conditional wind traversal");
		PmbPathfindingData path = new PmbPathfindingData();
		check(!path.hasExplicitStrategy(), "default pathfinding does not authorize expensive danger planning");
		path.setStrategy(PmbSkillScheduler.Strategy.COMBAT,
				new PmbPathfindingData.Strategy(PmbPathfindingData.RouteMode.EFFICIENT,
						PmbPathfindingData.DangerousFallPolicy.ENABLE, false));
		check(path.hasExplicitStrategy(), "explicit route strategy authorizes path planning");
		path.clear();
		check(!path.hasExplicitStrategy(), "clearing route strategy removes planning authorization");
	}
	private static Vec3[] windPostImpactDisplacements(PmbAirTrackingAiData data, int delay, int ticks) {
		Vec3[] positions = new Vec3[ticks + 1];
		positions[0] = Vec3.ZERO;
		Vec3 velocity = Vec3.ZERO;
		for (int tick = 0; tick < ticks; tick++) {
			if (tick >= PmbWindLaunchPhysics.firstSteeredPostImpactDisplacement(delay))
				velocity = PmbAirTrackingController.steeredVelocity(positions[tick], velocity,
						new Vec3(1, 0, 0), data, false);
			positions[tick + 1] = positions[tick].add(velocity);
			velocity = velocity.scale(.91);
		}
		return positions;
	}
	private static Vec3[] windRuntimePostImpactDisplacements(PmbAirTrackingAiData data,
			int impactTicks, int delay, int ticks) {
		Vec3[] positions = new Vec3[ticks + 1];
		positions[0] = Vec3.ZERO;
		Vec3 position = Vec3.ZERO, velocity = Vec3.ZERO;
		int remaining = PmbWindLaunchPhysics.runtimeSteeringDelay(impactTicks, delay);
		boolean queued = false;
		for (int absoluteTick = 1; absoluteTick <= impactTicks + ticks; absoluteTick++) {
			if (queued) velocity = PmbAirTrackingController.steeredVelocity(
					position, velocity, new Vec3(1, 0, 0), data, false);
			if (absoluteTick > impactTicks) {
				position = position.add(velocity);
				velocity = velocity.scale(.91);
				positions[absoluteTick - impactTicks] = position;
			}
			queued = false;
			if (remaining > 0) remaining--;
			else queued = true;
		}
		return positions;
	}
	private static void testInventoryScatterDrops() throws Exception {
		PmbInventory inventory = new PmbInventory();
		inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY,
				new CompoundTag()));
		check(!inventory.usesScatteredDeathDrops(), "missing inventory root defaults ScatterDrops false");

		CompoundTag missingField = new CompoundTag();
		CompoundTag missingFieldInventory = new CompoundTag();
		missingFieldInventory.putInt("Slots", 1);
		missingField.put(PmbInventory.TAG, missingFieldInventory);
		inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, missingField));
		check(!inventory.usesScatteredDeathDrops(), "missing ScatterDrops field defaults false");

		for (boolean value : List.of(false, true)) {
			CompoundTag root = new CompoundTag();
			CompoundTag storedInventory = new CompoundTag();
			storedInventory.putInt("Slots", 1);
			storedInventory.putBoolean("ScatterDrops", value);
			root.put(PmbInventory.TAG, storedInventory);
			inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root));
			check(inventory.usesScatteredDeathDrops() == value, "explicit ScatterDrops value reads");

			TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
			inventory.write(output);
			check(output.buildResult().getCompound(PmbInventory.TAG).orElseThrow()
					.getBoolean("ScatterDrops").orElseThrow() == value, "ScatterDrops value writes");

			PmbInventory copied = new PmbInventory();
			inventory.copyTo(copied);
			check(copied.usesScatteredDeathDrops() == value, "ScatterDrops value copies");
		}

		PmbInventory omitted = new PmbInventory();
		field(PmbInventory.class, "scatterDrops").setBoolean(omitted, true);
		TagValueOutput emptyOutput = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		omitted.write(emptyOutput);
		check(!emptyOutput.buildResult().contains(PmbInventory.TAG),
				"empty zero-slot inventory keeps root omission behavior");
		check(!new PmbInventory().usesScatteredDeathDrops(),
				"default death-drop branch remains stationary");
		check(omitted.usesScatteredDeathDrops(), "enabled death-drop branch selects player-style scatter");
	}
	private static void testInventoryCapacityAndSources() throws Exception {
		PmbInventory empty = new PmbInventory();
		check(empty.slots() == 0 && empty.capacity() == 0,
				"zero-slot inventory allocates no backing slots");
		for (int capacity : List.of(27, 128, 255, 256)) {
			PmbInventory inventory = inventory(capacity);
			int count = capacity == 256 ? 16 : capacity == 255 ? 15 : capacity == 128 ? 8 : 7;
			inventory.set(capacity, new ItemStack(Items.ARROW, count));
			PmbInventory loaded = roundTrip(inventory);
			check(loaded.slots() == capacity && loaded.capacity() == capacity,
					"inventory round trip preserves dynamic capacity " + capacity);
			check(loaded.get(capacity).is(Items.ARROW) && loaded.get(capacity).getCount() == count,
					"inventory round trip preserves highest slot item and count " + capacity);
		}

		PmbInventory clamped = inventoryFromRoot(inventoryRoot(257));
		check(clamped.slots() == 256 && clamped.capacity() == 256,
				"Slots 257 clamps to 256 dynamic backing slots");

		PmbInventory slotDonor = inventory(256);
		slotDonor.set(256, new ItemStack(Items.DIAMOND));
		CompoundTag invalidSlotRoot = writeInventory(slotDonor);
		invalidSlotRoot.getCompoundOrEmpty(PmbInventory.TAG).getListOrEmpty("Items")
				.getCompoundOrEmpty(0).putInt("Slot", 257);
		PmbInventory invalidSlot = inventoryFromRoot(invalidSlotRoot);
		check(invalidSlot.get(256).isEmpty(), "external Slot 257 is ignored");

		PmbInventory shrinkDonor = inventory(256);
		shrinkDonor.set(1, new ItemStack(Items.ARROW, 60));
		shrinkDonor.set(256, new ItemStack(Items.ARROW, 4));
		CompoundTag shrinkRoot = writeInventory(shrinkDonor);
		shrinkRoot.getCompoundOrEmpty(PmbInventory.TAG).putInt("Slots", 1);
		PmbInventory shrunk = inventoryFromRoot(shrinkRoot);
		check(shrunk.capacity() == 1 && shrunk.get(1).getCount() == 64,
				"capacity reduction merges high-slot contents into enabled low slots");
		check(shrunk.get(256).isEmpty(), "capacity reduction exposes no disabled high slot");
		ItemStack remainder = shrunk.add(new ItemStack(Items.DIAMOND, 3));
		check(remainder.is(Items.DIAMOND) && remainder.getCount() == 3,
				"full resized inventory preserves the complete overflow remainder");

		PmbInventory highPickup = inventory(256);
		for (int slot = 1; slot <= 27; slot++) highPickup.set(slot, new ItemStack(Items.ARROW, 64));
		check(highPickup.add(new ItemStack(Items.DIAMOND, 5)).isEmpty()
				&& highPickup.get(28).is(Items.DIAMOND) && highPickup.get(28).getCount() == 5,
				"pickup-style add continues into slots above 27");
		for (int slot = 28; slot <= 256; slot++) highPickup.set(slot, new ItemStack(Items.DIAMOND, 64));
		ItemStack highRemainder = highPickup.add(new ItemStack(Items.STICK, 2));
		check(highRemainder.is(Items.STICK) && highRemainder.getCount() == 2,
				"full 256-slot pickup preserves its complete remainder");

		PmbInventory overflowDonor = inventory(256);
		overflowDonor.set(1, new ItemStack(Items.ARROW, 64));
		overflowDonor.set(256, new ItemStack(Items.DIAMOND, 7));
		CompoundTag overflowRoot = writeInventory(overflowDonor);
		overflowRoot.getCompoundOrEmpty(PmbInventory.TAG).putInt("Slots", 1);
		List<ItemStack> overflowDrops = new ArrayList<>();
		PmbInventory overflowLoaded = new PmbInventory();
		overflowLoaded.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, overflowRoot),
				overflowDrops::add);
		check(overflowLoaded.get(1).is(Items.ARROW) && overflowLoaded.get(1).getCount() == 64
				&& overflowDrops.size() == 1 && overflowDrops.getFirst().is(Items.DIAMOND)
				&& overflowDrops.getFirst().getCount() == 7,
				"load shrink sends the exact unmerged high-slot remainder to overflow dropping");

		PmbInventory duplicateDonor = inventory(2);
		duplicateDonor.set(1, new ItemStack(Items.ARROW, 60));
		duplicateDonor.set(2, new ItemStack(Items.ARROW, 4));
		CompoundTag duplicateRoot = writeInventory(duplicateDonor);
		duplicateRoot.getCompoundOrEmpty(PmbInventory.TAG).getListOrEmpty("Items")
				.getCompoundOrEmpty(1).putInt("Slot", 1);
		PmbInventory duplicateLoaded = inventoryFromRoot(duplicateRoot);
		check(duplicateLoaded.get(1).is(Items.ARROW) && duplicateLoaded.get(1).getCount() == 64
				&& duplicateLoaded.get(2).isEmpty(),
				"duplicate Slot entries retain the first position and merge later contents through overflow");

		PmbInventory zeroSlotDonor = inventory(1);
		zeroSlotDonor.set(1, new ItemStack(Items.DIAMOND, 6));
		CompoundTag zeroSlotRoot = writeInventory(zeroSlotDonor);
		zeroSlotRoot.getCompoundOrEmpty(PmbInventory.TAG).getListOrEmpty("Items")
				.getCompoundOrEmpty(0).putInt("Slot", 0);
		PmbInventory zeroSlotLoaded = inventoryFromRoot(zeroSlotRoot);
		check(zeroSlotLoaded.get(1).isEmpty(), "explicit external Slot 0 is ignored");

		PmbActivationSources activation = new PmbActivationSources();
		activation.read(sourceInput(PmbActivationSources.FIELD,
				"inventory", "inventory:256", "inventory:1..256", "inventory:0", "inventory:257", "inventory:9..7"));
		check(activation.sources().equals(List.of(
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 256),
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 256, 256),
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 256))),
				"FetchSource accepts 256 and rejects zero, 257, and reversed ranges");
		TagValueOutput activationOutput = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		activation.write(activationOutput);
		check(activationOutput.buildResult().toString().contains("\"inventory\""),
				"full FetchSource range formats as inventory alias");

		PmbAmmoSources ammunition = new PmbAmmoSources();
		ammunition.read(sourceInput(PmbAmmoSources.FIELD,
				"inventory", "inventory:256", "inventory:1..256", "inventory:0", "inventory:257", "inventory:9..7"));
		check(ammunition.sources().equals(activation.sources()),
				"AmmoSource shares the 1..256 validation and inventory alias");
		check(PmbSkillSchema.validSource("inventory:256") && PmbSkillSchema.validSource("inventory:1..256")
				&& !PmbSkillSchema.validSource("inventory:0") && !PmbSkillSchema.validSource("inventory:257")
				&& !PmbSkillSchema.validSource("inventory:9..7"),
				"command schema enforces the expanded source range");

		Fixture mob = fixture();
		mob.inventory = inventory(256);
		mob.inventory.set(256, new ItemStack(Items.ARROW, 2));
		PmbAmmoSources slot256Ammo = new PmbAmmoSources();
		slot256Ammo.read(sourceInput(PmbAmmoSources.FIELD, "inventory:256"));
		PmbAmmoAccess.Source found = PmbAmmoAccess.find(mob, slot256Ammo, stack -> stack.is(Items.ARROW));
		check(found != null && found.inventorySlot() == 256 && found.consume(mob)
				&& mob.inventory.get(256).getCount() == 1,
				"slot 256 ammunition is found and consumed from its real slot");

		PmbInventory converted = new PmbInventory();
		mob.inventory.copyTo(converted);
		check(converted.capacity() == 256 && converted.get(256).getCount() == 1,
				"conversion copy allocates and preserves slot 256");

		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		mob.inventory.set(256, new ItemStack(Items.BOW));
		PmbActivationSources slot256Fetch = sources(
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 256, 256));
		PmbSkillItemAccess.Resolved slot256Resolved = resolve(mob, slot256Fetch, PmbPreferredHand.MAIN);
		PmbSkillItemAccess.ActionBinding slot256Binding = PmbSkillItemAccess.acquire(mob, slot256Resolved);
		check(slot256Binding != null && slot256Binding.inventorySlot() == 256
				&& mob.getMainHandItem().is(Items.BOW) && mob.inventory.get(256).is(Items.STICK),
				"FetchSource acquires slot 256 through the permanent inventory swap path");

		PmbInventory death = configuredInventory(256, 1.0F, false);
		death.set(1, new ItemStack(Items.ARROW, 2));
		death.set(256, new ItemStack(Items.DIAMOND, 7));
		List<PmbInventory.DeathDrop> deathDrops = death.drainDeathDrops(RandomSource.create(2008L), true);
		check(deathDrops.size() == 2
				&& deathDrops.get(0).stack().is(Items.ARROW) && deathDrops.get(0).stack().getCount() == 2
				&& deathDrops.get(1).stack().is(Items.DIAMOND) && deathDrops.get(1).stack().getCount() == 7
				&& deathDrops.stream().noneMatch(PmbInventory.DeathDrop::scattered)
				&& death.get(1).isEmpty() && death.get(256).isEmpty(),
				"death processing reaches slot 256 and preserves each whole stack in stationary mode");
		PmbInventory scatteredDeath = configuredInventory(256, 1.0F, true);
		scatteredDeath.set(256, new ItemStack(Items.DIAMOND, 9));
		List<PmbInventory.DeathDrop> scatteredDrops = scatteredDeath.drainDeathDrops(RandomSource.create(2008L), true);
		check(scatteredDrops.size() == 1 && scatteredDrops.getFirst().scattered()
				&& scatteredDrops.getFirst().stack().getCount() == 9,
				"slot 256 death processing preserves whole-stack scattered branch selection");
		PmbInventory suppressedDeath = configuredInventory(256, 1.0F, true);
		suppressedDeath.set(256, new ItemStack(Items.DIAMOND, 11));
		check(suppressedDeath.drainDeathDrops(RandomSource.create(2008L), false).isEmpty()
				&& suppressedDeath.get(256).isEmpty(),
				"doMobLoot false still drains slot 256 without producing a death drop");
	}

	private static PmbInventory inventory(int slots) throws Exception {
		return inventoryFromRoot(inventoryRoot(slots));
	}
	private static PmbInventory configuredInventory(int slots, float dropChance, boolean scatterDrops) throws Exception {
		CompoundTag root = inventoryRoot(slots);
		CompoundTag child = root.getCompoundOrEmpty(PmbInventory.TAG);
		child.putFloat("DropChance", dropChance);
		child.putBoolean("ScatterDrops", scatterDrops);
		return inventoryFromRoot(root);
	}
	private static PmbInventory inventoryFromRoot(CompoundTag root) throws Exception {
		PmbInventory inventory = new PmbInventory();
		inventory.read(fixture(), TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root));
		return inventory;
	}
	private static CompoundTag inventoryRoot(int slots) {
		CompoundTag root = new CompoundTag();
		CompoundTag child = new CompoundTag();
		child.putInt("Slots", slots);
		root.put(PmbInventory.TAG, child);
		return root;
	}
	private static CompoundTag writeInventory(PmbInventory inventory) {
		TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		inventory.write(output);
		return output.buildResult();
	}
	private static PmbInventory roundTrip(PmbInventory inventory) throws Exception {
		return inventoryFromRoot(writeInventory(inventory));
	}
	private static ValueInput sourceInput(String field, String... values) {
		CompoundTag root = new CompoundTag();
		ListTag list = new ListTag();
		for (String value : values) list.add(StringTag.valueOf(value));
		root.put(field, list);
		return TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root);
	}
	private static void testEquipment() throws Exception {
		Fixture mob = fixture();
		PmbActivationSources sources = sources(new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 3));
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 4));
		mob.inventory.set(1, new ItemStack(Items.BOW));
		var arrow = PmbAmmoAccess.find(mob, stack -> stack.is(Items.ARROW));
		var resolved = resolve(mob, sources, PmbPreferredHand.MAIN);
		check(resolved.inventorySlot() == 1, "first configured inventory slot");
		var action = PmbSkillItemAccess.acquire(mob, resolved);
		arrow.followEquipmentSwap(action);
		check(mob.getMainHandItem().is(Items.BOW) && mob.inventory.get(1).is(Items.ARROW), "permanent inventory exchange");
		check(arrow.inventorySlot() == 1 && arrow.matches(mob), "arrow follows displaced stack");
		check(arrow.consume(mob) && mob.inventory.get(1).getCount() == 3, "mapped inventory arrow consumed exactly once");
		mob.scheduler.bind("bow", action);
		mob.scheduler.release(mob, "bow");
		check(mob.getMainHandItem().is(Items.BOW), "ending action does not restore");
		check(resolve(mob, sources(), PmbPreferredHand.MAIN).transfer() == PmbSkillItemAccess.Transfer.DIRECT,
				"destination bow bypasses empty FetchSource");
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.ARROW, 4));
		mob.items.put(InteractionHand.OFF_HAND, new ItemStack(Items.BOW));
		arrow = PmbAmmoAccess.find(mob, stack -> stack.is(Items.ARROW));
		var hands = sources(new PmbActivationSources.Source(PmbActivationSources.Kind.OFF_HAND, 0, 0));
		resolved = resolve(mob, hands, PmbPreferredHand.MAIN);
		check(resolved.resources().length == 2, "cross-hand swap claims both hands");
		action = PmbSkillItemAccess.acquire(mob, resolved);
		arrow.followEquipmentSwap(action);
		check(arrow.hand() == InteractionHand.OFF_HAND && arrow.matches(mob), "arrow follows cross-hand exchange");
		check(arrow.consume(mob) && mob.getOffhandItem().getCount() == 3, "mapped offhand arrow consumed exactly once");
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
		mob.inventory.set(1, new ItemStack(Items.BOW));
		resolved = resolve(mob, sources, PmbPreferredHand.MAIN);
		mob.inventory.set(1, new ItemStack(Items.DIAMOND));
		check(PmbSkillItemAccess.acquire(mob, resolved) == null && mob.getMainHandItem().is(Items.STICK)
				&& mob.inventory.get(1).is(Items.DIAMOND), "changed source cannot be overwritten");
		mob.inventory.set(1, new ItemStack(Items.BOW));
		resolved = resolve(mob, sources, PmbPreferredHand.MAIN);
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
		check(PmbSkillItemAccess.acquire(mob, resolved) == null && mob.inventory.get(1).is(Items.BOW),
				"changed destination cannot be overwritten");
		@SuppressWarnings("unchecked") Map<PmbSkillScheduler.Resource, String> sustained =
				(Map<PmbSkillScheduler.Resource, String>) field(PmbSkillScheduler.class, "sustained").get(mob.scheduler);
		sustained.put(PmbSkillScheduler.Resource.MAIN_HAND, "shield");
		check(resolve(mob, sources, PmbPreferredHand.MAIN).hand() == InteractionHand.OFF_HAND, "ongoing owner triggers fallback");
		check(resolve(mob, sources, PmbPreferredHand.MAIN_ENFORCE) == null, "enforce rejects occupied destination");
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		var ordered = sources(new PmbActivationSources.Source(PmbActivationSources.Kind.MAIN_HAND, 0, 0),
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 3));
		check(resolve(mob, ordered, PmbPreferredHand.OFF).inventorySlot() == 1, "busy source skipped");
		sustained.clear();
		mob.scheduler.claim("other_candidate", PmbSkillScheduler.Resource.MAIN_HAND);
		check(resolve(mob, sources, PmbPreferredHand.MAIN).hand() == InteractionHand.MAIN_HAND,
				"same-tick competing claim does not change hand selection");

	}
	private static void testParameterSuggestions() throws Exception {
		var argument = new com.pmb.command.PmbSkillParametersArgument("bow", false);
		String prefix = "pmb skill active @s bow insert ";
		var empty = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix, prefix.length())).join();
		check(empty.getList().size() == 1 && empty.getList().getFirst().getText().equals("[")
				&& empty.getList().getFirst().getRange().getStart() == prefix.length(),
				"empty parameter entry suggests only opening bracket at argument offset");
		for (String invalid : List.of("enable", " [enable="))
			check(argument.listSuggestions(null,
					new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + invalid, prefix.length()))
					.join().isEmpty(), "non-bracket parameter prefix offers no suggestions");
		var values = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + "[enable=", prefix.length())).join();
		check(values.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList()
				.equals(List.of("0b", "1b")), "bracketed boolean value suggestions preserved");
		var sourceValues = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(
						prefix + "[FetchSource=[\"inventory:2", prefix.length())).join();
		check(sourceValues.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList()
				.contains("\"inventory:256\""), "expanded slot 256 is offered by source autocomplete");
		var rescueArgument = new com.pmb.command.PmbSkillParametersArgument("fall_rescue", false);
		var rescuePatch = rescueArgument.parse(new com.mojang.brigadier.StringReader(
				"[enable=1b,singleMethodPerFall=0b,methods=[\"wind_charge\",\"ender_pearl\"]]"));
		check(rescuePatch.values().get("singleMethodPerFall").asByte().orElse((byte) -1) == 0
				&& rescuePatch.values().get("methods") instanceof ListTag,
				"fall_rescue parent command parses only ordered methods and shared parameters");
		PmbSkillSchema.byId("fall_rescue").validate(PmbSkillSchema.byId("fall_rescue").defaultTag());
		var singleMethodSuggestions = rescueArgument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + "[singleMethodPerFall=", prefix.length())).join();
		check(singleMethodSuggestions.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList()
				.equals(List.of("0b", "1b")), "singleMethodPerFall offers typed boolean completion");
		var windMethod = new com.pmb.command.PmbSkillParametersArgument("fall_rescue.wind_charge", false);
		var methodPatch = windMethod.parse(new com.mojang.brigadier.StringReader(
				"[enable=1b,preferredHand=\"off\",FetchSource=[\"offhand\"],successChance=0.75f,cooldownTicks=20,rescueLeadTicks=4]"));
		check(methodPatch.values().get("successChance").asFloat().orElseThrow() == .75F
				&& !PmbSkillSchema.byId("fall_rescue").hasField("wind_charge")
				&& !PmbSkillSchema.byId("fall_rescue.wind_charge").hasField("methods"),
				"dotted method command has an independent schema");
		var methodSuggestions = windMethod.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + "[succ", prefix.length())).join();
		check(methodSuggestions.getList().stream().anyMatch(value -> value.getText().equals("successChance=")),
				"dotted rescue method offers independent field completion");
		for (String invalid : List.of("[unknown=1b]", "[doConsume=1b,doConsume=0b]",
				"[cooldownTicks=1.0f]", "[successChance=2.0f]")) {
			boolean rejected = false;
			try { windMethod.parse(new com.mojang.brigadier.StringReader(invalid)); }
			catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
			check(rejected, "invalid dotted rescue input rejected: " + invalid);
		}
	}
	private static void testPriorityArgument() throws Exception {
		var argument = new com.pmb.command.PmbPriorityListArgument();
		var parsed = argument.parse(new com.mojang.brigadier.StringReader(
				"[\"mace\",[\"ender_pearl\",\"bow\"],\"vanilla\"]"));
		check(parsed.equals(List.of(List.of("mace"), List.of("ender_pearl", "bow"), List.of("vanilla"))),
				"priority command parses strict mixed quoted list");
		for (String invalid : List.of("[mace,\"vanilla\"]", "[\"mace\"]",
				"[\"mace\",\"mace\",\"vanilla\"]", "[[],\"vanilla\"]")) {
			boolean rejected = false;
			try { argument.parse(new com.mojang.brigadier.StringReader(invalid)); }
			catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) { rejected = true; }
			check(rejected, "priority command rejects invalid input: " + invalid);
		}
		String prefix = "pmb skill priority @s set combat ";
		var opening = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix, prefix.length())).join();
		check(opening.getList().stream().anyMatch(value -> value.getText().equals("[")),
				"priority argument suggests opening bracket");
		String outer = "[\"mace\",";
		var outerSuggestions = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + outer, prefix.length())).join();
		check(outerSuggestions.getList().stream().anyMatch(value -> value.getText().equals("[")),
				"outer priority element suggests an equal-tier group");
		String grouped = "[[\"mace\",";
		var groupSuggestions = argument.listSuggestions(null,
				new com.mojang.brigadier.suggestion.SuggestionsBuilder(prefix + grouped, prefix.length())).join();
		check(groupSuggestions.getList().stream().noneMatch(value -> value.getText().equals("["))
				&& groupSuggestions.getList().stream().anyMatch(value -> value.getText().equals("\"bow\"")),
				"equal-tier group suggests skills but never a nested group");
		check(outerSuggestions.getList().stream().anyMatch(value -> value.getText().equals("\"fall_rescue\"")),
				"priority autocomplete derives and offers conditional skills");
	}
	private static void testMovement() throws Exception {
		Fixture mob = fixture(); var movement = mob.scheduler.movement(); List<String> trace = new ArrayList<>();
		field(PmbBowAiData.class, "configured").setBoolean(mob.ai.bow(), true);
		mob.tickCount = 20;
		movement.apply(mob, mob.scheduler);
		check(!movement.blocksVanillaBowMovement(mob), "no bow action leaves vanilla movement available");
		offer(mob, "bow", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.PASSIVE, 100, trace);
		offer(mob, "wind", PmbMovementController.Type.VELOCITY_MODIFIER, PmbMovementController.Tier.ACTIVE, 1, trace);
		offer(mob, "retreat", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.RETREAT, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(trace.equals(List.of("retreat", "wind")), "retreat wins, velocity modifier remains additive");
		check(movement.snapshot().tick() == mob.tickCount
				&& "retreat".equals(movement.snapshot().locomotionOwner())
				&& movement.snapshot().velocityModifierOwners().equals(List.of("wind")),
				"movement debug snapshot records actual applied owners");
		trace.clear();
		offer(mob, "active", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		offer(mob, "passive", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.PASSIVE, 999, trace);
		movement.apply(mob, mob.scheduler);
		check(trace.equals(List.of("active")), "active beats higher-rank passive movement");
		trace.clear();
		offer(mob, "stale", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		mob.tickCount += 2; movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "stale epoch is discarded");
		offer(mob, "cancelled", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.cancel("cancelled"); movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "cancel removes pending movement");
		movement.submit(mob, "invalid", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE,
				PmbSkillScheduler.Category.MAIN, 0, () -> false, () -> trace.add("invalid"));
		movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "invalid target predicate discarded");
		offer(mob, "stop", PmbMovementController.Type.HARD_STOP, PmbMovementController.Tier.PASSIVE, 0, trace);
		offer(mob, "retreat", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.RETREAT, 99, trace);
		offer(mob, "modifier", PmbMovementController.Type.VELOCITY_MODIFIER, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(trace.equals(List.of("stop")), "hard stop wins and suppresses velocity modifiers");
		trace.clear();
		Fixture oldTarget = fixture(), newTarget = fixture();
		mob.combat = oldTarget;
		movement.submit(mob, "old_wind", PmbMovementController.Type.VELOCITY_MODIFIER,
				PmbMovementController.Tier.ACTIVE, PmbSkillScheduler.Category.THROW, 1,
				() -> PmbMovementController.hasCurrentAuthority(mob, oldTarget, false), () -> trace.add("old"));
		mob.avoid = newTarget;
		movement.apply(mob, mob.scheduler);
		check(trace.isEmpty(), "retreat transition invalidates prior combat steering before skill tick");
		mob.avoid = null; mob.combat = newTarget;
		check(!PmbMovementController.hasCurrentAuthority(mob, oldTarget, false), "combat target change invalidates steering");
		mob.avoid = newTarget;
		check(!PmbMovementController.hasCurrentAuthority(mob, oldTarget, true), "avoid target change invalidates steering");
		mob.avoid = null;
		offer(mob, "wind", PmbMovementController.Type.VELOCITY_MODIFIER, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(!movement.blocksVanillaBowMovement(mob), "non-bow action cannot intercept vanilla bow movement");
		offer(mob, "bow", PmbMovementController.Type.LOCOMOTION, PmbMovementController.Tier.ACTIVE, 0, trace);
		movement.apply(mob, mob.scheduler);
		check(movement.blocksVanillaBowMovement(mob), "active bow movement winner enables interception");
		movement.cancel("bow");
		check(!movement.blocksVanillaBowMovement(mob), "cancelled winner cannot retain movement interception");
	}
	private static void testSkillDebugDiagnostics() throws Exception {
		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		prepareDebug(scheduler, true);
		scheduler.claim("shield", PmbSkillScheduler.Resource.USE_ITEM);
		scheduler.offer("bow", "line", PmbSkillScheduler.Category.MAIN, 10, () -> false, () -> {},
				PmbSkillScheduler.Resource.MAIN_HAND);
		scheduler.offer("pearl", "throw", PmbSkillScheduler.Category.THROW, 20, () -> true, () -> {},
				PmbSkillScheduler.Resource.USE_ITEM);
		scheduler.offer("shield", "raise", PmbSkillScheduler.Category.OFF, 10, () -> true, () -> {},
				PmbSkillScheduler.Resource.OFF_HAND);
		resolveCandidates(scheduler);
		List<PmbSkillDebugSnapshot.CandidateAttempt> attempts = debugAttempts(scheduler);
		check(attempts.stream().anyMatch(a -> a.label().equals("line")
				&& a.status() == PmbSkillDebugSnapshot.AttemptStatus.PRECLAIM_REJECTED),
				"debug records failed pre-claim without re-running it");
		check(attempts.stream().anyMatch(a -> a.label().equals("throw")
				&& a.status() == PmbSkillDebugSnapshot.AttemptStatus.RESOURCE_BLOCKED
				&& a.blockedResource() == PmbSkillScheduler.Resource.USE_ITEM
				&& "shield".equals(a.blocker())), "debug records resource blocker and owner");
		check(attempts.stream().anyMatch(a -> a.label().equals("raise")
				&& a.status() == PmbSkillDebugSnapshot.AttemptStatus.COMMIT_FAILED),
				"admitted action without execution marker becomes commit failed");

		PmbSkillScheduler[] current = {new PmbSkillScheduler()};
		prepareDebug(current[0], true);
		current[0].offer("mace", "smash", PmbSkillScheduler.Category.MAIN, 20, () -> true,
				() -> current[0].markCurrentCandidateExecuted("mace"), PmbSkillScheduler.Resource.SMASH);
		resolveCandidates(current[0]);
		check(debugAttempts(current[0]).getFirst().status() == PmbSkillDebugSnapshot.AttemptStatus.EXECUTED,
				"matching winner execution marker is recorded");
		current[0] = new PmbSkillScheduler();
		prepareDebug(current[0], true);
		current[0].offer("mace", "smash", PmbSkillScheduler.Category.MAIN, 20, () -> true,
				() -> current[0].markCurrentCandidateExecuted("bow"), PmbSkillScheduler.Resource.SMASH);
		resolveCandidates(current[0]);
		check(debugAttempts(current[0]).getFirst().status() == PmbSkillDebugSnapshot.AttemptStatus.COMMIT_FAILED,
				"mismatched execution marker is safely ignored");

		PmbSkillScheduler.Resource[] mutable = {PmbSkillScheduler.Resource.LOOK};
		var immutable = PmbSkillDebugSnapshot.CandidateAttempt.admitted("wind", "throw",
				PmbSkillScheduler.Category.THROW, 10, mutable);
		mutable[0] = PmbSkillScheduler.Resource.SMASH;
		check(immutable.resources().equals(List.of(PmbSkillScheduler.Resource.LOOK)),
				"candidate diagnostic copies mutable resource arrays");

		UUID debugUuid = UUID.randomUUID();
		var debugSnapshot = new PmbSkillDebugSnapshot(42, debugUuid, "minecraft:husk", 100,
				PmbSkillScheduler.Strategy.COMBAT, UUID.randomUUID(),
				List.of(new PmbSkillDebugSnapshot.SkillState("bow", true, true, true,
						"line=7,arc=11", "-"),
						new PmbSkillDebugSnapshot.SkillState("wind_charge", true, true, false,
								"throw=0,bounce=0", "tracking=infinite,lastClearReason=active"),
						new PmbSkillDebugSnapshot.SkillState("shield", true, false, false, "check=0", "-"),
						new PmbSkillDebugSnapshot.SkillState("mace", false, false, false, "smash=0", "-")),
				List.of(PmbSkillDebugSnapshot.CandidateAttempt.resourceBlocked("bow", "line",
						PmbSkillScheduler.Category.MAIN, 10,
						new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.USE_ITEM},
						PmbSkillScheduler.Resource.USE_ITEM, "shield"),
						PmbSkillDebugSnapshot.CandidateAttempt.admitted("mace", "smash",
								PmbSkillScheduler.Category.MAIN, 20, new PmbSkillScheduler.Resource[0])
								.commitFailed(true)),
				Map.of(), Map.of(PmbSkillScheduler.Resource.USE_ITEM, "shield"),
				Map.of("bow", "hand=MAIN_HAND"), null, true, List.of("bow"), false, "-");
		var formatted = debugSnapshot.formatForLog(103);
		check(formatted.startsWith("PMB SKILL DEBUG\nENTITY: minecraft:husk#42 uuid=" + debugUuid),
				"skill debug log formatter starts with prominent entity identity");
		check(formatted.contains("\nSKILLS:\n  ACTIVE:\n    bow [ACTIVE] cooldown{line=7,arc=11}")
				&& formatted.contains("    wind_charge [READY]")
				&& formatted.contains("\n  CONDITIONAL: []")
				&& formatted.contains("tracking=infinite,lastClearReason=active")
				&& !formatted.contains("shield [") && !formatted.contains("mace ["),
				"skill debug log shows only enabled skills and highlights active state");
		check(formatted.contains("\nLAST ATTEMPTS:\n  bow/line [RESOURCE_BLOCKED]")
				&& formatted.contains("mace/smash [COMMIT_FAILED_AFTER_PREEMPT]")
				&& formatted.contains("\nRESOURCES:\n  sustained={}\n  current={USE_ITEM=shield}")
				&& formatted.contains("\nBINDINGS:\n  bow=hand=MAIN_HAND")
				&& formatted.contains("\nMELEE: SUPPRESSED reasons=[bow]")
				&& formatted.contains("\nCONTEXT:\n  snapshotTick=100 ageTicks=3 strategy=COMBAT"),
				"skill debug log formatter keeps ordered diagnostic sections and key fields");
		check(inOrder(formatted, "\nENTITY:", "\nMELEE:", "\nSKILLS:", "\nLAST ATTEMPTS:",
				"\nRESOURCES:", "\nBINDINGS:", "\nCONTEXT:"),
				"skill debug log puts melee warning immediately after identity before skill diagnostics");

		var emptySnapshot = new PmbSkillDebugSnapshot(7, UUID.randomUUID(), "minecraft:zombie", 1,
				PmbSkillScheduler.Strategy.IDLE, null,
				List.of(new PmbSkillDebugSnapshot.SkillState("bow", true, false, false, "line=0", "-")),
				List.of(), Map.of(), Map.of(), Map.of(), null, false, List.of(), false, "-");
		String empty = emptySnapshot.formatForLog(1);
		check(empty.contains("\nSKILLS: []\nLAST ATTEMPTS: []\nRESOURCES: {}\nBINDINGS: []"),
				"empty debug sections remain visible with exact compact markers");

		Component chat = debugSnapshot.formatForChat(103);
		String chatText = chat.getString();
		check(chatText.contains("debug.portable-mob-behaviour.skills")
				&& chatText.contains("debug.portable-mob-behaviour.status.resource_blocked"),
				"chat formatter retains translatable keys for client localization");
		check(inOrder(chatText, "debug.portable-mob-behaviour.entity",
				"debug.portable-mob-behaviour.melee", "debug.portable-mob-behaviour.skills",
				"debug.portable-mob-behaviour.attempts", "debug.portable-mob-behaviour.resources",
				"debug.portable-mob-behaviour.bindings", "debug.portable-mob-behaviour.context"),
				"localized chat puts melee warning immediately after identity before skill diagnostics");
		check(containsColor(chat, ChatFormatting.GOLD) && containsColor(chat, ChatFormatting.GREEN)
				&& containsColor(chat, ChatFormatting.RED),
				"chat formatter applies title, active, and blocked/suppressed status colors");
		Component unavailable = PmbSkillDebugFormatter.formatUnavailableForChat(
				"minecraft:husk", 42, debugUuid);
		check(unavailable.getString().contains("debug.portable-mob-behaviour.snapshot.unavailable"),
				"unavailable chat report remains localizable");

		var buffer = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
		try {
			var request = new com.pmb.network.PmbProtocolPackets.SkillDebugRequest(2008);
			com.pmb.network.PmbProtocolPackets.SkillDebugRequest.CODEC.encode(buffer, request);
			check(com.pmb.network.PmbProtocolPackets.SkillDebugRequest.CODEC.decode(buffer).entityId() == 2008,
					"skill debug request varint codec round trip");
		} finally { buffer.release(); }
	}
	private static boolean containsColor(Component component, ChatFormatting formatting) {
		TextColor expected = TextColor.fromLegacyFormat(formatting);
		if (expected.equals(component.getStyle().getColor())) return true;
		return component.getSiblings().stream().anyMatch(child -> containsColor(child, formatting));
	}
	private static boolean inOrder(String text, String... markers) {
		int previous = -1;
		for (String marker : markers) {
			int current = text.indexOf(marker);
			if (current <= previous) return false;
			previous = current;
		}
		return true;
	}
	private static void testDebugCaptureGate() throws Exception {
		Fixture mob = fixture();
		for (int i = 0; i < 3; i++) mob.scheduler.movement().apply(mob, mob.scheduler);
		check(mob.scheduler.movement().snapshot() == null,
				"unconfigured mob does not allocate or retain a movement debug snapshot");

		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		int[] calls = {0, 0, 0};
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		scheduler.offerDynamic("bow", "plain", PmbSkillScheduler.Category.MAIN, 1,
				() -> { calls[0]++; return true; }, () -> calls[2]++,
				() -> { calls[1]++; return new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK}; });
		resolveCandidates(scheduler);
		check(java.util.Arrays.equals(calls, new int[] {1, 1, 1}),
				"unconfigured scheduler executes production pre-claim, resource and winner callbacks exactly once");
		check(field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler) == null,
				"unconfigured scheduler does not allocate candidate diagnostics");

		field(PmbBowAiData.class, "configured").setBoolean(mob.ai.bow(), true);
		mob.scheduler.movement().apply(mob, mob.scheduler);
		check(mob.scheduler.movement().snapshot() != null,
				"configured transition enables movement diagnostic capture");
		prepareDebug(scheduler, true);
		check(field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler) != null,
				"configured transition enables candidate diagnostic capture");
		field(PmbSkillScheduler.class, "lastDebugSnapshot").set(scheduler,
				new PmbSkillDebugSnapshot(1, java.util.UUID.randomUUID(), "minecraft:zombie", 1,
						PmbSkillScheduler.Strategy.IDLE, null, List.of(), List.of(), Map.of(), Map.of(),
						Map.of(), null, false, List.of(), false, "-"));
		prepareDebug(scheduler, false);
		check(field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler) == null
				&& scheduler.lastDebugSnapshot() == null,
				"configuration removal clears candidate capture and stale completed snapshot");
		field(PmbBowAiData.class, "configured").setBoolean(mob.ai.bow(), false);
		mob.scheduler.movement().apply(mob, mob.scheduler);
		check(mob.scheduler.movement().snapshot() == null,
				"configuration removal clears stale movement snapshot");
	}
	private static void prepareDebug(PmbSkillScheduler scheduler, boolean configured) throws Exception {
		var method = PmbSkillScheduler.class.getDeclaredMethod("prepareDebugCapture", boolean.class);
		method.setAccessible(true);
		method.invoke(scheduler, configured);
	}
	@SuppressWarnings("unchecked")
	private static List<PmbSkillDebugSnapshot.CandidateAttempt> debugAttempts(PmbSkillScheduler scheduler)
			throws Exception {
		return List.copyOf((List<PmbSkillDebugSnapshot.CandidateAttempt>)
				field(PmbSkillScheduler.class, "candidateAttempts").get(scheduler));
	}
	private static void testSchema() throws Exception {
		for (String id : List.of("bow", "shield", "wind_charge", "ender_pearl")) {
			var entry = PmbSkillSchema.byId(id).field("preferredHand");
			check(entry.choices().equals(List.of("main", "off", "main-enforce", "off-enforce")), "enum schema " + id);
			check(entry.defaultValue().asString().orElseThrow().equals(id.equals("bow") ? "main" : "off"), "default " + id);
		}
		check(!PmbSkillSchema.byId("mace").hasField("preferredHand"), "mace excludes preferredHand");
		check(PmbSkillSchema.byId("air_tracking") == null && !PmbSkillSchema.isRegisteredSkill("air_tracking"),
				"air tracking is pathfinding policy rather than a skill");
		check(PmbSkillSchema.byId("traversal").skillKind() == PmbSkillSchema.SkillKind.CONDITIONAL
				&& PmbSkillSchema.byId("traversal.wind_charge").skillKind() == PmbSkillSchema.SkillKind.CONDITIONAL
				&& PmbSkillSchema.byId("fall_rescue").skillKind() == PmbSkillSchema.SkillKind.CONDITIONAL
				&& PmbSkillSchema.byId("fall_rescue.ender_pearl").skillKind() == PmbSkillSchema.SkillKind.CONDITIONAL
				&& PmbSkillSchema.byId("wind_traversal") == null
				&& PmbSkillSchema.byId("bow").skillKind() == PmbSkillSchema.SkillKind.ACTIVE,
				"schema separates active and conditional skills");
		for (String id : List.of("shield", "wind_charge", "mace", "bow", "ender_pearl")) {
			var bias = PmbSkillSchema.byId(id).field("randomCooldownBias");
			check(bias != null && bias.defaultValue().asInt().orElseThrow() == 20
					&& bias.min() == 0 && bias.max() == 1200, "random cooldown schema " + id);
		}
		check(new PmbShieldAiData().randomCooldownBias() == 20
				&& new PmbWindChargeAiData().randomCooldownBias() == 20
				&& new PmbMaceAiData().randomCooldownBias() == 20
				&& new PmbBowAiData().randomCooldownBias() == 20
				&& new PmbEnderPearlAiData().randomCooldownBias() == 20,
				"legacy-absent skill data defaults random cooldown bias to twenty");
		check(new PmbWindChargeAiData().bouncePostJumpTicks() == 2
				&& PmbTraversalAiData.wind().postJumpTicks() == 2,
				"both post-jump delays default to two ticks");
		PmbBowAiData bow = new PmbBowAiData();
		bow.restoreCooldowns(0, 60, 80);
		check(bow.lineCooldownRemaining() == 60 && bow.arcCooldownRemaining() == 80,
				"runtime cooldown restore accepts configured base plus default bias");
		var maximumBias = new net.minecraft.nbt.CompoundTag();
		maximumBias.putInt("randomCooldownBias", 1200);
		PmbSkillSchema.byId("bow").validate(maximumBias);
		boolean rejected = false;
		try {
			var excessiveBias = new net.minecraft.nbt.CompoundTag();
			excessiveBias.putInt("randomCooldownBias", 1201);
			PmbSkillSchema.byId("bow").validate(excessiveBias);
		} catch (com.mojang.brigadier.exceptions.CommandSyntaxException expectedFailure) {
			rejected = true;
		}
		check(rejected, "command schema rejects random cooldown bias above maximum");
		PmbPathfindingData pathfinding = new PmbPathfindingData();
		check(pathfinding.airTracking().trackDurationTicks() == -1
				&& pathfinding.airTracking().trackAcceleration() == 0.012F
				&& pathfinding.airTracking().trackMaxHorizontalSpeed() == 0.3F,
				"pathfinding air tracking defaults preserve infinite duration and shared steering values");
		java.util.UUID firstTarget = java.util.UUID.randomUUID();
		check(PmbAirTrackingController.sameSession("mace", firstTarget, false,
				"mace", firstTarget, false)
				&& !PmbAirTrackingController.sameSession("mace", firstTarget, false,
						"mace", java.util.UUID.randomUUID(), false)
				&& !PmbAirTrackingController.sameSession("mace", firstTarget, false,
						"mace", firstTarget, true),
				"air tracking session identity includes source target and evasive mode");
		check(PmbAirTrackingController.withinMaceAirRange(0.0D, 16.0D)
				&& PmbAirTrackingController.withinMaceAirRange(9.0D, 16.0D)
				&& PmbAirTrackingController.withinMaceAirRange(256.0D, 16.0D)
				&& !PmbAirTrackingController.withinMaceAirRange(256.01D, 16.0D),
				"mace air source covers the full inclusive follow range, including smash range");
		check(PmbAirTrackingController.groundLifecycleState(true, false, false, true)
					.equals("wind_latched_waiting_next_tick")
				&& PmbAirTrackingController.groundLifecycleState(true, false, false, false).equals("target_invalid")
				&& PmbAirTrackingController.groundLifecycleState(true, true, true, true).equals("landed"),
				"wind latch lifecycle waits on the bounce ground tick, rejects an invalid target, then clears on landing");
		PmbAirTrackingController tracking = new PmbAirTrackingController();
		tracking.clear("landed");
		check(tracking.trackingState().equals("inactive") && tracking.clearReason().equals("landed"),
				"landing clears the completed air tracking session after its active infinite state");
		PmbPathfindingController.FallImpactContext fall = new PmbPathfindingController.FallImpactContext(
				PmbPathfindingController.FallCause.PLANNED_PATH, new Vec3(1, 8, 1), new Vec3(5, 0, 1), 8, 12);
		check(fall.approachPosition().equals(new Vec3(1, 8, 1))
				&& fall.expectedImpactPosition().equals(new Vec3(5, 0, 1)),
				"planned fall context keeps the safe approach anchor separate from the impact landing");
		check(PmbConditionalController.isSaferRescue(4, 8)
				&& !PmbConditionalController.isSaferRescue(8, 8)
				&& !PmbConditionalController.isSaferRescue(Float.NaN, 8),
				"pearl rescue promises only finite net damage strictly below the predicted fall damage");
	}
	private static void testDestructiveAiHierarchy() {
		CompoundTag root = new CompoundTag();
		CompoundTag ai = new CompoundTag(); root.put(PmbAiData.TAG, ai);
		CompoundTag skill = new CompoundTag(); ai.put("skill", skill);
		CompoundTag active = new CompoundTag(); skill.put("active", active);
		CompoundTag bow = new CompoundTag(); bow.putBoolean("enable", true); active.put("bow", bow);
		CompoundTag wind = new CompoundTag(); wind.putBoolean("enable", true);
		wind.putInt("bouncePostJumpTicks", 10); active.put("wind_charge", wind);
		CompoundTag conditional = new CompoundTag(); skill.put("conditional", conditional);
		CompoundTag traversal = new CompoundTag(); traversal.putBoolean("enable", true);
		ListTag traversalMethods = new ListTag(); traversalMethods.add(StringTag.valueOf("ender_pearl"));
		traversalMethods.add(StringTag.valueOf("wind_charge")); traversal.put("methods", traversalMethods);
		CompoundTag traversalWind = new CompoundTag(); traversalWind.putBoolean("enable", false);
		traversalWind.putFloat("minHorizontalConnectionDistance", .75F);
		traversalWind.putInt("postJumpTicks", 0);
		traversal.put("wind_charge", traversalWind);
		CompoundTag traversalPearl = new CompoundTag(); traversalPearl.putFloat("maxThrowPower", 2.0F);
		traversal.put("ender_pearl", traversalPearl); conditional.put("traversal", traversal);
		CompoundTag rescue = new CompoundTag(); rescue.putBoolean("enable", true); rescue.putBoolean("singleMethodPerFall", false);
		ListTag methods = new ListTag(); methods.add(StringTag.valueOf("ender_pearl")); methods.add(StringTag.valueOf("wind_charge"));
		rescue.put("methods", methods);
		CompoundTag rescueWind = new CompoundTag(); rescueWind.putFloat("successChance", .75F);
		rescue.put("wind_charge", rescueWind);
		CompoundTag rescuePearl = new CompoundTag(); rescuePearl.putBoolean("doTeleportDamage", true);
		rescue.put("ender_pearl", rescuePearl); conditional.put("fall_rescue", rescue);
		CompoundTag path = new CompoundTag(); ai.put("pathfinding", path);
		CompoundTag planner = new CompoundTag(); planner.putBoolean("debugPathParticles", true);
		path.put("planner", planner);
		CompoundTag air = new CompoundTag(); air.putFloat("trackAcceleration", .02F); path.put("air_tracking", air);
		PmbAiData data = new PmbAiData();
		data.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root));
		check(data.bow().isEnabled() && data.traversal().isEnabled() && data.fallRescue().isEnabled()
				&& data.traversal().methods().equals(List.of("ender_pearl", "wind_charge"))
				&& !data.traversal().wind().isEnabled()
				&& data.traversal().wind().minHorizontalConnectionDistance() == .75F
				&& data.traversal().wind().postJumpTicks() == 0
				&& data.windCharge().bouncePostJumpTicks() == 10
				&& data.traversal().pearl().maxThrowPower() == 2.0F
				&& !data.fallRescue().singleMethodPerFall()
				&& data.fallRescue().methods().equals(List.of("ender_pearl", "wind_charge"))
				&& data.fallRescue().wind().successChance() == .75F
				&& data.fallRescue().pearl().doTeleportDamage()
				&& data.airTracking().trackAcceleration() == .02F
				&& data.pathfinding().planner().debugPathParticles(),
				"new skill active/conditional and pathfinding hierarchy reads atomically");
		check(PmbSkillItemAccess.traversalMayRetainTarget(data),
				"enabled pearl traversal can retain an already acquired target through occlusion");
		PmbAiData retentionPolicy = new PmbAiData();
		retentionPolicy.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, root));
		PmbPathfindingData.Strategy combat = retentionPolicy.pathfinding().strategy(PmbSkillScheduler.Strategy.COMBAT);
		retentionPolicy.pathfinding().setStrategy(PmbSkillScheduler.Strategy.COMBAT,
				new PmbPathfindingData.Strategy(combat.routeMode(), combat.dangerousFallPolicy(), true));
		check(!PmbSkillItemAccess.traversalMayRetainTarget(retentionPolicy),
				"required combat sight prevents target retention through occlusion");
		retentionPolicy.pathfinding().setStrategy(PmbSkillScheduler.Strategy.COMBAT,
				new PmbPathfindingData.Strategy(PmbPathfindingData.RouteMode.VANILLA_ONLY,
						combat.dangerousFallPolicy(), false));
		check(!PmbSkillItemAccess.traversalMayRetainTarget(retentionPolicy),
				"vanilla-only combat pathfinding does not extend target retention");
		TagValueOutput output = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
		data.write(output);
		CompoundTag saved = output.buildResult().getCompoundOrEmpty(PmbAiData.TAG);
		check(saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("active").contains("bow")
				&& saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("active")
						.getCompoundOrEmpty("wind_charge").getIntOr("bouncePostJumpTicks", -1) == 10
				&& saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("conditional").contains("fall_rescue")
				&& saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("conditional").contains("traversal")
				&& saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("conditional")
						.getCompoundOrEmpty("traversal").getCompoundOrEmpty("wind_charge")
						.getFloatOr("minHorizontalConnectionDistance", -1) == .75F
				&& saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("conditional")
						.getCompoundOrEmpty("traversal").getCompoundOrEmpty("wind_charge")
						.getIntOr("postJumpTicks", -1) == 0
				&& !saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("conditional").contains("wind_traversal")
				&& !saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("conditional")
						.getCompoundOrEmpty("fall_rescue").getBooleanOr("singleMethodPerFall", true)
				&& saved.getCompoundOrEmpty("skill").getCompoundOrEmpty("conditional")
						.getCompoundOrEmpty("fall_rescue").contains("wind_charge")
				&& saved.getCompoundOrEmpty("pathfinding").contains("air_tracking")
				&& saved.getCompoundOrEmpty("pathfinding").getCompoundOrEmpty("planner")
						.getBooleanOr("debugPathParticles", false)
				&& !saved.contains("bow"), "only the new hierarchy is saved");

		CompoundTag legacyRoot = new CompoundTag(); CompoundTag legacyAi = new CompoundTag();
		CompoundTag legacyBow = new CompoundTag(); legacyBow.putBoolean("enable", true);
		legacyAi.put("bow", legacyBow); legacyRoot.put(PmbAiData.TAG, legacyAi);
		PmbAiData legacy = new PmbAiData();
		legacy.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, legacyRoot));
		check(!legacy.bow().isConfigured(), "legacy flat PmbAi is intentionally ignored without migration");
		CompoundTag oldConditional = new CompoundTag();
		oldConditional.put("wind_traversal", traversalWind.copy());
		oldConditional.put("pearl_traversal", traversalPearl.copy());
		CompoundTag oldSkill = new CompoundTag(); oldSkill.put("conditional", oldConditional);
		CompoundTag oldAi = new CompoundTag(); oldAi.put("skill", oldSkill);
		CompoundTag oldRoot = new CompoundTag(); oldRoot.put(PmbAiData.TAG, oldAi);
		PmbAiData old = new PmbAiData();
		old.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, oldRoot));
		check(!old.traversal().isConfigured(), "obsolete flat traversal nodes are not read");
		check(data.fallRescue().wind().isConfigured() && data.fallRescue().wind().isEnabled()
				&& data.traversal().pearl().isConfigured() && data.traversal().pearl().isEnabled(),
				"configured method nodes default enabled under their parent switch");
		CompoundTag overlongWind = new CompoundTag();
		CompoundTag overlongBounce = new CompoundTag(); overlongBounce.putInt("bouncePostJumpTicks", 15);
		overlongWind.put("wind_charge", overlongBounce);
		PmbWindChargeAiData clampedBounce = new PmbWindChargeAiData();
		clampedBounce.read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, overlongWind));
		CompoundTag earlyTraversal = new CompoundTag(); earlyTraversal.putInt("postJumpTicks", -3);
		PmbTraversalAiData clampedTraversal = PmbTraversalAiData.wind();
		clampedTraversal.readNode(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, earlyTraversal));
		check(clampedBounce.bouncePostJumpTicks() == 10 && clampedTraversal.postJumpTicks() == 0,
				"post-jump NBT timing clamps to zero through ten without changing the other mode");
	}
	private static void testSkillPriorities() {
		PmbSkillPriorities priorities = new PmbSkillPriorities();
		check(priorities.effective(PmbSkillScheduler.Strategy.COMBAT).equals(List.of(
				List.of("mace"), List.of("fall_rescue"), List.of("ender_pearl"), List.of("wind_charge"),
				List.of("traversal"), List.of("bow"),
				List.of("shield"), List.of("vanilla"))), "combat priority default");
		check(priorities.effective(PmbSkillScheduler.Strategy.RETREAT).equals(List.of(
				List.of("fall_rescue"), List.of("ender_pearl"), List.of("wind_charge"), List.of("traversal"),
				List.of("vanilla"))), "retreat omission default");
		check(priorities.effective(PmbSkillScheduler.Strategy.IDLE).equals(List.of(List.of("fall_rescue"),
				List.of("traversal"), List.of("vanilla"))),
				"idle conditional traversal default");
		PmbAiData priorityOnly = new PmbAiData();
		priorityOnly.skillPriorities().set(PmbSkillScheduler.Strategy.COMBAT, List.of(List.of("vanilla")));
		check(!priorityOnly.isConfigured() && priorityOnly.hasPersistentData(),
				"priority-only data persists without masquerading as a configured skill");

		var tag = new net.minecraft.nbt.ListTag();
		tag.add(net.minecraft.nbt.StringTag.valueOf("mace"));
		var equal = new net.minecraft.nbt.ListTag();
		equal.add(net.minecraft.nbt.StringTag.valueOf("ender_pearl"));
		equal.add(net.minecraft.nbt.StringTag.valueOf("bow"));
		tag.add(equal);
		tag.add(net.minecraft.nbt.StringTag.valueOf("vanilla"));
		var parsed = PmbSkillPriorities.parse(tag);
		check(parsed.equals(List.of(List.of("mace"), List.of("ender_pearl", "bow"), List.of("vanilla"))),
				"mixed quoted priority list parses into normalized tiers");
		check(PmbSkillPriorities.toTag(parsed).equals(tag), "priority mixed-list canonical round trip");
		priorities.set(PmbSkillScheduler.Strategy.COMBAT, parsed);
		check(priorities.hasOverride(PmbSkillScheduler.Strategy.COMBAT)
				&& priorities.tier(PmbSkillScheduler.Strategy.COMBAT, "bow") == 1
				&& priorities.tier(PmbSkillScheduler.Strategy.COMBAT, "wind_charge") == -1,
				"explicit list tier and omission semantics");
		priorities.reset(PmbSkillScheduler.Strategy.COMBAT);
		check(!priorities.hasOverrides(), "single-strategy reset returns to default without persistence");

		for (var invalid : List.of(
				List.of(List.of("mace")),
				List.of(List.of("mace"), List.of("mace"), List.of("vanilla")),
				List.of(List.of("unknown"), List.of("vanilla")))) {
			boolean rejected = false;
			try { PmbSkillPriorities.validate(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
			check(rejected, "invalid priority list rejected atomically: " + invalid);
		}
	}
	private static void testBowMeleeSuppressionRules() throws Exception {
		PmbBowAiData bow = new PmbBowAiData();
		field(PmbBowAiData.class, "configured").setBoolean(bow, true);
		field(PmbBowAiData.class, "enabled").setBoolean(bow, true);
		field(PmbBowAiData.class, "lineMinRange").setFloat(bow, 4.0F);
		field(PmbBowAiData.class, "lineMaxRange").setFloat(bow, 8.0F);
		field(PmbBowAiData.class, "lineShootChance").setFloat(bow, 1.0F);
		field(PmbBowAiData.class, "arcMinRange").setFloat(bow, 12.0F);
		field(PmbBowAiData.class, "arcMaxRange").setFloat(bow, 16.0F);
		field(PmbBowAiData.class, "arcShootChance").setFloat(bow, 1.0F);

		check(PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN, true, false)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN, false, true)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF, true, false)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF, false, true),
				"soft bow hand preferences accept an already held bow in either hand");
		check(PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN_ENFORCE, true, false)
				&& !PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN_ENFORCE, false, true)
				&& PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF_ENFORCE, false, true)
				&& !PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.OFF_ENFORCE, true, false),
				"enforced bow hand preferences accept only their required hand");
		check(!PmbSkillItemAccess.hasBowInAllowedHand(PmbPreferredHand.MAIN, false, false),
				"inventory or binding availability without a held bow cannot suppress melee");

		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, false, 4.0D, true, true),
				"missing invalid dead cross-level or unattackable target cannot suppress melee");
		check(PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 4.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 8.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 12.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 16.0D, true, false),
				"line and arc closed range boundaries suppress melee without consumed ammunition");
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 3.99D, true, true)
				&& !PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 10.0D, true, true)
				&& !PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 16.01D, true, true),
				"outside and gap distances restore ordinary melee");

		field(PmbBowAiData.class, "lineShootChance").setFloat(bow, 0.0F);
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, true)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 14.0D, true, true),
				"zero-chance mode does not suppress while another live range still can");
		field(PmbBowAiData.class, "lineShootChance").setFloat(bow, 1.0F);
		field(PmbBowAiData.class, "lineCooldown").setInt(bow, 100);
		check(PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, false),
				"cooldown does not participate in lightweight melee suppression");

		field(PmbBowAiData.class, "doConsume").setBoolean(bow, true);
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, false)
				&& PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, true),
				"consuming bow requires currently available supported ammunition");
		Fixture ammunitionMob = fixture();
		ammunitionMob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		ammunitionMob.items.put(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));
		PmbAmmoAccess.Source ammunition = PmbAmmoAccess.find(ammunitionMob, stack -> stack.is(Items.ARROW));
		check(ammunition != null && PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D,
				true, true), "real hand ammunition satisfies consuming bow suppression");
		check(ammunition.consume(ammunitionMob)
				&& PmbAmmoAccess.find(ammunitionMob, stack -> stack.is(Items.ARROW)) == null
				&& !PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, false),
				"exhausting the last supported arrow restores ordinary melee eligibility");
		field(PmbBowAiData.class, "enabled").setBoolean(bow, false);
		check(!PmbSkillItemAccess.shouldSuppressMeleeForBow(bow, true, 6.0D, true, true),
				"disabled bow never suppresses ordinary melee");
	}
	private static void testSkillTimingAndPreClaim() throws Exception {
		var zero = net.minecraft.util.RandomSource.create(2008L);
		var zeroControl = net.minecraft.util.RandomSource.create(2008L);
		check(!PmbSkillTiming.passesChance(zero, 0.0F)
				&& zero.nextInt() == zeroControl.nextInt(), "zero chance fails without consuming RNG");
		var one = net.minecraft.util.RandomSource.create(2008L);
		var oneControl = net.minecraft.util.RandomSource.create(2008L);
		check(PmbSkillTiming.passesChance(one, 1.0F)
				&& one.nextInt() == oneControl.nextInt(), "one chance succeeds without consuming RNG");
		var expected = net.minecraft.util.RandomSource.create(77L);
		boolean expectedChance = expected.nextFloat() < 0.5F;
		check(PmbSkillTiming.passesChance(net.minecraft.util.RandomSource.create(77L), 0.5F) == expectedChance,
				"intermediate chance uses strict random less-than comparison");
		var cooldownRandom = net.minecraft.util.RandomSource.create(91L);
		boolean inRange = true, sawMinimum = false, sawMaximum = false;
		for (int i = 0; i < 10000; i++) {
			int cooldown = PmbSkillTiming.cooldown(cooldownRandom, 40, 20);
			inRange &= cooldown >= 40 && cooldown <= 60;
			sawMinimum |= cooldown == 40;
			sawMaximum |= cooldown == 60;
		}
		check(inRange, "cooldown remains in inclusive configured interval");
		check(sawMinimum && sawMaximum, "cooldown randomization reaches both inclusive endpoints");

		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		List<String> trace = new ArrayList<>();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> false,
				() -> trace.add("high"), PmbSkillScheduler.Resource.MAIN_HAND);
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 10, () -> true,
				() -> trace.add("low"), PmbSkillScheduler.Resource.MAIN_HAND);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("low")), "failed pre-claim does not reserve resources from lower priority skill");

		scheduler = new PmbSkillScheduler();
		int[] cooldownWrites = {0};
		scheduler.claim("sustained", PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("bow", PmbSkillScheduler.Category.THROW, 10,
				() -> { cooldownWrites[0]++; return true; }, () -> trace.add("candidate"),
				PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(cooldownWrites[0] == 0 && !trace.contains("candidate"),
				"resource suppression occurs before probability and consumes no attempt cooldown");
		scheduler = new PmbSkillScheduler();
		int[] finalChecks = {0};
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 10,
				() -> { cooldownWrites[0]++; return true; }, () -> finalChecks[0]++,
				PmbSkillScheduler.Resource.MAIN_HAND);
		resolveCandidates(scheduler);
		check(finalChecks[0] == 1 && cooldownWrites[0] == 1,
				"admitted winner evaluates probability before final revalidation");

		boolean[] bouncePassed = {false};
		scheduler = new PmbSkillScheduler();
		trace.clear();
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> { bouncePassed[0] = false; return false; }, () -> trace.add("bounce"),
				PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> !bouncePassed[0], () -> trace.add("throw"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("throw")), "wind throw fallback follows failed bounce probability");
		scheduler = new PmbSkillScheduler();
		trace.clear();
		bouncePassed[0] = false;
		scheduler.claim("sustained", PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> { bouncePassed[0] = true; return true; }, () -> trace.add("bounce"),
				PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("wind", PmbSkillScheduler.Category.THROW, 10,
				() -> !bouncePassed[0], () -> trace.add("throw"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.isEmpty(), "wind throw does not fallback after successful bounce probability loses resources");
	}
	@SuppressWarnings("unchecked")
	private static void testPriorityPreemptionHarness() throws Exception {
		PmbSkillScheduler scheduler = new PmbSkillScheduler();
		List<String> trace = new ArrayList<>();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		scheduler.claim("impulse_landing", PmbSkillScheduler.Resource.LOOK);
		scheduler.maintainPhase("bow", "charge", () -> trace.add("landing-cancels-bow"),
				new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.USE_ITEM, PmbSkillScheduler.Resource.LOOK});
		scheduler.maintainPhase("pearl", "follow_through", () -> trace.add("unexpected-follow-cancel"),
				new PmbSkillScheduler.Resource[0], PmbSkillScheduler.Resource.LOOK);
		check(scheduler.ownsResource("impulse_landing", PmbSkillScheduler.Resource.LOOK)
				&& trace.equals(List.of("landing-cancels-bow")),
				"landing LOOK survives real maintainPhase rebuild and denies required/optional follow-through look");
		scheduler.offerPlanned("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> true, () -> trace.add("unexpected-smash"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("landing-cancels-bow")), "committed landing completion blocks new LOOK action for its tick");
		scheduler.unclaim("impulse_landing", PmbSkillScheduler.Resource.LOOK);
		scheduler.maintainPhase("bow", "charge", () -> trace.add("unexpected-cancel"),
				new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.LOOK});
		check(scheduler.ownsResource("bow", PmbSkillScheduler.Resource.LOOK), "next tick without landing restores ordinary LOOK phase ownership");
		scheduler.unclaim("bow", PmbSkillScheduler.Resource.LOOK);
		field(PmbSkillScheduler.class, "landingPriorityOwner").set(scheduler, "shield");
		scheduler.claim("impulse_landing", PmbSkillScheduler.Resource.LOOK);
		scheduler.maintainPhase("bow", "charge", () -> trace.add("unexpected-higher-cancel"),
				new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.LOOK});
		check(scheduler.ownsResource("bow", PmbSkillScheduler.Resource.LOOK)
				&& !trace.contains("unexpected-higher-cancel"), "lower-priority landing cannot cancel an existing higher LOOK phase");
		scheduler.unclaim("bow", PmbSkillScheduler.Resource.LOOK);
		field(PmbSkillScheduler.class, "landingPriorityOwner").set(scheduler, "bow");
		scheduler.claim("impulse_landing", PmbSkillScheduler.Resource.LOOK);
		scheduler.maintainPhase("bow", "charge", () -> trace.add("unexpected-equal-cancel"),
				new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.LOOK});
		check(scheduler.ownsResource("bow", PmbSkillScheduler.Resource.LOOK)
				&& !trace.contains("unexpected-equal-cancel"), "equal-priority landing preserves the existing LOOK phase");
		scheduler = new PmbSkillScheduler(); trace.clear();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel-bow"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		prepareDebug(scheduler, true);
		scheduler.offerPlanned("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> false, () -> trace.add("mace"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.isEmpty(), "failed final check does not preempt incumbent or commit candidate");
		check(debugAttempts(scheduler).size() == 1
				&& debugAttempts(scheduler).getFirst().status() == PmbSkillDebugSnapshot.AttemptStatus.FINAL_REJECTED,
				"failed final validation is visible in scheduler debug evidence");

		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		scheduler.offerPlannedResult("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> true, () -> { trace.add("mace"); return PmbSkillScheduler.CommitResult.COMMITTED; },
				PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("mace", "cancel-bow")),
				"strictly higher candidate cancels conflicting required phase only after successful commit");

		scheduler = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		trace.clear();
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel-bow"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		prepareDebug(scheduler, true);
		int[] lowerChecks = {0};
		scheduler.offerPlannedResult("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> true, () -> PmbSkillScheduler.CommitResult.FAILED,
				PmbSkillScheduler.Resource.LOOK);
		scheduler.offer("ender_pearl", PmbSkillScheduler.Category.THROW, 10,
				() -> { lowerChecks[0]++; return true; }, () -> trace.add("pearl"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("pearl")) && lowerChecks[0] == 1
				&& debugAttempts(scheduler).getFirst().status()
						== PmbSkillDebugSnapshot.AttemptStatus.COMMIT_FAILED,
				"failed would-be preemption restores incumbent and permits lower rescue-style candidates");

		check(java.util.Arrays.stream(PmbSkillScheduler.Resource.values())
				.noneMatch(resource -> resource.name().equals("NAVIGATION")),
				"navigation is absent from action resources rather than ignored as a special case");

		scheduler = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		scheduler.maintainPhase("ender_pearl", "follow_through", () -> {},
				new PmbSkillScheduler.Resource[] {}, PmbSkillScheduler.Resource.LOOK);
		trace.clear();
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20, () -> true,
				() -> trace.add("bow"), PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(trace.equals(List.of("bow"))
				&& scheduler.ownsResource("bow", PmbSkillScheduler.Resource.LOOK)
				&& scheduler.activePhases().values().stream().anyMatch(phase -> phase.owner().equals("ender_pearl")),
				"required candidate displaces an optional claim without cancelling its phase");

		scheduler = new PmbSkillScheduler();
		trace.clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
		scheduler.offer("ender_pearl", PmbSkillScheduler.Category.THROW, 20, () -> true, () -> trace.add("pearl"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("mace")), "first successful irreversible commit ends tick arbitration");

		scheduler = new PmbSkillScheduler();
		PmbSkillPriorities priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("mace", "bow"), List.of("vanilla")));
		trace.clear();
		for (int tick = 0; tick < 2; tick++) {
			((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
			scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
			scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("bow"));
			resolveCandidates(scheduler);
		}
		check(trace.equals(List.of("mace", "bow")), "equal priority tier rotates winner per entity without incumbent preemption");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("mace", "bow"), List.of("vanilla")));
		trace.clear();
		int[] maceChecks = {0}, bowChecks = {0};
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20,
				() -> { maceChecks[0]++; return false; }, () -> trace.add("mace"));
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20,
				() -> { bowChecks[0]++; return true; }, () -> trace.add("bow"));
		resolveCandidates(scheduler);
		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20,
				() -> { maceChecks[0]++; return true; }, () -> trace.add("mace"));
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20,
				() -> { bowChecks[0]++; return true; }, () -> trace.add("bow"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("bow", "mace")) && maceChecks[0] == 2 && bowChecks[0] == 1,
				"contested tier advances after the actual winner and never checks later candidates after commit");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("mace", "bow"), List.of("vanilla")));
		trace.clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
		resolveCandidates(scheduler);
		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("mace"));
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20, () -> true, () -> trace.add("bow"));
		resolveCandidates(scheduler);
		check(trace.equals(List.of("mace", "mace")),
				"an uncontested equal-tier action does not advance the round-robin cursor");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT, List.of(List.of("vanilla")));
		int[] omittedChecks = {0};
		scheduler.offer("mace", PmbSkillScheduler.Category.MAIN, 20,
				() -> { omittedChecks[0]++; return true; }, () -> trace.add("omitted"));
		resolveCandidates(scheduler);
		check(omittedChecks[0] == 0 && !trace.contains("omitted"),
				"omitting a skill from the strategy prevents even its probability and cooldown check");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("vanilla"), List.of("bow")));
		int[] lowerActionChecks = {0};
		scheduler.offer("bow", PmbSkillScheduler.Category.MAIN, 20,
				() -> { lowerActionChecks[0]++; return true; }, () -> trace.add("lower-action"));
		resolveCandidates(scheduler);
		check(lowerActionChecks[0] == 1 && trace.contains("lower-action"),
				"vanilla priority only competes when it offers an action candidate");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("bow", "ender_pearl"), List.of("vanilla")));
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		trace.clear();
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel-bow"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		scheduler.maintainPhase("pearl", "follow_through", () -> trace.add("cancel-pearl"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		check(trace.equals(List.of("cancel-pearl")) && scheduler.ownsResource("bow", PmbSkillScheduler.Resource.LOOK),
				"an equal-priority active phase cannot preempt its incumbent");

		scheduler = new PmbSkillScheduler();
		priorities = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities").get(scheduler);
		priorities.set(PmbSkillScheduler.Strategy.RETREAT, List.of(List.of("bow"), List.of("vanilla")));
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		trace.clear();
		scheduler.maintainPhase("bow", "charge", () -> trace.add("cancel"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.RETREAT);
		scheduler.maintainPhase("bow", "charge", () -> true, () -> trace.add("cancel"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		check(trace.isEmpty(), "strategy change preserves a still-listed phase whose authority remains valid");
		scheduler.maintainPhase("bow", "charge", () -> false, () -> trace.add("cancel"),
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.LOOK});
		check(trace.equals(List.of("cancel")), "phase-local authority invalidation cancels the phase");
	}
	@SuppressWarnings("unchecked")
	private static void testTransactionalItemPreemption() throws Exception {
		Fixture mob = fixture();
		mob.items.put(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
		mob.inventory.set(1, new ItemStack(Items.MACE));
		PmbSkillItemAccess.ActionBinding bowBinding = PmbSkillItemAccess.acquire(mob,
				new PmbSkillItemAccess.Resolved(PmbSkillItemAccess.Transfer.DIRECT,
						InteractionHand.MAIN_HAND, InteractionHand.MAIN_HAND, 0,
						mob.getMainHandItem().copy(), ItemStack.EMPTY));
		check(mob.scheduler.bind("bow", bowBinding), "fixture binds the incumbent bow action");
		field(PmbSkillScheduler.class, "strategy").set(mob.scheduler, PmbSkillScheduler.Strategy.COMBAT);
		List<String> trace = new ArrayList<>();
		mob.scheduler.maintainPhase("bow", "charge", () -> {
			trace.add("cancel-bow"); mob.scheduler.release(mob, "bow");
		}, new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.MAIN_HAND,
				PmbSkillScheduler.Resource.USE_ITEM});
		Map<PmbSkillScheduler.Resource, String> sustained =
				(Map<PmbSkillScheduler.Resource, String>) field(PmbSkillScheduler.class, "sustained").get(mob.scheduler);
		sustained.put(PmbSkillScheduler.Resource.MAIN_HAND, "bow");
		sustained.put(PmbSkillScheduler.Resource.USE_ITEM, "bow");
		PmbActivationSources inventoryOne = sources(
				new PmbActivationSources.Source(PmbActivationSources.Kind.INVENTORY, 1, 1));
		PmbSkillItemAccess.Resolved mace = PmbSkillItemAccess.resolveForRequiredHand(mob, inventoryOne,
				List.of(InteractionHand.MAIN_HAND), stack -> stack.is(Items.MACE), InteractionHand.MAIN_HAND);
		check(mace != null && mace.transfer() == PmbSkillItemAccess.Transfer.INVENTORY_SWAP,
				"higher-priority mace resolves through a lower-priority bow hand lease");
		PmbSkillItemAccess.Resolved failedMace = mace;
		mob.scheduler.offerPlannedResult("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> true, () -> {
					PmbSkillItemAccess.ActionBinding lease = PmbSkillItemAccess.acquire(mob, failedMace);
					check(lease != null && mob.getMainHandItem().is(Items.MACE),
							"failed mace attempt temporarily equips the required weapon");
					check(mob.scheduler.bind("mace", lease), "temporary mace binding is unique");
					check(lease.rollback(mob), "failed mace attempt rolls back its equipment transaction");
					mob.scheduler.release(mob, "mace");
					return PmbSkillScheduler.CommitResult.FAILED;
				}, PmbSkillScheduler.Resource.MAIN_HAND, PmbSkillScheduler.Resource.SMASH);
		mob.scheduler.offerPlannedResult("fall_rescue", "rescue", PmbSkillScheduler.Category.THROW, 10,
				() -> true, () -> true, () -> { trace.add("rescue"); return PmbSkillScheduler.CommitResult.COMMITTED; });
		resolveCandidates(mob.scheduler);
		check(trace.equals(List.of("rescue")) && mob.scheduler.hasBinding("bow")
				&& mob.getMainHandItem().is(Items.BOW) && mob.inventory.get(1).is(Items.MACE),
				"failed mace restores bow phase, binding and exact item layout before rescue continues");

		((List<?>) field(PmbSkillScheduler.class, "candidates").get(mob.scheduler)).clear();
		mace = PmbSkillItemAccess.resolveForRequiredHand(mob, inventoryOne,
				List.of(InteractionHand.MAIN_HAND), stack -> stack.is(Items.MACE), InteractionHand.MAIN_HAND);
		PmbSkillItemAccess.Resolved committedMace = mace;
		mob.scheduler.offerPlannedResult("mace", "smash", PmbSkillScheduler.Category.MAIN, 20,
				() -> true, () -> true, () -> {
					PmbSkillItemAccess.ActionBinding lease = PmbSkillItemAccess.acquire(mob, committedMace);
					check(lease != null && mob.scheduler.bind("mace", lease),
							"successful mace transaction acquires before incumbent cancellation");
					lease.authorizeAction(mob);
					mob.scheduler.release(mob, "mace");
					return PmbSkillScheduler.CommitResult.COMMITTED;
				}, PmbSkillScheduler.Resource.MAIN_HAND, PmbSkillScheduler.Resource.SMASH);
		resolveCandidates(mob.scheduler);
		check(trace.equals(List.of("rescue", "cancel-bow")) && !mob.scheduler.hasBinding("bow")
				&& mob.getMainHandItem().is(Items.MACE) && mob.inventory.get(1).is(Items.BOW),
				"committed mace permanently swaps once and only then cancels the bow incumbent");

		Fixture shieldMob = fixture();
		shieldMob.items.put(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
		shieldMob.inventory.set(1, new ItemStack(Items.ENDER_PEARL));
		PmbSkillItemAccess.ActionBinding shieldBinding = PmbSkillItemAccess.acquire(shieldMob,
				new PmbSkillItemAccess.Resolved(PmbSkillItemAccess.Transfer.DIRECT,
						InteractionHand.OFF_HAND, InteractionHand.OFF_HAND, 0,
						shieldMob.getOffhandItem().copy(), ItemStack.EMPTY));
		check(shieldMob.scheduler.bind("shield", shieldBinding), "fixture binds the incumbent shield action");
		field(PmbSkillScheduler.class, "strategy").set(shieldMob.scheduler, PmbSkillScheduler.Strategy.COMBAT);
		shieldMob.scheduler.maintainPhase("shield", "guard", () -> {},
				new PmbSkillScheduler.Resource[] {PmbSkillScheduler.Resource.OFF_HAND,
						PmbSkillScheduler.Resource.USE_ITEM});
		Map<PmbSkillScheduler.Resource, String> shieldSustained =
				(Map<PmbSkillScheduler.Resource, String>) field(PmbSkillScheduler.class, "sustained").get(shieldMob.scheduler);
		shieldSustained.put(PmbSkillScheduler.Resource.OFF_HAND, "shield");
		PmbSkillItemAccess.Resolved pearl = PmbSkillItemAccess.resolvePreferred(shieldMob, inventoryOne,
				List.of(InteractionHand.OFF_HAND), stack -> stack.is(Items.ENDER_PEARL),
				PmbPreferredHand.OFF_ENFORCE, "pearl");
		check(pearl != null, "higher-priority pearl resolves through a lower-priority shield hand lease");
		PmbSkillItemAccess.ActionBinding pearlLease = PmbSkillItemAccess.acquire(shieldMob, pearl);
		check(pearlLease != null && pearlLease.rollback(shieldMob)
				&& shieldMob.scheduler.hasBinding("shield")
				&& shieldMob.getOffhandItem().is(Items.SHIELD)
				&& shieldMob.inventory.get(1).is(Items.ENDER_PEARL),
				"shield preemption rollback neither duplicates nor loses either item");

		PmbSkillPriorities equal = (PmbSkillPriorities) field(PmbSkillScheduler.class, "priorities")
				.get(shieldMob.scheduler);
		equal.set(PmbSkillScheduler.Strategy.COMBAT,
				List.of(List.of("ender_pearl", "shield"), List.of("vanilla")));
		check(PmbSkillItemAccess.resolvePreferred(shieldMob, inventoryOne,
				List.of(InteractionHand.OFF_HAND), stack -> stack.is(Items.ENDER_PEARL),
				PmbPreferredHand.OFF_ENFORCE, "pearl") == null,
				"same-tier incumbent hand remains non-preemptable");
	}
	private static void testBowTacticalFallPolicy() {
		Vec3 origin = new Vec3(0, 10, 0);
		Vec3 target = new Vec3(0, 10, 10);
		Vec3 retreat = PmbPathfindingController.bowTacticalDestination(origin, target,
				true, true, true, true);
		Vec3 orbit = PmbPathfindingController.bowTacticalDestination(origin, target,
				false, true, true, true);
		check(retreat.z() < 0 && Math.abs(retreat.x()) > 0,
				"bow safe-distance retreat exposes its real backward/sideways candidate point");
		check(orbit.x() < 0 && orbit.z() > 0,
				"bow mobile orbit exposes its real forward/sideways candidate point");
		for (PmbPathfindingData.DangerousFallPolicy policy : PmbPathfindingData.DangerousFallPolicy.values())
			check(PmbPathfindingController.permitsDangerousStep(policy, false, false, false),
					"safe bow tactical step remains allowed under " + policy);
		check(!PmbPathfindingController.permitsDangerousStep(
				PmbPathfindingData.DangerousFallPolicy.DISABLE, true, true, true),
				"disable rejects a cliff-edge bow step even with a known landing");
		check(PmbPathfindingController.permitsDangerousStep(
				PmbPathfindingData.DangerousFallPolicy.ENABLE, true, true, false)
				&& !PmbPathfindingController.permitsDangerousStep(
						PmbPathfindingData.DangerousFallPolicy.ENABLE, true, false, false),
				"enable requires a supported local cliff edge but no counter promise");
		check(PmbPathfindingController.permitsDangerousStep(
				PmbPathfindingData.DangerousFallPolicy.CONDITIONAL_REQUIRED, true, true, true)
				&& !PmbPathfindingController.permitsDangerousStep(
						PmbPathfindingData.DangerousFallPolicy.CONDITIONAL_REQUIRED, true, true, false),
				"conditional_required gates bow cliff retreat/orbit on a rescue promise");

		check(!PmbPathfindingController.permitsOrdinarySpecialRoute(
				PmbPathfindingData.RouteMode.VANILLA_ONLY, true, false),
				"vanilla_only never authorizes a PMB dangerous edge");
		check(!PmbPathfindingController.permitsOrdinarySpecialRoute(
				PmbPathfindingData.RouteMode.FALLBACK, true, true)
				&& PmbPathfindingController.permitsOrdinarySpecialRoute(
						PmbPathfindingData.RouteMode.FALLBACK, true, false),
				"fallback authorizes a PMB edge only when the vanilla path is incomplete");
		check(PmbPathfindingController.permitsOrdinarySpecialRoute(
				PmbPathfindingData.RouteMode.EFFICIENT, true, true)
				&& !PmbPathfindingController.permitsOrdinarySpecialRoute(
						PmbPathfindingData.RouteMode.EFFICIENT, false, false),
				"efficient may compare a full vanilla path but never escapes FOLLOW_RANGE");
		check(PmbPathfindingController.shouldReplanRetreat(100, 110, true, true, false)
				== false
				&& PmbPathfindingController.shouldReplanRetreat(110, 110, true, true, false)
				&& !PmbPathfindingController.shouldReplanRetreat(110, 120, true, true, false)
				&& PmbPathfindingController.shouldReplanRetreat(120, 120, true, true, false),
				"stable retreat threat obeys different configured repath intervals");
		check(PmbPathfindingController.shouldReplanRetreat(101, 110, false, true, false)
				&& PmbPathfindingController.shouldReplanRetreat(101, 110, true, true, true)
				&& PmbPathfindingController.shouldReplanRetreat(101, 110, true, false, false),
				"threat replacement, material movement, and missing destination replan immediately");

		PmbPathfindingController.RetreatPlan retreatPlan = new PmbPathfindingController.RetreatPlan();
		Object stableThreat = new Object();
		int[] selected = {0};
		int[] submitted = {0};
		java.util.function.Supplier<Vec3> selector = () -> new Vec3(++selected[0], 0, 0);
		var initial = retreatPlan.update(100, stableThreat, Vec3.ZERO, 2, 10, selector);
		if (initial.submitNavigation()) submitted[0]++;
		var retained = retreatPlan.update(105, stableThreat, Vec3.ZERO, 2, 10, selector);
		if (retained.submitNavigation()) submitted[0]++;
		var due = retreatPlan.update(110, stableThreat, Vec3.ZERO, 2, 20, selector);
		if (due.submitNavigation()) submitted[0]++;
		check(selected[0] == 2 && submitted[0] == 2 && !retained.submitNavigation()
				&& due.destination().x() == 2,
				"existing away destination is retained before due and reselected/resubmitted when due");
		var longerInterval = retreatPlan.update(125, stableThreat, Vec3.ZERO, 2, 20, selector);
		if (longerInterval.submitNavigation()) submitted[0]++;
		check(selected[0] == 2 && submitted[0] == 2,
				"updated longer retreat interval delays the next production refresh");
		var movedThreat = retreatPlan.update(126, stableThreat, new Vec3(3, 0, 0), 2, 20, selector);
		if (movedThreat.submitNavigation()) submitted[0]++;
		check(selected[0] == 3 && submitted[0] == 3 && movedThreat.destination().x() == 3,
				"material threat movement immediately reselects and resubmits the retreat route");
	}
	private static void testFallRescueMethodOrder() {
		check(!PmbConditionalController.plannedPromiseEligible(.5F, 1.0F, 8, 4,
				true, true, true), "planned rescue below minRescueDamage cannot promise a counter");
		check(PmbConditionalController.plannedPromiseEligible(4.0F, 1.0F, 8, 0,
				true, true, true), "lead zero remains a valid future pre-impact rescue promise");
		check(!PmbConditionalController.plannedPromiseEligible(4.0F, 1.0F, 8, 4,
				false, true, true)
				&& !PmbConditionalController.plannedPromiseEligible(4.0F, 1.0F, 8, 4,
						true, false, true)
				&& !PmbConditionalController.plannedPromiseEligible(4.0F, 1.0F, 8, 4,
						true, true, false),
				"cooldown, missing method item, and impossible pearl trajectory reject promises");
		check(!PmbConditionalController.rescueWindowOpen(2, 0)
				&& PmbConditionalController.rescueWindowOpen(1, 0)
				&& PmbConditionalController.rescueWindowOpen(4, 4),
				"lead zero opens on the last airborne pre-movement tick and configured lead opens on time");
		Vec3 promisedEndpoint = new Vec3(8, 64, 8);
		Vec3 currentBelow = new Vec3(2, 40, 2);
		check(PmbConditionalController.rescueTarget(true, promisedEndpoint, currentBelow).equals(promisedEndpoint)
				&& PmbConditionalController.rescueTarget(false, promisedEndpoint, currentBelow).equals(currentBelow),
				"planned pearl commit keeps the promised endpoint while accidental rescue uses the current landing");
		check(PmbConditionalController.samePlannedEndpoint(promisedEndpoint, promisedEndpoint)
				&& !PmbConditionalController.samePlannedEndpoint(promisedEndpoint, currentBelow),
				"changed planned endpoint invalidates the existing conditional_required promise");
		int highEndpointTicks = PmbConditionalController.estimatedImpactTicks(20, 15, -.3);
		int lowEndpointTicks = PmbConditionalController.estimatedImpactTicks(20, 5, -.3);
		check(highEndpointTicks < lowEndpointTicks,
				"planned rescue release timing follows the promised endpoint height");
		int plannedLastTick = PmbConditionalController.estimatedImpactTicks(10.2, 10, -.5);
		check(plannedLastTick == 1 && PmbConditionalController.rescueWindowOpen(plannedLastTick, 0),
				"lead zero opens on the promised endpoint's final airborne tick");
		Vec3 accidentalLanding = PmbConditionalController.rescueTarget(false, promisedEndpoint, currentBelow);
		check(PmbConditionalController.estimatedImpactTicks(50, accidentalLanding.y(), -.5)
				== PmbConditionalController.estimatedImpactTicks(50, currentBelow.y(), -.5),
				"accidental rescue timing remains based on the current landing estimate");
		PmbConditionalController failed = new PmbConditionalController();
		failed.recordAccidentalAttempt("wind_charge", false);
		check(!failed.accidentalMethodAllowed("ender_pearl", true)
				&& !failed.accidentalMethodAllowed("ender_pearl", false),
				"wind chance failure remains terminal across later ticks of the same fall");

		PmbConditionalController infeasible = new PmbConditionalController();
		check(infeasible.accidentalMethodAllowed("ender_pearl", true),
				"physically infeasible wind records no attempt and permits pearl fallback");

		PmbConditionalController committed = new PmbConditionalController();
		committed.recordAccidentalAttempt("wind_charge", true);
		check(!committed.accidentalMethodAllowed("ender_pearl", true),
				"chance success alone cannot unlock a lower rescue method before commit");
		committed.recordCommittedRescue("wind_charge");
		check(!committed.accidentalMethodAllowed("ender_pearl", true)
				&& committed.accidentalMethodAllowed("ender_pearl", false),
				"committed rescue locks default single-method falls while 0b preserves the follow-up method");

		PmbConditionalController plannedCommitted = new PmbConditionalController();
		plannedCommitted.recordCommittedRescue("wind_charge");
		check(!plannedCommitted.shouldResetAccidentalFall(false, .8)
				&& !plannedCommitted.shouldResetAccidentalFall(false, .1)
				&& !plannedCommitted.accidentalMethodAllowed("ender_pearl", true)
				&& plannedCommitted.accidentalMethodAllowed("ender_pearl", false),
				"planned wind keeps the single-method lock through ascent and planned-to-accidental transition");
		check(plannedCommitted.shouldResetAccidentalFall(true, 0),
				"landing explicitly ends the committed rescue fall epoch");
		plannedCommitted.finishFallIfGrounded(false);
		check(!plannedCommitted.accidentalMethodAllowed("ender_pearl", true),
				"airborne teleport or rebound cannot release the method lock");
		plannedCommitted.finishFallIfGrounded(true);
		check(plannedCommitted.accidentalMethodAllowed("ender_pearl", true),
				"first actual landing releases the single-method lock");
		plannedCommitted.clear();
		check(plannedCommitted.accidentalMethodAllowed("wind_charge", true),
				"runtime reset clears attempts and method lock");
		for (int drop : new int[]{10, 20, 40, 80}) {
			double height = drop, velocity = 0; boolean feasible = false;
			while (height > 0) {
				if (PmbConditionalController.rescueWindowOpen(PmbConditionalController.estimatedImpactTicks(height, 0, velocity), 4)
						&& PmbConditionalController.rescueWindFeasible(height, new Vec3(0, velocity, 0))) feasible = true;
				height += velocity; velocity = (velocity - .08) * .98;
			}
			check(feasible, "rescue wind has a timely in-radius frame for " + drop + " block fall");
		}
		check(!PmbConditionalController.rescueWindFeasible(5.446, new Vec3(0, -1.355, 0)),
				"wind waits when early explosion would be outside its 2.4 block radius");
		check(PmbConditionalController.plannedWindFeasible(10, Vec3.ZERO, 4),
				"planned wind promise forecasts a feasible release inside the later lead window");
		check(!PmbConditionalController.plannedWindFeasible(10, Vec3.ZERO, 0),
				"planned wind does not promise a projectile with no timely final-tick release");
		String fallbackMethod = PmbConditionalController.firstPromisableMethod(
				PmbFallRescueAiData.DEFAULT_METHODS,
				method -> method.equals("wind_charge")
						? PmbConditionalController.plannedWindFeasible(10, Vec3.ZERO, 0)
						: PmbConditionalController.plannedPromiseEligible(8, 1, 8, 4, true, true, true));
		check("ender_pearl".equals(fallbackMethod),
				"physically infeasible planned wind permits a feasible pearl promise in methods order");
		check(!PmbConditionalController.plannedPearlHasFlightWindow(20, 0, 0)
				&& !PmbConditionalController.plannedPearlHasFlightWindow(20, 0, 1)
				&& PmbConditionalController.plannedPearlHasFlightWindow(20, 0, 4),
				"pearl-only planned descents reject lead zero/one that cannot arrive before the owner");
		PmbConditionalController aborted = new PmbConditionalController();
		aborted.recordAccidentalAttempt("wind_charge", true);
		aborted.abortAttempt("wind_charge");
		check(aborted.accidentalMethodAllowed("wind_charge", true), "failed final commit does not leave rescue awaiting forever");
		Vec3 owned = new Vec3(1, 2, 3);
		check(PmbImpulseSessionController.ownsCurrent(owned, owned)
				&& !PmbImpulseSessionController.ownsCurrent(owned, new Vec3(1, 2, 3)),
				"equal-valued external impulse context is not PMB ownership");
		for (int flight : new int[]{1, 4, 20}) {
			Vec3 offset = new Vec3(3, -7, 2);
			Vec3 velocity = PmbPearlTrajectorySimulator.airInitialVelocity(offset, flight);
			Vec3 displacement = Vec3.ZERO;
			for (int tick = 0; tick < flight; tick++) {
				velocity = velocity.add(0, -.03, 0).scale(.99);
				displacement = displacement.add(velocity);
			}
			check(displacement.distanceToSqr(offset) < 1.0E-15,
					"pearl inverse flight uses vanilla gravity then drag then move at " + flight + " ticks");
		}
	}
	private static void resolveCandidates(PmbSkillScheduler scheduler) throws Exception {
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		var method = PmbSkillScheduler.class.getDeclaredMethod("resolveCandidates");
		method.setAccessible(true);
		method.invoke(scheduler);
	}
	private static void testFactionCompatCodec() {
		var legacy = com.pmb.faction.PmbVanillaCompatRules.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"piglin\":{\"greed\":false}}"))
				.getOrThrow();
		check(!legacy.piglin().greed(), "legacy piglin compatibility field preserved");
		check(legacy.breeze().windChargeNoAnger(), "legacy compatibility data defaults Breeze no-anger on");
		check(legacy.piglin().unreachableGiveUp() && legacy.piglin().admiringPausesActiveSkills(),
				"legacy piglin data enables guarded compatibility defaults");
		check(legacy.piglin().hurtAdmirationPolicy().equals("vanilla")
				&& legacy.piglin().hurtAdmirationDisableTicks() == 400
				&& legacy.piglin().hurtAdmiredItemPolicy().equals("drop") && !legacy.brainAiDebug(),
				"legacy piglin policy and high-volume debug use safe defaults");
		for (int value : new int[]{-1, 72001}) {
			check(com.pmb.faction.PmbVanillaCompatRules.CODEC.parse(JsonOps.INSTANCE,
					JsonParser.parseString("{\"piglin\":{\"hurtAdmirationDisableTicks\":" + value + "}}"))
					.error().isPresent(), "out-of-range admiration disable ticks rejected: " + value);
		}
		check(com.pmb.faction.PmbVanillaCompatRules.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"piglin\":{\"hurtAdmirationDisableTicks\":0}}"))
				.getOrThrow().piglin().hurtAdmirationDisableTicks() == 0,
				"zero admiration disable ticks accepted");
		var piglinRules = com.pmb.faction.PmbPiglinCompatRules.DEFAULT;
		check(piglinRules.effectiveHurtAdmirationDisableTicks() == 400,
				"default accepted hurt disables admiration for 400 ticks");
		check(piglinRules.withHurtAdmirationDisableTicks(0).effectiveHurtAdmirationDisableTicks() == 0,
				"zero timer preserves admiration for any responsible attacker");
		check(piglinRules.withHurtAdmirationPolicy("continue").effectiveHurtAdmirationDisableTicks() == 0,
				"continue policy overrides positive disable timer");
		check(piglinRules.withHurtAdmirationPolicy("combat").withHurtAdmirationDisableTicks(17)
				.effectiveHurtAdmirationDisableTicks() == 17,
				"legal ally revenge uses configured positive duration");
		check(com.pmb.faction.PmbVanillaCompatRules.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"piglin\":{\"hurtAdmirationPolicy\":\"bad\"}}"))
				.error().isPresent(), "invalid admiration policy rejected");
		check(com.pmb.faction.PmbVanillaCompatRules.CODEC.parse(JsonOps.INSTANCE,
				JsonParser.parseString("{\"piglin\":{\"hurtAdmiredItemPolicy\":\"bad\"}}"))
				.error().isPresent(), "invalid admired-item policy rejected");

		var configured = new com.pmb.faction.PmbVanillaCompatRules(
				com.pmb.faction.PmbPiglinCompatRules.DEFAULT,
				new com.pmb.faction.PmbBreezeCompatRules(false), true);
		var encoded = com.pmb.faction.PmbVanillaCompatRules.CODEC
				.encodeStart(JsonOps.INSTANCE, configured).getOrThrow();
		var decoded = com.pmb.faction.PmbVanillaCompatRules.CODEC
				.parse(JsonOps.INSTANCE, encoded).getOrThrow();
		check(!decoded.breeze().windChargeNoAnger(), "Breeze compatibility override survives codec round trip");
		check(decoded.brainAiDebug(), "Brain AI debug switch survives codec round trip");
		var timed = configured.withPiglin(configured.piglin().withHurtAdmirationDisableTicks(1200));
		check(com.pmb.faction.PmbVanillaCompatRules.CODEC.parse(JsonOps.INSTANCE,
				com.pmb.faction.PmbVanillaCompatRules.CODEC.encodeStart(JsonOps.INSTANCE, timed).getOrThrow())
				.getOrThrow().piglin().hurtAdmirationDisableTicks() == 1200,
				"configured admiration disable ticks survive codec round trip");
	}
	private static void testFactionBrainIntent() throws Exception {
		Class<?> coordinator = Class.forName("com.pmb.faction.PmbBrainIntentCoordinator");
		var choose = coordinator.getDeclaredMethod("choose", boolean.class, boolean.class, boolean.class);
		choose.setAccessible(true);
		check(choose.invoke(null, true, true, true).toString().equals("ADMIRE"),
				"gold admiration owns the Brain even with pending combat and avoid targets");
		check(choose.invoke(null, false, true, true).toString().equals("AVOID"),
				"Faction avoidance precedes combat when not admiring");
		check(choose.invoke(null, false, false, true).toString().equals("COMBAT"),
				"Faction combat publishes after admiration ends");
		check(choose.invoke(null, false, false, false).toString().equals("VANILLA"),
				"non-faction Brain intent is unchanged");
	}
	private static void testFactionTargetRetention() throws Exception {
		var selection = com.pmb.faction.PmbFactionAi.class.getDeclaredMethod(
				"scannedCombatTarget", Object.class, Object.class);
		selection.setAccessible(true);
		var retention = com.pmb.faction.PmbFactionAi.class.getDeclaredMethod(
				"retainsCombatTarget", boolean.class, com.pmb.faction.PmbFactionAttitude.class,
				boolean.class, boolean.class);
		retention.setAccessible(true);
		var avoidWalk = com.pmb.faction.PmbFactionAi.class.getDeclaredMethod(
				"shouldClearAvoidanceWalkTarget", boolean.class, boolean.class);
		avoidWalk.setAccessible(true);
		var hostile = com.pmb.faction.PmbFactionAttitude.HOSTILE;
		var current = new Object();
		for (int scan = 0; scan < 3; scan++) {
			check((boolean) retention.invoke(null, true, hostile, false, false),
					"valid faction combat target survives temporary sight loss");
			check(selection.invoke(null, current, null) == current,
					"periodic visible-target scan retains validated combat target");
		}
		check(selection.invoke(null, null, null) == null,
				"first hostile acquisition still requires visible candidate");
		var nearerVisible = new Object();
		check(selection.invoke(null, current, nearerVisible) == nearerVisible,
				"nearer visible hostile replaces current target");
		check(!(boolean) retention.invoke(null, false, hostile, false, false),
				"dead, out-of-range, or other-dimension combat target is removed");
		check(!(boolean) retention.invoke(null, true, com.pmb.faction.PmbFactionAttitude.ALLIED, false, false),
				"relationship change to allied clears combat target");
		check(!(boolean) retention.invoke(null, true, com.pmb.faction.PmbFactionAttitude.ACTIVELY_EVASIVE, false, false),
				"relationship change to evasive clears combat target");
		check(!(boolean) retention.invoke(null, true, hostile, true, false),
				"enabled piglin avoidance rejects combat target");
		check((boolean) retention.invoke(null, true, hostile, false, false),
				"disabled piglin avoidance retains hostile combat target");
		check((boolean) avoidWalk.invoke(null, false, false),
				"suppressed idle avoidance discards its old walking target");
		check(!(boolean) avoidWalk.invoke(null, true, false),
				"faction combat preserves FIGHT walking target");
		check(!(boolean) avoidWalk.invoke(null, false, true),
				"Brain combat preserves FIGHT walking target");
		var hurtBroadcast = com.pmb.faction.PmbFactionAi.class.getDeclaredMethod(
				"validHurtBroadcast", boolean.class, boolean.class, boolean.class, boolean.class);
		hurtBroadcast.setAccessible(true);
		var angerWrite = com.pmb.faction.PmbFactionAi.class.getDeclaredMethod(
				"angerWriteApplies", boolean.class, boolean.class);
		angerWrite.setAccessible(true);
		check(!(boolean) hurtBroadcast.invoke(null, true, true, true, false),
				"piglin self-hurt cannot be mistaken for an ally response");
		check(!(boolean) hurtBroadcast.invoke(null, false, false, true, false),
				"another attack target cannot reuse a teammate's hurt broadcast");
		check(!(boolean) hurtBroadcast.invoke(null, false, true, false, false),
				"non-allied piglin broadcast cannot force Faction admiration interruption");
		check(!(boolean) hurtBroadcast.invoke(null, false, true, true, true),
				"allied attacker cannot trigger combat admiration interruption");
		check((boolean) hurtBroadcast.invoke(null, false, true, true, false),
				"new legitimate ally damage enables same-attacker suppression bypass during vanilla Sensor check");
		check(!(boolean) angerWrite.invoke(null, true, false),
				"Sensor rejection without ANGRY_AT write cannot interrupt admiration");
		check(!(boolean) angerWrite.invoke(null, false, true),
				"gold guarding or unscoped anger cannot impersonate an ally-damage write");
		check((boolean) angerWrite.invoke(null, true, true),
				"confirmed ally-damage ANGRY_AT write may interrupt combat admiration");
		var broadcastDepth = com.pmb.faction.PmbFactionAi.class.getDeclaredMethod("hurtBroadcastDepth");
		broadcastDepth.setAccessible(true);
		check((int) broadcastDepth.invoke(null) == 0, "hurt broadcast starts with no active scope");
		com.pmb.faction.PmbFactionAi.withVanillaHurtBroadcast(null, null, () -> {
			try {
				check((int) broadcastDepth.invoke(null) == 1, "normal hurt broadcast enters one scope");
				com.pmb.faction.PmbFactionAi.withVanillaHurtBroadcast(null, null, () -> {
					try {
						check((int) broadcastDepth.invoke(null) == 2, "nested hurt broadcast has independent scope");
					} catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
				});
				check((int) broadcastDepth.invoke(null) == 1, "nested broadcast restores outer scope");
			} catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
		});
		check((int) broadcastDepth.invoke(null) == 0, "normal broadcast clears scope");
		RuntimeException failure = new RuntimeException("expected broadcast failure");
		try {
			com.pmb.faction.PmbFactionAi.withVanillaHurtBroadcast(null, null, () -> { throw failure; });
			throw new AssertionError("broadcast exception was swallowed");
		} catch (RuntimeException actual) {
			check(actual == failure, "broadcast propagates the original exception unchanged");
		}
		check((int) broadcastDepth.invoke(null) == 0,
				"exceptional broadcast clears ThreadLocal before any later anger decision");
	}
	private static void testWindChargeNeutralFallbackPredicate() throws Exception {
		var predicate = com.pmb.faction.PmbFactionCombat.class.getDeclaredMethod(
				"shouldApplyWindChargeNeutralFallback", boolean.class, boolean.class, boolean.class,
				boolean.class, boolean.class, boolean.class);
		predicate.setAccessible(true);
		check(!(boolean) predicate.invoke(null, true, false, true, false, false, true),
				"non-wind damage cannot use wind neutral fallback");
		check(!(boolean) predicate.invoke(null, true, true, false, false, false, true),
				"victim outside vanilla wind no-anger tag cannot use fallback");
		check(!(boolean) predicate.invoke(null, true, true, true, true, false, true),
				"generic no-anger damage retains vanilla precedence");
		check(!(boolean) predicate.invoke(null, true, true, true, false, true, true),
				"Breeze owner is preserved by enabled compatibility rule");
		check((boolean) predicate.invoke(null, true, true, true, false, false, true),
				"non-Breeze owner bypasses the species compatibility exception");
		check((boolean) predicate.invoke(null, true, true, true, false, true, false),
				"disabled compatibility rule permits Breeze owner fallback");
		check(!(boolean) predicate.invoke(null, false, true, true, false, false, true),
				"ownerless damage cannot use neutral fallback");
	}
	private static void offer(Fixture mob, String owner, PmbMovementController.Type type,
			PmbMovementController.Tier tier, int rank, List<String> trace) {
		mob.scheduler.movement().submit(mob, owner, type, tier, PmbSkillScheduler.Category.MAIN,
				rank, () -> true, () -> trace.add(owner));
	}
	private static PmbSkillItemAccess.Resolved resolve(Fixture mob, PmbActivationSources sources, PmbPreferredHand preference) {
		return PmbSkillItemAccess.resolvePreferred(mob, sources, List.of(InteractionHand.MAIN_HAND),
				stack -> stack.is(Items.BOW), preference, "bow");
	}
	private static PmbActivationSources sources(PmbActivationSources.Source... entries) throws Exception {
		var sources = new PmbActivationSources();
		field(PmbActivationSources.class, "explicit").setBoolean(sources, true);
		@SuppressWarnings("unchecked") List<PmbActivationSources.Source> list =
				(List<PmbActivationSources.Source>) field(PmbActivationSources.class, "sources").get(sources);
		list.addAll(List.of(entries));
		return sources;
	}
	private static void testRescueVelocityAndLook() throws Exception {
		check(PmbConditionalController.rescueInheritedVelocity(new Vec3(3, -2, 4)).equals(new Vec3(0, -2, 0)),
				"rescue pearls inherit only negative vertical velocity");
		check(PmbConditionalController.rescueInheritedVelocity(new Vec3(3, 2, 4)).equals(Vec3.ZERO),
				"upward and horizontal owner motion never enter rescue inheritance");
		Vec3 scatteredImpulse = new Vec3(.1, -1.2, .2);
		check(PmbConditionalController.combineRescueVelocity(scatteredImpulse, new Vec3(0, -3, 0))
				.subtract(scatteredImpulse).distanceTo(new Vec3(0, -3, 0)) < 1.0E-9,
				"sampled impulse gets inherited velocity added exactly once without spreading the inherited part");
		for (int drop : new int[]{10, 20, 50, 200}) for (int lead : new int[]{5, 10}) {
			double height = drop, speed = 0;
			boolean feasible = false;
			while (height > 0) {
				int deadline = PmbConditionalController.estimatedImpactTicks(height, 0, speed);
				if (PmbConditionalController.rescueWindowOpen(deadline, lead)) {
					Vec3 inherited = PmbConditionalController.rescueInheritedVelocity(new Vec3(0, speed, 0));
					for (int ticks = 1; ticks < deadline; ticks++) {
						Vec3 world = PmbPearlTrajectorySimulator.airInitialVelocity(new Vec3(0, -height - 1.64, 0), ticks);
						if (world.subtract(inherited).length() <= 1.5) feasible = true;
					}
				}
				height += speed; speed = (speed - .08) * .98;
			}
			check(feasible, "rescue relative impulse is feasible at drop=" + drop + ", lead=" + lead);
		}
		check(PmbPearlTrajectorySimulator.airInitialVelocity(new Vec3(0, -20, 0), 1)
				.subtract(new Vec3(0, -3, 0)).length() > 1.5,
				"inheritance still rejects an impossible one-tick rescue rather than ignoring power");
		Fixture mob = fixture();
		CompoundTag enabled = new CompoundTag(); enabled.putBoolean("enable", true);
		CompoundTag rescueParent = new CompoundTag(); rescueParent.put("fall_rescue", enabled);
		mob.ai.fallRescue().read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, rescueParent));
		PmbConditionalController controller = new PmbConditionalController();
		PmbSkillScheduler scheduler = mob.scheduler;
		check(mob.ai.fallRescue().singleMethodPerFall(),
				"omitted singleMethodPerFall defaults to one successful rescue method per landing");
		controller.recordCommittedRescue("wind_charge");
		check(controller.singleMethodLocked(mob.ai.fallRescue())
				&& !controller.canPromisePlannedFall(mob, scheduler, null),
				"committed rescue blocks a second planned fall promise in the same airborne epoch");
		controller.beginRescueLook(mob, "wind_charge", new Vec3(0, -1, 0));
		controller.cancelTemporary();
		controller.finishFallIfGrounded(false);
		check(controller.singleMethodLocked(mob.ai.fallRescue())
				&& field(PmbConditionalController.class, "rescueLookTarget").get(controller) == null,
				"temporary NoAI block cancels LOOK but preserves the in-air rescue method lock");
		controller.cancelTemporary();
		check(controller.singleMethodLocked(mob.ai.fallRescue()),
				"temporary shield vulnerability block cannot unlock a second rescue during the same fall");
		controller.clear();
		check(!controller.singleMethodLocked(mob.ai.fallRescue()),
				"full runtime clear still releases the method lock");
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		controller.beginRescueLook(mob, "ender_pearl", new Vec3(0, -1, 0));
		controller.maintainLook(mob, scheduler);
		check(scheduler.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK)
				&& scheduler.canClaim("other", PmbSkillScheduler.Resource.MAIN_HAND)
				&& scheduler.canClaim("other", PmbSkillScheduler.Resource.USE_ITEM),
				"real rescue follow-through owns only LOOK, freeing hands and USE_ITEM");
		PmbSkillScheduler firstLookScheduler = scheduler;
		scheduler.offerDynamicPlannedResult("fall_rescue", "chance_failed", PmbSkillScheduler.Category.THROW, 1,
				() -> false, () -> true, () -> PmbSkillScheduler.CommitResult.COMMITTED,
				() -> { controller.clearRescueLook(); firstLookScheduler.dropPhase("fall_rescue", "rescue_look_ender_pearl"); },
				() -> new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.LOOK});
		resolveCandidates(scheduler);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(controller) == null
				&& scheduler.canClaim("other", PmbSkillScheduler.Resource.LOOK),
				"failed rescue chance clears its same-tick LOOK phase and claim");
		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		controller.beginRescueLook(mob, "ender_pearl", new Vec3(0, -1, 0));
		controller.maintainLook(mob, scheduler);
		scheduler.offerPlannedResult("mace", "failed", PmbSkillScheduler.Category.MAIN, 1,
				() -> true, () -> true, () -> PmbSkillScheduler.CommitResult.FAILED, PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(controller) != null
				&& scheduler.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK),
				"failed higher-priority commit preserves rescue LOOK state and ownership");
		((List<?>) field(PmbSkillScheduler.class, "candidates").get(scheduler)).clear();
		scheduler.offerPlannedResult("mace", "success", PmbSkillScheduler.Category.MAIN, 1,
				() -> true, () -> true, () -> PmbSkillScheduler.CommitResult.COMMITTED, PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(controller) != null,
				"successful higher-priority commit pauses rescue LOOK without discarding its fall state");
		((Map<?, ?>) field(PmbSkillScheduler.class, "claimedThisTick").get(scheduler)).clear();
		((Map<?, ?>) field(PmbSkillScheduler.class, "activePhases").get(scheduler)).clear();
		controller.maintainLook(mob, scheduler);
		check(scheduler.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK),
				"rescue LOOK resumes after higher-priority action releases LOOK");
		controller.beginRescueLook(mob, "wind_charge", new Vec3(0, -1, 0));
		enabled.putBoolean("enable", false);
		mob.ai.fallRescue().read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, rescueParent));
		controller.maintainLook(mob, scheduler);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(controller) == null,
				"disabling fall rescue clears its sustained LOOK through real maintainPhase validation");
		enabled.putBoolean("enable", true);
		mob.ai.fallRescue().read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, rescueParent));
		scheduler = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.COMBAT);
		controller.beginRescueLook(mob, "wind_charge", new Vec3(0, -1, 0));
		controller.maintainLook(mob, scheduler);
		boolean[] lowerRan = {false};
		scheduler.offerPlannedResult("bow", "charge", PmbSkillScheduler.Category.MAIN, 1,
				() -> true, () -> true, () -> { lowerRan[0] = true; return PmbSkillScheduler.CommitResult.COMMITTED; },
				PmbSkillScheduler.Resource.LOOK);
		resolveCandidates(scheduler);
		check(!lowerRan[0] && field(PmbConditionalController.class, "rescueLookTarget").get(controller) != null,
				"lower priority cannot interrupt real rescue follow-through");
		controller.finishLookTick(false);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(controller) != null,
				"airborne rebound or teleport does not terminate rescue LOOK");
		controller.finishLookTick(true);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(controller) == null
				&& scheduler.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK),
				"landing clears runtime only after retaining LOOK for completion tick");
		((Map<?, ?>) field(PmbSkillScheduler.class, "claimedThisTick").get(scheduler)).clear();
		((Map<?, ?>) field(PmbSkillScheduler.class, "activePhases").get(scheduler)).clear();
		controller.maintainLook(mob, scheduler);
		check(scheduler.canClaim("bow", PmbSkillScheduler.Resource.LOOK), "next tick after landing frees LOOK");
		controller.beginRescueLook(mob, "ender_pearl", new Vec3(0, -1, 0));
		field(PmbSkillScheduler.class, "strategy").set(scheduler, PmbSkillScheduler.Strategy.BLOCKED);
		controller.maintainLook(mob, scheduler);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(controller) == null,
				"blocked strategy drops rescue follow-through through priority validation");
		PmbSkillScheduler methodScheduler = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(methodScheduler, PmbSkillScheduler.Strategy.COMBAT);
		methodScheduler.claim("shield", PmbSkillScheduler.Resource.OFF_HAND);
		boolean[] windAttempted = {false}, pearlCommitted = {false};
		methodScheduler.offerDynamicPlannedResult("fall_rescue", "wind_charge", PmbSkillScheduler.Category.THROW, 100,
				() -> { windAttempted[0] = true; return true; }, () -> true,
				() -> PmbSkillScheduler.CommitResult.COMMITTED,
				() -> { controller.clearRescueLook(); methodScheduler.dropPhase("fall_rescue", "rescue_look_wind_charge"); },
				() -> new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.OFF_HAND, PmbSkillScheduler.Resource.LOOK});
		methodScheduler.offerDynamicPlannedResult("fall_rescue", "ender_pearl", PmbSkillScheduler.Category.THROW, 99,
				() -> { controller.beginRescueLook(mob, "ender_pearl", new Vec3(0, -1, 0)); return true; },
				() -> true,
				() -> { pearlCommitted[0] = true; return PmbSkillScheduler.CommitResult.COMMITTED; },
				() -> { controller.clearRescueLook(); methodScheduler.dropPhase("fall_rescue", "rescue_look_ender_pearl"); },
				() -> new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.MAIN_HAND, PmbSkillScheduler.Resource.LOOK});
		resolveCandidates(methodScheduler);
		check(!windAttempted[0] && pearlCommitted[0]
				&& methodScheduler.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK),
				"blocked wind hand skips its chance and admits the later free-hand pearl with LOOK in the same tick");
		controller.clearRescueLook();
		methodScheduler.dropPhase("fall_rescue", "rescue_look_ender_pearl");
		CompoundTag followupSettings = new CompoundTag();
		followupSettings.putBoolean("enable", true);
		followupSettings.putBoolean("singleMethodPerFall", false);
		rescueParent.put("fall_rescue", followupSettings);
		mob.ai.fallRescue().read(TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY, rescueParent));
		PmbConditionalController followup = new PmbConditionalController();
		followup.recordCommittedRescue("wind_charge");
		followup.beginRescueLook(mob, "wind_charge", new Vec3(0, -2, 0));
		check(!followup.singleMethodLocked(mob.ai.fallRescue()),
				"singleMethodPerFall false permits a later rescue after committed wind");
		PmbSkillScheduler blockedPearl = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(blockedPearl, PmbSkillScheduler.Strategy.COMBAT);
		followup.maintainLook(mob, blockedPearl);
		blockedPearl.claim("other", PmbSkillScheduler.Resource.MAIN_HAND);
		boolean[] blockedPearlRolled = {false};
		blockedPearl.offerDynamicPlannedResult("fall_rescue", "ender_pearl", PmbSkillScheduler.Category.THROW, 99,
				() -> { blockedPearlRolled[0] = true; return true; }, () -> true,
				() -> PmbSkillScheduler.CommitResult.COMMITTED,
				() -> followup.abortStagedRescueLook(null),
				() -> new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.MAIN_HAND, PmbSkillScheduler.Resource.LOOK});
		resolveCandidates(blockedPearl);
		check(!blockedPearlRolled[0] && "wind_charge".equals(field(PmbConditionalController.class, "rescueLookMethod").get(followup))
				&& blockedPearl.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK),
				"resource-blocked follow-up pearl does not erase committed wind LOOK or spend chance");
		PmbSkillScheduler failedPearl = new PmbSkillScheduler();
		field(PmbSkillScheduler.class, "strategy").set(failedPearl, PmbSkillScheduler.Strategy.COMBAT);
		followup.maintainLook(mob, failedPearl);
		boolean[] failedPearlRolled = {false};
		failedPearl.offerDynamicPlannedResult("fall_rescue", "ender_pearl", PmbSkillScheduler.Category.THROW, 99,
				() -> { failedPearlRolled[0] = true; return false; }, () -> true,
				() -> PmbSkillScheduler.CommitResult.COMMITTED,
				() -> followup.abortStagedRescueLook(null),
				() -> new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.MAIN_HAND, PmbSkillScheduler.Resource.LOOK});
		resolveCandidates(failedPearl);
		check(failedPearlRolled[0] && "wind_charge".equals(field(PmbConditionalController.class, "rescueLookMethod").get(followup))
				&& failedPearl.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK),
				"failed follow-up pearl chance preserves committed wind LOOK");
		PmbConditionalController.RescueLookClaim stagedPearl = followup.stageRescueLook(mob, "ender_pearl", new Vec3(0, -3, 0));
		followup.abortStagedRescueLook(stagedPearl);
		check("wind_charge".equals(field(PmbConditionalController.class, "rescueLookMethod").get(followup)),
				"failed final check restores earlier committed LOOK after tentative pearl preview");
		PmbConditionalController bound = (PmbConditionalController) field(PmbSkillScheduler.class, "conditional").get(mob.scheduler);
		bound.beginRescueLook(mob, "wind_charge", new Vec3(0, -1, 0));
		mob.scheduler.clearBindings(mob);
		check(field(PmbConditionalController.class, "rescueLookTarget").get(bound) == null,
				"scheduler runtime reset clears conditional LOOK state");
	}
	private static Field field(Class<?> type, String name) throws Exception {
		Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
	}
	private static Fixture fixture() throws Exception {
		Unsafe unsafe = (Unsafe) field(Unsafe.class, "theUnsafe").get(null);
		Fixture mob = (Fixture) unsafe.allocateInstance(Fixture.class);
		mob.items = new EnumMap<>(InteractionHand.class);
		mob.scheduler = new PmbSkillScheduler(); mob.inventory = new PmbInventory(); mob.ai = new PmbAiData();
		mob.inventory.read(mob, TagValueInput.create(ProblemReporter.DISCARDING, RegistryAccess.EMPTY,
				inventoryRoot(3)));
		return mob;
	}
	private static void check(boolean condition, String label) {
		if (!condition) throw new AssertionError(label); checks++;
	}
	private static final class Fixture extends Mob implements PmbSchedulerHolder, PmbInventoryHolder, PmbAiHolder,
			com.pmb.faction.PmbFactionMobState {
		private net.minecraft.world.entity.LivingEntity combat, avoid;
		private net.minecraft.world.entity.LivingEntity lastBrain, suppressed;
		private net.minecraft.world.phys.Vec3 suppressedPosition;
		private boolean factionAdmiring;
		private long suppressedUntil;
		private Map<InteractionHand, ItemStack> items;
		private PmbSkillScheduler scheduler;
		private PmbInventory inventory;
		private PmbAiData ai;
		private Fixture() { super(EntityTypes.ZOMBIE, null); }
		@Override public ItemStack getItemInHand(InteractionHand hand) { return items.getOrDefault(hand, ItemStack.EMPTY); }
		@Override public ItemStack getMainHandItem() { return getItemInHand(InteractionHand.MAIN_HAND); }
		@Override public ItemStack getOffhandItem() { return getItemInHand(InteractionHand.OFF_HAND); }
		@Override public void setItemInHand(InteractionHand hand, ItemStack item) { items.put(hand, item); }
		@Override public boolean isAlive() { return true; }
		@Override public boolean isNoAi() { return false; }
		@Override public PmbSkillScheduler pmb$getSkillScheduler() { return scheduler; }
		@Override public PmbInventory pmb$getInventory() { return inventory; }
		@Override public PmbAiData pmb$getAiData() { return ai; }
		@Override public net.minecraft.world.entity.LivingEntity getTarget() { return combat; }
		@Override public net.minecraft.world.entity.LivingEntity pmb$getFactionCombatTarget() { return combat; }
		@Override public void pmb$setFactionCombatTarget(net.minecraft.world.entity.LivingEntity target) { combat = target; }
		@Override public net.minecraft.world.entity.LivingEntity pmb$getFactionAvoidTarget() { return avoid; }
		@Override public void pmb$setFactionAvoidTarget(net.minecraft.world.entity.LivingEntity target) { avoid = target; }
		@Override public int pmb$getFactionHurtTimestamp() { return 0; }
		@Override public void pmb$setFactionHurtTimestamp(int timestamp) {}
		@Override public boolean pmb$wasFactionAvoiding() { return avoid != null; }
		@Override public void pmb$setFactionAvoiding(boolean value) {}
		@Override public boolean pmb$isFactionGroupRevenge() { return false; }
		@Override public void pmb$setFactionGroupRevenge(boolean value) {}
		@Override public boolean pmb$wasFactionAdmiring() { return factionAdmiring; }
		@Override public void pmb$setFactionAdmiring(boolean value) { factionAdmiring = value; }
		@Override public net.minecraft.world.entity.LivingEntity pmb$getLastBrainCombatTarget() { return lastBrain; }
		@Override public void pmb$setLastBrainCombatTarget(net.minecraft.world.entity.LivingEntity target) { lastBrain = target; }
		@Override public net.minecraft.world.entity.LivingEntity pmb$getSuppressedTarget() { return suppressed; }
		@Override public void pmb$setSuppressedTarget(net.minecraft.world.entity.LivingEntity target) { suppressed = target; }
		@Override public long pmb$getSuppressedUntil() { return suppressedUntil; }
		@Override public void pmb$setSuppressedUntil(long tick) { suppressedUntil = tick; }
		@Override public net.minecraft.world.phys.Vec3 pmb$getSuppressedPosition() { return suppressedPosition; }
		@Override public void pmb$setSuppressedPosition(net.minecraft.world.phys.Vec3 position) { suppressedPosition = position; }
	}
}
