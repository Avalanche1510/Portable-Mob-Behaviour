package com.pmb.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Stateless presentation for server-log and localized chat copies of a skill snapshot. */
public final class PmbSkillDebugFormatter {
	private static final String PREFIX = "debug.portable-mob-behaviour.";

	private PmbSkillDebugFormatter() {}

	public static String formatForLog(PmbSkillDebugSnapshot snapshot, int currentTick) {
		StringBuilder out = new StringBuilder(1024);
		out.append("PMB SKILL DEBUG\n")
				.append("ENTITY: ").append(snapshot.entityType()).append('#').append(snapshot.entityId())
				.append(" uuid=").append(snapshot.entityUuid())
				.append("\nMELEE: ").append(snapshot.ordinaryMeleeSuppressed() ? "SUPPRESSED" : "AVAILABLE")
				.append(" reasons=").append(snapshot.meleeSuppressionReasons());
		appendSkills(out, snapshot.skills());
		appendAttempts(out, snapshot.attempts());
		appendResources(out, snapshot.sustainedClaims(), snapshot.allClaims());
		appendBindings(out, snapshot.bindings());
		out.append("\nCONTEXT:")
				.append("\n  snapshotTick=").append(snapshot.tick())
				.append(" ageTicks=").append(Math.max(0, currentTick - snapshot.tick()))
				.append(" strategy=").append(snapshot.strategy())
				.append(" authorityTarget=").append(snapshot.authorityTarget())
				.append("\n  itemUse={using=").append(snapshot.usingItem())
				.append(",hand=").append(snapshot.usedItemHand()).append('}')
				.append("\n  movement=").append(snapshot.movement());
		return out.toString();
	}

	public static Component formatForChat(PmbSkillDebugSnapshot snapshot, int currentTick) {
		MutableComponent out = Component.empty();
		line(out, tr("title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		line(out, heading("entity").append(Component.literal(": " + snapshot.entityType() + '#'
				+ snapshot.entityId() + " uuid=" + snapshot.entityUuid()).withStyle(ChatFormatting.WHITE)));
		MutableComponent melee = tr(snapshot.ordinaryMeleeSuppressed() ? "melee.suppressed" : "melee.available")
				.withStyle(snapshot.ordinaryMeleeSuppressed() ? ChatFormatting.RED : ChatFormatting.GREEN);
		line(out, heading("melee").append(Component.literal(": ")).append(melee)
				.append(Component.literal(" reasons=" + snapshot.meleeSuppressionReasons()).withStyle(ChatFormatting.GRAY)));

		List<PmbSkillDebugSnapshot.SkillState> enabled = enabledSkills(snapshot.skills());
		if (enabled.isEmpty()) {
			line(out, heading("skills").append(Component.literal(": []").withStyle(ChatFormatting.GRAY)));
		} else {
			line(out, heading("skills").append(Component.literal(":")));
			for (var skill : enabled) {
				MutableComponent state = tr(skill.activeThisTick() ? "skill.active" : "skill.ready")
						.withStyle(skill.activeThisTick() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
				line(out, Component.literal("  " + skill.id() + " [").withStyle(ChatFormatting.WHITE)
						.append(state).append(Component.literal("] cooldown{" + skill.cooldown()
								+ "} state{" + skill.state() + '}').withStyle(ChatFormatting.GRAY)));
			}
		}

		if (snapshot.attempts().isEmpty()) {
			line(out, heading("attempts").append(Component.literal(": []").withStyle(ChatFormatting.GRAY)));
		} else {
			line(out, heading("attempts").append(Component.literal(":")));
			for (var attempt : snapshot.attempts()) line(out, formatAttemptForChat(attempt));
		}

		if (snapshot.sustainedClaims().isEmpty() && snapshot.allClaims().isEmpty()) {
			line(out, heading("resources").append(Component.literal(": {}").withStyle(ChatFormatting.GRAY)));
		} else {
			line(out, heading("resources").append(Component.literal(":")));
			line(out, Component.literal("  sustained=" + formatResources(snapshot.sustainedClaims()))
					.withStyle(ChatFormatting.GRAY));
			line(out, Component.literal("  current=" + formatResources(snapshot.allClaims()))
					.withStyle(ChatFormatting.WHITE));
		}

		if (snapshot.bindings().isEmpty()) {
			line(out, heading("bindings").append(Component.literal(": []").withStyle(ChatFormatting.GRAY)));
		} else {
			line(out, heading("bindings").append(Component.literal(":")));
			for (String binding : formatBindings(snapshot.bindings()))
				line(out, Component.literal("  " + binding).withStyle(ChatFormatting.WHITE));
		}

		line(out, heading("context").append(Component.literal(":")));
		line(out, Component.literal("  snapshotTick=" + snapshot.tick() + " ageTicks="
				+ Math.max(0, currentTick - snapshot.tick()) + " strategy=" + snapshot.strategy()
				+ " authorityTarget=" + snapshot.authorityTarget()).withStyle(ChatFormatting.GRAY));
		line(out, Component.literal("  itemUse={using=" + snapshot.usingItem() + ",hand="
				+ snapshot.usedItemHand() + '}').withStyle(ChatFormatting.GRAY));
		line(out, Component.literal("  movement=" + snapshot.movement()).withStyle(ChatFormatting.GRAY));
		return out;
	}

	public static String formatUnavailableForLog(String entityType, int entityId, Object uuid) {
		return "PMB SKILL DEBUG\nENTITY: " + entityType + '#' + entityId + " uuid=" + uuid
				+ "\nSNAPSHOT: unavailable (no completed configured PMB skill tick)";
	}

