package com.pmb.faction;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

public final class PmbFactionAi {
	private static final int SCAN_INTERVAL = 10;
	private static final int FLEE_REPATH_INTERVAL = 10;

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

		PmbFactionAttitude attitude = PmbFactionResolver.attitude(
				PmbFactionSavedData.get(level.getServer()), piglin, vanillaAvoid);
		boolean conflictsWithFactionCombat = state.pmb$getFactionCombatTarget() == vanillaAvoid
				|| attitude == PmbFactionAttitude.HOSTILE;
		if (conflictsWithFactionCombat || !definition.vanillaCompatRules().piglin().avoidance()) {
			brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
			brain.eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}

	private static LivingEntity resolvePiglinAttackTarget(AbstractPiglin piglin, LivingEntity vanillaTarget) {
		PmbFactionMobState state = (PmbFactionMobState) piglin;
		LivingEntity factionTarget = state.pmb$getFactionCombatTarget();
		if (factionTarget != null && factionTarget.isAlive() && factionTarget.level() == piglin.level()
				&& !preservesPiglinAvoidanceAgainst(piglin, factionTarget)) {
			return factionTarget;
		}
		if (vanillaTarget == null || !(piglin.level() instanceof ServerLevel level)) {
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
		PmbFactionMobState state = (PmbFactionMobState) mob;
		if (preservesPiglinAvoidanceAgainst(mob, attacker)) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionGroupRevenge(false);
			return;
		}
		state.pmb$setFactionAvoidTarget(null);
		state.pmb$setFactionCombatTarget(attacker);
		state.pmb$setFactionGroupRevenge(true);
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
		if (!isInRange(mob, combat, followRange)) {
			state.pmb$setFactionCombatTarget(null);
			state.pmb$setFactionGroupRevenge(false);
		} else {
			PmbFactionAttitude attitude = PmbFactionResolver.attitude(data, mob, combat);
			if (preservesPiglinAvoidanceAgainst(mob, combat)
					|| attitude == PmbFactionAttitude.ALLIED || (attitude != PmbFactionAttitude.HOSTILE
					&& attitude != PmbFactionAttitude.NEUTRAL && !state.pmb$isFactionGroupRevenge())) {
				state.pmb$setFactionCombatTarget(null);
				state.pmb$setFactionGroupRevenge(false);
			}
		}
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
				.filter(target -> mob.getSensing().hasLineOfSight(target))
				.filter(mob::canAttack)
				.min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
		if (hostile != null) {
			state.pmb$setFactionCombatTarget(hostile);
			state.pmb$setFactionGroupRevenge(false);
		} else if (state.pmb$getFactionCombatTarget() != null
				&& PmbFactionResolver.attitude(data, mob, state.pmb$getFactionCombatTarget())
						!= PmbFactionAttitude.NEUTRAL) {
			state.pmb$setFactionCombatTarget(null);
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
			if (!navigationMovesAway(mob, avoid)
					&& (afterVanillaAi || mob.tickCount % FLEE_REPATH_INTERVAL == 0
							|| mob.getNavigation().isDone())) {
				navigateAway(mob, avoid, definition.rules().evasiveSpeedMultiplier());
			}
			return;
		}

		if (state.pmb$wasFactionAvoiding() && !afterVanillaAi) {
			mob.getBrain().eraseMemory(MemoryModuleType.AVOID_TARGET);
			mob.getNavigation().stop();
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
				|| mob instanceof PiglinBrute piglin && hasPiglinAngerTarget(piglin, target);
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

	private static void navigateAway(Mob mob, LivingEntity threat, float speedMultiplier) {
		Vec3 away = mob.position().subtract(threat.position());
		if (away.lengthSqr() < 1.0E-6D) {
			double angle = Math.toRadians(Math.floorMod(mob.getId() * 137, 360));
			away = new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
		}
		away = away.normalize();

		if (mob instanceof PathfinderMob pathfinderMob) {
			Vec3 reachable = DefaultRandomPos.getPosAway(pathfinderMob, 16, 7, threat.position());
			if (reachable != null && isFartherFromThreat(mob, threat, reachable)
					&& mob.getNavigation().moveTo(reachable.x, reachable.y, reachable.z,
					speedMultiplier)) {
				return;
			}
		}

		double[] turns = {0.0D, Math.PI / 4.0D, -Math.PI / 4.0D, Math.PI / 2.0D, -Math.PI / 2.0D};
		for (double turn : turns) {
			double cos = Math.cos(turn);
			double sin = Math.sin(turn);
			double x = away.x * cos - away.z * sin;
			double z = away.x * sin + away.z * cos;
			Vec3 destination = mob.position().add(x * 10.0D, 0.0D, z * 10.0D);
			if (mob.getNavigation().moveTo(destination.x, destination.y, destination.z, speedMultiplier)) {
				return;
			}
		}

		if (mob.getNavigation() instanceof FlyingPathNavigation
				|| mob.getNavigation() instanceof WaterBoundPathNavigation) {
			Vec3 destination = mob.position().add(away.scale(10.0D));
			mob.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, speedMultiplier);
		} else {
			mob.getNavigation().stop();
		}
	}

	private static boolean navigationMovesAway(Mob mob, LivingEntity threat) {
		BlockPos targetPos = mob.getNavigation().getTargetPos();
		if (targetPos == null || mob.getNavigation().isDone()) {
			return false;
		}
		Vec3 destination = Vec3.atCenterOf(targetPos);
		return isFartherFromThreat(mob, threat, destination);
	}

	private static boolean isFartherFromThreat(Mob mob, LivingEntity threat, Vec3 destination) {
		return destination.distanceToSqr(threat.position()) > mob.distanceToSqr(threat) + 4.0D;
	}

	private static boolean isInRange(Mob source, LivingEntity target, double range) {
		return target != null && target.isAlive() && target.level() == source.level()
				&& source.distanceToSqr(target) <= range * range;
	}

	private static void clearRuntimeState(Mob mob, PmbFactionMobState state) {
		state.pmb$setFactionCombatTarget(null);
		state.pmb$setFactionAvoidTarget(null);
		state.pmb$setFactionAvoiding(false);
		state.pmb$setFactionGroupRevenge(false);
	}
}
