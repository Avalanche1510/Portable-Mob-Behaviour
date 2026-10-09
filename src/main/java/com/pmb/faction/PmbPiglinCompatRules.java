package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbPiglinCompatRules(
		boolean greed,
		boolean guarding,
		boolean avoidance,
		boolean unreachableGiveUp,
		boolean admiringPausesActiveSkills,
		String hurtAdmirationPolicy,
		int hurtAdmirationDisableTicks,
		String hurtAdmiredItemPolicy) {
	public static final PmbPiglinCompatRules DEFAULT = new PmbPiglinCompatRules(
			true, true, true, true, true, "vanilla", 400, "drop");
	private static final Codec<Integer> ADMIRATION_TICKS = Codec.intRange(0, 72000);
	private static final Codec<String> HURT_ADMIRATION_POLICY = Codec.STRING.validate(value ->
			java.util.Set.of("vanilla", "combat", "continue").contains(value)
					? com.mojang.serialization.DataResult.success(value)
					: com.mojang.serialization.DataResult.error(() -> "Invalid hurtAdmirationPolicy: " + value));
	private static final Codec<String> HURT_ADMIRED_ITEM_POLICY = Codec.STRING.validate(value ->
			java.util.Set.of("drop", "store").contains(value)
					? com.mojang.serialization.DataResult.success(value)
					: com.mojang.serialization.DataResult.error(() -> "Invalid hurtAdmiredItemPolicy: " + value));

	public static final Codec<PmbPiglinCompatRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("greed", true).forGetter(PmbPiglinCompatRules::greed),
			Codec.BOOL.optionalFieldOf("guarding", true).forGetter(PmbPiglinCompatRules::guarding),
			Codec.BOOL.optionalFieldOf("avoidance", true).forGetter(PmbPiglinCompatRules::avoidance),
			Codec.BOOL.optionalFieldOf("unreachableGiveUp", true).forGetter(PmbPiglinCompatRules::unreachableGiveUp),
			Codec.BOOL.optionalFieldOf("admiringPausesActiveSkills", true)
					.forGetter(PmbPiglinCompatRules::admiringPausesActiveSkills),
			HURT_ADMIRATION_POLICY.optionalFieldOf("hurtAdmirationPolicy", "vanilla")
					.forGetter(PmbPiglinCompatRules::hurtAdmirationPolicy),
			ADMIRATION_TICKS.optionalFieldOf("hurtAdmirationDisableTicks", 400)
					.forGetter(PmbPiglinCompatRules::hurtAdmirationDisableTicks),
			HURT_ADMIRED_ITEM_POLICY.optionalFieldOf("hurtAdmiredItemPolicy", "drop")
					.forGetter(PmbPiglinCompatRules::hurtAdmiredItemPolicy)
	).apply(instance, PmbPiglinCompatRules::new));

	public PmbPiglinCompatRules withGreed(boolean value) { return new PmbPiglinCompatRules(value, guarding, avoidance, unreachableGiveUp, admiringPausesActiveSkills, hurtAdmirationPolicy, hurtAdmirationDisableTicks, hurtAdmiredItemPolicy); }
	public PmbPiglinCompatRules withGuarding(boolean value) { return new PmbPiglinCompatRules(greed, value, avoidance, unreachableGiveUp, admiringPausesActiveSkills, hurtAdmirationPolicy, hurtAdmirationDisableTicks, hurtAdmiredItemPolicy); }
	public PmbPiglinCompatRules withAvoidance(boolean value) { return new PmbPiglinCompatRules(greed, guarding, value, unreachableGiveUp, admiringPausesActiveSkills, hurtAdmirationPolicy, hurtAdmirationDisableTicks, hurtAdmiredItemPolicy); }
	public PmbPiglinCompatRules withUnreachableGiveUp(boolean value) { return new PmbPiglinCompatRules(greed, guarding, avoidance, value, admiringPausesActiveSkills, hurtAdmirationPolicy, hurtAdmirationDisableTicks, hurtAdmiredItemPolicy); }
	public PmbPiglinCompatRules withAdmiringPausesActiveSkills(boolean value) { return new PmbPiglinCompatRules(greed, guarding, avoidance, unreachableGiveUp, value, hurtAdmirationPolicy, hurtAdmirationDisableTicks, hurtAdmiredItemPolicy); }
	public PmbPiglinCompatRules withHurtAdmirationPolicy(String value) { return new PmbPiglinCompatRules(greed, guarding, avoidance, unreachableGiveUp, admiringPausesActiveSkills, value, hurtAdmirationDisableTicks, hurtAdmiredItemPolicy); }
	public PmbPiglinCompatRules withHurtAdmirationDisableTicks(int value) { return new PmbPiglinCompatRules(greed, guarding, avoidance, unreachableGiveUp, admiringPausesActiveSkills, hurtAdmirationPolicy, value, hurtAdmiredItemPolicy); }
	public PmbPiglinCompatRules withHurtAdmiredItemPolicy(String value) { return new PmbPiglinCompatRules(greed, guarding, avoidance, unreachableGiveUp, admiringPausesActiveSkills, hurtAdmirationPolicy, hurtAdmirationDisableTicks, value); }

	public int effectiveHurtAdmirationDisableTicks() {
		return hurtAdmirationPolicy.equals("continue") ? 0 : hurtAdmirationDisableTicks;
	}
}
