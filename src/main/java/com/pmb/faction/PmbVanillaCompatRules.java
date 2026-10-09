package com.pmb.faction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PmbVanillaCompatRules(
		PmbPiglinCompatRules piglin,
		PmbBreezeCompatRules breeze,
		boolean brainAiDebug) {
	public static final PmbVanillaCompatRules DEFAULT = new PmbVanillaCompatRules(
			PmbPiglinCompatRules.DEFAULT, PmbBreezeCompatRules.DEFAULT, false);

	public static final Codec<PmbVanillaCompatRules> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			PmbPiglinCompatRules.CODEC.optionalFieldOf("piglin", PmbPiglinCompatRules.DEFAULT)
					.forGetter(PmbVanillaCompatRules::piglin),
			PmbBreezeCompatRules.CODEC.optionalFieldOf("breeze", PmbBreezeCompatRules.DEFAULT)
					.forGetter(PmbVanillaCompatRules::breeze),
			Codec.BOOL.optionalFieldOf("brainAiDebug", false).forGetter(PmbVanillaCompatRules::brainAiDebug)
	).apply(instance, PmbVanillaCompatRules::new));

	public PmbVanillaCompatRules withPiglin(PmbPiglinCompatRules value) { return new PmbVanillaCompatRules(value, breeze, brainAiDebug); }
	public PmbVanillaCompatRules withBreeze(PmbBreezeCompatRules value) { return new PmbVanillaCompatRules(piglin, value, brainAiDebug); }
	public PmbVanillaCompatRules withBrainAiDebug(boolean value) { return new PmbVanillaCompatRules(piglin, breeze, value); }
}
