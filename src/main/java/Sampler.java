import java.util.Random;

public class Sampler {

	private final ThreadLocal<Random> random;

	public Sampler(final ThreadLocal<Random> random) {
		this.random = random;
	}

	public Vector radiance(final Scene scene, final Ray r, int depth) {
		final IntersectionResult intersection = scene.intersect(r);
		if (intersection.isMiss()) {
			return new Vector(0, 0, 0);
		} // if miss, return black
		final Sphere obj = intersection.object;
		Vector f = obj.color;
		final double p = Math.max(f.x, Math.max(f.y, f.z));
		if (++depth > 5 || p == 0) {
			if (random.get().nextDouble() < p) {
				f = f.scale(1 / p);
			} else {
				return obj.emission;
			}
		} // R.R.
		return obj.material.getBSDF(this, scene, r, depth, intersection, f);
	}
}
