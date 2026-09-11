package com.pmb.ai;

/** Air-only pearl trajectory, with gravity applied before drag and movement. */
public final class PmbEnderPearlBallistics {
	private static final double DRAG = 0.99D;
	private static final double GRAVITY = 0.03D;
	private static final double LOG_DRAG = Math.log(DRAG);

	private PmbEnderPearlBallistics() {}

	public enum Status { SOLVED, POWER_LIMITED, INVALID }

	public record Solution(Status status, double speed, double time) {}

	private record Crossing(double height, double time) {}

	public static Solution solve(double distance, double height, double angle, double maxSpeed) {
		if (!Double.isFinite(distance) || !Double.isFinite(height) || !Double.isFinite(angle)
				|| !Double.isFinite(maxSpeed) || distance < 1.0E-6D || maxSpeed <= 0.0D
				|| angle <= 0.0D || angle >= Math.PI / 2.0D || height >= distance * Math.tan(angle)) {
			return new Solution(Status.INVALID, 0.0D, 0.0D);
		}
		Crossing maximum = crossing(distance, angle, maxSpeed);
		if (maximum != null && (!Double.isFinite(maximum.height()) || !Double.isFinite(maximum.time()))) {
			return new Solution(Status.INVALID, 0.0D, 0.0D);
		}
		if (maximum == null || maximum.height() < height) {
			return new Solution(Status.POWER_LIMITED, maxSpeed, 0.0D);
		}
		double low = 0.0D;
		double high = maxSpeed;
		for (int i = 0; i < 80; i++) {
			double speed = (low + high) * 0.5D;
			Crossing result = crossing(distance, angle, speed);
			if (result != null && result.height() >= height) high = speed;
			else low = speed;
		}
		Crossing result = crossing(distance, angle, high);
		if (result == null || !Double.isFinite(result.height()) || !Double.isFinite(result.time())
				|| Math.abs(result.height() - height) > 1.0E-6D * Math.max(1.0D, Math.abs(height))) {
			return new Solution(Status.INVALID, 0.0D, 0.0D);
		}
		return new Solution(Status.SOLVED, high, result.time());
	}

	private static Crossing crossing(double distance, double angle, double speed) {
		double horizontalSpeed = Math.cos(angle) * speed;
		double fractionOfLimit = distance * (1.0D - DRAG) / (horizontalSpeed * DRAG);
		if (!(fractionOfLimit > 0.0D && fractionOfLimit < 1.0D)) return null;
		double time = Math.log1p(-fractionOfLimit) / LOG_DRAG;
		double before = Math.floor(time);
		double after = before + 1.0D;
		double sumBefore = dragSum(before);
		double sumAfter = dragSum(after);
		double fraction = (distance / horizontalSpeed - sumBefore) / (sumAfter - sumBefore);
		if (!Double.isFinite(fraction)) return new Crossing(Double.NaN, Double.NaN);
		fraction = Math.clamp(fraction, 0.0D, 1.0D);
		double verticalSpeed = Math.sin(angle) * speed;
		double yBefore = verticalSpeed * sumBefore - GRAVITY * DRAG / (1.0D - DRAG) * (before - sumBefore);
		double yAfter = verticalSpeed * sumAfter - GRAVITY * DRAG / (1.0D - DRAG) * (after - sumAfter);
		return new Crossing(yBefore + (yAfter - yBefore) * fraction, before + fraction);
	}

	private static double dragSum(double ticks) {
		return DRAG * -Math.expm1(ticks * LOG_DRAG) / (1.0D - DRAG);
	}
}
