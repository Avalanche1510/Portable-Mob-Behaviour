package com.pmb.ai;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Local special-edge provider. It never replaces ordinary navigation when a full vanilla route is preferred. */
public final class PmbTraversalController {
	/** One planning round only; repeated endpoints reuse both successful and null path results. */
	static final class LocalWindChecks {
		private final Map<BlockPos, Path> paths = new HashMap<>();
		private final int[] pathChecks = {0, 0};
		record PathResult(boolean available, Path path) {}
		PathResult path(BlockPos endpoint, int group, Supplier<Path> compute) {
			if (!paths.containsKey(endpoint)) {
				if (group >= 0 && pathChecks[group] >= (group == 0 ? 3 : 2))
					return new PathResult(false, null);
				if (group >= 0) pathChecks[group]++;
				paths.put(endpoint, compute.get());
			}
			return new PathResult(true, paths.get(endpoint));
		}
	}
	private enum PlanningStage { IDLE, VANILLA, WIND, PEARL }
	static final class WindSearchContext {
		final PmbLocalEdgePlanner.WindSearchProgress progress = new PmbLocalEdgePlanner.WindSearchProgress();
		private PmbPathfindingController.TraversalObjective objective;
		private PmbSkillScheduler.Strategy strategy;
		private Vec3 origin;
		void update(PmbPathfindingController.TraversalObjective current, PmbSkillScheduler.Strategy currentStrategy,
				Vec3 position, double threshold) {
			if (!sameObjective(current, objective) || strategy != currentStrategy || origin == null
					|| origin.distanceToSqr(position) > threshold * threshold
					|| objective.destination().distanceToSqr(current.destination()) > threshold * threshold) {
				progress.reset(); objective = current; strategy = currentStrategy; origin = position;
			}
		}
		void reset() { progress.reset(); objective = null; strategy = null; origin = null; }
		void methodAvailability(boolean enabled, boolean listed) {
			if (!enabled || !listed) reset();
		}
	}
	private final WindSearchContext windSearch = new WindSearchContext();
	private List<String> windRoundDetails = List.of();
	private int windRoundTick = Integer.MIN_VALUE;
	private String planningDecision = "not_started";
	String diagnostics(Mob mob, PmbSkillScheduler scheduler) {
		PmbAiData ai = ((PmbAiHolder) mob).pmb$getAiData();
		var policy = ai.pathfinding().strategy(scheduler.strategy());
		var current = scheduler.pathfinding().traversalObjective();
		var entity = current == null ? null : current.authorityEntity();
		return "decision=" + planningDecision + " objective=" + current
				+ " targetPresent=" + (entity != null) + " targetId=" + (entity == null ? null : entity.getUUID())
				+ " LOS=" + (entity != null && mob.getSensing().hasLineOfSight(entity))
				+ " requireEyeSight=" + policy.requireEyeSight() + " routeMode=" + policy.routeMode()
				+ " distance=" + (current == null ? null : mob.position().distanceTo(current.destination()))
				+ " height=" + (current == null ? null : current.destination().y() - mob.getY())
				+ " maxDistance=" + ai.windTraversal().maxConnectionDistance()
				+ " maxHeight=" + ai.windTraversal().maxConnectionHeight()
				+ " objectiveMemoryTicks(replanDelayOnly)=" + ai.pathfinding().planner().objectiveMemoryTicks()
				+ " nextPlanTick=" + nextPlanTick + " roundTick=" + windRoundTick
				+ " roundAge=" + (windRoundTick == Integer.MIN_VALUE ? -1 : mob.tickCount - windRoundTick)
				+ " candidates=" + windRoundDetails;
	}
	private int nextPlanTick;
	private PmbPathfindingController.TraversalObjective plannedObjective;
	private Vec3 plannedTargetPosition;
	private PlanningStage planningStage = PlanningStage.IDLE;
	private int methodIndex;
	private Path vanillaPath;
	private boolean fullVanilla;
	private String proposal;
	private PmbLocalEdgePlanner.Edge activeWindEdge;
	private PmbLocalEdgePlanner.Edge preparingWindEdge;
	private PmbSkillItemAccess.Resolved preparingWindItem;
	private int preparingWindReleaseTick;
	private String windPreparationFailure;
	private int releasedWindTick = Integer.MIN_VALUE;
	private int activeWindDeadline = Integer.MIN_VALUE;
	private int activeWindNextPathTick;
	private boolean activeWindPathSubmitted;
	private int activeWindLastProgressTick;
	private double activeWindBestDistance;
	private Vec3 blockedWindTakeoff;
	private int blockedWindUntil;
	private String selectedWindReason;
	private String lastWindEndpoints;
	private Path observedVanillaPath;
	private int observedVanillaNodeIndex;
	private double vanillaBestGoalDistance;
	private int vanillaProgressTick;
	private int nextVanillaStallResetTick;
	private int vanillaStallUntil;
	public String proposal() { return proposal; }
	public void claimResources(Mob mob, PmbSkillScheduler scheduler) {
		if (preparingWindEdge == null) return;
		scheduler.maintainPhase(PmbTraversalGroupAiData.TAG, "wind_preparation",
				() -> validWindPreparation(mob, scheduler), () -> cancelWindPreparation(mob),
				preparingWindItem.resources(PmbSkillScheduler.Resource.LOOK));
	}
	public PmbPathfindingController.RouteStage routeStage() {
		if (proposal != null && proposal.startsWith("pearl_")) return PmbPathfindingController.RouteStage.PEARL;
		if (activeWindEdge != null || planningStage == PlanningStage.WIND)
			return PmbPathfindingController.RouteStage.WIND;
		if (planningStage == PlanningStage.PEARL) return PmbPathfindingController.RouteStage.PEARL;
		if (planningStage == PlanningStage.VANILLA || "searching".equals(proposal))
			return PmbPathfindingController.RouteStage.SEARCHING;
		return proposal == null ? PmbPathfindingController.RouteStage.NONE
				: PmbPathfindingController.RouteStage.WAITING_CONDITIONAL;
	}

