import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

// smallpt, a Path Tracer by Kevin Beason, 2008
class Smallpt {

	static Sphere spheres[] = {// Scene: radius, position, emission, color, material
			new Sphere(1e5, new Vector(1e5 + 1, 40.8, 81.6), new Vector(0, 0, 0), new Vector(.75, .25, .25), Material.DIFFUSE),// Left
			new Sphere(1e5, new Vector(-1e5 + 99, 40.8, 81.6), new Vector(0, 0, 0), new Vector(.25, .25, .75), Material.DIFFUSE),// Rght
			new Sphere(1e5, new Vector(50, 40.8, 1e5), new Vector(0, 0, 0), new Vector(.75, .75, .75), Material.DIFFUSE),// Back
			new Sphere(1e5, new Vector(50, 40.8, -1e5 + 170), new Vector(0, 0, 0), new Vector(0, 0, 0), Material.DIFFUSE),// Frnt
			new Sphere(1e5, new Vector(50, 1e5, 81.6), new Vector(0, 0, 0), new Vector(.75, .75, .75), Material.DIFFUSE),// Botm
			new Sphere(1e5, new Vector(50, -1e5 + 81.6, 81.6), new Vector(0, 0, 0), new Vector(.75, .75, .75), Material.DIFFUSE),// Top
			new Sphere(16.5, new Vector(27, 16.5, 47), new Vector(0, 0, 0), new Vector(.999, .999, .999), Material.SPECULAR),// Mirr
			new Sphere(16.5, new Vector(73, 16.5, 78), new Vector(0, 0, 0), new Vector(.999, .999, .999), Material.REFRACTIVE),// Glas
			new Sphere(1.5, new Vector(50, 81.6 - 16.5, 81.6), new Vector(400, 400, 400), new Vector(0, 0, 0), Material.DIFFUSE)
	// Lite
	};

	static double clamp(final double x) {
		return x < 0 ? 0 : x > 1 ? 1 : x;
	}

	static double increaseBrightness(final double x) {
		return Math.pow(clamp(x), 1 / 2.2);
	}

	static int toInt(final double x) {
		return (int) (clamp(x) * 255 + .5);
	}

	static void intersect(final Ray r, final Intersection intersection) {
		final double inf = intersection.t = 1e20;
		for (int i = spheres.length - 1; i >= 0; i--) {
			final double d = spheres[i].intersect(r);
			if (d > 0 && d < intersection.t) {
				intersection.t = d;
				intersection.id = i;
			}
		}
		intersection.b = intersection.t < inf;
	}

	public static final Random random = new Random(1337);

	static Vector radiance(final Ray r, final int depth) {
		return radiance(r, depth, 1);
	}

	static Vector radiance(final Ray r, int depth, final int E) {
		final Intersection intersection = new Intersection();
		intersect(r, intersection);
		if (!intersection.b) {
			return new Vector(0, 0, 0);
		} // if miss, return black
		final Sphere obj = spheres[intersection.id]; // the hit object
		final Vector intersectionPoint = r.getVector(intersection.t);
		final Vector normal = intersectionPoint.minus(obj.center).norm();
		final Vector nl = normal.dot(r.direction) < 0 ? normal : normal.scale(-1);
		Vector f = obj.color;
		final double p = f.x > f.y && f.x > f.z ? f.x : f.y > f.z ? f.y : f.z; // max refl
		if (++depth > 5 || p == 0) {
			if (random.nextDouble() < p) {
				f = f.scale(1 / p);
			} else {
				return obj.emission.scale(E);
			}
		} // R.R.
		if (obj.material == Material.DIFFUSE) { // Ideal DIFFUSE reflection
			return diffuse(depth, E, intersection, obj, intersectionPoint, nl, f);
		} else if (obj.material == Material.SPECULAR) { // Ideal SPECULAR reflection
			return specular(r, depth, obj, intersectionPoint, normal, f);
		}
		return refraction(r, depth, obj, intersectionPoint, normal, nl, f);
	}

