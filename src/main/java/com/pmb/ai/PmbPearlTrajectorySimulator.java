package com.pmb.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Vanilla-equivalent discrete pearl flight used only for block/loaded-chunk feasibility. */
public final class PmbPearlTrajectorySimulator {
	private PmbPearlTrajectorySimulator() {}
	public record Result(Status status, Vec3 position, int ticks, Vec3 residualVelocity) {}
	public enum Status { BLOCK_HIT, NO_HIT, UNLOADED_CHUNK, INVALID }

	public static Result simulate(ServerLevel level, Mob owner, Vec3 start, Vec3 initialVelocity, int maxTicks) {
		if (!finite(start) || !finite(initialVelocity) || initialVelocity.lengthSqr() <= 1.0E-10)
			return new Result(Status.INVALID, start, 0, initialVelocity);
		Vec3 position = start;
		Vec3 velocity = initialVelocity;
		if (!loadedAndInside(level, start)) return new Result(Status.UNLOADED_CHUNK, start, 0, velocity);
		for (int tick = 1; tick <= maxTicks; tick++) {
			boolean water = level.getFluidState(BlockPos.containing(position)).is(net.minecraft.tags.FluidTags.WATER);
			velocity = velocity.add(0, -.03, 0).scale(water ? .8 : .99);
			Vec3 next = position.add(velocity);
			if (!loadedSegment(level, position, next))
				return new Result(Status.UNLOADED_CHUNK, position, tick - 1, velocity);
			HitResult hit = level.clip(new ClipContext(position, next, ClipContext.Block.COLLIDER,
					ClipContext.Fluid.NONE, owner));
			if (hit.getType() == HitResult.Type.BLOCK) {
				BlockHitResult block = (BlockHitResult) hit;
				return new Result(Status.BLOCK_HIT, block.getLocation(), tick, velocity);
			}
			position = next;
		}
		return new Result(Status.NO_HIT, position, maxTicks, velocity);
	}
	static Vec3 airInitialVelocity(Vec3 offset, int ticks) {
		if (ticks <= 0) return null;
		double drag = .99;
		double factor = drag * (1 - Math.pow(drag, ticks)) / (1 - drag);
		if (factor <= 1.0E-9) return null;
		double gravity = .03 * drag / (1 - drag) * (ticks - factor);
		return new Vec3(offset.x() / factor, (offset.y() + gravity) / factor, offset.z() / factor);
	}
	private static boolean loadedSegment(ServerLevel level, Vec3 start, Vec3 end) {
		int steps = Math.max(1, (int) Math.ceil(start.distanceTo(end) / 4.0));
		for (int step = 1; step <= steps; step++)
			if (!loadedAndInside(level, start.lerp(end, step / (double) steps))) return false;
		return true;
	}
	private static boolean loadedAndInside(ServerLevel level, Vec3 position) {
		BlockPos block = BlockPos.containing(position);
		return level.getWorldBorder().isWithinBounds(block) && level.hasChunkAt(block);
	}
	private static boolean finite(Vec3 value) {
		return Double.isFinite(value.x()) && Double.isFinite(value.y()) && Double.isFinite(value.z());
	}
}
