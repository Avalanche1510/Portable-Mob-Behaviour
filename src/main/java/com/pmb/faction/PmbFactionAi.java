package com.pmb.faction;

import com.pmb.ai.PmbSkillScheduler;
import com.pmb.ai.PmbAiHolder;
import com.pmb.ai.PmbAmmoAccess;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayDeque;

public final class PmbFactionAi {
	private static final int SCAN_INTERVAL = 10;
	private static final int FLEE_REPATH_INTERVAL = 10;
	private record HurtBroadcast(AbstractPiglin victim, LivingEntity attacker) {}
	private static final ThreadLocal<ArrayDeque<HurtBroadcast>> HURT_BROADCAST = new ThreadLocal<>();

	private PmbFactionAi() {
	}

	public static void tick(Mob mob, ServerLevel level) {
		PmbFactionMobState state = (PmbFactionMobState) mob;
		if (!mob.isAlive()) {
			clearRuntimeState(mob, state);
			return;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		var factionId = PmbFactionResolver.factionOf(mob);
		PmbFactionDefinition definition = factionId == null ? null : data.get(factionId);
		if (definition == null) {
			if (factionId != null) {
				((PmbFactionHolder) mob).pmb$setFactionId(null);
			}
			clearRuntimeState(mob, state);
			return;
		}

		double followRange = Math.max(0.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
		processRetaliation(mob, state, data, followRange);
		validateTargets(mob, state, data, followRange);

		if (Math.floorMod(mob.getId(), SCAN_INTERVAL) == Math.floorMod((int) level.getGameTime(), SCAN_INTERVAL)) {
			scan(mob, level, state, data, followRange);
		}
		enforce(mob, state, data, definition, false);
	}

	public static void enforceAfterVanillaAi(Mob mob) {
		if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level)) {
			return;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		var factionId = PmbFactionResolver.factionOf(mob);
		PmbFactionDefinition definition = factionId == null ? null : data.get(factionId);
		if (definition != null) {
			enforce(mob, (PmbFactionMobState) mob, data, definition, true);
		}
	}

	public static boolean mayAttack(Mob mob, LivingEntity target) {
		if (!isCombatTargetable(target)) {
			return false;
		}
		if (!isValidHurtBroadcastResponse(mob, target) && isSuppressedPiglinTarget(mob, target)) return false;
		if (preservesPiglinAvoidanceAgainst(mob, target)) {
			return false;
		}
		if (!(mob.level() instanceof ServerLevel level)) {
			return true;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		PmbFactionAttitude attitude = PmbFactionResolver.attitude(data, mob, target);
		if (attitude == null || attitude == PmbFactionAttitude.HOSTILE) {
			return true;
		}
		PmbFactionMobState state = (PmbFactionMobState) mob;
		return (mob instanceof AbstractPiglin && attitude == PmbFactionAttitude.NEUTRAL)
				|| state.pmb$getFactionCombatTarget() == target
				&& (attitude == PmbFactionAttitude.NEUTRAL || state.pmb$isFactionGroupRevenge());
	}

	/**
	 * Piglins use this result both while selecting idle targets and while validating an
	 * existing fight target. A PMB target must be returned even when it is not one of
	 * the vanilla piglin-specific candidates. Conversely, a neutral vanilla target is
	 * retained only when a real piglin anger event created ANGRY_AT (opening guarded
	 * containers, breaking guarded blocks, or retaliation).
	 */
	public static LivingEntity resolvePiglinAttackTarget(Piglin piglin, LivingEntity vanillaTarget) {
		return resolvePiglinAttackTarget((AbstractPiglin) piglin, vanillaTarget);
	}

	public static LivingEntity resolvePiglinBruteAttackTarget(PiglinBrute piglin, LivingEntity vanillaTarget) {
		return resolvePiglinAttackTarget((AbstractPiglin) piglin, vanillaTarget);
	}

	public static boolean preservesPiglinGreed(Piglin piglin) {
		return piglinCompatRules(piglin).greed();
	}

	public static boolean pausesActiveSkills(Piglin piglin) {
		if (!hasValidFaction(piglin)) return false;
		PmbPiglinCompatRules rules = piglinCompatRules(piglin);
		return rules.greed() && rules.admiringPausesActiveSkills()
				&& piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM);
	}

	public static boolean preservesAdmirationOnHurt(Piglin piglin) {
		if (!hasValidFaction(piglin)) return false;
		PmbPiglinCompatRules rules = piglinCompatRules(piglin);
		return rules.greed()
				&& rules.effectiveHurtAdmirationDisableTicks() == 0
				&& piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM);
	}

