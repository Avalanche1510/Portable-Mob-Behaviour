package com.pmb.mixin.client;

import com.pmb.client.PmbShieldVulnerableRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class PmbLivingEntityRenderStateMixin implements PmbShieldVulnerableRenderState {
	@Unique
	private boolean pmb$shieldVulnerable;

	@Override
	public boolean pmb$isShieldVulnerable() {
		return pmb$shieldVulnerable;
	}

	@Override
	public void pmb$setShieldVulnerable(boolean shieldVulnerable) {
		pmb$shieldVulnerable = shieldVulnerable;
	}
}
