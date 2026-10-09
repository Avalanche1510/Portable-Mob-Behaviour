package com.pmb.ai;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Offers event-driven conditional actions to the same arbiter used by active skills. */
public final class PmbConditionalController {
	private record RescuePearlPlan(Vec3 impulse, Vec3 inheritedVelocity, Vec3 target, Vec3 landing,
			float netDamage, int flightTicks) {}
	private record FutureFallState(double height, double verticalVelocity) {}
	private Vec3 rescueLookTarget;
	private net.minecraft.world.entity.Entity rescueLookProjectile;
	private net.minecraft.world.level.Level rescueLookLevel;
	private String rescueLookMethod;
	private long rescueLookRevision;
	record RescueLookState(Vec3 target, net.minecraft.world.entity.Entity projectile,
			net.minecraft.world.level.Level level, String method) {}
	record RescueLookClaim(RescueLookState previous, long revision) {}
	void beginRescueLook(Mob mob, String method, Vec3 target) {
		rescueLookTarget = target;
		rescueLookProjectile = null;
		rescueLookLevel = mob.level();
		rescueLookMethod = method;
		rescueLookRevision++;
	}
	RescueLookClaim stageRescueLook(Mob mob, String method, Vec3 target) {
		RescueLookState previous = new RescueLookState(rescueLookTarget, rescueLookProjectile,
				rescueLookLevel, rescueLookMethod);
		beginRescueLook(mob, method, target);
		return new RescueLookClaim(previous, rescueLookRevision);
	}
	void abortStagedRescueLook(RescueLookClaim claim) {
		if (claim == null || rescueLookRevision != claim.revision()) return;
		rescueLookTarget = claim.previous().target();
		rescueLookProjectile = claim.previous().projectile();
		rescueLookLevel = claim.previous().level();
		rescueLookMethod = claim.previous().method();
		rescueLookRevision++;
	}
	private void trackRescuePearl(ThrownEnderpearl pearl, Vec3 fallback) {
		rescueLookProjectile = pearl;
		rescueLookTarget = fallback;
	}
	void clearRescueLook() {
		rescueLookTarget = null; rescueLookProjectile = null; rescueLookLevel = null; rescueLookMethod = null;
		rescueLookRevision++;
	}
	public void clear() { clearRescueLook(); clearPlanned(); resetAccidentalFall(); committedMethodThisFall = false; methodReasons.clear(); }
	public void cancelTemporary() { clearRescueLook(); clearPlanned(); methodReasons.clear(); }
	public void maintainLook(Mob mob, PmbSkillScheduler scheduler) {
		if (rescueLookTarget == null) return;
		if (inWaterOrBubble(mob)) { cancelWaterRescue(scheduler); return; }
		PmbFallRescueAiData rescue = ((PmbAiHolder) mob).pmb$getAiData().fallRescue();
		if (!mob.isAlive() || mob.isNoAi() || mob.level() != rescueLookLevel
				|| ((PmbAiHolder) mob).pmb$getAiData().shield().isVulnerable()
				|| !rescue.isEnabled() || !rescue.methods().contains(rescueLookMethod)
				|| scheduler.priorityTier(PmbFallRescueAiData.TAG) < 0
				|| !(rescueLookMethod.equals("wind_charge") ? rescue.wind() : rescue.pearl()).isEnabled()) {
			clearRescueLook();
			return;
		}
		scheduler.maintainPhase("fall_rescue", "rescue_look_" + rescueLookMethod,
				() -> true, () -> {}, new PmbSkillScheduler.Resource[]{PmbSkillScheduler.Resource.LOOK});
	}
	public void applyLook(Mob mob, PmbSkillScheduler scheduler) {
		if (rescueLookTarget == null) return;
		if (inWaterOrBubble(mob)) { cancelWaterRescue(scheduler); return; }
		if (scheduler.ownsResource("fall_rescue", PmbSkillScheduler.Resource.LOOK)) {
			Vec3 aim = rescueLookTarget;
			if (rescueLookProjectile != null && !rescueLookProjectile.isRemoved()) {
				aim = rescueLookProjectile.position();
				if (aim.distanceToSqr(mob.getEyePosition()) > .25) rescueLookTarget = aim;
				else aim = rescueLookTarget;
			}
			mob.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, aim);
			mob.setYHeadRot(mob.getYRot());
			mob.setYBodyRot(mob.getYRot());
			mob.getLookControl().setLookAt(aim.x(), aim.y(), aim.z(), 180, 180);
		}
		finishLookTick(mob.onGround());
	}
	void finishLookTick(boolean onGround) { if (onGround) clearRescueLook(); }
	private record PlannedPromise(String method, Vec3 expectedImpactPosition) {}
	private int fallEpoch = Integer.MIN_VALUE;
	private final Set<String> attemptedMethods = new HashSet<>();
	private boolean terminalProbabilityFailure;
	private boolean awaitingAccidentalCommit;
	private boolean allowLowerAfterCommit;
	private boolean committedMethodThisFall;
	private PmbPathfindingController.FallImpactContext plannedFall;
	private PmbPathfindingController.RouteStage plannedStage = PmbPathfindingController.RouteStage.NONE;
	private int plannedDeadline;
	private int plannedWaitTicks;
	private int plannedRefreshTick = Integer.MIN_VALUE;
	private int promiseRetryTick;
	private PlannedPromise offeredPromise;
	private PlannedPromise plannedPromise;
	private final java.util.Map<String, String> methodReasons = new java.util.LinkedHashMap<>();
	public String debugState() { return methodReasons.toString(); }
	private void reasonAll(PmbFallRescueAiData rescue, String reason) {
		for (String method : rescue.methods()) methodReasons.put(method, reason);
	}

	public void planFall(Mob mob, PmbPathfindingController.FallImpactContext context, int waitTicks) {
		if (plannedFall == null || !plannedFall.expectedImpactPosition().equals(context.expectedImpactPosition())) {
			if (offeredPromise == null || !offeredPromise.expectedImpactPosition().equals(context.expectedImpactPosition()))
				return;
			plannedFall = context;
			plannedPromise = offeredPromise;
			offeredPromise = null;
			plannedStage = PmbPathfindingController.RouteStage.APPROACH;
			plannedWaitTicks = waitTicks;
			plannedDeadline = Integer.MAX_VALUE;
		} else plannedFall = context;
		plannedRefreshTick = mob.tickCount;
	}
	public PmbPathfindingController.RouteStage plannedFallStage() { return plannedStage; }
	public boolean canPromisePlannedFall(Mob mob, PmbSkillScheduler scheduler,
			PmbPathfindingController.FallImpactContext context) {
		if (inWaterOrBubble(mob)) return false;
		finishFallIfGrounded(mob.onGround());
		if (mob.tickCount < promiseRetryTick) return false;
		PmbFallRescueAiData rescue = ((PmbAiHolder) mob).pmb$getAiData().fallRescue();
		if (singleMethodLocked(rescue)) return false;
		if (!rescue.isEnabled() || scheduler.priorityTier(PmbFallRescueAiData.TAG) < 0
				|| context.expectedDamage() < rescue.minRescueDamage()) return false;
		PlannedPromise required = plannedFall == null ? null : plannedPromise;
		if (plannedFall != null && (required == null
				|| !samePlannedEndpoint(required.expectedImpactPosition(), context.expectedImpactPosition()))) return false;
		if (required == null) offeredPromise = null;
		String chosen = firstPromisableMethod(rescue.methods(), method -> {
			if (required != null && !required.method().equals(method)) return false;
			PmbFallRescueAiData.Method config = method.equals("wind_charge") ? rescue.wind() : rescue.pearl();
			if (!config.isEnabled()) return false;
			PmbSkillItemAccess.Resolved item = PmbSkillItemAccess.resolvePreferred(mob, config.fetchSource(),
					List.of(InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND),
					stack -> stack.is(method.equals("wind_charge") ? Items.WIND_CHARGE : Items.ENDER_PEARL),
					config.preferredHand(), "fall_rescue");
			if (method.equals("wind_charge")) {
				return plannedPromiseEligible(context.expectedDamage(), rescue.minRescueDamage(),
						context.estimatedImpactTicks(), config.rescueLeadTicks(), config.ready(), item != null,
						plannedWindCanBeatImpact(mob, context, config.rescueLeadTicks()));
			}
			if (item == null || !config.ready()) return false;
			if (mob.level() instanceof ServerLevel level) {
				RescuePearlPlan plan = plannedPearlPromise(level, mob, context, config);
				boolean safeTrajectory = plan != null && isSaferRescue(plan.netDamage(), context.expectedDamage());
				return plannedPromiseEligible(context.expectedDamage(), rescue.minRescueDamage(),
						context.estimatedImpactTicks(), config.rescueLeadTicks(), config.ready(), true,
						safeTrajectory);
			}
			return false;
		});
		if (chosen == null) return false;
		if (required == null) offeredPromise = new PlannedPromise(chosen, context.expectedImpactPosition());
		return true;
	}
	static String firstPromisableMethod(List<String> methods, java.util.function.Predicate<String> eligible) {
		for (String method : methods) if (eligible.test(method)) return method;
		return null;
	}
	private static boolean plannedWindCanBeatImpact(Mob mob, PmbPathfindingController.FallImpactContext context,
			int leadTicks) {
		Vec3 velocity = mob.onGround() ? Vec3.ZERO : mob.getDeltaMovement();
		double startY = mob.onGround() ? context.approachPosition().y() : mob.getY();
		return plannedWindFeasible(startY - context.expectedImpactPosition().y(), velocity, leadTicks);
	}
	static boolean plannedWindFeasible(double height, Vec3 initialVelocity, int leadTicks) {
		double vertical = initialVelocity.y();
		for (int tick = 0; tick <= 120 && height > 0; tick++) {
			if (rescueWindowOpen(estimatedImpactTicks(height, 0, vertical), leadTicks)
					&& rescueWindFeasible(height, new Vec3(initialVelocity.x(), vertical, initialVelocity.z()))) return true;
			height += vertical;
			vertical = (vertical - .08) * .98;
		}
		return false;
	}
	private static RescuePearlPlan plannedPearlPromise(ServerLevel level, Mob mob,
			PmbPathfindingController.FallImpactContext context, PmbFallRescueAiData.Method config) {
		Vec3 origin = mob.onGround() ? context.approachPosition() : mob.position();
		double startY = mob.onGround() ? context.approachPosition().y() : mob.getY();
		if (!plannedPearlHasFlightWindow(startY - context.expectedImpactPosition().y(),
				mob.onGround() ? 0 : mob.getDeltaMovement().y(), config.rescueLeadTicks())) return null;
		FutureFallState release = firstFutureRelease(startY - context.expectedImpactPosition().y(),
				mob.onGround() ? 0 : mob.getDeltaMovement().y(), config.rescueLeadTicks());
		if (release == null) return null;
		double ownerY = context.expectedImpactPosition().y() + release.height();
		Vec3 start = new Vec3(origin.x(), ownerY + mob.getEyeHeight() - .1, origin.z());
		return solveRescuePearl(level, mob, start, context.expectedImpactPosition(),
				1.5F * config.maxThrowPower(), config.doTeleportDamage(), ownerY,
				new Vec3(0, release.verticalVelocity(), 0));
	}
	static boolean plannedPearlHasFlightWindow(double height, double verticalVelocity, int leadTicks) {
		FutureFallState release = firstFutureRelease(height, verticalVelocity, leadTicks);
		return release != null && estimatedImpactTicks(release.height(), 0, release.verticalVelocity()) > 1;
	}
	private static FutureFallState firstFutureRelease(double height, double verticalVelocity, int leadTicks) {
		for (int tick = 0; tick <= 120 && height > 0; tick++) {
			if (rescueWindowOpen(estimatedImpactTicks(height, 0, verticalVelocity), leadTicks))
				return new FutureFallState(height, verticalVelocity);
			height += verticalVelocity;
			verticalVelocity = (verticalVelocity - .08) * .98;
		}
		return null;
	}
	static boolean plannedPromiseEligible(float expectedDamage, float minimumDamage, int estimatedImpactTicks,
			int rescueLeadTicks, boolean methodReady, boolean itemAvailable, boolean physicalPlanAvailable) {
		return Float.isFinite(expectedDamage) && expectedDamage >= minimumDamage
				&& estimatedImpactTicks >= 0 && rescueLeadTicks >= 0
				&& methodReady && itemAvailable && physicalPlanAvailable;
	}
	static boolean rescueWindowOpen(int estimatedImpactTicks, int rescueLeadTicks) {
		// The estimate is sampled before entity movement. One remaining tick is therefore
		// the final airborne submission point for a configured lead of zero.
		return estimatedImpactTicks <= Math.max(1, rescueLeadTicks);
	}
	static boolean samePlannedEndpoint(Vec3 promised, Vec3 current) {
		return promised != null && promised.equals(current);
	}
	static Vec3 rescueTarget(boolean planned, Vec3 promisedImpactPosition, Vec3 currentLandingEstimate) {
		return planned ? promisedImpactPosition : currentLandingEstimate;
	}
	public void tick(Mob mob, PmbSkillScheduler scheduler) {
		PmbFallRescueAiData rescue = ((PmbAiHolder) mob).pmb$getAiData().fallRescue();
		rescue.tickCooldowns();
		if (rescue.isEnabled() && inWaterOrBubble(mob)) {
			cancelWaterRescue(scheduler);
			methodReasons.clear();
			reasonAll(rescue, "water_contact");
			return;
		}
		finishFallIfGrounded(mob.onGround());
		methodReasons.clear();
		reasonAll(rescue, "not_falling");
		if (plannedFall != null && plannedStage != PmbPathfindingController.RouteStage.AIRBORNE
				&& mob.tickCount - plannedRefreshTick > 1) clearPlanned();
		if (plannedFall != null && mob.tickCount > plannedDeadline && plannedStage != PmbPathfindingController.RouteStage.AIRBORNE) {
			mob.getNavigation().stop();
			promiseRetryTick = mob.tickCount + Math.max(1, plannedWaitTicks);
			clearPlanned();
		}
		boolean planned = plannedFall != null;
		if (planned && plannedStage == PmbPathfindingController.RouteStage.APPROACH
				&& horizontalDistanceSqr(mob.position(), plannedFall.approachPosition()) <= 2.25) {
			plannedStage = PmbPathfindingController.RouteStage.ARMED;
			plannedDeadline = mob.tickCount + plannedWaitTicks;
		}
		if (planned && plannedStage == PmbPathfindingController.RouteStage.ARMED
				&& !canPromisePlannedFall(mob, scheduler, plannedFall)) {
			mob.getNavigation().stop();
			promiseRetryTick = mob.tickCount + Math.max(1, plannedWaitTicks);
			clearPlanned();
			planned = false;
		}
		if (planned && !mob.onGround() && mob.getDeltaMovement().y() < 0)
			plannedStage = PmbPathfindingController.RouteStage.AIRBORNE;
		if (planned && plannedStage == PmbPathfindingController.RouteStage.AIRBORNE && mob.onGround()) {
			clearPlanned();
			resetAccidentalFall();
			return;
		}
		if (!planned && (mob.onGround() || mob.getDeltaMovement().y() >= 0)) {
			if (shouldResetAccidentalFall(mob.onGround(), mob.getDeltaMovement().y())) resetAccidentalFall();
			return;
		}
		if (!rescue.isEnabled() || !(mob.level() instanceof ServerLevel level)) return;
		if (singleMethodLocked(rescue)) {
			reasonAll(rescue, "method_committed_this_fall");
			return;
		}
		float expectedDamage = planned ? plannedFall.expectedDamage() : expectedFallDamage(mob);
		if (expectedDamage < rescue.minRescueDamage()) { reasonAll(rescue, "damage_below_threshold=" + expectedDamage); return; }
		if (planned && plannedStage != PmbPathfindingController.RouteStage.AIRBORNE) return;
		if (fallEpoch == Integer.MIN_VALUE) fallEpoch = mob.tickCount;
		if (!planned && (terminalProbabilityFailure || awaitingAccidentalCommit)) {
			reasonAll(rescue, terminalProbabilityFailure ? "chance_failed_this_fall" : "awaiting_commit"); return;
		}
		reasonAll(rescue, "earlier_method_selected");
		final boolean plannedAction = planned;
		int methodOrder = 0;
		for (String method : rescue.methods()) {
			int candidateRank = 100 - methodOrder++;
			if (plannedAction && (plannedPromise == null || !plannedPromise.method().equals(method))) continue;
			if (attemptedMethods.contains(method)
					|| !plannedAction && !accidentalMethodAllowed(method, rescue.singleMethodPerFall())) { methodReasons.put(method, "already_attempted"); continue; }
			PmbFallRescueAiData.Method config = method.equals("wind_charge") ? rescue.wind() : rescue.pearl();
			if (!config.isEnabled()) { methodReasons.put(method, "method_disabled"); continue; }
			if (!config.ready()) { methodReasons.put(method, "cooldown"); continue; }
			Vec3 impact = rescueTarget(plannedAction,
					plannedPromise == null ? null : plannedPromise.expectedImpactPosition(), landingEstimate(level, mob));
			if (!rescueWindowOpen(estimatedImpactTicks(mob.getY(), impact.y(), mob.getDeltaMovement().y()),
					config.rescueLeadTicks())) { methodReasons.put(method, "window_not_open"); continue; }
			PmbSkillItemAccess.Resolved item = PmbSkillItemAccess.resolvePreferred(mob, config.fetchSource(),
					List.of(InteractionHand.OFF_HAND, InteractionHand.MAIN_HAND),
					stack -> stack.is(method.equals("wind_charge") ? Items.WIND_CHARGE : Items.ENDER_PEARL),
					config.preferredHand(), "fall_rescue");
			if (item == null) { methodReasons.put(method, "item_or_hand_unavailable"); continue; }
			RescuePearlPlan pearlPlan = null;
			if (method.equals("wind_charge") && !windCanBeatImpact(mob, impact)) {
				methodReasons.put(method, "no_timely_in_radius_explosion"); continue;
			}
			if (method.equals("ender_pearl")) {
				Vec3 start = mob.getEyePosition().add(0, -.1, 0);
				pearlPlan = solveRescuePearl(level, mob, start, impact,
						1.5F * config.maxThrowPower(), config.doTeleportDamage());
				if (pearlPlan == null || !isSaferRescue(pearlPlan.netDamage(), expectedDamage)) {
					methodReasons.put(method, pearlPlan == null ? "no_timely_safe_trajectory" : "net_damage_not_lower"); continue;
				}
			}
			methodReasons.put(method, "candidate");
			RescuePearlPlan cachedPearlPlan = pearlPlan;
			RescueLookClaim[] stagedLook = new RescueLookClaim[1];
			scheduler.offerDynamicPlannedResult("fall_rescue", method, PmbSkillScheduler.Category.THROW, candidateRank,
					() -> {
						if (!plannedAction && (terminalProbabilityFailure || awaitingAccidentalCommit
								|| singleMethodLocked(rescue))) return false;
						if (plannedAction) {
							attemptedMethods.add(method);
							stagedLook[0] = stageRescueLook(mob, method,
									cachedPearlPlan == null ? impact : cachedPearlPlan.landing());
							return true;
						}
						config.attempted(mob.getRandom());
						boolean passed = PmbSkillTiming.passesChance(mob.getRandom(), config.successChance());
						recordAccidentalAttempt(method, passed);
						if (passed) stagedLook[0] = stageRescueLook(mob, method,
								cachedPearlPlan == null ? impact : cachedPearlPlan.landing());
						return passed;
					}, () -> !mob.onGround() && mob.getDeltaMovement().y() < 0
							&& PmbSkillItemAccess.stillMatches(mob, item),
					() -> {
						PmbSkillScheduler.CommitResult result = commit(level, mob, scheduler, method, config, item,
								expectedDamage, cachedPearlPlan);
						if (result == PmbSkillScheduler.CommitResult.COMMITTED && plannedAction) {
							config.attempted(mob.getRandom());
							recordCommittedRescue(method);
							clearPlanned();
						} else if (result == PmbSkillScheduler.CommitResult.COMMITTED) recordCommittedRescue(method);
						return result;
					}, () -> { abortAttempt(method); abortStagedRescueLook(stagedLook[0]); },
					() -> item.resources(PmbSkillScheduler.Resource.LOOK));
			// Offer every feasible method in configured order. The scheduler admits
			// the next one only if earlier methods cannot claim or commit resources.
		}
	}
	private void resetAccidentalFall() {
		fallEpoch = Integer.MIN_VALUE;
		attemptedMethods.clear();
		terminalProbabilityFailure = false;
		awaitingAccidentalCommit = false;
		allowLowerAfterCommit = false;
	}
	private void cancelWaterRescue(PmbSkillScheduler scheduler) {
		clearRescueLook();
		clearPlanned();
		resetAccidentalFall();
		committedMethodThisFall = false;
		scheduler.dropPhase("fall_rescue", "rescue_look_wind_charge");
		scheduler.dropPhase("fall_rescue", "rescue_look_ender_pearl");
	}
	private static boolean inWaterOrBubble(Mob mob) {
		BlockPos feet = mob.blockPosition();
		return mob.isInWater() || mob.level() != null && (mob.level().getFluidState(feet).is(FluidTags.WATER)
				|| mob.level().getBlockState(feet).is(Blocks.BUBBLE_COLUMN));
	}
	void recordAccidentalAttempt(String method, boolean passed) {
		attemptedMethods.add(method);
		terminalProbabilityFailure = !passed;
		awaitingAccidentalCommit = passed;
		allowLowerAfterCommit = false;
	}
	void recordCommittedRescue(String method) {
		attemptedMethods.add(method);
		committedMethodThisFall = true;
		terminalProbabilityFailure = false;
		awaitingAccidentalCommit = false;
		allowLowerAfterCommit = true;
	}
	void abortAttempt(String method) {
		if (awaitingAccidentalCommit) {
			awaitingAccidentalCommit = false;
			attemptedMethods.remove(method);
		}
	}
	boolean singleMethodLocked(PmbFallRescueAiData rescue) {
		return rescue.singleMethodPerFall() && committedMethodThisFall;
	}
	void finishFallIfGrounded(boolean onGround) {
		if (onGround) committedMethodThisFall = false;
	}
	boolean accidentalMethodAllowed(String method, boolean singleMethodPerFall) {
		return !(singleMethodPerFall && committedMethodThisFall)
				&& !attemptedMethods.contains(method) && !terminalProbabilityFailure && !awaitingAccidentalCommit
				&& (attemptedMethods.isEmpty() || allowLowerAfterCommit);
	}
	boolean shouldResetAccidentalFall(boolean onGround, double verticalVelocity) {
		return onGround || verticalVelocity >= 0 && !allowLowerAfterCommit;
	}
	private void clearPlanned() {
		plannedFall = null;
		plannedPromise = null;
		offeredPromise = null;
		plannedStage = PmbPathfindingController.RouteStage.NONE;
		plannedDeadline = Integer.MAX_VALUE;
		plannedRefreshTick = Integer.MIN_VALUE;
	}
	static int estimatedImpactTicks(double currentY, double impactY, double verticalVelocity) {
		double distance = Math.max(0, currentY - impactY);
		double velocity = Math.min(0, verticalVelocity);
		for (int ticks = 0; ticks <= 120; ticks++) {
			if (distance <= 0) return ticks;
			distance += velocity;
			velocity = (velocity - .08) * .98;
		}
		return 121;
	}
	private static float expectedFallDamage(Mob mob) {
		ServerLevel level = (ServerLevel) mob.level();
		if (firstFallImpactIsWater(level, mob)) return 0;
		Vec3 landing = landingEstimate(level, mob);
		double remaining = Math.max(0, mob.getY() - landing.y());
		double effectiveDistance = mob.fallDistance + remaining;
		if (mob.isIgnoringFallDamageFromCurrentImpulse() && mob.currentImpulseImpactPos != null)
			effectiveDistance = Math.min(effectiveDistance,
					Math.max(0, mob.currentImpulseImpactPos.y() - landing.y()));
		float raw = (float) (Math.max(0, effectiveDistance - 3.0)
				* mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER));
		return PmbFallDamagePrediction.afterProtection(level, mob, raw);
	}
	private static boolean firstFallImpactIsWater(ServerLevel level, Mob mob) {
		Vec3 position = mob.position();
		Vec3 velocity = mob.getDeltaMovement();
		for (int tick = 0; tick < 120 && position.y() > level.getMinY(); tick++) {
			Vec3 next = position.add(velocity);
			int samples = Math.max(1, (int) Math.ceil(position.distanceTo(next) / .25));
			if (samples > 16) return false; // Unknown extreme motion must not suppress rescue.
			for (int sample = 1; sample <= samples; sample++) {
				Vec3 point = position.lerp(next, sample / (double) samples);
				BlockPos feet = BlockPos.containing(point);
				if (!level.hasChunkAt(feet)) return false;
				if (!level.noCollision(mob, mob.getBoundingBox().move(point.subtract(mob.position())))) return false;
				if (level.getFluidState(feet).is(FluidTags.WATER)) return true;
			}
			position = next;
			velocity = new Vec3(velocity.x() * .91, (velocity.y() - .08) * .98, velocity.z() * .91);
		}
		return false;
	}
	private static double horizontalDistanceSqr(Vec3 first, Vec3 second) {
		double x = first.x() - second.x();
		double z = first.z() - second.z();
		return x * x + z * z;
	}

	private PmbSkillScheduler.CommitResult commit(ServerLevel level, Mob mob, PmbSkillScheduler scheduler,
			String method, PmbFallRescueAiData.Method config, PmbSkillItemAccess.Resolved item, float originalDamage,
			RescuePearlPlan cachedPearlPlan) {
		PmbSkillItemAccess.ActionBinding binding = PmbSkillItemAccess.acquire(mob, item);
		if (binding == null) return PmbSkillScheduler.CommitResult.FAILED;
		InteractionHand hand = binding.hand();
		ItemStack stack = mob.getItemInHand(hand);
		ItemStack projectileStack = stack.copyWithCount(1);
		if (method.equals("wind_charge")) {
			Vec3 start = mob.position().add(0, .15, 0);
			WindCharge charge = new WindCharge(level, start.x(), start.y(), start.z(), Vec3.ZERO);
			((PmbWindImpulseHolder) charge).pmb$setImpulseSource("fall_rescue");
			charge.setOwner(mob);
			charge.shoot(0, -1, 0, rescueWindSpeed(mob.getDeltaMovement().y()), 0);
			if (!level.addFreshEntity(charge)) return rollbackFailedSpawn(mob, binding);
			beginRescueLook(mob, method, landingEstimate(level, mob));
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.WIND_CHARGE_THROW,
					SoundSource.NEUTRAL, .5F, 1);
			scheduler.airTracking().commitPosition(mob, "fall_rescue", landingEstimate(level, mob));
		} else {
			RescuePearlPlan plan = cachedPearlPlan;
			if (plan == null || !isSaferRescue(plan.netDamage(), originalDamage)) {
				if (!binding.rollback(mob))
					throw new IllegalStateException("PMB could not roll back an uncommitted fall-rescue equipment swap");
				return PmbSkillScheduler.CommitResult.FAILED;
			}
			Vec3 velocity = plan.impulse();
			ThrownEnderpearl pearl = new ThrownEnderpearl(level, mob, projectileStack);
			((PmbEnderPearlDamageHolder) pearl).pmb$setTeleportDamage(config.doTeleportDamage());
			pearl.shoot(velocity.x(), velocity.y(), velocity.z(), (float) velocity.length(), (1 - config.throwAccuracy()) * 12);
			pearl.setDeltaMovement(combineRescueVelocity(pearl.getDeltaMovement(), plan.inheritedVelocity()));
			Vec3 actual = pearl.getDeltaMovement();
			var sampled = PmbPearlTrajectorySimulator.simulate(level, mob, pearl.position(), actual, 120);
			int deadline = estimatedImpactTicks(mob.getY(), plan.target().y(), mob.getDeltaMovement().y());
			float sampledDamage = (config.doTeleportDamage() ? reducedPearlDamage(level, mob) : 0)
					+ residualFallDamage(level, mob, sampled.position(), mob.getDeltaMovement().y());
			if (sampled.status() != PmbPearlTrajectorySimulator.Status.BLOCK_HIT || sampled.ticks() >= deadline
					|| !PmbLocalEdgePlanner.safeStanding(level, mob, sampled.position())
					|| !isSaferRescue(sampledDamage, originalDamage)) {
				if (!binding.rollback(mob)) throw new IllegalStateException("Unable to roll back rejected rescue spread");
				return PmbSkillScheduler.CommitResult.FAILED;
			}
			if (!level.addFreshEntity(pearl)) return rollbackFailedSpawn(mob, binding);
			beginRescueLook(mob, method, sampled.position());
			trackRescuePearl(pearl, sampled.position());
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.ENDER_PEARL_THROW,
					SoundSource.NEUTRAL, .5F, 1);
		}
		mob.swing(hand, true);
		if (config.doConsume()) {
			stack.consume(1, mob);
			mob.setItemInHand(hand, stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
		}
		binding.authorizeAction(mob);
		scheduler.markCurrentCandidateExecuted("fall_rescue");
		return PmbSkillScheduler.CommitResult.COMMITTED;
	}
	private static PmbSkillScheduler.CommitResult rollbackFailedSpawn(Mob mob, PmbSkillItemAccess.ActionBinding binding) {
		if (!binding.rollback(mob)) throw new IllegalStateException("Unable to roll back rejected rescue spawn");
		return PmbSkillScheduler.CommitResult.FAILED;
	}
	static Vec3 rescueInheritedVelocity(Vec3 ownerVelocity) { return new Vec3(0, Math.min(0, ownerVelocity.y()), 0); }
	static Vec3 combineRescueVelocity(Vec3 impulse, Vec3 inherited) { return impulse.add(inherited); }
	private static boolean windCanBeatImpact(Mob mob, Vec3 impact) {
		return rescueWindFeasible(mob.getY() - impact.y(), mob.getDeltaMovement());
	}
	static float rescueWindSpeed(double verticalVelocity) {
		return (float) (1.5 + Math.max(0, -verticalVelocity));
	}
	static boolean rescueWindFeasible(double height, Vec3 ownerVelocity) {
		int mobTicks = estimatedImpactTicks(height, 0, ownerVelocity.y());
		int windTicks = (int) Math.ceil(Math.max(0, height + .15) / rescueWindSpeed(ownerVelocity.y()));
		if (windTicks >= mobTicks || windTicks < 1) return false;
		double ownerHeight = height;
		double verticalVelocity = ownerVelocity.y();
		for (int tick = 0; tick < windTicks; tick++) {
			ownerHeight += verticalVelocity;
			verticalVelocity = (verticalVelocity - .08) * .98;
		}
		// The owner moves before the newly spawned projectile. A ground hit explodes
		// 0.25 blocks above the surface, and vanilla measures its 2.4 radius from feet.
		double horizontal = ownerVelocity.horizontalDistanceSqr() * windTicks * windTicks;
		return ownerHeight > 0 && horizontal + Math.pow(ownerHeight - .25, 2) < 2.4 * 2.4;
	}

	private static Vec3 landingEstimate(ServerLevel level, Mob mob) {
		BlockPos.MutableBlockPos cursor = BlockPos.containing(mob.position()).mutable();
		int minimum = level.getMinY();
		while (cursor.getY() > minimum && level.hasChunkAt(cursor)
				&& level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) cursor.move(0, -1, 0);
		return Vec3.atBottomCenterOf(cursor.above());
	}
	private static RescuePearlPlan solveRescuePearl(ServerLevel level, Mob mob, Vec3 start, Vec3 target,
			float maxSpeed, boolean doTeleportDamage) {
		return solveRescuePearl(level, mob, start, target, maxSpeed, doTeleportDamage,
				mob.getY(), mob.getDeltaMovement());
	}
	private static RescuePearlPlan solveRescuePearl(ServerLevel level, Mob mob, Vec3 start, Vec3 target,
			float maxSpeed, boolean doTeleportDamage, double ownerY, Vec3 ownerVelocity) {
		Vec3 offset = target.subtract(start);
		if (offset.lengthSqr() < 1.0E-6) return null;
		// Rescue owns the time/angle choice. Search discrete flight times under vanilla drag/gravity.
		RescuePearlPlan best = null;
		int deadline = estimatedImpactTicks(ownerY, target.y(), ownerVelocity.y());
		Vec3 inherited = rescueInheritedVelocity(ownerVelocity);
		for (int ticks = 1; ticks < Math.min(81, deadline); ticks++) {
			Vec3 candidate = PmbPearlTrajectorySimulator.airInitialVelocity(offset.add(0, -.01, 0), ticks);
			Vec3 impulse = candidate.subtract(inherited);
			if (impulse.length() <= maxSpeed) {
				PmbPearlTrajectorySimulator.Result result = PmbPearlTrajectorySimulator.simulate(level, mob, start, candidate, 120);
				if (result.status() == PmbPearlTrajectorySimulator.Status.BLOCK_HIT
						&& result.ticks() < deadline
						&& PmbLocalEdgePlanner.safeStanding(level, mob, result.position())) {
					float pearlDamage = doTeleportDamage ? reducedPearlDamage(level, mob) : 0;
					float residual = residualFallDamage(level, mob, result.position(), ownerVelocity.y());
					RescuePearlPlan plan = new RescuePearlPlan(impulse, inherited, target, result.position(), pearlDamage + residual, result.ticks());
					if (best == null || plan.flightTicks() < best.flightTicks()
							|| plan.flightTicks() == best.flightTicks() && plan.netDamage() < best.netDamage()) best = plan;
				}
			}
		}
		return best;
	}
	private static float reducedPearlDamage(ServerLevel level, Mob mob) {
		float protection = EnchantmentHelper.getDamageProtection(level, mob, mob.damageSources().enderPearl());
		return CombatRules.getDamageAfterMagicAbsorb(5.0F, protection);
	}
	static boolean isSaferRescue(float rescueDamage, float originalDamage) {
		return Float.isFinite(rescueDamage) && Float.isFinite(originalDamage)
				&& rescueDamage >= 0 && rescueDamage < originalDamage;
	}
	private static float residualFallDamage(ServerLevel level, Mob mob, Vec3 teleportPosition, double verticalVelocity) {
		BlockPos.MutableBlockPos cursor = BlockPos.containing(teleportPosition).mutable();
		while (cursor.getY() > level.getMinY() && level.hasChunkAt(cursor)
				&& level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) cursor.move(0, -1, 0);
		double distance = Math.max(0, teleportPosition.y() - cursor.above().getY());
		double velocity = Math.min(0, verticalVelocity);
		double fallen = 0;
		for (int ticks = 0; ticks < 200 && fallen < distance; ticks++) {
			double requestedDrop = Math.max(0, -velocity);
			// Entity movement clips the final tick against the supporting block; only
			// that resolved Y displacement contributes to fallDistance.
			fallen += Math.min(requestedDrop, distance - fallen);
			velocity = (velocity - .08) * .98;
		}
		float raw = (float) (Math.max(0, fallen - 3.0)
				* mob.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FALL_DAMAGE_MULTIPLIER));
		return PmbFallDamagePrediction.afterProtection(level, mob, raw);
	}
}
