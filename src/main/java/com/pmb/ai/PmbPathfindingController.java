package com.pmb.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Owns PMB movement objectives. Skills and Faction submit intent; only this controller moves the mob. */
public final class PmbPathfindingController {
	public enum IntentKind { RETREAT, ACTIVE_TACTICAL, PURSUIT, IDLE }
	public enum RouteStage { NONE, VANILLA, SEARCHING, WIND, PEARL, APPROACH, ARMED, AIRBORNE, WAITING_CONDITIONAL }
	public enum ConditionalResult { PENDING, COMMITTED, CAPABILITY_LOST, EVENT_INVALID, FAILED_BEFORE_COMMIT, FAILED_AFTER_COMMIT }
	public enum FallCause { PLANNED_PATH, ACCIDENTAL }
	public record FallImpactContext(FallCause cause, Vec3 approachPosition, Vec3 expectedImpactPosition,
			float expectedDamage, int estimatedImpactTicks) {}
	public record DebugState(IntentKind intent, RouteStage stage, String owner, String proposal, String promise) {}
	public record TraversalObjective(IntentKind kind, LivingEntity authorityEntity, Vec3 destination, String owner) {}
	private record Objective(String owner, IntentKind kind, LivingEntity entity, Vec3 position, int epoch,
			Runnable movement) {}

	private final List<Objective> objectives = new ArrayList<>();
	private DebugState debug = new DebugState(null, RouteStage.NONE, null, null, null);
	private LivingEntity retreatThreat;
	private int retreatEpoch = Integer.MIN_VALUE;
	private TraversalObjective traversalObjective;
	private final RetreatPlan retreatPlan = new RetreatPlan();

	static final class RetreatPlan {
		record Update(Vec3 destination, boolean submitNavigation) {}
		private Object threatIdentity;
		private Vec3 threatPosition;
		private Vec3 destination;
		private int nextRepathTick = Integer.MIN_VALUE;

		Update update(int tick, Object threat, Vec3 currentThreatPosition, float moveThreshold,
				int repathIntervalTicks, Supplier<Vec3> destinationSelector) {
			boolean sameThreat = threatIdentity == threat;
			boolean threatMoved = threatPosition == null
					|| threatPosition.distanceToSqr(currentThreatPosition) > moveThreshold * moveThreshold;
			boolean replan = shouldReplanRetreat(tick, nextRepathTick, sameThreat,
					destination != null, threatMoved);
			if (replan) {
				destination = destinationSelector.get();
				threatIdentity = threat;
				threatPosition = currentThreatPosition;
				nextRepathTick = tick + repathIntervalTicks;
			}
			return new Update(destination, replan);
		}
		void clear() {
			threatIdentity = null;
			threatPosition = null;
			destination = null;
			nextRepathTick = Integer.MIN_VALUE;
		}
	}

	public DebugState debug() { return debug; }
	public TraversalObjective traversalObjective() { return traversalObjective; }
	void refreshTraversalDebug(RouteStage traversalStage, String proposal) {
		if (debug.stage() == RouteStage.APPROACH || debug.stage() == RouteStage.ARMED
				|| debug.stage() == RouteStage.AIRBORNE) return;
		if (traversalStage == RouteStage.NONE && proposal == null) return;
		debug = new DebugState(debug.intent(), traversalStage, debug.owner(), proposal, debug.promise());
	}
	public void clear() { objectives.clear(); retreatThreat = null; traversalObjective = null;
		retreatPlan.clear();
		debug = new DebugState(null, RouteStage.NONE, null, null, null); }

	/** Faction submits only the threat. Point selection and navigation belong here. */
	public void submitRetreat(Mob mob, LivingEntity threat) {
		retreatThreat = threat;
		retreatEpoch = mob.tickCount;
	}