	/** -1 leaves vanilla timing untouched for a non-member or disabled greed. */
	public static int piglinHurtDisableTicks(Piglin piglin) {
		if (!hasValidFaction(piglin)) return -1;
		PmbPiglinCompatRules rules = piglinCompatRules(piglin);
		if (!rules.greed()) return -1;
		return rules.effectiveHurtAdmirationDisableTicks();
	}

	public static void onPiglinDirectHurt(Piglin piglin, LivingEntity attacker) {
		if (isCombatTargetable(attacker)) clearSuppressionOnFreshAttack(piglin, attacker);
	}

	public static void withVanillaHurtBroadcast(AbstractPiglin victim, LivingEntity attacker, Runnable action) {
		beginVanillaHurtBroadcast(victim, attacker);
		try {
			action.run();
		} finally {
			endVanillaHurtBroadcast();
		}
	}

	private static void beginVanillaHurtBroadcast(AbstractPiglin victim, LivingEntity attacker) {
		ArrayDeque<HurtBroadcast> stack = HURT_BROADCAST.get();
		if (stack == null) {
			stack = new ArrayDeque<>();
			HURT_BROADCAST.set(stack);
		}
		stack.push(new HurtBroadcast(victim, attacker));
	}

	private static void endVanillaHurtBroadcast() {
		ArrayDeque<HurtBroadcast> stack = HURT_BROADCAST.get();
		if (stack != null && !stack.isEmpty()) stack.pop();
		if (stack == null || stack.isEmpty()) HURT_BROADCAST.remove();
	}

	static int hurtBroadcastDepth() {
		ArrayDeque<HurtBroadcast> stack = HURT_BROADCAST.get();
		return stack == null ? 0 : stack.size();
	}

	public static void onVanillaGroupAngerWritten(AbstractPiglin responder, LivingEntity attacker) {
		if (!(responder instanceof Piglin piglin) || !angerWriteApplies(
				isValidHurtBroadcastResponse(piglin, attacker), hasPiglinAngerTarget(piglin, attacker))) return;
		clearSuppressionOnFreshAttack(piglin, attacker);
		onPiglinAllyAnger(piglin, attacker);
	PmbBrainAiDebug.event(piglin, "vanilla_group_anger_written", () ->
				"victim=" + HURT_BROADCAST.get().peek().victim().getId() + " target=" + attacker.getId()
						+ " angryAt=" + attacker.getUUID());
	}

	private static boolean isValidHurtBroadcastResponse(Mob responder, LivingEntity attacker) {
		if (!(responder instanceof Piglin) || !isCombatTargetable(attacker)
				|| !(responder.level() instanceof ServerLevel level)) return false;
		ArrayDeque<HurtBroadcast> stack = HURT_BROADCAST.get();
		if (stack == null) return false;
		HurtBroadcast broadcast = stack.peek();
		if (broadcast == null) return false;
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		String factionId = PmbFactionResolver.factionOf(responder);
		if (factionId == null || data.get(factionId) == null) return false;
		return validHurtBroadcast(broadcast.victim() == responder,
				broadcast.attacker() == null || broadcast.attacker() == attacker,
				PmbFactionResolver.isMemberOrAllied(data, responder, broadcast.victim()),
				PmbFactionResolver.isMemberOrAllied(data, responder, attacker));
	}

	static boolean validHurtBroadcast(boolean responderIsVictim, boolean attackerMatches,
			boolean victimIsAlly, boolean attackerIsAlly) {
		return !responderIsVictim && attackerMatches && victimIsAlly && !attackerIsAlly;
	}

	static boolean angerWriteApplies(boolean validHurtBroadcast, boolean angryAtWrittenForAttacker) {
		return validHurtBroadcast && angryAtWrittenForAttacker;
	}

