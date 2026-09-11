package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbBreezeCompatRules(boolean windChargeNoAnger) {
	public static final PmbBreezeCompatRules DEFAULT = new PmbBreezeCompatRules(true);

	public static final Codec<PmbBreezeCompatRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.optionalFieldOf("windChargeNoAnger", true)
					.forGetter(PmbBreezeCompatRules::windChargeNoAnger)
	).apply(instance, PmbBreezeCompatRules::new));
}