	public static Component formatUnavailableForChat(String entityType, int entityId, Object uuid) {
		MutableComponent out = Component.empty();
		line(out, tr("title").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		line(out, heading("entity").append(Component.literal(": " + entityType + '#' + entityId
				+ " uuid=" + uuid).withStyle(ChatFormatting.WHITE)));
		line(out, heading("snapshot").append(Component.literal(": "))
				.append(tr("snapshot.unavailable").withStyle(ChatFormatting.YELLOW)));
		return out;
	}

	private static void appendSkills(StringBuilder out, List<PmbSkillDebugSnapshot.SkillState> skills) {
		List<PmbSkillDebugSnapshot.SkillState> enabled = enabledSkills(skills);
		if (enabled.isEmpty()) {
			out.append("\nSKILLS: []");
			return;
		}
		out.append("\nSKILLS:");
		for (var skill : enabled) out.append("\n  ").append(skill.id()).append(" [")
				.append(skill.activeThisTick() ? "ACTIVE" : "READY").append("] cooldown{")
				.append(skill.cooldown()).append("} state{").append(skill.state()).append('}');
	}

	private static void appendAttempts(StringBuilder out, List<PmbSkillDebugSnapshot.CandidateAttempt> attempts) {
		if (attempts.isEmpty()) {
			out.append("\nLAST ATTEMPTS: []");
			return;
		}
		out.append("\nLAST ATTEMPTS:");
		for (var attempt : attempts) {
			out.append("\n  ").append(attempt.owner()).append('/').append(attempt.label())
					.append(" [").append(statusText(attempt.status())).append("] category=")
					.append(attempt.category()).append(" rank=").append(attempt.rank())
					.append(" resources=").append(attempt.resources());
			if (attempt.blockedResource() != null) out.append(" blocker=").append(attempt.blockedResource())
					.append(':').append(attempt.blocker());
		}
	}

	private static void appendResources(StringBuilder out, Map<PmbSkillScheduler.Resource, String> sustained,
			Map<PmbSkillScheduler.Resource, String> current) {
		if (sustained.isEmpty() && current.isEmpty()) {
			out.append("\nRESOURCES: {}");
			return;
		}
		out.append("\nRESOURCES:")
				.append("\n  sustained=").append(formatResources(sustained))
				.append("\n  current=").append(formatResources(current));
	}

	private static void appendBindings(StringBuilder out, Map<String, String> bindings) {
		List<String> values = formatBindings(bindings);
		if (values.isEmpty()) {
			out.append("\nBINDINGS: []");
			return;
		}
		out.append("\nBINDINGS:");
		for (String binding : values) out.append("\n  ").append(binding);
	}

	private static MutableComponent formatAttemptForChat(PmbSkillDebugSnapshot.CandidateAttempt attempt) {
		String status = statusText(attempt.status());
		ChatFormatting color = switch (attempt.status()) {
			case EXECUTED -> ChatFormatting.GREEN;
			case ADMITTED -> ChatFormatting.YELLOW;
			case RESOURCE_BLOCKED, FINAL_REJECTED -> ChatFormatting.RED;
			case PRECLAIM_REJECTED -> ChatFormatting.GRAY;
		};
		MutableComponent line = Component.literal("  " + attempt.owner() + '/' + attempt.label() + " [")
				.withStyle(ChatFormatting.WHITE)
				.append(tr("status." + status.toLowerCase().replace('+', '.')).withStyle(color))
				.append(Component.literal("] category=" + attempt.category() + " rank=" + attempt.rank()
						+ " resources=" + attempt.resources()).withStyle(ChatFormatting.GRAY));
		if (attempt.blockedResource() != null) line.append(Component.literal(" blocker="
				+ attempt.blockedResource() + ':' + attempt.blocker()).withStyle(ChatFormatting.RED));
		return line;
	}

	private static List<PmbSkillDebugSnapshot.SkillState> enabledSkills(
			List<PmbSkillDebugSnapshot.SkillState> skills) {
		return skills.stream().filter(PmbSkillDebugSnapshot.SkillState::enabled).toList();
	}

	private static String statusText(PmbSkillDebugSnapshot.AttemptStatus status) {
		return switch (status) {
			case EXECUTED -> "ADMITTED+EXECUTED";
			case FINAL_REJECTED -> "ADMITTED+FINAL_REJECTED";
			default -> status.name();
		};
	}

	private static String formatResources(Map<PmbSkillScheduler.Resource, String> claims) {
		List<String> values = new ArrayList<>();
		for (PmbSkillScheduler.Resource resource : PmbSkillScheduler.Resource.values()) {
			String owner = claims.get(resource);
			if (owner != null) values.add(resource + "=" + owner);
		}
		return '{' + String.join(", ", values) + '}';
	}

	private static List<String> formatBindings(Map<String, String> bindings) {
		return bindings.entrySet().stream().sorted(Comparator.comparing(Map.Entry::getKey))
				.map(entry -> entry.getKey() + '=' + entry.getValue()).toList();
	}

	private static MutableComponent heading(String name) {
		return tr(name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
	}

	private static MutableComponent tr(String suffix) {
		return Component.translatable(PREFIX + suffix);
	}

	private static void line(MutableComponent root, Component line) {
		if (!root.getSiblings().isEmpty()) root.append("\n");
		root.append(line);
	}

}
