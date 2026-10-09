package com.pmb.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A deliberately lower-bound wind-charge launch from a downward floor hit in 26.2. */
final class PmbWindLaunchPhysics {
	private PmbWindLaunchPhysics() {}
	static record Launch(Vec3 position, Vec3 velocity, int impactTicks) {}
	private static final double RADIUS = 1.2;
	private static final double MULTIPLIER = 1.22;
	private static final int MAX_PROJECTILE_TICKS = 12;
	private static double nextProjectileSpeed(double speed) { return speed + .1; }

	static Launch predict(ServerLevel level, Mob mob, Vec3 takeoff, int postJumpTicks) {
		if (postJumpTicks < 0 || postJumpTicks > 10) return null;
		if (unmodeledMotion(mob)) return null;
		if (postJumpTicks > 0 && mob.isSprinting()) return null;
		double y = takeoff.y();
		double vertical = postJumpTicks == 0 ? 0 : predictedJumpSpeed(level, mob, takeoff);
		if (postJumpTicks > 0 && vertical <= 0) return null;
		for (int tick = 0; tick < postJumpTicks; tick++) {
			y += vertical;
			vertical = (vertical - .08) * .98;
			Vec3 position = new Vec3(takeoff.x(), y, takeoff.z());
			if (!clearMobBox(level, mob, position)) return null;
		}
		return estimate(level, mob, new Vec3(takeoff.x(), y, takeoff.z()),
				new Vec3(0, vertical, 0));
	}
	private static double predictedJumpSpeed(ServerLevel level, Mob mob, Vec3 takeoff) {
		BlockPos at = BlockPos.containing(takeoff);
		BlockPos below = BlockPos.containing(takeoff.x(), takeoff.y() - .5, takeoff.z());
		if (!level.hasChunkAt(at) || !level.hasChunkAt(below)) return 0;
		float atFactor = level.getBlockState(at).getBlock().getJumpFactor();
		float belowFactor = level.getBlockState(below).getBlock().getJumpFactor();
		double blockFactor = atFactor == 1 ? belowFactor : atFactor;
		return mob.getAttributeValue(Attributes.JUMP_STRENGTH) * blockFactor
				+ mob.getJumpBoostPower();
	}

	static Launch current(ServerLevel level, Mob mob) {
		return estimate(level, mob, mob.position(), mob.getDeltaMovement());
	}
	static Launch current(ServerLevel level, Mob mob, java.util.function.Consumer<String> diagnostic) {
		if (unmodeledMotion(mob)) { diagnostic.accept("unsupported_motion_state"); return null; }
		if (mob.getDeltaMovement().horizontalDistanceSqr() > .025 * .025) {
			diagnostic.accept("horizontal_motion"); return null;
		}
		if (!clearMobBox(level, mob, mob.position())) { diagnostic.accept("launch_body_obstructed"); return null; }
		Launch launch = current(level, mob);
		if (launch == null) diagnostic.accept("wind_impulse_unproven");
		return launch;
	}