	/** Bow submits a tactical objective; it does not write MoveControl directly. */
	public void submitBowTactical(Mob mob, LivingEntity target, boolean retreat, boolean mobile,
			boolean clockwise, boolean forward) {
		float forwardAmount = retreat ? -0.7F : (forward ? 0.2F : -0.2F);
		float sideways = clockwise ? 0.5F : -0.5F;
		Vec3 intended = bowTacticalDestination(mob.position(), target.position(), retreat, mobile, clockwise, forward);
		if (!retreat && !mobile) intended = mob.position();
		else if (mob.level() instanceof net.minecraft.server.level.ServerLevel level) {
			float horizon = ((PmbAiHolder) mob).pmb$getAiData().pathfinding().planner().planningHorizon();
			Vec3 landing = standingBelow(level, mob, intended, Math.max(5, (int) Math.ceil(horizon)));
			// Unknown ground is represented as an intentionally unapproved deep step.
			// The dangerous-edge planner will reject it unless it finds a real supported landing.
			intended = landing != null ? landing : intended.add(0, -Math.max(5, horizon), 0);
		}
		Vec3 destination = intended;
		objectives.add(new Objective("bow", IntentKind.ACTIVE_TACTICAL, target, destination, mob.tickCount, () -> {
			mob.getNavigation().stop();
			if (!retreat && !mobile) { mob.getMoveControl().setWait(); return; }
			((PmbBowMoveControl) mob.getMoveControl()).pmb$strafeForBow(forwardAmount, sideways);
		}));
	}
	static Vec3 bowTacticalDestination(Vec3 origin, Vec3 target, boolean retreat, boolean mobile,
			boolean clockwise, boolean forward) {
		if (!retreat && !mobile) return origin;
		Vec3 toward = target.subtract(origin).multiply(1, 0, 1);
		if (toward.lengthSqr() < 1.0E-6) toward = new Vec3(0, 0, 1);
		else toward = toward.normalize();
		Vec3 right = new Vec3(-toward.z(), 0, toward.x());
		double forwardAmount = retreat ? -.7 : (forward ? .2 : -.2);
		double sideways = clockwise ? .5 : -.5;
		Vec3 direction = toward.scale(forwardAmount).add(right.scale(sideways));
		return origin.add(direction.normalize().scale(2));
	}

