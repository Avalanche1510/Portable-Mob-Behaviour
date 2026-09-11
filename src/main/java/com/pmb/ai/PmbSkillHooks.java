package com.pmb.ai;

public final class PmbSkillHooks {
	private PmbSkillHooks() {}
	public interface Shield { void pmb$tickShieldSkill(); void pmb$cancelShieldSkill(); void pmb$claimShieldResources(PmbSkillScheduler scheduler); void pmb$authorizeShieldAction(); }
	public interface Wind { void pmb$tickWindSkill(); void pmb$cancelWindSkill(); void pmb$claimWindResources(PmbSkillScheduler scheduler); }
	public interface Pearl { void pmb$tickPearlSkill(); void pmb$cancelPearlSkill(); void pmb$claimPearlResources(PmbSkillScheduler scheduler); }
	public interface Mace { void pmb$tickMaceSkill(); void pmb$cancelMaceSkill(); boolean pmb$isPerformingMaceSmash(); }
	public interface Bow { void pmb$tickBowSkill(); void pmb$cancelBowSkill(); void pmb$claimBowResources(PmbSkillScheduler scheduler); }
}
