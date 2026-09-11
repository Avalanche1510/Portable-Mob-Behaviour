package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbVanillaCompatRules(
		PmbPiglinCompatRules piglin,
		PmbBreezeCompatRules breeze) {
	public static final PmbVanillaCompatRules DEFAULT = new PmbVanillaCompatRules(
			PmbPiglinCompatRules.DEFAULT, PmbBreezeCompatRules.DEFAULT);

	public static final Codec<PmbVanillaCompatRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PmbPiglinCompatRules.CODEC.optionalFieldOf("piglin", PmbPiglinCompatRules.DEFAULT)
					.forGetter(PmbVanillaCompatRules::piglin),
			PmbBreezeCompatRules.CODEC.optionalFieldOf("breeze", PmbBreezeCompatRules.DEFAULT)
					.forGetter(PmbVanillaCompatRules::breeze)
	).apply(instance, PmbVanillaCompatRules::new));
}
