package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbFactionRules(
		boolean allowInternalConflict,
		boolean allowFriendlyFire,
		boolean groupRevenge,
		boolean overrideTeamRules,
		PmbFactionAttitude defaultAttitude,
		float evasiveSpeedMultiplier) {
	public static final PmbFactionRules DEFAULT = new PmbFactionRules(false, true, false, true,
			PmbFactionAttitude.NEUTRAL, 1.25F);

	public static final Codec<PmbFactionRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("allowInternalConflict", false).forGetter(PmbFactionRules::allowInternalConflict),
			Codec.BOOL.optionalFieldOf("allowFriendlyFire", true).forGetter(PmbFactionRules::allowFriendlyFire),
			Codec.BOOL.optionalFieldOf("groupRevenge", false).forGetter(PmbFactionRules::groupRevenge),
			Codec.BOOL.optionalFieldOf("overrideTeamRules", true).forGetter(PmbFactionRules::overrideTeamRules),
			Codec.STRING.optionalFieldOf("defaultAttitude", "neutral")
					.xmap(PmbFactionAttitude::parseOrNeutral, PmbFactionAttitude::serializedName)
					.forGetter(PmbFactionRules::defaultAttitude),
			Codec.floatRange(0.0F, 100.0F).optionalFieldOf("evasiveSpeedMultiplier", 1.25F)
					.forGetter(PmbFactionRules::evasiveSpeedMultiplier)
	).apply(instance, PmbFactionRules::new));
}
