package com.pmb.ai;

import java.util.EnumMap;
import java.util.Locale;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import static com.pmb.ai.PmbAiNbtReader.clamp;
import static com.pmb.ai.PmbAiNbtReader.getBooleanOr;
import static com.pmb.ai.PmbAiNbtReader.getFloatOr;
import static com.pmb.ai.PmbAiNbtReader.getIntOr;

/** Persisted policy for PMB movement. Runtime routes and objectives intentionally live elsewhere. */
public final class PmbPathfindingData {
	public static final String TAG = "pathfinding";

	public enum RouteMode {
		VANILLA_ONLY("vanilla_only"), FALLBACK("fallback"), EFFICIENT("efficient");
		private final String serialized;
		RouteMode(String serialized) { this.serialized = serialized; }
		public String serialized() { return serialized; }
		static RouteMode parse(String value, RouteMode fallback) {
			for (RouteMode mode : values()) if (mode.serialized.equals(value)) return mode;
			return fallback;
		}
	}

	public enum DangerousFallPolicy {
		ENABLE("enable"), DISABLE("disable"), CONDITIONAL_REQUIRED("conditional_required");
		private final String serialized;
		DangerousFallPolicy(String serialized) { this.serialized = serialized; }
		public String serialized() { return serialized; }
		static DangerousFallPolicy parse(String value, DangerousFallPolicy fallback) {
			for (DangerousFallPolicy policy : values()) if (policy.serialized.equals(value)) return policy;
			return fallback;
		}
	}

	public record Planner(float planningHorizon, int repathIntervalTicks, int objectiveMemoryTicks,
			float targetMoveThreshold, int conditionalWaitTicks, boolean debugPathParticles) {
		public static final Planner DEFAULT = new Planner(32.0F, 10, 40, 2.0F, 40, false);
	}
	public record Strategy(RouteMode routeMode, DangerousFallPolicy dangerousFallPolicy, boolean requireEyeSight) {}
	public record Retreat(float speedMultiplier, int searchHorizontalRange, int searchVerticalRange,
			int repathIntervalTicks) {
		public static final Retreat DEFAULT = new Retreat(1.25F, 16, 7, 10);
	}

	private Planner planner = Planner.DEFAULT;
	private Retreat retreat = Retreat.DEFAULT;
	private final EnumMap<PmbSkillScheduler.Strategy, Strategy> strategies = defaults();
	private final PmbAirTrackingAiData airTracking = new PmbAirTrackingAiData();
	private boolean configured;
	private boolean explicitStrategy;

	public Planner planner() { return planner; }
	public Retreat retreat() { return retreat; }
	public Strategy strategy(PmbSkillScheduler.Strategy strategy) {
		return strategies.getOrDefault(strategy, strategies.get(PmbSkillScheduler.Strategy.IDLE));
	}
	public PmbAirTrackingAiData airTracking() { return airTracking; }
	public boolean isConfigured() { return configured || airTracking.isConfigured(); }
	public boolean hasExplicitStrategy() { return explicitStrategy; }
	public void setPlanner(Planner value) { planner = value; configured = true; }
	public void setStrategy(PmbSkillScheduler.Strategy key, Strategy value) {
		strategies.put(key, value); configured = true; explicitStrategy = true;
	}
	public void setRetreat(Retreat value) { retreat = value; configured = true; }