	public void tick(Mob mob, PmbSkillScheduler scheduler) {
		if (!mob.isAlive() || mob.isNoAi()) { clear(); return; }
		objectives.removeIf(objective -> mob.tickCount - objective.epoch() > 1);
		if (retreatEpoch >= mob.tickCount - 1 && valid(mob, retreatThreat)) {
			LivingEntity threat = retreatThreat;
			PmbPathfindingData.Retreat retreat = ((PmbAiHolder) mob).pmb$getAiData().pathfinding().retreat();
			float threshold = ((PmbAiHolder) mob).pmb$getAiData().pathfinding().planner().targetMoveThreshold();
			RetreatPlan.Update update = retreatPlan.update(mob.tickCount, threat, threat.position(), threshold,
					retreat.repathIntervalTicks(), () -> chooseAwayDestination(mob, threat, retreat));
			Vec3 destination = update.destination();
			objectives.add(new Objective("faction_retreat", IntentKind.RETREAT, threat, destination, mob.tickCount,
					() -> navigateAway(mob, destination, retreat, update.submitNavigation())));
		} else if (retreatEpoch < mob.tickCount - 1 || !valid(mob, retreatThreat)) {
			retreatPlan.clear();
		}
		Objective winner = objectives.stream().min(Comparator.comparingInt(value -> value.kind().ordinal())).orElse(null);
		traversalObjective = selectTraversalObjective(mob, scheduler, winner);
		boolean movementApproved = planDangerousFall(mob, scheduler, traversalObjective);
		if (winner == null) {
			RouteStage fallStage = scheduler.plannedFallStage();
			String traversalProposal = scheduler.traversalProposal();
			IntentKind passiveIntent = traversalObjective == null ? null : traversalObjective.kind();
			RouteStage stage = fallStage != RouteStage.NONE ? fallStage
					: traversalProposal == null ? RouteStage.NONE
					: traversalProposal.equals("searching") ? RouteStage.SEARCHING : RouteStage.WAITING_CONDITIONAL;
			debug = new DebugState(passiveIntent, stage, traversalObjective == null ? null : traversalObjective.owner(),
					traversalProposal, fallStage == RouteStage.NONE ? null : "fall_rescue");
			objectives.clear();
			return;
		}
		if (movementApproved) scheduler.movement().submit(mob, winner.owner(), PmbMovementController.Type.LOCOMOTION,
				winner.kind() == IntentKind.RETREAT ? PmbMovementController.Tier.RETREAT : PmbMovementController.Tier.ACTIVE,
				PmbSkillScheduler.Category.MAIN, 0, () -> validObjective(mob, winner), winner.movement());
		RouteStage fallStage = scheduler.plannedFallStage();
		debug = new DebugState(winner.kind(), fallStage == RouteStage.NONE ? RouteStage.VANILLA : fallStage,
				winner.owner(), scheduler.traversalProposal() == null ? "vanilla_route" : scheduler.traversalProposal(),
				fallStage == RouteStage.NONE ? null : "fall_rescue");
		objectives.clear();
	}
	private TraversalObjective selectTraversalObjective(Mob mob, PmbSkillScheduler scheduler, Objective winner) {
		if (winner != null && winner.position() != null)
			return new TraversalObjective(winner.kind(), winner.entity(), winner.position(), winner.owner());
		LivingEntity target = mob.getTarget();
		if (valid(mob, target)) return new TraversalObjective(IntentKind.PURSUIT, target, target.position(), "vanilla");
		if (traversalObjective != null && traversalObjective.kind() == IntentKind.IDLE
				&& (scheduler.traversalProposal() != null || scheduler.plannedFallStage() != RouteStage.NONE))
			return traversalObjective;
		BlockPos navigationTarget = mob.getNavigation().getTargetPos();
		if (navigationTarget != null && !mob.getNavigation().isDone())
			return new TraversalObjective(IntentKind.IDLE, null, Vec3.atBottomCenterOf(navigationTarget), "vanilla_idle");
		return null;
	}
	private static boolean planDangerousFall(Mob mob, PmbSkillScheduler scheduler, TraversalObjective objective) {
		if (objective == null || !(mob.level() instanceof net.minecraft.server.level.ServerLevel level)) return true;
		PmbAiData ai = ((PmbAiHolder) mob).pmb$getAiData();
		// The vanilla movement objective exists for every Mob. It must not by
		// itself activate PMB's extra path search on an unconfigured creature.
		if (!ai.pathfinding().hasExplicitStrategy() && !ai.fallRescue().isEnabled()
				&& !ai.bow().isEnabled()) return true;
		PmbPathfindingData.Strategy policy = ai.pathfinding().strategy(scheduler.strategy());
		if (policy.routeMode() == PmbPathfindingData.RouteMode.VANILLA_ONLY
				&& !objective.owner().equals("bow")) return true;
		Vec3 destination = objective.destination();
		double drop = mob.getY() - destination.y();
		double horizontal = mob.position().multiply(1, 0, 1).distanceTo(destination.multiply(1, 0, 1));
		if (drop <= 4) return true;
		if (!objective.owner().equals("bow") && !ordinaryRouteAllowsDangerousEdge(mob, objective, policy)) return true;
		if (horizontal > ai.pathfinding().planner().planningHorizon()
				|| !level.hasChunkAt(BlockPos.containing(destination))) return !objective.owner().equals("bow");
		// Dangerous-edge validation and broad special-route search share the same
		// per-level queue. A denied unit postpones this check; it never means that
		// the route or counter is unreachable.
		if (!PmbPlanningBudget.acquire(level, mob, 1)) return false;
		PmbLocalEdgePlanner.Edge edge = PmbLocalEdgePlanner.findDangerousDrop(level, mob, destination,
				ai.pathfinding().planner().planningHorizon());
		if (edge == null) return !objective.owner().equals("bow");
		if (policy.dangerousFallPolicy() == PmbPathfindingData.DangerousFallPolicy.DISABLE) return false;
		if (policy.requireEyeSight() && objective.authorityEntity() != null
				&& !mob.getSensing().hasLineOfSight(objective.authorityEntity())) return false;
		Vec3 impact = edge.landing();
		if (!level.noCollision(mob, mob.getBoundingBox().move(impact.subtract(mob.position())))) return false;
		double edgeDrop = Math.max(0, mob.getY() - impact.y());
		double effectiveDrop = edgeDrop;
		if (mob.isIgnoringFallDamageFromCurrentImpulse() && mob.currentImpulseImpactPos != null)
			effectiveDrop = Math.min(effectiveDrop, mob.currentImpulseImpactPos.y() - impact.y());
		float rawDamage = (float) (Math.max(0, effectiveDrop - 3)
				* mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER));
		float damage = PmbFallDamagePrediction.afterProtection(level, mob, rawDamage);
		int ticks = (int) Math.ceil(Math.sqrt(2 * edgeDrop / .08));
		FallImpactContext context = new FallImpactContext(FallCause.PLANNED_PATH, edge.takeoff(), impact, damage, ticks);
		boolean promised = policy.dangerousFallPolicy() != PmbPathfindingData.DangerousFallPolicy.CONDITIONAL_REQUIRED
				|| scheduler.acceptsPlannedFall(mob, context);
		if (!permitsDangerousStep(policy.dangerousFallPolicy(), true, true, promised)) return false;
		if (policy.dangerousFallPolicy() == PmbPathfindingData.DangerousFallPolicy.CONDITIONAL_REQUIRED) {
			scheduler.planFall(mob, context,
					ai.pathfinding().planner().conditionalWaitTicks());
		}
		// Execute the authorized local edge explicitly. Vanilla navigation owns the safe approach;
		// at the anchor MoveControl crosses the ledge and the conditional transitions ARMED/AIRBORNE.
		scheduler.movement().submit(mob, "planned_fall", PmbMovementController.Type.LOCOMOTION,
				objective.kind() == IntentKind.RETREAT ? PmbMovementController.Tier.RETREAT : PmbMovementController.Tier.ACTIVE,
				PmbSkillScheduler.Category.MAIN, 100, () -> mob.isAlive() && !mob.isNoAi(), () -> {
					if (horizontalDistanceSqr(mob.position(), edge.takeoff()) > 2.25) {
						mob.getNavigation().moveTo(edge.takeoff().x(), edge.takeoff().y(), edge.takeoff().z(), 1.0);
					} else {
						mob.getNavigation().stop();
						mob.getMoveControl().setWantedPosition(edge.landing().x(), edge.takeoff().y(), edge.landing().z(), 1.0);
					}
				});
		return false;
	}
	private static boolean ordinaryRouteAllowsDangerousEdge(Mob mob, TraversalObjective objective,
			PmbPathfindingData.Strategy policy) {
		boolean insideFollowRange = insideFollowRange(mob, objective);
		if (!insideFollowRange || policy.routeMode() == PmbPathfindingData.RouteMode.VANILLA_ONLY) return false;
		Path vanilla = mob.getNavigation().createPath(BlockPos.containing(objective.destination()), 0);
		boolean fullVanilla = vanilla != null && vanilla.canReach();
		return permitsOrdinarySpecialRoute(policy.routeMode(), insideFollowRange, fullVanilla);
	}
	static boolean permitsOrdinarySpecialRoute(PmbPathfindingData.RouteMode mode, boolean insideFollowRange,
			boolean fullVanillaPath) {
		if (!insideFollowRange || mode == PmbPathfindingData.RouteMode.VANILLA_ONLY) return false;
		return mode == PmbPathfindingData.RouteMode.EFFICIENT || !fullVanillaPath;
	}
	static boolean shouldReplanRetreat(int tick, int nextRepathTick, boolean sameThreat,
			boolean hasDestination, boolean threatMoved) {
		return !sameThreat || !hasDestination || threatMoved || tick >= nextRepathTick;
	}
	private static boolean insideFollowRange(Mob mob, TraversalObjective objective) {
		double follow = mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
		return objective.authorityEntity() != null
				? mob.distanceToSqr(objective.authorityEntity()) <= follow * follow
				: mob.position().distanceToSqr(objective.destination()) <= follow * follow;
	}
	static boolean permitsDangerousStep(PmbPathfindingData.DangerousFallPolicy policy,
			boolean dangerous, boolean supportedEdge, boolean promised) {
		if (!dangerous) return true;
		return switch (policy) {
			case DISABLE -> false;
			case ENABLE -> supportedEdge;
			case CONDITIONAL_REQUIRED -> supportedEdge && promised;
		};
	}
	private static Vec3 standingBelow(net.minecraft.server.level.ServerLevel level, Mob mob,
			Vec3 horizontal, int maxDrop) {
		for (int drop = 0; drop <= maxDrop; drop++) {
			Vec3 candidate = new Vec3(horizontal.x(), mob.getY() - drop, horizontal.z());
			BlockPos block = BlockPos.containing(candidate);
			if (!level.hasChunkAt(block)) return null;
			if (PmbLocalEdgePlanner.safeStanding(level, mob, candidate)) return candidate;
		}
		return null;
	}
	private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
		double x = first.x() - second.x(), z = first.z() - second.z();
		return x * x + z * z;
	}

	private static boolean validObjective(Mob mob, Objective objective) {
		return mob.isAlive() && !mob.isNoAi() && (objective.entity() == null || valid(mob, objective.entity()));
	}
	private static boolean valid(Mob mob, LivingEntity target) {
		return target != null && target.isAlive() && target.level() == mob.level();
	}
	private static Vec3 chooseAwayDestination(Mob mob, LivingEntity threat, PmbPathfindingData.Retreat policy) {
		if (mob instanceof PathfinderMob pathfinder) {
			Vec3 reachable = DefaultRandomPos.getPosAway(pathfinder, policy.searchHorizontalRange(),
					policy.searchVerticalRange(), threat.position());
			if (reachable != null && farther(mob, threat, reachable)) return reachable;
		}
		Vec3 away = mob.position().subtract(threat.position());
		if (away.lengthSqr() < 1.0E-6) {
			double angle = Math.toRadians(Math.floorMod(mob.getId() * 137, 360));
			away = new Vec3(Math.cos(angle), 0, Math.sin(angle));
		}
		away = away.normalize();
		return mob.position().add(away.scale(10));
	}
	private static void navigateAway(Mob mob, Vec3 destination,
			PmbPathfindingData.Retreat policy, boolean startNavigation) {
		if (destination == null || !startNavigation) return;
		if (mob.getNavigation().moveTo(destination.x(), destination.y(), destination.z(), policy.speedMultiplier())) return;
		if (mob.getNavigation() instanceof FlyingPathNavigation || mob.getNavigation() instanceof WaterBoundPathNavigation) {
			mob.getMoveControl().setWantedPosition(destination.x(), destination.y(), destination.z(), policy.speedMultiplier());
		} else mob.getNavigation().stop();
	}
	private static boolean navigationMovesAway(Mob mob, LivingEntity threat) {
		BlockPos pos = mob.getNavigation().getTargetPos();
		return pos != null && !mob.getNavigation().isDone() && farther(mob, threat, Vec3.atCenterOf(pos));
	}
	private static boolean farther(Mob mob, LivingEntity threat, Vec3 destination) {
		return destination.distanceToSqr(threat.position()) > mob.distanceToSqr(threat) + 4;
	}
}
