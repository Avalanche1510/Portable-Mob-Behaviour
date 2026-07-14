package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbPiglinCompatRules(
		boolean greed,
		boolean guarding,
		boolean avoidance) {
	public static final PmbPiglinCompatRules DEFAULT = new PmbPiglinCompatRules(true, true, true);

	public static final Codec<PmbPiglinCompatRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("greed", true).forGetter(PmbPiglinCompatRules::greed),
			Codec.BOOL.optionalFieldOf("guarding", true).forGetter(PmbPiglinCompatRules::guarding),
			Codec.BOOL.optionalFieldOf("avoidance", true).forGetter(PmbPiglinCompatRules::avoidance)
	).apply(instance, PmbPiglinCompatRules::new));
}