	public void read(ValueInput ai) {
		var pathInput = ai.child(TAG);
		if (pathInput.isEmpty()) { clear(); return; }
		configured = true;
		ValueInput path = pathInput.get();
		explicitStrategy = path.child("strategies").isPresent();
		ValueInput plannerInput = path.childOrEmpty("planner");
		planner = new Planner(
				clamp(getFloatOr(plannerInput, "planningHorizon", 32.0F), 1.0F, 256.0F),
				clamp(getIntOr(plannerInput, "repathIntervalTicks", 10), 1, 1200),
				clamp(getIntOr(plannerInput, "objectiveMemoryTicks", 40), 0, 72000),
				clamp(getFloatOr(plannerInput, "targetMoveThreshold", 2.0F), 0.0F, 64.0F),
				clamp(getIntOr(plannerInput, "conditionalWaitTicks", 40), 1, 72000),
				getBooleanOr(plannerInput, "debugPathParticles", false));

		strategies.clear();
		strategies.putAll(defaults());
		ValueInput strategyInput = path.childOrEmpty("strategies");
		for (PmbSkillScheduler.Strategy strategy : configurableStrategies()) {
			ValueInput node = strategyInput.childOrEmpty(key(strategy));
			Strategy fallback = strategies.get(strategy);
			strategies.put(strategy, new Strategy(
					RouteMode.parse(node.getStringOr("routeMode", fallback.routeMode().serialized()), fallback.routeMode()),
					DangerousFallPolicy.parse(node.getStringOr("dangerousFallPolicy",
							fallback.dangerousFallPolicy().serialized()), fallback.dangerousFallPolicy()),
					getBooleanOr(node, "requireEyeSight", fallback.requireEyeSight())));
		}

		airTracking.readDirect(path);
		ValueInput retreatInput = path.childOrEmpty("retreat");
		retreat = new Retreat(
				clamp(getFloatOr(retreatInput, "speedMultiplier", 1.25F), 0.0F, 100.0F),
				clamp(getIntOr(retreatInput, "searchHorizontalRange", 16), 1, 256),
				clamp(getIntOr(retreatInput, "searchVerticalRange", 7), 1, 256),
				clamp(getIntOr(retreatInput, "repathIntervalTicks", 10), 1, 1200));
	}

	public void write(ValueOutput ai) {
		if (!isConfigured()) return;
		ValueOutput path = ai.child(TAG);
		ValueOutput plannerOutput = path.child("planner");
		plannerOutput.putFloat("planningHorizon", planner.planningHorizon());
		plannerOutput.putInt("repathIntervalTicks", planner.repathIntervalTicks());
		plannerOutput.putInt("objectiveMemoryTicks", planner.objectiveMemoryTicks());
		plannerOutput.putFloat("targetMoveThreshold", planner.targetMoveThreshold());
		plannerOutput.putInt("conditionalWaitTicks", planner.conditionalWaitTicks());
		plannerOutput.putBoolean("debugPathParticles", planner.debugPathParticles());
		if (explicitStrategy) {
			ValueOutput strategyOutput = path.child("strategies");
			for (PmbSkillScheduler.Strategy strategy : configurableStrategies()) {
				Strategy value = strategies.get(strategy);
				ValueOutput node = strategyOutput.child(key(strategy));
				node.putString("routeMode", value.routeMode().serialized());
				node.putString("dangerousFallPolicy", value.dangerousFallPolicy().serialized());
				node.putBoolean("requireEyeSight", value.requireEyeSight());
			}
		}
		airTracking.writeDirect(path);
		ValueOutput retreatOutput = path.child("retreat");
		retreatOutput.putFloat("speedMultiplier", retreat.speedMultiplier());
		retreatOutput.putInt("searchHorizontalRange", retreat.searchHorizontalRange());
		retreatOutput.putInt("searchVerticalRange", retreat.searchVerticalRange());
		retreatOutput.putInt("repathIntervalTicks", retreat.repathIntervalTicks());
	}

	public void clear() {
		configured = false;
		explicitStrategy = false;
		planner = Planner.DEFAULT;
		retreat = Retreat.DEFAULT;
		strategies.clear();
		strategies.putAll(defaults());
		airTracking.clear();
	}

	private static EnumMap<PmbSkillScheduler.Strategy, Strategy> defaults() {
		EnumMap<PmbSkillScheduler.Strategy, Strategy> result = new EnumMap<>(PmbSkillScheduler.Strategy.class);
		result.put(PmbSkillScheduler.Strategy.COMBAT,
				new Strategy(RouteMode.FALLBACK, DangerousFallPolicy.CONDITIONAL_REQUIRED, false));
		result.put(PmbSkillScheduler.Strategy.RETREAT,
				new Strategy(RouteMode.FALLBACK, DangerousFallPolicy.CONDITIONAL_REQUIRED, false));
		result.put(PmbSkillScheduler.Strategy.IDLE,
				new Strategy(RouteMode.VANILLA_ONLY, DangerousFallPolicy.DISABLE, false));
		return result;
	}
	private static java.util.List<PmbSkillScheduler.Strategy> configurableStrategies() {
		return java.util.List.of(PmbSkillScheduler.Strategy.COMBAT, PmbSkillScheduler.Strategy.RETREAT,
				PmbSkillScheduler.Strategy.IDLE);
	}
	private static String key(PmbSkillScheduler.Strategy strategy) {
		return strategy.name().toLowerCase(Locale.ROOT);
	}
}
