package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbFactionRules(
		boolean allowInternalConflict,
		boolean allowFriendlyFire,
		boolean groupRevenge,
		boolean overrideTeamRules,
		PmbFactionAttitude defaultAttitude) {
	public static final PmbFactionRules DEFAULT = new PmbFactionRules(false, true, false, true,
			PmbFactionAttitude.NEUTRAL);

	public static final Codec<PmbFactionRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("allowInternalConflict", false).forGetter(PmbFactionRules::allowInternalConflict),
			Codec.BOOL.optionalFieldOf("allowFriendlyFire", true).forGetter(PmbFactionRules::allowFriendlyFire),
			Codec.BOOL.optionalFieldOf("groupRevenge", false).forGetter(PmbFactionRules::groupRevenge),
			Codec.BOOL.optionalFieldOf("overrideTeamRules", true).forGetter(PmbFactionRules::overrideTeamRules),
			Codec.STRING.optionalFieldOf("defaultAttitude", "neutral")
					.xmap(PmbFactionAttitude::parseOrNeutral, PmbFactionAttitude::serializedName)
					.forGetter(PmbFactionRules::defaultAttitude)
	).apply(instance, PmbFactionRules::new));
}