	public void tick(Mob mob, PmbSkillScheduler scheduler) {
		PmbAiData ai = ((PmbAiHolder) mob).pmb$getAiData();
		ai.windTraversal().tickCooldown();
		ai.pearlTraversal().tickCooldown();
		windSearch.methodAvailability(ai.windTraversal().isEnabled(), ai.traversal().methods().contains("wind_charge"));
		PmbPathfindingController.TraversalObjective objective = scheduler.pathfinding().traversalObjective();
		if (!(mob.level() instanceof ServerLevel level) || !mob.isAlive() || !ai.traversal().isEnabled()
				|| !hasEnabledMethod(ai)
				|| scheduler.priorityTier(PmbTraversalGroupAiData.TAG) < 0
				|| objective == null || !validObjective(mob, objective)) {
			planningDecision = !mob.isAlive() ? "dead" : !ai.traversal().isEnabled() ? "traversal_disabled"
					: !hasEnabledMethod(ai) ? "no_enabled_method" : scheduler.priorityTier(PmbTraversalGroupAiData.TAG) < 0
					? "priority_excluded" : objective == null ? "no_objective" : "invalid_objective";
			windSearch.reset(); clearTask(mob); proposal = null; return;
		}
		Vec3 destination = objective.destination();
		PmbPathfindingData.Strategy policy = ai.pathfinding().strategy(scheduler.strategy());
		if (policy.routeMode() == PmbPathfindingData.RouteMode.VANILLA_ONLY) {
			planningDecision = "vanilla_only"; windSearch.reset(); clearTask(mob); proposal = null; return;
		}
		if (policy.requireEyeSight() && objective.authorityEntity() != null
				&& !mob.getSensing().hasLineOfSight(objective.authorityEntity())) {
			planningDecision = "requireEyeSight_blocked"; clearTask(mob); return;
		}
		if (!insideFollowRange(mob, objective)) {
			planningDecision = "outside_follow_range"; windSearch.reset(); clearTask(mob); proposal = null; return;
		}
		windSearch.update(objective, scheduler.strategy(), mob.position(), ai.pathfinding().planner().targetMoveThreshold());
		planningDecision = "processing_" + planningStage;
		if (activeWindEdge != null && preparingWindEdge == null && !mob.onGround()) {
			discardWindEdge(mob); return;
		}
		if (preparingWindEdge != null) {
			if (!validWindPreparation(mob, scheduler)
					|| !sameObjective(objective, plannedObjective)
					|| plannedTargetPosition == null
					|| plannedTargetPosition.distanceToSqr(objective.destination())
							> ai.pathfinding().planner().targetMoveThreshold()
							* ai.pathfinding().planner().targetMoveThreshold()) {
				cancelWindPreparation(mob); return;
			}
			processWindPreparation(level, mob, scheduler, objective, ai.windTraversal());
			return;
		}
		float moveThreshold = ai.pathfinding().planner().targetMoveThreshold();
		boolean destinationMoved = plannedTargetPosition == null
				|| plannedTargetPosition.distanceToSqr(destination) > moveThreshold * moveThreshold;
		if (releasedWindTick != Integer.MIN_VALUE) {
			// The committed edge is already flying. Keep its cached arc and never
			// start a fresh vanilla search mid-flight, even after the replan timer.
			// An invalidated objective discards only the old drawing now; the new
			// objective is planned as soon as landing makes planning meaningful.
			if (destinationMoved || !sameObjective(objective, plannedObjective)) {
				PmbPathParticleDebug.clear(mob);
				plannedObjective = objective;
				plannedTargetPosition = destination;
				nextPlanTick = mob.tickCount;
			}
			if (mob.tickCount - releasedWindTick > 80) PmbPathParticleDebug.clear(mob);
			if (holdReleasedWind(releasedWindTick, mob.tickCount, mob.onGround())) return;
			releasedWindTick = Integer.MIN_VALUE;
		}
		if (destinationMoved || !sameObjective(objective, plannedObjective)) {
			observedVanillaPath = null;
			vanillaStallUntil = 0;
		}
		monitorVanillaProgress(mob, objective);
		if (activeWindEdge != null) {
			if (!sameObjective(objective, plannedObjective) || destinationMoved) {
				mob.getNavigation().stop();
				clearTask(mob); beginTask(mob, objective);
			}
			else { processWindEdge(level, mob, scheduler, objective, ai.windTraversal()); return; }
		}
		boolean objectiveChanged = !sameObjective(objective, plannedObjective) || plannedTargetPosition == null
				|| destinationMoved;
		if (objectiveChanged) beginTask(mob, objective);
		else if (planningStage == PlanningStage.IDLE) {
			if (mob.tickCount < nextPlanTick) { planningDecision = "waiting_replan"; return; }
			beginTask(mob, objective);
		}
		if (!PmbPlanningBudget.acquire(level, mob, planningStage == PlanningStage.WIND ? 10 : 1)) {
			planningDecision = "global_planning_budget_" + planningStage; return;
		}
		if (planningStage == PlanningStage.VANILLA) {
			vanillaPath = mob.getNavigation().createPath(net.minecraft.core.BlockPos.containing(destination), 0);
			fullVanilla = vanillaPath != null && vanillaPath.canReach()
					&& mob.tickCount >= vanillaStallUntil;
			PmbPathParticleDebug.vanilla(mob, vanillaPath, false);
			if (fullVanilla && policy.routeMode() == PmbPathfindingData.RouteMode.FALLBACK) {
				PmbPathParticleDebug.selectVanilla(mob);
				proposal = null;
				finishTask(mob, ai);
			} else selectMethod(ai.traversal());
			return;
		}
		double distance = mob.position().distanceTo(destination);
		double height = destination.y() - mob.getY();
		PmbTraversalAiData wind = ai.windTraversal();
		if (planningStage == PlanningStage.WIND && mob.onGround() && wind.isEnabled()
				&& wind.canCommit()
				&& distance <= wind.maxConnectionDistance() && Math.abs(height) <= wind.maxConnectionHeight()
				&& (!fullVanilla || policy.routeMode() == PmbPathfindingData.RouteMode.EFFICIENT)) {
			PmbSkillItemAccess.Resolved item = resolve(mob, wind, Items.WIND_CHARGE, PmbTraversalGroupAiData.TAG);
			if (item != null) {
				int[] flowChecks = {0};
				LocalWindChecks localCache = new LocalWindChecks();
				selectedWindReason = "no_safe_candidate";
				boolean observed = PmbPathParticleDebug.observed(mob);
				windRoundDetails = observed ? new ArrayList<>(12) : List.of(); windRoundTick = mob.tickCount;
				Vec3 partialAnchor = vanillaPath != null && !vanillaPath.canReach()
						&& vanillaPath.getNodeCount() > 0
						? Vec3.atBottomCenterOf(vanillaPath.getNode(vanillaPath.getNodeCount() - 1).asBlockPos())
						: null;
				PmbLocalEdgePlanner.Edge edge = PmbLocalEdgePlanner.findWind(level, mob, destination,
						ai.pathfinding().planner().planningHorizon(), wind.maxConnectionDistance(),
						wind.maxConnectionHeight(), wind.minHorizontalConnectionDistance(), wind.postJumpTicks(), partialAnchor,
						objective.kind() == PmbPathfindingController.IntentKind.PURSUIT
								&& objective.authorityEntity() != null
								&& mob.canAttack(objective.authorityEntity()) ? objective.authorityEntity() : null,
					ai.pathfinding().airTracking(), candidate -> {
						selectedWindReason = "candidate_policy_unproven";
						lastWindEndpoints = candidate.takeoff() + "->" + candidate.landing();
						if (mob.tickCount < blockedWindUntil && blockedWindTakeoff != null
								&& horizontalDistanceSqr(blockedWindTakeoff, candidate.takeoff()) < 1) {
							selectedWindReason = "blocked_takeoff"; return false;
						}
						if (!safeWindDescent(level, mob, candidate)) return false;
						return acceptsWindCandidate(level, mob, candidate, wind.minSavedTicks(),
										policy.routeMode(), flowChecks, localCache);
					}, () -> selectedWindReason, reason -> selectedWindReason = reason,
					PmbPathParticleDebug.watched(mob)
							? (simulated, selected) -> PmbPathParticleDebug.wind(mob, simulated, selected) : null,
					windSearch.progress, observed ? windRoundDetails::add : null);
				if (edge != null) {
					activeWindEdge = edge;
					activeWindNextPathTick = mob.tickCount;
					activeWindPathSubmitted = false;
					activeWindLastProgressTick = mob.tickCount;
					activeWindBestDistance = mob.position().distanceToSqr(edge.takeoff());
					activeWindDeadline = mob.tickCount + ai.pathfinding().planner().conditionalWaitTicks();
					proposal = windProposal(edge, "approach");
					processWindEdge(level, mob, scheduler, objective, wind);
					return;
				}
			} else selectedWindReason = "item_unavailable";
		}
		else if (planningStage == PlanningStage.WIND) {
			selectedWindReason = !mob.onGround() ? "airborne_no_cache"
					: !wind.canCommit() ? "cooldown"
					: distance > wind.maxConnectionDistance() ? "distance_limit"
					: Math.abs(height) > wind.maxConnectionHeight() ? "height_limit"
					: fullVanilla && policy.routeMode() == PmbPathfindingData.RouteMode.FALLBACK
							? "fallback_vanilla_route" : "method_disabled";
		}
		if (planningStage == PlanningStage.WIND) {
			planningDecision = "wind_rejected_" + selectedWindReason;
			proposal = "wind_rejected_" + (selectedWindReason == null ? "no_candidate" : selectedWindReason);
			if (!advanceMethod(ai.traversal())) finishTask(mob, ai);
			return;
		}
		proposal = null;
		PmbTraversalAiData pearl = ai.pearlTraversal();
		if (planningStage == PlanningStage.PEARL && !fullVanilla && pearl.isEnabled()
				&& pearl.canCommit() && distance <= pearl.maxConnectionDistance() && Math.abs(height) <= pearl.maxConnectionHeight()) {
			PmbSkillItemAccess.Resolved item = resolve(mob, pearl, Items.ENDER_PEARL, PmbTraversalGroupAiData.TAG);
			if (item != null) {
				proposal = "pearl_last_resort->" + net.minecraft.core.BlockPos.containing(destination);
				offerPearl(level, mob, scheduler, destination, pearl, item);
			}
		}
		if (planningStage == PlanningStage.PEARL && advanceMethod(ai.traversal())) return;
		finishTask(mob, ai);
	}
	private static boolean hasEnabledMethod(PmbAiData ai) {
		return ai.traversal().methods().contains("wind_charge") && ai.windTraversal().isEnabled()
				|| ai.traversal().methods().contains("ender_pearl") && ai.pearlTraversal().isEnabled();
	}
	private void selectMethod(PmbTraversalGroupAiData group) {
		while (methodIndex < group.methods().size()) {
			String method = group.methods().get(methodIndex);
			if (method.equals("wind_charge") && group.wind().isEnabled()) {
				planningStage = PlanningStage.WIND; return;
			}
			if (method.equals("ender_pearl") && group.pearl().isEnabled()) {
				planningStage = PlanningStage.PEARL; return;
			}
			methodIndex++;
		}
		planningStage = PlanningStage.IDLE;
	}
	private boolean advanceMethod(PmbTraversalGroupAiData group) {
		methodIndex++;
		selectMethod(group);
		return planningStage != PlanningStage.IDLE;
	}