	private static Vector diffuse(final int depth, final int E, final Intersection intersection, final Sphere obj, final Vector intersectionPoint, final Vector nl, final Vector f) {
		final double r1 = 2 * Math.PI * random.nextDouble();
		final double r2 = random.nextDouble();
		final double r2s = Math.sqrt(r2);
		final Vector w = nl;
		final Vector u = ((Math.abs(w.x) > .1 ? new Vector(0, 1, 0) : new Vector(1, 0, 0)).cross(w)).norm();
		final Vector v = w.cross(u);
		final Vector d = (u.scale(Math.cos(r1) * r2s).plus(v.scale(Math.sin(r1) * r2s)).plus(w.scale(Math.sqrt(1 - r2)))).norm();

		// Loop over any lights
		Vector e = new Vector(0, 0, 0);
		for (int i = 0; i < spheres.length; i++) {
			final Sphere s = spheres[i];
			if (s.emission.x <= 0 && s.emission.y <= 0 && s.emission.z <= 0) {
				continue;
			} // skip non-lights

			final Vector sw = s.center.minus(intersectionPoint);
			final Vector su = ((Math.abs(sw.x) > .1 ? new Vector(0, 1, 0) : new Vector(1, 0, 0)).cross(sw)).norm();
			final Vector sv = sw.cross(su);
			final double cos_a_max = Math.sqrt(1 - s.radius * s.radius / (intersectionPoint.minus(s.center)).dot(intersectionPoint.minus(s.center)));
			final double eps1 = random.nextDouble();
			final double eps2 = random.nextDouble();
			final double cos_a = 1 - eps1 + eps1 * cos_a_max;
			final double sin_a = Math.sqrt(1 - cos_a * cos_a);
			final double phi = 2 * Math.PI * eps2;
			final Vector l = su.scale(Math.cos(phi) * sin_a).plus(sv.scale(Math.sin(phi) * sin_a)).plus(sw.scale(cos_a)).norm();
			intersect(new Ray(intersectionPoint, l), intersection);
			if (intersection.b && intersection.id == i) { // shadow ray
				final double omega = 2 * Math.PI * (1 - cos_a_max);
				e = e.plus(f.multiply(s.emission.scale(l.dot(nl) * omega)).scale(1 / Math.PI));
			}
		}

		return obj.emission.scale(E).plus(e).plus(f.multiply(radiance(new Ray(intersectionPoint, d), depth, 0)));
	}

	private static Vector specular(final Ray r, final int depth, final Sphere obj, final Vector intersectionPoint, final Vector normal, final Vector f) {
		return obj.emission.plus(f.multiply(radiance(new Ray(intersectionPoint, r.direction.minus(normal.scale(2 * normal.dot(r.direction)))), depth)));
	}

	private static Vector refraction(final Ray r, final int depth, final Sphere obj, final Vector intersectionPoint, final Vector normal, final Vector nl, final Vector f) {
		final Ray reflRay = new Ray(intersectionPoint, r.direction.minus(normal.scale(2 * normal.dot(r.direction)))); // Ideal dielectric REFRACTION
		final boolean into = normal.dot(nl) > 0; // Ray from outside going in?
		final double nc = 1;
		final double nt = 1.5;
		final double nnt = into ? nc / nt : nt / nc;
		final double ddn = r.direction.dot(nl);
		final double cos2t = 1 - nnt * nnt * (1 - ddn * ddn);
		if (cos2t < 0) {
			return obj.emission.plus(f.multiply(radiance(reflRay, depth)));
		}
		final Vector tdir = (r.direction.scale(nnt).minus(normal.scale(((into ? 1 : -1) * (ddn * nnt + Math.sqrt(cos2t)))))).norm();
		final double a = nt - nc;
		final double b = nt + nc;
		final double R0 = a * a / (b * b);
		final double c = 1 - (into ? -ddn : tdir.dot(normal));
		final double Re = R0 + (1 - R0) * c * c * c * c * c;
		final double Tr = 1 - Re;
		final double P = .25 + .5 * Re;
		final double RP = Re / P;
		final double TP = Tr / (1 - P);
		final Vector xx = getXX(depth, intersectionPoint, reflRay, tdir, Re, Tr, P, RP, TP);
		return obj.emission.plus(f.multiply(xx));
	}