	private static Launch estimate(ServerLevel level, Mob mob, Vec3 throwFeet, Vec3 motion) {
		if (unmodeledMotion(mob) || !clearMobBox(level, mob, throwFeet)
				|| motion.x() * motion.x() + motion.z() * motion.z() > .025 * .025)
			return null;
		Vec3 projectile = throwFeet.add(0, .15, 0);
		double projectileSpeed = 1.5;
		Vec3 position = throwFeet;
		Vec3 velocity = motion;
		boolean grounded = mob.onGround() && Math.abs(throwFeet.y() - mob.getY()) < .01
				&& motion.y() <= 0;
		for (int tick = 1; tick <= MAX_PROJECTILE_TICKS; tick++) {
			// 26.2 AbstractHurtingProjectile.applyInertia adds accelerationPower .1
			// before each movement clip. AbstractWindCharge has inertia 1.
			projectileSpeed = nextProjectileSpeed(projectileSpeed);
			Vec3 nextProjectile = projectile.add(0, -projectileSpeed, 0);
			if (!loadedSegment(level, projectile, nextProjectile)
					|| !level.getEntities(mob, new AABB(projectile, nextProjectile).inflate(.3),
							entity -> entity.isPickable() && !entity.isSpectator()).isEmpty()) return null;
			BlockHitResult hit = level.clip(new ClipContext(projectile, nextProjectile,
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
			if (!grounded) {
				position = position.add(velocity);
				if (!clearMobBox(level, mob, position)) return null;
				velocity = new Vec3(velocity.x() * .91, (velocity.y() - .08) * .98,
						velocity.z() * .91);
			}
			if (hit.getType() == HitResult.Type.BLOCK) {
				if (hit.getDirection() != Direction.UP) return null;
				Vec3 center = hit.getLocation().add(0, .25, 0);
				AABB predictedBox = mob.getBoundingBox().move(position.subtract(mob.position()));
				// Match ServerExplosion.getSeenPercent sampling, but on the box at the
				// explosion tick. The old release-tick box could see around new cover.
				double exposure = seenPercent(level, mob, center, predictedBox);
				if (exposure <= 0) return null;
				Vec3 push = windPush(position, center, mob.getEyeHeight(), exposure,
						mob.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE));
				if (push.y() <= 0) return null;
				return new Launch(position, velocity.add(push), tick);
			}
			projectile = nextProjectile;
		}
		return null;
	}
	private static boolean clearMobBox(ServerLevel level, Mob mob, Vec3 position) {
		AABB box = mob.getBoundingBox().move(position.subtract(mob.position()));
		for (double x : new double[] {box.minX, box.maxX})
			for (double z : new double[] {box.minZ, box.maxZ})
				if (!loadedSegment(level, new Vec3(x, box.minY, z),
						new Vec3(x, box.maxY, z))) return false;
		for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX - 1.0E-6); x++)
			for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY - 1.0E-6); y++)
				for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ - 1.0E-6); z++) {
					BlockPos block = new BlockPos(x, y, z);
					if (!level.hasChunkAt(block) || !level.getFluidState(block).isEmpty()
							|| level.getBlockState(block).is(Blocks.COBWEB)) return false;
				}
		return level.noCollision(mob, box);
	}
	private static boolean unmodeledMotion(Mob mob) {
		return Math.abs(mob.getGravity() - .08) > 1.0E-6
				|| mob.isInWater() || mob.isInLava() || mob.onClimbable()
				|| mob.isPassenger() || mob.isFallFlying()
				|| mob.hasEffect(MobEffects.SLOW_FALLING)
				|| mob.hasEffect(MobEffects.LEVITATION);
	}
	private static boolean loadedSegment(ServerLevel level, Vec3 from, Vec3 to) {
		int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / .4));
		for (int index = 0; index <= samples; index++)
			if (!level.hasChunkAt(BlockPos.containing(from.lerp(to, index / (double) samples)))) return false;
		return true;
	}
	private static double seenPercent(ServerLevel level, Mob mob, Vec3 center, AABB box) {
		boolean[] unloaded = {false};
		double result = sampleExposure(box, sample -> {
			if (!loadedSegment(level, sample, center)) { unloaded[0] = true; return false; }
			return level.clip(new ClipContext(sample, center, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, mob)).getType() == HitResult.Type.MISS;
		});
		return unloaded[0] ? 0 : result;
	}
	static double sampleExposure(AABB box, java.util.function.Predicate<Vec3> visibleRay) {
		double dx = 1 / ((box.maxX - box.minX) * 2 + 1);
		double dy = 1 / ((box.maxY - box.minY) * 2 + 1);
		double dz = 1 / ((box.maxZ - box.minZ) * 2 + 1);
		double offsetX = (1 - Math.floor(1 / dx) * dx) / 2;
		double offsetZ = (1 - Math.floor(1 / dz) * dz) / 2;
		int visible = 0, total = 0;
		for (double x = 0; x <= 1; x += dx)
			for (double y = 0; y <= 1; y += dy)
				for (double z = 0; z <= 1; z += dz) {
					Vec3 sample = new Vec3(box.minX + (box.maxX - box.minX) * x + offsetX,
							box.minY + (box.maxY - box.minY) * y,
							box.minZ + (box.maxZ - box.minZ) * z + offsetZ);
					if (visibleRay.test(sample)) visible++;
					total++;
				}
		return total == 0 ? 0 : visible / (double) total;
	}
	static Vec3 windPush(Vec3 position, Vec3 center, double eyeHeight,
			double exposure, double resistance) {
		double distance = position.distanceTo(center);
		if (distance <= 1.0E-6 || distance >= RADIUS * 2) return Vec3.ZERO;
		return position.add(0, eyeHeight, 0).subtract(center).normalize()
				.scale(conservativeImpulse(distance, exposure, resistance));
	}
	/** Mob.tick TAIL queues a modifier; the next serverAiStep applies it. */
	static int runtimeSteeringDelay(int impactTicks, int postImpactDelay) {
		return Math.max(0, impactTicks) + Math.max(0, postImpactDelay);
	}
	/** Conservatively reserve the first post-impact displacement for that handoff. */
	static int firstSteeredPostImpactDisplacement(int postImpactDelay) {
		return Math.max(0, postImpactDelay) + 1;
	}
	static int projectileTravelTicks(double floorDistance) {
		if (floorDistance <= 0) return 0;
		double speed = 1.5, traveled = 0;
		for (int tick = 1; tick <= MAX_PROJECTILE_TICKS; tick++) {
			speed = nextProjectileSpeed(speed);
			traveled += speed;
			if (traveled >= floorDistance) return tick;
		}
		return -1;
	}
	static double conservativeImpulse(double distance, double exposure, double resistance) {
		return Math.max(0, 1 - distance / (RADIUS * 2))
				* Math.max(0, Math.min(1, exposure)) * MULTIPLIER
				* Math.max(0, 1 - resistance);
	}
}