	private void beginTask(Mob mob, PmbPathfindingController.TraversalObjective objective) {
		plannedObjective = objective;
		PmbPathParticleDebug.begin(mob);
		plannedTargetPosition = objective.destination();
		selectedWindReason = null;
		lastWindEndpoints = null;
		planningStage = PlanningStage.VANILLA;
		methodIndex = 0;
		vanillaPath = null;
		fullVanilla = false;
		proposal = "searching";
	}
	private void finishTask(Mob mob, PmbAiData ai) {
		if (fullVanilla && activeWindEdge == null) PmbPathParticleDebug.selectVanilla(mob);
		planningStage = PlanningStage.IDLE;
		methodIndex = 0;
		vanillaPath = null;
		int delay = ai.pathfinding().planner().repathIntervalTicks();
		if (proposal == null) delay = Math.max(delay, ai.pathfinding().planner().objectiveMemoryTicks());
		nextPlanTick = mob.tickCount + delay;
	}
	private void clearTask(Mob mob) {
		clearTask(mob, false);
	}
	private void clearTask(Mob mob, boolean keepReleasedWindTrace) {
		if (preparingWindEdge != null)
			PmbSkillScheduler.of(mob).dropPhase(PmbTraversalGroupAiData.TAG, "wind_preparation");
		if (keepReleasedWindTrace) PmbPathParticleDebug.released(mob);
		else PmbPathParticleDebug.clear(mob);
		preparingWindEdge = null;
		preparingWindItem = null;
		plannedObjective = null;
		plannedTargetPosition = null;
		releasedWindTick = Integer.MIN_VALUE;
		activeWindEdge = null;
		activeWindDeadline = Integer.MIN_VALUE;
		activeWindNextPathTick = 0;
		activeWindPathSubmitted = false;
		selectedWindReason = null;
		lastWindEndpoints = null;
		planningStage = PlanningStage.IDLE;
		vanillaPath = null;
		fullVanilla = false;
	}
	static boolean holdReleasedWind(int releaseTick, int now, boolean grounded) {
		return releaseTick != Integer.MIN_VALUE && now - releaseTick <= 80
				&& (now <= releaseTick + 2 || !grounded);
	}
	private static boolean insideFollowRange(Mob mob, PmbPathfindingController.TraversalObjective objective) {
		double follow = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
		return objective.authorityEntity() != null ? mob.distanceToSqr(objective.authorityEntity()) <= follow * follow
				: mob.position().distanceToSqr(objective.destination()) <= follow * follow;
	}
	private void monitorVanillaProgress(Mob mob, PmbPathfindingController.TraversalObjective objective) {
		if (activeWindEdge != null || objective.kind() != PmbPathfindingController.IntentKind.PURSUIT
				|| mob.getNavigation().isDone()) {
			observedVanillaPath = null;
			return;
		}
		Path currentPath = mob.getNavigation().getPath();
		int currentNodeIndex = currentPath == null ? -1 : currentPath.getNextNodeIndex();
		double goalDistance = horizontalDistanceSqr(mob.position(), objective.destination());
		if (observedVanillaPath == null) {
			observedVanillaPath = currentPath;
			observedVanillaNodeIndex = currentNodeIndex;
			vanillaBestGoalDistance = goalDistance;
			vanillaProgressTick = mob.tickCount;
			return;
		}
		if (hasVanillaProgress(currentPath == observedVanillaPath, observedVanillaNodeIndex,
				currentNodeIndex, vanillaBestGoalDistance, goalDistance)) {
			vanillaBestGoalDistance = goalDistance;
			vanillaProgressTick = mob.tickCount;
		}
		observedVanillaPath = currentPath;
		observedVanillaNodeIndex = currentNodeIndex;
		if (mob.tickCount - vanillaProgressTick < 30 || mob.tickCount < nextVanillaStallResetTick) return;
		mob.getNavigation().stop();
		vanillaStallUntil = mob.tickCount + 60;
		nextVanillaStallResetTick = mob.tickCount + 60;
		observedVanillaPath = null;
		beginTask(mob, objective);
		proposal = "vanilla_stall_reset->" + BlockPos.containing(objective.destination());
	}
	static boolean hasVanillaProgress(boolean samePath, int previousNodeIndex, int currentNodeIndex,
			double bestGoalDistanceSquared, double currentGoalDistanceSquared) {
		return samePath && currentNodeIndex > previousNodeIndex
				|| currentGoalDistanceSquared + .25 < bestGoalDistanceSquared;
	}
	static boolean isLocalVanillaMiss(Path path, Vec3 destination) {
		if (path == null || path.getNodeCount() == 0) return false;
		BlockPos end = path.getNode(path.getNodeCount() - 1).asBlockPos();
		return Math.abs(end.getY() - destination.y()) <= 1
				&& horizontalDistanceSqr(Vec3.atBottomCenterOf(end), destination) <= 16;
	}
	private boolean acceptsWindCandidate(ServerLevel level, Mob mob, PmbLocalEdgePlanner.Edge candidate,
			int minSavedTicks, PmbPathfindingData.RouteMode mode, int[] flowChecks,
			LocalWindChecks localCache) {
		GlobalWindAdmission globalAdmission = globalWindAdmission(mode);
		if (globalAdmission == GlobalWindAdmission.KEEP_VANILLA) {
			selectedWindReason = "fallback_vanilla_route"; return false;
		}
		if (candidate.airIntercept() && mode == PmbPathfindingData.RouteMode.EFFICIENT) {
			// Air contact is a separate endpoint, not an occupied standing cell.
			// It never inherits the flowing-water exemption.
			if (candidate.safeDescent() == null) {
				selectedWindReason = "no_safe_post_contact_descent"; return false;
			}
		}
		if (!candidate.airIntercept() && mode == PmbPathfindingData.RouteMode.EFFICIENT
				&& PmbLocalEdgePlanner.crossesFlowingWater(level, candidate.takeoff(), candidate.landing())) {
			if (flowChecks[0]++ >= 2) {
				selectedWindReason = "flow_continuation_budget"; return false;
			}
			boolean suffix = vanillaPath != null && vanillaPath.canReach()
					&& provenSuffixNode(vanillaPath, candidate.landing(), plannedTargetPosition) >= 0;
			LivingEntity target = plannedObjective != null
					&& plannedObjective.kind() == PmbPathfindingController.IntentKind.PURSUIT
					? plannedObjective.authorityEntity() : null;
			if (suffix || PmbLocalEdgePlanner.boundedDryContinuation(level, mob,
					candidate.landing(), plannedTargetPosition, target)) {
				selectedWindReason = "flowing_water_dry_shore";
				return true;
			}
			selectedWindReason = "flow_continuation_unknown";
			return false;
		}
		if (globalAdmission == GlobalWindAdmission.COMPARE_FULL_ROUTE) {
			int suffix = provenWalkableSuffixNode(vanillaPath, candidate.landing(), plannedTargetPosition,
					node -> PmbLocalEdgePlanner.walkableConnection(level, mob, candidate.landing(), node));
			boolean saved = suffix >= 0 && savesMinimumTicks(level, mob, vanillaPath, candidate, suffix, minSavedTicks);
			selectedWindReason = suffix < 0 ? "full_route_no_connected_suffix"
					: saved ? "full_route_connected_suffix_saved" : "full_route_savings_below_" + minSavedTicks;
			return saved;
		}
		int localGroup = candidate.rim() ? 1 : 0;
		BlockPos landing = BlockPos.containing(candidate.landing());
		if (!level.hasChunkAt(landing)) { selectedWindReason = "unknown_local_chunk"; return false; }
		LocalWindChecks.PathResult localResult = localCache.path(landing, localGroup,
				() -> mob.getNavigation().createPath(landing, 0));
		if (!localResult.available()) { selectedWindReason = "unknown_local_budget"; return false; }
		Path local = localResult.path();
		if (local != null && local.canReach()) {
			boolean saved = savesMinimumTicks(level, mob, local, candidate, candidate.landing(), minSavedTicks);
			selectedWindReason = saved ? "local_saved" : "local_savings_below_" + minSavedTicks;
			return saved;
		}
		boolean obstacle = PmbLocalEdgePlanner.blockedOrdinaryApproach(level, mob,
				candidate.takeoff(), candidate.landing());
		selectedWindReason = partialRouteReason(obstacle, candidate.takeoff(), candidate.landing(), plannedTargetPosition);
		return selectedWindReason.equals("partial_route_obstacle_progress");
	}
	enum GlobalWindAdmission { KEEP_VANILLA, COMPARE_FULL_ROUTE, CHECK_LOCAL_ROUTE }
	GlobalWindAdmission globalWindAdmission(PmbPathfindingData.RouteMode mode) {
		// A navigation stall can request replanning, but does not make a newly
		// computed complete route incomplete for wind's admission policy.
		if (vanillaPath == null || !vanillaPath.canReach()) return GlobalWindAdmission.CHECK_LOCAL_ROUTE;
		return mode == PmbPathfindingData.RouteMode.FALLBACK
				? GlobalWindAdmission.KEEP_VANILLA : GlobalWindAdmission.COMPARE_FULL_ROUTE;
	}
	static String partialRouteReason(boolean obstacle, Vec3 takeoff, Vec3 landing, Vec3 target) {
		if (!obstacle) return "no_local_obstacle";
		return landing.distanceToSqr(target) + 1.0E-6 < takeoff.distanceToSqr(target)
				? "partial_route_obstacle_progress" : "no_target_progress";
	}
	private boolean safeWindDescent(ServerLevel level, Mob mob, PmbLocalEdgePlanner.Edge edge) {
		Vec3 descent = edge.safeDescent();
		if (descent == null || !(edge.airIntercept()
				? PmbLocalEdgePlanner.stableAirDescent(level, mob, descent)
				: PmbLocalEdgePlanner.safeStanding(level, mob, descent))) {
			selectedWindReason = "no_safe_descent"; return false;
		}
		if (level.getFluidState(BlockPos.containing(descent)).is(FluidTags.WATER)) return true;
		float raw = (float) Math.max(0, edge.takeoff().y() - descent.y() - 3)
				* (float) mob.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
		boolean safe = PmbFallDamagePrediction.afterProtection(level, mob, raw) <= 0;
		if (!safe) selectedWindReason = "unsafe_descent";
		return safe;
	}
	private static boolean validObjective(Mob mob, PmbPathfindingController.TraversalObjective objective) {
		LivingEntity authority = objective.authorityEntity();
		return authority == null || authority.isAlive() && authority.level() == mob.level();
	}
	private static boolean sameObjective(PmbPathfindingController.TraversalObjective first,
			PmbPathfindingController.TraversalObjective second) {
		return first != null && second != null && first.kind() == second.kind()
				&& first.authorityEntity() == second.authorityEntity() && first.owner().equals(second.owner());
	}
	private void processWindEdge(ServerLevel level, Mob mob, PmbSkillScheduler scheduler,
			PmbPathfindingController.TraversalObjective objective, PmbTraversalAiData data) {
		PmbLocalEdgePlanner.Edge edge = activeWindEdge;
		if (!data.isEnabled() || !((PmbAiHolder) mob).pmb$getAiData().traversal().methods().contains("wind_charge")) {
			discardWindEdge(mob); return;
		}
		if (mob.tickCount > activeWindDeadline) { discardWindEdge(mob); return; }
		if (edge == null || !PmbLocalEdgePlanner.safeStanding(level, mob, edge.takeoff())
				|| !(edge.airIntercept()
						? PmbLocalEdgePlanner.stableAirDescent(level, mob, edge.safeDescent())
						: PmbLocalEdgePlanner.safeStanding(level, mob, edge.safeDescent()))) {
			discardWindEdge(mob); return;
		}
		PmbSkillItemAccess.Resolved item = resolve(mob, data, Items.WIND_CHARGE, PmbTraversalGroupAiData.TAG);
		if (item == null || !data.canCommit()) { discardWindEdge(mob); return; }
		double approachDistance = mob.position().distanceToSqr(edge.takeoff());
		if (!atWindTakeoff(mob.position(), edge.takeoff())) {
			if (completedWindApproachFailed(mob.getNavigation().isDone(), approachDistance,
					activeWindPathSubmitted)) {
				blockWindEdge(mob, edge, "path_ended");
				return;
			}
			if (approachDistance + .1 < activeWindBestDistance) {
				activeWindBestDistance = approachDistance;
				activeWindLastProgressTick = mob.tickCount;
			}
			if (mob.tickCount - activeWindLastProgressTick >= 30) {
				blockWindEdge(mob, edge, "no_progress");
				return;
			}
			proposal = windProposal(edge, "approach") + " progress=" + (mob.tickCount - activeWindLastProgressTick);
			scheduler.movement().submit(mob, PmbTraversalGroupAiData.TAG, PmbMovementController.Type.LOCOMOTION,
					objective.kind() == PmbPathfindingController.IntentKind.RETREAT
							? PmbMovementController.Tier.RETREAT : PmbMovementController.Tier.ACTIVE,
					PmbSkillScheduler.Category.THROW, 50, () -> activeWindEdge == edge,
					() -> {
						// Navigation stops at coarse block centers. Finish the short, clear
						// same-floor approach with MoveControl instead of launching nearby.
						if (horizontalDistanceSqr(mob.position(), edge.takeoff()) <= 2.25
								&& Math.abs(mob.getY() - edge.takeoff().y()) <= .1
								&& PmbLocalEdgePlanner.clearTakeoffApproach(level, mob, edge.takeoff())) {
							mob.getNavigation().stop();
							mob.getMoveControl().setWantedPosition(edge.takeoff().x(), edge.takeoff().y(), edge.takeoff().z(), .35);
							return;
						}
						if (mob.tickCount < activeWindNextPathTick) return;
						activeWindNextPathTick = mob.tickCount + 10;
						if (!mob.getNavigation().moveTo(edge.takeoff().x(), edge.takeoff().y(), edge.takeoff().z(), 1)) {
							blockWindEdge(mob, edge, "path_rejected");
						} else activeWindPathSubmitted = true;
					});
			return;
		}
		holdWindMovement(mob, scheduler, edge);
		if (!settledWindMotion(mob.getDeltaMovement())) {
			proposal = windProposal(edge, "settling");
			return;
		}
		proposal = windProposal(edge, "launch");
		offerWind(level, mob, scheduler, edge, data, item);
	}
	static boolean atWindTakeoff(Vec3 actual, Vec3 takeoff) {
		return horizontalDistanceSqr(actual, takeoff) <= .35 * .35 && Math.abs(actual.y() - takeoff.y()) <= .1;
	}
	static boolean settledWindMotion(Vec3 motion) {
		return Math.abs(motion.x()) < .003 && Math.abs(motion.z()) < .003;
	}
	private void holdWindMovement(Mob mob, PmbSkillScheduler scheduler, PmbLocalEdgePlanner.Edge edge) {
		mob.getNavigation().stop();
		scheduler.movement().submit(mob, PmbTraversalGroupAiData.TAG, PmbMovementController.Type.LOCOMOTION,
				plannedObjective != null && plannedObjective.kind() == PmbPathfindingController.IntentKind.RETREAT
						? PmbMovementController.Tier.RETREAT : PmbMovementController.Tier.ACTIVE,
				PmbSkillScheduler.Category.THROW, 50,
				() -> activeWindEdge == edge || preparingWindEdge == edge,
				() -> {
					mob.getNavigation().stop();
					mob.getMoveControl().setWait();
					mob.setXxa(0); mob.setZza(0); mob.setYya(0);
				});
	}
	static boolean completedWindApproachFailed(boolean navigationDone, double distanceSquared,
			boolean pathSubmitted) {
		return pathSubmitted && navigationDone && distanceSquared > 2.25;
	}
	private boolean validWindPreparation(Mob mob, PmbSkillScheduler scheduler) {
		if (preparingWindEdge == null || preparingWindItem == null || plannedObjective == null
				|| !mob.isAlive() || mob.isNoAi() || mob.onGround()
				|| mob.hasEffect(MobEffects.WEAKNESS)
				|| ((PmbAiHolder) mob).pmb$getAiData().shield().isVulnerable()
				|| !((PmbAiHolder) mob).pmb$getAiData().traversal().isEnabled()
				|| !((PmbAiHolder) mob).pmb$getAiData().windTraversal().isEnabled()
				|| scheduler.priorityTier(PmbTraversalGroupAiData.TAG) < 0
				|| !validObjective(mob, plannedObjective)
				|| !PmbSkillItemAccess.stillMatches(mob, preparingWindItem)) return false;
		LivingEntity target = plannedObjective.authorityEntity();
		return target == null || plannedObjective.kind() != PmbPathfindingController.IntentKind.PURSUIT
				|| mob.getTarget() == target && mob.canAttack(target);
	}
	private void cancelWindPreparation(Mob mob) {
		if (preparingWindEdge == null) return;
		BlockPos from = BlockPos.containing(preparingWindEdge.takeoff());
		String reason = windPreparationFailure == null ? "state_or_resource_changed" : windPreparationFailure;
		clearTask(mob);
		PmbSkillScheduler.of(mob).dropPhase(PmbTraversalGroupAiData.TAG, "wind_preparation");
		nextPlanTick = mob.tickCount + Math.max(1,
				((PmbAiHolder) mob).pmb$getAiData().pathfinding().planner().repathIntervalTicks());
		proposal = "wind_preparation_cancelled_" + reason + " " + from;
	}
	private void processWindPreparation(ServerLevel level, Mob mob, PmbSkillScheduler scheduler,
			PmbPathfindingController.TraversalObjective objective, PmbTraversalAiData data) {
		holdWindMovement(mob, scheduler, preparingWindEdge);
		if (scheduler.ownsResource(PmbTraversalGroupAiData.TAG, PmbSkillScheduler.Resource.LOOK))
			lookAtWindFeet(mob);
		PmbLocalEdgePlanner.Edge edge = preparingWindEdge;
		proposal = windProposal(edge, "preparing") + " release=" + preparingWindReleaseTick;
		if (mob.tickCount < preparingWindReleaseTick) return;
		PmbSkillItemAccess.Resolved item = preparingWindItem;
		scheduler.offerDynamicPlannedResult(PmbTraversalGroupAiData.TAG, "wind_release",
				PmbSkillScheduler.Category.THROW, 50, () -> true,
				() -> {
					windPreparationFailure = "state_or_objective_changed";
					if (!validWindPreparation(mob, scheduler)
							|| !sameObjective(objective, scheduler.pathfinding().traversalObjective())) return false;
					windPreparationFailure = "resource_rejected";
					return PmbLocalEdgePlanner.revalidateWindFlight(level, mob, edge,
							objective.authorityEntity(),
							((PmbAiHolder) mob).pmb$getAiData().pathfinding().airTracking(),
							reason -> windPreparationFailure = reason);
				},
				() -> releaseWind(level, mob, scheduler, edge, data, item, false),
				() -> cancelWindPreparation(mob),
				() -> item.resources(PmbSkillScheduler.Resource.LOOK));
	}
	private void blockWindEdge(Mob mob, PmbLocalEdgePlanner.Edge edge, String reason) {
		blockedWindTakeoff = edge.takeoff();
		blockedWindUntil = mob.tickCount + 60;
		mob.getNavigation().stop();
		discardWindEdge(mob);
		proposal = "wind_blocked_" + reason + "->" + BlockPos.containing(blockedWindTakeoff)
				+ " until=" + blockedWindUntil;
	}
	private String windProposal(PmbLocalEdgePlanner.Edge edge, String stage) {
		return "wind_" + stage + " " + BlockPos.containing(edge.takeoff()) + "->"
				+ BlockPos.containing(edge.landing()) + (edge.airIntercept() ? " air_intercept" : "")
				+ " route=" + selectedWindReason;
	}
	private void discardWindEdge(Mob mob) {
		PmbAiData ai = ((PmbAiHolder) mob).pmb$getAiData();
		clearTask(mob);
		proposal = null;
		nextPlanTick = mob.tickCount + Math.max(1, ai.pathfinding().planner().repathIntervalTicks());
	}
	private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
		double x = first.x() - second.x(), z = first.z() - second.z();
		return x * x + z * z;
	}

	private static PmbSkillItemAccess.Resolved resolve(Mob mob, PmbTraversalAiData data,
			net.minecraft.world.item.Item item, String owner) {
		return PmbSkillItemAccess.resolvePreferred(mob, data.fetchSource(),
				List.of(InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND), stack -> stack.is(item),
				data.preferredHand(), owner);
	}
	private void offerWind(ServerLevel level, Mob mob, PmbSkillScheduler scheduler,
			PmbLocalEdgePlanner.Edge edge,
			PmbTraversalAiData data, PmbSkillItemAccess.Resolved item) {
		scheduler.offerDynamicPlannedResult(PmbTraversalGroupAiData.TAG, "wind_edge", PmbSkillScheduler.Category.THROW, 50,
				() -> true, () -> data.canCommit() && mob.onGround()
						&& atWindTakeoff(mob.position(), edge.takeoff()) && settledWindMotion(mob.getDeltaMovement())
						&& PmbSkillItemAccess.stillMatches(mob, item), () -> {
					String[] rejection = {"wind_impulse_unproven"};
					if (!PmbLocalEdgePlanner.preflightWindDeparture(level, mob, edge, data.postJumpTicks(),
							plannedObjective.authorityEntity(), ((PmbAiHolder) mob).pmb$getAiData().pathfinding().airTracking(),
							reason -> rejection[0] = reason)) {
						blockWindEdge(mob, edge, "departure_" + rejection[0]);
						return PmbSkillScheduler.CommitResult.FAILED;
					}
					if (data.postJumpTicks() > 0) {
						if (mob.hasEffect(MobEffects.WEAKNESS)) return PmbSkillScheduler.CommitResult.FAILED;
						preparingWindEdge = edge;
						preparingWindItem = item;
						preparingWindReleaseTick = mob.tickCount + data.postJumpTicks();
						windPreparationFailure = null;
						holdWindMovement(mob, scheduler, edge);
						data.committed(mob.getRandom());
						lookAtWindFeet(mob);
						mob.jumpFromGround();
						scheduler.markCurrentCandidateExecuted(PmbTraversalGroupAiData.TAG);
						return PmbSkillScheduler.CommitResult.COMMITTED;
					}
					return releaseWind(level, mob, scheduler, edge, data, item, true);
				}, () -> item.resources(PmbSkillScheduler.Resource.LOOK));
	}
	private static void lookAtWindFeet(Mob mob) {
		mob.getLookControl().setLookAt(mob.getX(), mob.getY() - 2, mob.getZ(), 180, 180);
		mob.setXRot(90);
	}
	private PmbSkillScheduler.CommitResult releaseWind(ServerLevel level, Mob mob,
			PmbSkillScheduler scheduler, PmbLocalEdgePlanner.Edge edge,
			PmbTraversalAiData data, PmbSkillItemAccess.Resolved item, boolean chargeCooldown) {
					PmbSkillItemAccess.ActionBinding binding = PmbSkillItemAccess.acquire(mob, item);
					if (binding == null) {
						windPreparationFailure = "item_binding_failed";
						if (preparingWindEdge != null) cancelWindPreparation(mob);
						else blockWindEdge(mob, edge, "item_binding_failed");
						return PmbSkillScheduler.CommitResult.FAILED;
					}
					Vec3 start = mob.position().add(0, .15, 0);
					WindCharge charge = new WindCharge(level, start.x(), start.y(), start.z(), Vec3.ZERO);
					((PmbWindImpulseHolder) charge).pmb$setImpulseSource(PmbTraversalGroupAiData.TAG);
					charge.setOwner(mob); charge.shoot(0, -1, 0, 1.5F, 0); level.addFreshEntity(charge);
					mob.lookAt(EntityAnchorArgument.Anchor.EYES, edge.landing());
					mob.setYHeadRot(mob.getYRot());
					mob.setYBodyRot(mob.getYRot());
					mob.swing(binding.hand(), true); consume(mob, binding.hand(), data.doConsume());
					binding.authorizeAction(mob);
					if (chargeCooldown) data.committed(mob.getRandom());
					// A previous air session may have queued a modifier earlier in this
					// scheduler tick; it cannot act as this edge's first steering tick.
					scheduler.movement().cancel("air_tracking");
						PmbWindLaunchPhysics.Launch launch = PmbWindLaunchPhysics.current(level, mob);
						scheduler.airTracking().commitPosition(mob, PmbTraversalGroupAiData.TAG,
								edge.safeDescent(), PmbWindLaunchPhysics.runtimeSteeringDelay(
									launch == null ? 0 : launch.impactTicks(), edge.steeringDelay()));
					scheduler.markCurrentCandidateExecuted(PmbTraversalGroupAiData.TAG);
					preparingWindEdge = null;
					preparingWindItem = null;
					scheduler.dropPhase(PmbTraversalGroupAiData.TAG, "wind_preparation");
					PmbPathfindingController.TraversalObjective releasedObjective = plannedObjective;
					Vec3 releasedDestination = plannedTargetPosition;
					clearTask(mob, true);
					windSearch.reset();
					plannedObjective = releasedObjective;
					plannedTargetPosition = releasedDestination;
					releasedWindTick = mob.tickCount;
					nextPlanTick = mob.tickCount + Math.max(1,
							((PmbAiHolder) mob).pmb$getAiData().pathfinding().planner().repathIntervalTicks());
					return PmbSkillScheduler.CommitResult.COMMITTED;
	}
	private static void offerPearl(ServerLevel level, Mob mob, PmbSkillScheduler scheduler, Vec3 destination,
			PmbTraversalAiData data, PmbSkillItemAccess.Resolved item) {
		Vec3 start = mob.getEyePosition().add(0, -.1, 0);
		Vec3 offset = destination.subtract(start);
		double angle = Math.toRadians(data.throwAngle());
		PmbEnderPearlBallistics.Solution solution = PmbEnderPearlBallistics.solve(
				Math.hypot(offset.x(), offset.z()), offset.y(), angle, 1.5F * data.maxThrowPower());
		if (solution.status() != PmbEnderPearlBallistics.Status.SOLVED) return;
		Vec3 horizontal = offset.multiply(1, 0, 1).normalize();
		Vec3 velocity = horizontal.scale(Math.cos(angle) * solution.speed()).add(0, Math.sin(angle) * solution.speed(), 0);
		PmbPearlTrajectorySimulator.Result simulation = PmbPearlTrajectorySimulator.simulate(level, mob, start, velocity, 300);
		if (simulation.status() != PmbPearlTrajectorySimulator.Status.BLOCK_HIT
				|| simulation.position().distanceToSqr(destination) > 16.0
				|| !safeTeleport(level, mob, simulation.position())) return;
		scheduler.offerDynamicPlannedResult(PmbTraversalGroupAiData.TAG, "pearl_edge", PmbSkillScheduler.Category.THROW, 50,
				() -> true, () -> data.canCommit() && PmbSkillItemAccess.stillMatches(mob, item), () -> {
					PmbSkillItemAccess.ActionBinding binding = PmbSkillItemAccess.acquire(mob, item);
					if (binding == null) return PmbSkillScheduler.CommitResult.FAILED;
					ItemStack stack = mob.getItemInHand(binding.hand());
					ThrownEnderpearl pearl = new ThrownEnderpearl(level, mob, stack.copyWithCount(1));
					((PmbEnderPearlDamageHolder) pearl).pmb$setTeleportDamage(data.doTeleportDamage());
					Projectile.spawnProjectileUsingShoot(pearl, level, stack, velocity.x(), velocity.y(), velocity.z(),
							(float) velocity.length(), (1 - data.throwAccuracy()) * 12);
					level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDER_PEARL_THROW,
							SoundSource.NEUTRAL, .5F, 1);
					mob.swing(binding.hand(), true); consume(mob, binding.hand(), data.doConsume());
					binding.authorizeAction(mob); data.committed(mob.getRandom());
					scheduler.markCurrentCandidateExecuted(PmbTraversalGroupAiData.TAG);
					return PmbSkillScheduler.CommitResult.COMMITTED;
				}, () -> item.resources(PmbSkillScheduler.Resource.LOOK));
	}
	private static boolean safeTeleport(ServerLevel level, Mob mob, Vec3 position) {
		return PmbLocalEdgePlanner.safeStanding(level, mob, position);
	}
	private static boolean savesMinimumTicks(ServerLevel level, Mob mob, Path vanilla,
			PmbLocalEdgePlanner.Edge edge, Vec3 destination, int minimum) {
		int suffixNode = provenSuffixNode(vanilla, edge.landing(), destination);
		return savesMinimumTicks(level, mob, vanilla, edge, suffixNode, minimum);
	}
	private static boolean savesMinimumTicks(ServerLevel level, Mob mob, Path vanilla,
			PmbLocalEdgePlanner.Edge edge, int suffixNode, int minimum) {
		if (suffixNode < 0) return false;
		double blocksPerTick = Math.max(.05, mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.15);
		double vanillaTicks = 0;
		boolean avoidsLava = false;
		for (int index = 0; index <= suffixNode; index++) {
			BlockPos node = vanilla.getNode(index).asBlockPos();
			if (!level.hasChunkAt(node)) return false;
			if (level.getFluidState(node).is(FluidTags.LAVA) && !mob.fireImmune()) avoidsLava = true;
			double multiplier = level.getBlockState(node).is(Blocks.COBWEB) ? 10
					: level.getFluidState(node).is(FluidTags.WATER) ? 2 : 1;
			vanillaTicks += multiplier / blocksPerTick;
		}
		double approachTicks = mob.position().distanceTo(edge.takeoff()) / blocksPerTick;
		double flightTicks = edge.flightTicks() > 0 ? edge.flightTicks() : 35;
		return meetsSavings(avoidsLava, vanillaTicks, approachTicks, flightTicks, minimum);
	}
	static boolean meetsSavings(boolean avoidsLava, double vanillaTicks, double approachTicks, double flightTicks, int minimum) {
		return avoidsLava || vanillaTicks - approachTicks - flightTicks >= minimum;
	}
	static int provenWalkableSuffixNode(Path vanilla, Vec3 landing, Vec3 destination,
			java.util.function.Predicate<Vec3> connected) {
		if (vanilla == null || !vanilla.canReach()) return -1;
		int suffix = -1;
		double bestGap = Double.POSITIVE_INFINITY;
		for (int index = 0; index < vanilla.getNodeCount(); index++) {
			Vec3 node = Vec3.atBottomCenterOf(vanilla.getNode(index).asBlockPos());
			double gap = node.distanceToSqr(landing);
			boolean near = gap <= .75 * .75 || index == vanilla.getNodeCount() - 1
					&& landing.distanceToSqr(destination) <= 1 && gap <= 1;
			if (near && gap < bestGap && connected.test(node)) { suffix = index; bestGap = gap; }
		}
		return suffix;
	}
	static int provenSuffixNode(Path vanilla, Vec3 landing, Vec3 destination) {
		int suffixNode = -1;
		double bestGap = Double.POSITIVE_INFINITY;
		for (int index = 0; index < vanilla.getNodeCount(); index++) {
			BlockPos node = vanilla.getNode(index).asBlockPos();
			double gap = Vec3.atBottomCenterOf(node).distanceToSqr(landing);
			if (gap <= .75 * .75 && gap < bestGap) { suffixNode = index; bestGap = gap; }
		}
		// Reuse only a demonstrated vanilla suffix, or an edge that reaches the
		// final destination itself. A shortcut to a nearby fluid cell is not by
		// itself evidence that the remainder of the journey is faster or reachable.
		if (suffixNode < 0 && vanilla.canReach()
				&& landing.distanceToSqr(destination) <= 1.0 && vanilla.getNodeCount() > 0)
			return vanilla.getNodeCount() - 1;
		return suffixNode;
	}
	private static void consume(Mob mob, InteractionHand hand, boolean consume) {
		if (!consume) return;
		ItemStack stack = mob.getItemInHand(hand); stack.consume(1, mob);
		mob.setItemInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
	}
}