	public static void onPiglinAllyAnger(Piglin piglin, LivingEntity attacker) {
		if (!isCombatTargetable(attacker) || !(piglin.level() instanceof ServerLevel level)) return;
		PmbPiglinCompatRules rules = piglinCompatRules(piglin);
		if (!rules.greed() || !rules.hurtAdmirationPolicy().equals("combat")
				|| rules.effectiveHurtAdmirationDisableTicks() == 0
				|| !piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)) return;
		if (PmbFactionResolver.isMemberOrAllied(PmbFactionSavedData.get(level.getServer()), piglin, attacker)) return;
		if (!piglin.canAttack(attacker)) return;
		piglin.getBrain().eraseMemory(MemoryModuleType.ADMIRING_ITEM);
		piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ADMIRING_DISABLED, true,
				rules.hurtAdmirationDisableTicks());
		var held = piglin.getItemInHand(InteractionHand.OFF_HAND);
		if (held.isEmpty()) return;
		piglin.setItemInHand(InteractionHand.OFF_HAND, net.minecraft.world.item.ItemStack.EMPTY);
		var remainder = rules.hurtAdmiredItemPolicy().equals("store")
				? piglin.getInventory().addItem(held.copy()) : held.copy();
		if (!remainder.isEmpty()) {
			var dropped = piglin.spawnAtLocation(level, remainder);
			if (dropped != null) dropped.setPickUpDelay(40);
			else piglin.setItemInHand(InteractionHand.OFF_HAND, remainder);
		}
		PmbBrainAiDebug.event(piglin, "PMB_admiration_interrupt", () ->
				"attacker=" + attacker.getId() + " itemPolicy=" + rules.hurtAdmiredItemPolicy());
	}

	public static boolean shouldFightNearestZombified(Piglin piglin) {
		if (!(piglin.level() instanceof ServerLevel level)) {
			return false;
		}
		String factionId = PmbFactionResolver.factionOf(piglin);
		PmbFactionDefinition definition = factionId == null
				? null : PmbFactionSavedData.get(level.getServer()).get(factionId);
		if (definition == null) {
			return false;
		}
		if (definition.vanillaCompatRules().piglin().avoidance()) {
			return false;
		}
		LivingEntity nearestZombified = getRegisteredMemory(
				piglin.getBrain(), MemoryModuleType.NEAREST_VISIBLE_ZOMBIFIED);
		if (nearestZombified == null) {
			return false;
		}
		PmbFactionMobState state = (PmbFactionMobState) piglin;
		return state.pmb$getFactionCombatTarget() == nearestZombified
				|| PmbFactionResolver.attitude(PmbFactionSavedData.get(level.getServer()),
						piglin, nearestZombified) == PmbFactionAttitude.HOSTILE;
	}

	/**
	 * Piglin core behaviors copy a nearby zombified mob into AVOID_TARGET before
	 * updateActivity chooses between FIGHT and AVOID. When Faction marks that same
	 * mob hostile, allowing both memories to survive for even one activity update
	 * makes the piglin alternate poses, sounds, and movement. Resolve the conflict
	 * immediately before vanilla selects its activity, while preserving a genuine
	 * PMB evasive target and vanilla avoidance of non-hostile entities.
	 */
	public static void preparePiglinActivityUpdate(Piglin piglin) {
		if (!(piglin.level() instanceof ServerLevel level)) {
			return;
		}
		String factionId = PmbFactionResolver.factionOf(piglin);
		PmbFactionDefinition definition = factionId == null
				? null : PmbFactionSavedData.get(level.getServer()).get(factionId);
		if (definition == null) {
			return;
		}
		observePiglinUnreachable(piglin, definition);

		Brain<?> brain = piglin.getBrain();
		LivingEntity vanillaAvoid = getRegisteredMemory(brain, MemoryModuleType.AVOID_TARGET);
		if (vanillaAvoid == null) {
			return;
		}
		PmbFactionMobState state = (PmbFactionMobState) piglin;
		if (state.pmb$getFactionAvoidTarget() == vanillaAvoid) {
			return;
		}
		if (definition.vanillaCompatRules().piglin().avoidance()) {
			if (state.pmb$getFactionCombatTarget() == vanillaAvoid) {
				state.pmb$setFactionCombatTarget(null);
				state.pmb$setFactionGroupRevenge(false);
			}
			if (getRegisteredMemory(brain, MemoryModuleType.ATTACK_TARGET) == vanillaAvoid) {
				brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
			return;
		}

		brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
		// FIGHT also writes WALK_TARGET. Clearing it every time CORE restores a
		// suppressed vanilla avoidance memory repeatedly interrupts pursuit.
		if (shouldClearAvoidanceWalkTarget(state.pmb$getFactionCombatTarget() != null,
				getRegisteredMemory(brain, MemoryModuleType.ATTACK_TARGET) != null)) {
			brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}

	/** Run the same publication boundary before each supported vanilla Brain chooses an activity. */
	public static void prepareBrainActivityUpdate(Mob mob) {
		if (!PmbBrainIntentCoordinator.supports(mob)
				|| !(mob.level() instanceof ServerLevel level)) return;
		String id = PmbFactionResolver.factionOf(mob);
		PmbFactionDefinition definition = id == null ? null
				: PmbFactionSavedData.get(level.getServer()).get(id);
		if (definition == null) return;
		if (mob instanceof Piglin piglin) preparePiglinActivityUpdate(piglin);
		enforce(mob, (PmbFactionMobState) mob,
				PmbFactionSavedData.get(level.getServer()), definition, true);
	}

	static boolean shouldClearAvoidanceWalkTarget(boolean factionCombat, boolean brainCombat) {
		return !factionCombat && !brainCombat;
	}

	private static LivingEntity resolvePiglinAttackTarget(AbstractPiglin piglin, LivingEntity vanillaTarget) {
		if (defersCombatForAdmiration(piglin)) return null;
		PmbFactionMobState state = (PmbFactionMobState) piglin;
		LivingEntity factionTarget = state.pmb$getFactionCombatTarget();
		if (isCombatTargetable(factionTarget) && factionTarget.level() == piglin.level()
				&& !preservesPiglinAvoidanceAgainst(piglin, factionTarget)
				&& !isSuppressedPiglinTarget(piglin, factionTarget)) {
			return factionTarget;
		}
		if (!isCombatTargetable(vanillaTarget) || isSuppressedPiglinTarget(piglin, vanillaTarget)
				|| !(piglin.level() instanceof ServerLevel level)) {
			return null;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		PmbFactionAttitude attitude = PmbFactionResolver.attitude(data, piglin, vanillaTarget);
		if (attitude == null || attitude == PmbFactionAttitude.HOSTILE) {
			return vanillaTarget;
		}
		boolean preservesGuarding = !(piglin instanceof Piglin)
				|| piglinCompatRules(piglin).guarding();
		return attitude == PmbFactionAttitude.NEUTRAL && preservesGuarding
				&& hasPiglinAngerTarget(piglin, vanillaTarget)
				? vanillaTarget : null;
	}

	public static void authorizeRevenge(Mob mob, LivingEntity attacker) {
		clearSuppressionOnFreshAttack(mob, attacker);
		PmbFactionMobState state = (PmbFactionMobState) mob;
		if (!isCombatTargetable(attacker) || preservesPiglinAvoidanceAgainst(mob, attacker)) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionGroupRevenge(false);
			return;
		}
		state.pmb$setFactionAvoidTarget(null);
		state.pmb$setFactionCombatTarget(attacker);
		state.pmb$setFactionGroupRevenge(true);
		if (mob instanceof Piglin piglin) onPiglinAllyAnger(piglin, attacker);
	}

	public static void authorizeNeutralRetaliation(Mob mob, LivingEntity attacker) {
		clearSuppressionOnFreshAttack(mob, attacker);
		if (!(mob.level() instanceof ServerLevel level) || !isCombatTargetable(attacker)) {
			return;
		}
		PmbFactionSavedData data = PmbFactionSavedData.get(level.getServer());
		String factionId = PmbFactionResolver.factionOf(mob);
		if (factionId == null || data.get(factionId) == null) {
			return;
		}
		double followRange = Math.max(0.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
		if (!isInRange(mob, attacker, followRange)
				|| PmbFactionResolver.attitude(data, mob, attacker) != PmbFactionAttitude.NEUTRAL
				|| preservesPiglinAvoidanceAgainst(mob, attacker)) {
			return;
		}

		PmbFactionMobState state = (PmbFactionMobState) mob;
		LivingEntity previousAvoid = state.pmb$getFactionAvoidTarget();
		LivingEntity previousCombat = state.pmb$getFactionCombatTarget();
		boolean previousGroupRevenge = state.pmb$isFactionGroupRevenge();
		state.pmb$setFactionAvoidTarget(null);
		state.pmb$setFactionCombatTarget(attacker);
		state.pmb$setFactionGroupRevenge(false);
		if (!mob.canAttack(attacker)) {
			state.pmb$setFactionAvoidTarget(previousAvoid);
			state.pmb$setFactionCombatTarget(previousCombat);
			state.pmb$setFactionGroupRevenge(previousGroupRevenge);
		}
	}

	public static LivingEntity factionCombatTarget(Mob mob) {
		return ((PmbFactionMobState) mob).pmb$getFactionCombatTarget();
	}

	public static boolean hasFactionCombatTarget(Mob mob) {
		return factionCombatTarget(mob) != null;
	}

	private static void processRetaliation(Mob mob, PmbFactionMobState state, PmbFactionSavedData data,
			double followRange) {
		int timestamp = mob.getLastHurtByMobTimestamp();
		if (timestamp == state.pmb$getFactionHurtTimestamp()) {
			return;
		}
		state.pmb$setFactionHurtTimestamp(timestamp);
		LivingEntity attacker = mob.getLastHurtByMob();
		clearSuppressionOnFreshAttack(mob, attacker);
		if (!isInRange(mob, attacker, followRange)) {
			return;
		}
		PmbFactionAttitude attitude = PmbFactionResolver.attitude(data, mob, attacker);
		if (preservesPiglinAvoidanceAgainst(mob, attacker)) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionGroupRevenge(false);
			return;
		}
		if (attitude == PmbFactionAttitude.PASSIVELY_EVASIVE
				|| attitude == PmbFactionAttitude.ACTIVELY_EVASIVE) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionGroupRevenge(false);
			state.pmb$setFactionAvoidTarget(attacker);
		} else if (attitude == PmbFactionAttitude.NEUTRAL || attitude == PmbFactionAttitude.HOSTILE) {
			state.pmb$setFactionAvoidTarget(null);
			state.pmb$setFactionCombatTarget(attacker);
			state.pmb$setFactionGroupRevenge(false);
		}
	}

	private static void validateTargets(Mob mob, PmbFactionMobState state, PmbFactionSavedData data,
			double followRange) {
		LivingEntity avoid = state.pmb$getFactionAvoidTarget();
		if (!isInRange(mob, avoid, followRange)) {
			state.pmb$setFactionAvoidTarget(null);
		} else {
			PmbFactionAttitude attitude = PmbFactionResolver.attitude(data, mob, avoid);
			if (attitude != PmbFactionAttitude.PASSIVELY_EVASIVE
					&& attitude != PmbFactionAttitude.ACTIVELY_EVASIVE) {
				state.pmb$setFactionAvoidTarget(null);
			}
		}

		LivingEntity combat = state.pmb$getFactionCombatTarget();
		boolean combatInRange = isInRange(mob, combat, followRange);
		PmbFactionAttitude combatAttitude = combatInRange
				? PmbFactionResolver.attitude(data, mob, combat) : null;
		if (!retainsCombatTarget(combatInRange, combatAttitude,
				combatInRange && preservesPiglinAvoidanceAgainst(mob, combat),
				state.pmb$isFactionGroupRevenge()) || isSuppressedPiglinTarget(mob, combat)) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionGroupRevenge(false);
		}
	}

	static boolean retainsCombatTarget(boolean inRange, PmbFactionAttitude attitude,
			boolean piglinAvoids, boolean groupRevenge) {
		return inRange && !piglinAvoids && attitude != PmbFactionAttitude.ALLIED
				&& (attitude == PmbFactionAttitude.HOSTILE || attitude == PmbFactionAttitude.NEUTRAL
					|| groupRevenge);
	}

	static <T> T scannedCombatTarget(T validatedCurrent, T nearestVisibleHostile) {
		return nearestVisibleHostile != null ? nearestVisibleHostile : validatedCurrent;
	}

	private static void scan(Mob mob, ServerLevel level, PmbFactionMobState state, PmbFactionSavedData data,
			double followRange) {
		List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
				mob.getBoundingBox().inflate(followRange), target -> target != mob && isInRange(mob, target, followRange));

		LivingEntity activeThreat = state.pmb$isFactionGroupRevenge() ? null : candidates.stream()
				.filter(target -> PmbFactionResolver.attitude(data, mob, target)
						== PmbFactionAttitude.ACTIVELY_EVASIVE)
				.filter(target -> mob.getSensing().hasLineOfSight(target))
				.min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
		if (activeThreat != null) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionAvoidTarget(activeThreat);
			return;
		}

		if (state.pmb$getFactionAvoidTarget() != null) {
			return;
		}

		LivingEntity hostile = candidates.stream()
				.filter(target -> PmbFactionResolver.attitude(data, mob, target) == PmbFactionAttitude.HOSTILE)
				.filter(target -> !preservesPiglinAvoidanceAgainst(mob, target))
				.filter(target -> !isSuppressedPiglinTarget(mob, target))
				.filter(target -> mob.getSensing().hasLineOfSight(target))
				.filter(mob::canAttack)
				.min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
		LivingEntity current = state.pmb$getFactionCombatTarget();
		LivingEntity selected = scannedCombatTarget(current, hostile);
		if (selected != current) {
			state.pmb$setFactionCombatTarget(selected);
			state.pmb$setFactionGroupRevenge(false);
		}
	}

	private static void enforce(Mob mob, PmbFactionMobState state, PmbFactionSavedData data,
			PmbFactionDefinition definition,
			boolean afterVanillaAi) {
		if (mob instanceof Piglin piglin && !definition.vanillaCompatRules().piglin().greed()) {
			piglin.getBrain().eraseMemory(MemoryModuleType.ADMIRING_ITEM);
			piglin.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
			piglin.getBrain().eraseMemory(MemoryModuleType.NEAREST_PLAYER_HOLDING_WANTED_ITEM);
		}
		LivingEntity avoid = state.pmb$getFactionAvoidTarget();
		if (PmbBrainIntentCoordinator.choose(mob, definition, avoid,
				state.pmb$getFactionCombatTarget()) == PmbBrainIntentCoordinator.Intent.ADMIRE) {
			state.pmb$setFactionAdmiring(true);
			// Retain the faction intent internally, but never expose an attack memory to
			// vanilla FIGHT/CrossbowAttack while ADMIRING_ITEM owns the Brain.
			// The explicit PMB-skill opt-out still controls the separate Mob target.
			if (definition.vanillaCompatRules().piglin().admiringPausesActiveSkills()
					&& mob.getTarget() != null) mob.setTarget(null);
			else if (!definition.vanillaCompatRules().piglin().admiringPausesActiveSkills()
					&& state.pmb$getFactionCombatTarget() != null
					&& mob.getTarget() != state.pmb$getFactionCombatTarget())
				mob.setTarget(state.pmb$getFactionCombatTarget());
			if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET))
				mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			if (avoid != null && getRegisteredMemory(mob.getBrain(), MemoryModuleType.AVOID_TARGET) == avoid)
				mob.getBrain().eraseMemory(MemoryModuleType.AVOID_TARGET);
			return;
		}
		if (state.pmb$wasFactionAdmiring()) {
			state.pmb$setFactionAdmiring(false);
			validateTargets(mob, state, data, Math.max(0.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE)));
			avoid = state.pmb$getFactionAvoidTarget();
		}
		if (avoid != null) {
			if (mob.getTarget() != null) {
				mob.setTarget(null);
			}
			if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
				mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
			if (getRegisteredMemory(mob.getBrain(), MemoryModuleType.AVOID_TARGET) != avoid) {
				mob.getBrain().setMemory(MemoryModuleType.AVOID_TARGET, avoid);
			}
			state.pmb$setFactionAvoiding(true);
			PmbSkillScheduler.of(mob).pathfinding().submitRetreat(mob, avoid);
			return;
		}

		if (state.pmb$wasFactionAvoiding() && !afterVanillaAi) {
			mob.getBrain().eraseMemory(MemoryModuleType.AVOID_TARGET);
			state.pmb$setFactionAvoiding(false);
		}
		if (mob instanceof Piglin && !definition.vanillaCompatRules().piglin().avoidance()
				&& mob.getBrain().hasMemoryValue(MemoryModuleType.AVOID_TARGET)) {
			mob.getBrain().eraseMemory(MemoryModuleType.AVOID_TARGET);
		}
		LivingEntity combat = state.pmb$getFactionCombatTarget();
		if (combat != null && preservesPiglinAvoidanceAgainst(mob, combat)) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionGroupRevenge(false);
			if (mob.getTarget() == combat) {
				mob.setTarget(null);
			}
			if (getRegisteredMemory(mob.getBrain(), MemoryModuleType.ATTACK_TARGET) == combat) {
				mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
			}
			combat = null;
		}
		if (combat != null) {
			if (mob.getTarget() != combat) {
				mob.setTarget(combat);
			}
			if (getRegisteredMemory(mob.getBrain(), MemoryModuleType.ATTACK_TARGET) != combat) {
				mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, combat);
			}
			if (mob instanceof Piglin) state.pmb$setLastBrainCombatTarget(combat);
			return;
		}

		LivingEntity goalTarget = mob.getTarget();
		if (goalTarget != null && !mayRetainVanillaTarget(mob, goalTarget, state, data)) {
			mob.setTarget(null);
		}
		LivingEntity brainTarget = getRegisteredMemory(mob.getBrain(), MemoryModuleType.ATTACK_TARGET);
		if (brainTarget != null && !mayRetainVanillaTarget(mob, brainTarget, state, data)) {
			mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
		}
	}

	private static boolean mayRetainVanillaTarget(Mob mob, LivingEntity target, PmbFactionMobState state,
			PmbFactionSavedData data) {
		if (!isCombatTargetable(target) || isSuppressedPiglinTarget(mob, target)) {
			return false;
		}
		PmbFactionAttitude attitude = PmbFactionResolver.attitude(data, mob, target);
		if (attitude == null || attitude == PmbFactionAttitude.HOSTILE) {
			return true;
		}
		if (attitude != PmbFactionAttitude.NEUTRAL) {
			return false;
		}
		return state.pmb$getFactionCombatTarget() == target
				|| state.pmb$isFactionGroupRevenge()
				|| mob instanceof Piglin piglin && piglinCompatRules(piglin).guarding()
						&& hasPiglinAngerTarget(piglin, target)
				|| mob instanceof PiglinBrute brute && hasPiglinAngerTarget(brute, target);
	}

	private static boolean hasPiglinAngerTarget(AbstractPiglin piglin, LivingEntity target) {
		return piglin.getBrain().getMemory(MemoryModuleType.ANGRY_AT)
				.map(target.getUUID()::equals).orElse(false);
	}

	private static PmbPiglinCompatRules piglinCompatRules(AbstractPiglin piglin) {
		if (!(piglin.level() instanceof ServerLevel level)) {
			return PmbPiglinCompatRules.DEFAULT;
		}
		String factionId = PmbFactionResolver.factionOf(piglin);
		PmbFactionDefinition definition = factionId == null
				? null : PmbFactionSavedData.get(level.getServer()).get(factionId);
		return definition == null ? PmbPiglinCompatRules.DEFAULT : definition.vanillaCompatRules().piglin();
	}

	private static boolean hasValidFaction(AbstractPiglin piglin) {
		if (!(piglin.level() instanceof ServerLevel level)) return false;
		String id = PmbFactionResolver.factionOf(piglin);
		return id != null && PmbFactionSavedData.get(level.getServer()).get(id) != null;
	}

	private static boolean defersCombatForAdmiration(Mob mob) {
		return mob instanceof Piglin piglin && hasValidFaction(piglin)
				&& piglinCompatRules(piglin).greed()
				&& piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM);
	}

	private static boolean preservesPiglinAvoidanceAgainst(Mob mob, LivingEntity target) {
		if (!(mob instanceof Piglin piglin) || target == null || !piglinCompatRules(piglin).avoidance()) {
			return false;
		}
		LivingEntity vanillaAvoid = getRegisteredMemory(piglin.getBrain(), MemoryModuleType.AVOID_TARGET);
		return vanillaAvoid == target || PiglinAi.isZombified(target);
	}

	private static <T> T getRegisteredMemory(Brain<?> brain, MemoryModuleType<T> type) {
		return brain.checkMemory(type, MemoryStatus.REGISTERED)
				? brain.getMemory(type).orElse(null) : null;
	}

	private static boolean isInRange(Mob source, LivingEntity target, double range) {
		return isCombatTargetable(target) && target.level() == source.level()
				&& source.distanceToSqr(target) <= range * range;
	}

	public static boolean isCombatTargetable(LivingEntity target) {
		return target != null && target.isAlive() && (!(target instanceof Player player)
				|| !player.isCreative() && !player.isSpectator());
	}

	private static void clearRuntimeState(Mob mob, PmbFactionMobState state) {
		state.pmb$setFactionCombatTarget(null);
		state.pmb$setFactionAvoidTarget(null);
		state.pmb$setFactionAvoiding(false);
		state.pmb$setFactionGroupRevenge(false);
		state.pmb$setFactionAdmiring(false);
		state.pmb$setLastBrainCombatTarget(null);
		state.pmb$setSuppressedTarget(null);
		state.pmb$setSuppressedPosition(null);
	}

	private static void observePiglinUnreachable(Piglin piglin, PmbFactionDefinition definition) {
		if (piglin.getBrain().hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)
				|| ((PmbFactionMobState) piglin).pmb$wasFactionAdmiring()) return;
		if (!definition.vanillaCompatRules().piglin().unreachableGiveUp()) return;
		PmbFactionMobState state = (PmbFactionMobState) piglin;
		LivingEntity previous = state.pmb$getLastBrainCombatTarget();
		if (previous == null || state.pmb$getFactionCombatTarget() != previous
				|| getRegisteredMemory(piglin.getBrain(), MemoryModuleType.ATTACK_TARGET) != null) return;
		Long since = getRegisteredMemory(piglin.getBrain(), MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		if (since == null || piglin.level().getGameTime() - since <= 200 || canActuallyShoot(piglin, previous)) return;
		state.pmb$setSuppressedTarget(previous);
		state.pmb$setSuppressedPosition(previous.position());
		state.pmb$setSuppressedUntil(piglin.level().getGameTime() + 60);
		PmbBrainAiDebug.event(piglin, "PMB_unreachable_give_up", () ->
				"target=" + previous.getId() + " cantReachAge=" + (piglin.level().getGameTime() - since)
						+ " suppressTicks=60");
		state.pmb$setFactionCombatTarget(null);
		state.pmb$setFactionGroupRevenge(false);
		state.pmb$setLastBrainCombatTarget(null);
		if (piglin.getTarget() == previous) piglin.setTarget(null);
	}

	private static boolean canActuallyShoot(Piglin piglin, LivingEntity target) {
		if (piglin.getMainHandItem().is(net.minecraft.world.item.Items.CROSSBOW)
				&& piglin.distanceToSqr(target) <= 16.0D * 16.0D
				&& piglin.getSensing().hasLineOfSight(target)) return true;
		var bow = ((PmbAiHolder) piglin).pmb$getAiData().bow();
		return bow.isEnabled() && (piglin.getMainHandItem().is(net.minecraft.world.item.Items.BOW)
				|| piglin.getOffhandItem().is(net.minecraft.world.item.Items.BOW))
				&& (!bow.doConsume() || PmbAmmoAccess.find(piglin, bow.ammoSource(),
						((net.minecraft.world.item.BowItem) net.minecraft.world.item.Items.BOW)
								.getAllSupportedProjectiles()) != null)
				&& bow.canShootAtDistance(piglin.distanceTo(target))
				&& (!bow.requireEyeSight() || piglin.getSensing().hasLineOfSight(target));
	}

	private static boolean isSuppressedPiglinTarget(Mob mob, LivingEntity target) {
		if (!(mob instanceof Piglin piglin) || target == null) return false;
		PmbFactionMobState state = (PmbFactionMobState) mob;
		if (state.pmb$getSuppressedTarget() != target) return false;
		if (!piglinCompatRules(piglin).unreachableGiveUp()
				|| mob.level().getGameTime() >= state.pmb$getSuppressedUntil()
				|| state.pmb$getSuppressedPosition() == null
				|| state.pmb$getSuppressedPosition().distanceToSqr(target.position()) > 16.0D
				|| canActuallyShoot(piglin, target)) {
			state.pmb$setSuppressedTarget(null);
			state.pmb$setSuppressedPosition(null);
			return false;
		}
		return true;
	}

	private static void clearSuppressionOnFreshAttack(Mob mob, LivingEntity attacker) {
		if (mob instanceof Piglin && attacker != null) {
			PmbFactionMobState state = (PmbFactionMobState) mob;
			if (state.pmb$getSuppressedTarget() == attacker) state.pmb$setSuppressedTarget(null);
		}
	}
}
