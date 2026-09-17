package com.pmb.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Mob;

/** Immutable, server-only diagnostic copy of one completed PMB skill tick. */
public record PmbSkillDebugSnapshot(int entityId, UUID entityUuid, String entityType, int tick,
		PmbSkillScheduler.Strategy strategy, UUID authorityTarget, List<SkillState> skills,
		List<CandidateAttempt> attempts, Map<PmbSkillScheduler.Resource, String> sustainedClaims,
		Map<PmbSkillScheduler.Resource, String> allClaims, Map<String, String> bindings,
		PmbMovementController.DebugSnapshot movement, boolean ordinaryMeleeSuppressed,
		List<String> meleeSuppressionReasons, boolean usingItem, String usedItemHand) {
	public PmbSkillDebugSnapshot {
		skills = List.copyOf(skills);
		attempts = List.copyOf(attempts);
		sustainedClaims = Map.copyOf(sustainedClaims);
		allClaims = Map.copyOf(allClaims);
		bindings = Map.copyOf(bindings);
		meleeSuppressionReasons = List.copyOf(meleeSuppressionReasons);
	}
	public enum AttemptStatus { PRECLAIM_REJECTED, RESOURCE_BLOCKED, ADMITTED, FINAL_REJECTED, EXECUTED }
	public record SkillState(String id, boolean configured, boolean enabled, boolean activeThisTick,
			String cooldown, String state) {}
	public record CandidateAttempt(String owner, String label, PmbSkillScheduler.Category category, int rank,
			AttemptStatus status, List<PmbSkillScheduler.Resource> resources,
			PmbSkillScheduler.Resource blockedResource, String blocker) {
		public CandidateAttempt { resources = List.copyOf(resources); }
		public static CandidateAttempt preClaimRejected(String owner, String label,
				PmbSkillScheduler.Category category, int rank) {
			return new CandidateAttempt(owner, label, category, rank, AttemptStatus.PRECLAIM_REJECTED,
					List.of(), null, null);
		}
		public static CandidateAttempt resourceBlocked(String owner, String label,
				PmbSkillScheduler.Category category, int rank, PmbSkillScheduler.Resource[] resources,
				PmbSkillScheduler.Resource blockedResource, String blocker) {
			return new CandidateAttempt(owner, label, category, rank, AttemptStatus.RESOURCE_BLOCKED,
					List.of(resources.clone()), blockedResource, blocker);
		}
		public static CandidateAttempt admitted(String owner, String label, PmbSkillScheduler.Category category,
				int rank, PmbSkillScheduler.Resource[] resources) {
			return new CandidateAttempt(owner, label, category, rank, AttemptStatus.ADMITTED,
					List.of(resources.clone()), null, null);
		}
		CandidateAttempt completed(boolean executed) {
			return new CandidateAttempt(owner, label, category, rank,
					executed ? AttemptStatus.EXECUTED : AttemptStatus.FINAL_REJECTED,
					resources, blockedResource, blocker);
		}
	}

	static PmbSkillDebugSnapshot capture(Mob mob, PmbSkillScheduler scheduler, PmbAiData ai,
			List<CandidateAttempt> attempts, Map<PmbSkillScheduler.Resource, String> sustained,
			Map<PmbSkillScheduler.Resource, String> allClaims,
			Map<String, PmbSkillItemAccess.ActionBinding> bindings,
			PmbMovementController.DebugSnapshot movement) {
		List<String> executed = attempts.stream().filter(a -> a.status() == AttemptStatus.EXECUTED)
				.map(CandidateAttempt::owner).toList();
		List<SkillState> skills = List.of(
				new SkillState("shield", ai.shield().isConfigured(), ai.shield().isEnabled(),
						active("shield", sustained, executed),
						"check=" + ai.shield().cooldownRemaining() + ",disabled=" + ai.shield().disabledCooldownRemaining(),
						"useTicks=" + ai.shield().useTicks() + ",vulnerableTicks=" + ai.shield().vulnerableTicks()),
				new SkillState("wind_charge", ai.windCharge().isConfigured(), ai.windCharge().isEnabled(),
						active("wind", sustained, executed),
						"throw=" + ai.windCharge().throwCooldownRemaining() + ",bounce=" + ai.windCharge().bounceCooldownRemaining(), "-"),
				new SkillState("mace", ai.mace().isConfigured(), ai.mace().isEnabled(),
						active("mace", sustained, executed), "smash=" + ai.mace().cooldownRemaining(), "-"),
				new SkillState("bow", ai.bow().isConfigured(), ai.bow().isEnabled(),
						active("bow", sustained, executed),
						"line=" + ai.bow().lineCooldownRemaining() + ",arc=" + ai.bow().arcCooldownRemaining(), "-"),
				new SkillState("ender_pearl", ai.enderPearl().isConfigured(), ai.enderPearl().isEnabled(),
						active("pearl", sustained, executed), "throw=" + ai.enderPearl().cooldownRemaining(), "-"));
		Map<String, String> bindingCopies = new java.util.TreeMap<>();
		bindings.forEach((key, value) -> bindingCopies.put(key, value.debugSummary()));
		List<String> suppression = new ArrayList<>();
		if (!mob.isAlive()) suppression.add("dead");
		if (ai.shield().isVulnerable()) suppression.add("shield_vulnerability");
		if (PmbSkillItemAccess.shouldSuppressMeleeForBow(mob)) suppression.add("bow");
		if (PmbSkillItemAccess.shouldSuppressUnscheduledFallingMace(mob)) suppression.add("falling_mace");
		return new PmbSkillDebugSnapshot(mob.getId(), mob.getUUID(),
				BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString(), mob.tickCount,
				scheduler.strategy(), scheduler.authorityTarget(), skills, List.copyOf(attempts),
				Map.copyOf(new EnumMap<>(sustained)), Map.copyOf(new EnumMap<>(allClaims)),
				Map.copyOf(bindingCopies), movement, !suppression.isEmpty(), List.copyOf(suppression),
				mob.isUsingItem(), mob.isUsingItem() ? mob.getUsedItemHand().toString() : "-");
	}

	private static boolean active(String owner, Map<PmbSkillScheduler.Resource, String> sustained,
			List<String> executed) {
		return sustained.containsValue(owner) || executed.contains(owner);
	}

	public String formatForLog(int currentTick) {
		return PmbSkillDebugFormatter.formatForLog(this, currentTick);
	}

	public Component formatForChat(int currentTick) {
		return PmbSkillDebugFormatter.formatForChat(this, currentTick);
	}
}
