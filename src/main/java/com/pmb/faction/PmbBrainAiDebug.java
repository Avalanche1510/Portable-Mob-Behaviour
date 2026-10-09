package com.pmb.faction;

import com.pmb.PortableMobBehaviour;
import com.pmb.ai.PmbSchedulerHolder;
import com.pmb.ai.PmbSkillDebugSnapshot;
import com.pmb.ai.PmbSkillScheduler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinBrute;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

/** Explicitly enabled, high-volume server-side Brain diagnosis. */
public final class PmbBrainAiDebug {
	private static final Map<Mob, String> LAST = new WeakHashMap<>();

	private PmbBrainAiDebug() {}

	public static boolean enabled(Mob mob) {
		if (!(mob instanceof Piglin || mob instanceof PiglinBrute || mob instanceof Hoglin)
				|| !(mob.level() instanceof ServerLevel level)) return false;
		String faction = PmbFactionResolver.factionOf(mob);
		if (faction == null) return false;
		PmbFactionDefinition definition = PmbFactionSavedData.get(level.getServer()).get(faction);
		return definition != null && definition.vanillaCompatRules().brainAiDebug();
	}

	public static void event(Mob mob, String name, Supplier<String> details) {
		if (!enabled(mob)) return;
		PortableMobBehaviour.LOGGER.info("PMB Brain event tick={} entity={} uuid={} source={} {}",
				mob.level().getGameTime(), mob.getId(), mob.getUUID(), name, details.get());
	}

	public static void tick(Mob mob) {
		if (!(mob instanceof Piglin || mob instanceof PiglinBrute || mob instanceof Hoglin)) return;
		if (!enabled(mob)) {
			if (!LAST.isEmpty()) LAST.remove(mob);
			return;
		}
		ServerLevel level = (ServerLevel) mob.level();
		Brain<?> brain = mob.getBrain();
		PmbFactionMobState faction = (PmbFactionMobState) mob;
		PmbSkillScheduler scheduler = ((PmbSchedulerHolder) mob).pmb$getSkillScheduler();
		PmbSkillDebugSnapshot skill = scheduler.lastDebugSnapshot();
		String brainState = "activity=" + brain.getActiveNonCoreActivity().orElse(null)
				+ " attack=" + entity(memory(brain, MemoryModuleType.ATTACK_TARGET))
				+ " avoid=" + entity(memory(brain, MemoryModuleType.AVOID_TARGET))
				+ " walk=" + memory(brain, MemoryModuleType.WALK_TARGET)
				+ " cantReachSince=" + memory(brain, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
				+ " angryAt=" + memory(brain, MemoryModuleType.ANGRY_AT)
				+ " admiring=" + memory(brain, MemoryModuleType.ADMIRING_ITEM)
				+ " admiringDisabled=" + memory(brain, MemoryModuleType.ADMIRING_DISABLED)
				+ " pacified=" + memory(brain, MemoryModuleType.PACIFIED);
		String last = LAST.put(mob, brainState);
		if (last != null && !last.equals(brainState))
			event(mob, "observed_transition", () -> "old={" + last + "} new={" + brainState + "}");
		PortableMobBehaviour.LOGGER.info(
				"PMB Brain snapshot tick={} dimension={} type={} id={} uuid={} faction={} {} mobTarget={} aggressive={} factionCombat={} groupRevenge={} factionAvoid={} strategy={} skillPhases={} bowUsing={} skillSnapshot={}",
				level.getGameTime(), level.dimension().identifier(), BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()),
				mob.getId(), mob.getUUID(), PmbFactionResolver.factionOf(mob), brainState,
				entity(mob.getTarget()), mob.isAggressive(), entity(faction.pmb$getFactionCombatTarget()),
				faction.pmb$isFactionGroupRevenge(), entity(faction.pmb$getFactionAvoidTarget()),
				scheduler.strategy(), scheduler.activePhases().keySet(),
				mob.isUsingItem() && mob.getUseItem().is(net.minecraft.world.item.Items.BOW),
				skill == null ? "<unconfigured>" : skill);
	}

	private static <T> T memory(Brain<?> brain, MemoryModuleType<T> type) {
		return brain.checkMemory(type, MemoryStatus.REGISTERED) ? brain.getMemory(type).orElse(null) : null;
	}

	private static String entity(LivingEntity entity) {
		return entity == null ? "null" : entity.getId() + "/" + entity.getUUID();
	}
}