	private static Vector getXX(final int depth, final Vector x, final Ray reflRay, final Vector tdir, final double Re, final double Tr, final double P, final double RP, final double TP) {
		if (depth > 2) {
			if (random.nextDouble() < P) { // Russian roulette
				return radiance(reflRay, depth).scale(RP);
			} else {
				return radiance(new Ray(x, tdir), depth).scale(TP);
			}
		} else {
			return radiance(reflRay, depth).scale(Re).plus(radiance(new Ray(x, tdir), depth).scale(Tr));
		}
	}

	public static void main(final String[] argv) throws IOException {
		final int w = argv.length == 3 ? Integer.valueOf(argv[0]) : 256; // # samples
		final int h = argv.length == 3 ? Integer.valueOf(argv[1]) : 256; // # samples
		final int samples = argv.length == 3 ? Integer.valueOf(argv[2]) / 4 : 1; // # samples
		System.err.println(String.format("Options %dx%d with %d samples", w, h, samples));
		final Ray camera = new Ray(new Vector(50, 52, 295.6), new Vector(0, -0.042612, -1).norm()); // cam pos, dir
		final Vector cx = new Vector(w * .5135 / h, 0, 0);
		final Vector cy = (cx.cross(camera.direction)).norm().scale(.5135);
		final Vector[][] image = renderImage(w, h, samples, camera, cx, cy);
		writeImage(w, h, image);

	}

	private static Vector[][] renderImage(final int w, final int h, final int samples, final Ray camera, final Vector cx, final Vector cy) {
		final Vector[][] image = new Vector[h][];
		for (int y = 0; y < h; y++) { // Loop over image rows
			System.err.println(String.format("\rRendering (%d spp) %5.2f%%", samples * 4, 100. * y / (h - 1)));
			for (int x = 0; x < w; x++) {
				for (int sy = 0; sy < 2; sy++) {
					Vector r = new Vector(0, 0, 0);
					for (int sx = 0; sx < 2; sx++, r = new Vector(0, 0, 0)) { // 2x2 subpixel cols
						for (int s = 0; s < samples; s++) {
							final double r1 = 2 * random.nextDouble();
							final double dx = r1 < 1 ? Math.sqrt(r1) - 1 : 1 - Math.sqrt(2 - r1);
							final double r2 = 2 * random.nextDouble();
							final double dy = r2 < 1 ? Math.sqrt(r2) - 1 : 1 - Math.sqrt(2 - r2);
							final Vector d = cx.scale((((sx + .5 + dx) / 2 + x) / w - .5)).plus(cy.scale((((sy + .5 + dy) / 2 + y) / h - .5))).plus(camera.direction);
							r = r.plus(radiance(new Ray(camera.origin.plus(d.scale(140)), d.norm()), 0).scale((1. / samples)));
						} // Camera rays are pushed ^^^^^ forward to start in interior
						if (image[y] == null) {
							image[y] = new Vector[w];
						}
						if (image[y][x] == null) {
							image[y][x] = new Vector(0, 0, 0);
						}
						image[y][x] = image[y][x].plus(new Vector(clamp(r.x), clamp(r.y), clamp(r.z)).scale(.25));
					}
				}
			}
		}
		return image;
	}

	private static void writeImage(final int w, final int h, final Vector[][] c) throws IOException {
		final File f = new File("target/image.png"); // Write image to PPM file.

		final BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < c.length; y++) {
			for (int x = 0; x < c[0].length; x++) {
				final int a[] = new int[3];
				a[0] = toInt(increaseBrightness(c[y][x].x));
				a[1] = toInt(increaseBrightness(c[y][x].y));
				a[2] = toInt(increaseBrightness(c[y][x].z));
				image.setRGB(x, c.length - y - 1, new Color(a[0], a[1], a[2]).getRGB());
			}
		}

		ImageIO.write(image, "png", f);
	}
}
