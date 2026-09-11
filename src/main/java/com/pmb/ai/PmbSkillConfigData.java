package com.pmb.ai;

import java.util.Set;

/** Common persistence surface used by the modular skills command. */
public interface PmbSkillConfigData {
	boolean isConfigured();
	Set<String> explicitFields();
	void resetRuntime();
}
