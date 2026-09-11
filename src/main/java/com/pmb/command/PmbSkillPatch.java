package com.pmb.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.Tag;

public record PmbSkillPatch(Map<String, Tag> values, List<String> keys) {
	public PmbSkillPatch {
		values = Map.copyOf(new LinkedHashMap<>(values));
		keys = List.copyOf(keys);
	}
}
