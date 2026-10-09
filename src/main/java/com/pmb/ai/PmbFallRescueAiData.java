package com.pmb.ai;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getBooleanOr;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;

/** Composite conditional for planned and accidental dangerous falls. */
public final class PmbFallRescueAiData implements PmbSkillConfigData {
	public static final String TAG = "fall_rescue";
	public static final List<String> DEFAULT_METHODS = List.of("wind_charge", "ender_pearl");
	private boolean configured;
	private final Set<String> explicit = new LinkedHashSet<>();
	private boolean enabled;
	private float minRescueDamage = 1.0F;
	private boolean singleMethodPerFall = true;
	private List<String> methods = DEFAULT_METHODS;
	private final Method wind = new Method(false);
	private final Method pearl = new Method(true);

	@Override public boolean isConfigured() { return configured; }
	@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicit); }
	public boolean isEnabled() { return configured && enabled; }
	public float minRescueDamage() { return minRescueDamage; }
	public boolean singleMethodPerFall() { return singleMethodPerFall; }
	public List<String> methods() { return methods; }
	public Method wind() { return wind; }
	public Method pearl() { return pearl; }

	public void read(ValueInput parent) {
		Optional<ValueInput> child = parent.child(TAG);
		if (child.isEmpty()) { clear(); return; }
		ValueInput input = child.get(); configured = true; explicit.clear();
		enabled = getBooleanOr(input, "enable", false);
		minRescueDamage = clamp(getFloatOr(input, "minRescueDamage", 1), 0, 1000);
		singleMethodPerFall = getBooleanOr(input, "singleMethodPerFall", true);
		if (input.contains("enable")) explicit.add("enable");
		if (input.contains("minRescueDamage")) explicit.add("minRescueDamage");
		if (input.contains("singleMethodPerFall")) explicit.add("singleMethodPerFall");
		methods = readMethods(input);
		if (input.contains("methods")) explicit.add("methods");
		wind.clear();
		pearl.clear();
		input.child("wind_charge").ifPresent(wind::read);
		input.child("ender_pearl").ifPresent(pearl::read);
	}
	private List<String> readMethods(ValueInput input) {
		var list = input.list("methods", Codec.STRING);
		if (list.isEmpty()) return DEFAULT_METHODS;
		List<String> result = new ArrayList<>();
		for (String id : list.get()) if ((id.equals("wind_charge") || id.equals("ender_pearl")) && !result.contains(id)) result.add(id);
		return List.copyOf(result);
	}
	public void write(ValueOutput output) {
		if (explicit.contains("enable")) output.putBoolean("enable", enabled);
		if (explicit.contains("minRescueDamage")) output.putFloat("minRescueDamage", minRescueDamage);
		if (explicit.contains("singleMethodPerFall")) output.putBoolean("singleMethodPerFall", singleMethodPerFall);
		if (explicit.contains("methods")) output.store("methods", Codec.STRING.listOf(), methods);
		if (wind.isConfigured()) wind.write(output.child("wind_charge"));
		if (pearl.isConfigured()) pearl.write(output.child("ender_pearl"));
	}
	@Override public void resetRuntime() { wind.cooldown = 0; pearl.cooldown = 0; }
	public void tickCooldowns() { wind.tick(); pearl.tick(); }
	int windCooldownRemaining() { return wind.cooldown; }
	int pearlCooldownRemaining() { return pearl.cooldown; }
	void restoreCooldowns(int elapsed, int windRemaining, int pearlRemaining) {
		wind.restore(elapsed, windRemaining); pearl.restore(elapsed, pearlRemaining);
	}
	public void clear() {
		configured = false; explicit.clear(); enabled = false; minRescueDamage = 1; singleMethodPerFall = true; methods = DEFAULT_METHODS;
		wind.clear(); pearl.clear();
	}

	public static final class Method implements PmbSkillConfigData {
		private final boolean pearl;
		private final Set<String> explicit = new LinkedHashSet<>();
		private boolean configured;
		private boolean enabled = true;
		private final PmbActivationSources fetchSource = new PmbActivationSources();
		private PmbPreferredHand preferredHand = PmbPreferredHand.OFF;
		private boolean doConsume;
		private boolean doTeleportDamage;
		private float successChance = 1;
		private int cooldownTicks = 20;
		private int randomCooldownBias;
		private int rescueLeadTicks = 4;
		private float maxThrowPower = 1;
		private float throwAccuracy = 1;
		private int cooldown;
		private Method(boolean pearl) { this.pearl = pearl; }
		@Override public boolean isConfigured() { return configured; }
		@Override public Set<String> explicitFields() { return new LinkedHashSet<>(explicit); }
		public boolean isEnabled() { return enabled; }
		public PmbActivationSources fetchSource() { return fetchSource; }
		public PmbPreferredHand preferredHand() { return preferredHand; }
		public boolean doConsume() { return doConsume; }
		public boolean doTeleportDamage() { return doTeleportDamage; }
		public float successChance() { return successChance; }
		public int rescueLeadTicks() { return rescueLeadTicks; }
		public float maxThrowPower() { return maxThrowPower; }
		public float throwAccuracy() { return throwAccuracy; }
		public boolean ready() { return cooldown <= 0; }
		public void attempted(net.minecraft.util.RandomSource random) { cooldown = PmbSkillTiming.cooldown(random, cooldownTicks, randomCooldownBias); }
		private void tick() { if (cooldown > 0) cooldown--; }
		void restore(int elapsed, int remaining) {
			cooldown = Math.max(0, Math.min(cooldownTicks + randomCooldownBias, Math.max(0, remaining)) - elapsed);
		}
		private void read(ValueInput input) {
			configured = true; explicit.clear();
			for (String key : java.util.List.of("enable", "preferredHand", "FetchSource", "doConsume", "doTeleportDamage",
					"successChance", "cooldownTicks", "randomCooldownBias", "rescueLeadTicks", "maxThrowPower", "throwAccuracy"))
				if (input.contains(key)) explicit.add(key);
			enabled = getBooleanOr(input, "enable", true);
			preferredHand = PmbPreferredHand.parse(input.getStringOr("preferredHand", ""), PmbPreferredHand.OFF);
			fetchSource.read(input); doConsume = getBooleanOr(input, "doConsume", false);
			doTeleportDamage = pearl && getBooleanOr(input, "doTeleportDamage", false);
			successChance = clamp(getFloatOr(input, "successChance", 1), 0, 1);
			cooldownTicks = clamp(getIntOr(input, "cooldownTicks", 20), 0, 72000);
			randomCooldownBias = clamp(getIntOr(input, "randomCooldownBias", 0), 0, 1200);
			rescueLeadTicks = clamp(getIntOr(input, "rescueLeadTicks", 4), 0, 1200);
			maxThrowPower = clamp(getFloatOr(input, "maxThrowPower", 1), .1F, 10);
			throwAccuracy = clamp(getFloatOr(input, "throwAccuracy", 1), 0, 1); cooldown = 0;
		}
		private void write(ValueOutput output) {
			if (explicit.contains("enable")) output.putBoolean("enable", enabled);
			if (explicit.contains("preferredHand")) output.putString("preferredHand", preferredHand.value());
			if (explicit.contains("FetchSource")) fetchSource.write(output);
			if (explicit.contains("doConsume")) output.putBoolean("doConsume", doConsume);
			if (pearl && explicit.contains("doTeleportDamage")) output.putBoolean("doTeleportDamage", doTeleportDamage);
			if (explicit.contains("successChance")) output.putFloat("successChance", successChance);
			if (explicit.contains("cooldownTicks")) output.putInt("cooldownTicks", cooldownTicks);
			if (explicit.contains("randomCooldownBias")) output.putInt("randomCooldownBias", randomCooldownBias);
			if (explicit.contains("rescueLeadTicks")) output.putInt("rescueLeadTicks", rescueLeadTicks);
			if (pearl && explicit.contains("maxThrowPower")) output.putFloat("maxThrowPower", maxThrowPower);
			if (pearl && explicit.contains("throwAccuracy")) output.putFloat("throwAccuracy", throwAccuracy);
		}
		@Override public void resetRuntime() { cooldown = 0; }
		private void clear() { configured = false; explicit.clear(); enabled = true; fetchSource.clear(); preferredHand = PmbPreferredHand.OFF; doConsume = false;
			doTeleportDamage = false; successChance = 1; cooldownTicks = 20; randomCooldownBias = 0;
			rescueLeadTicks = 4; maxThrowPower = 1; throwAccuracy = 1; cooldown = 0; }
	}
}
