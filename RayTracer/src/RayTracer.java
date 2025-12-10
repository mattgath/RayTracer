import java.io.*;
import java.util.*;

public class RayTracer {

    static Random rand = new Random();

    static class Vec3 {
        double x, y, z;

        Vec3(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }

        Vec3 add(Vec3 v) { return new Vec3(x + v.x, y + v.y, z + v.z); }
        Vec3 sub(Vec3 v) { return new Vec3(x - v.x, y - v.y, z - v.z); }
        Vec3 mul(double t) { return new Vec3(x * t, y * t, z * t); }
        Vec3 mul(Vec3 v) { return new Vec3(x * v.x, y * v.y, z * v.z); }
        double dot(Vec3 v) { return x * v.x + y * v.y + z * v.z; }
        double length() { return Math.sqrt(x*x + y*y + z*z); }
        Vec3 normalize() { double l = length(); return new Vec3(x/l, y/l, z/l); }

        static Vec3 randomUnitVector() {
            while (true) {
                Vec3 p = new Vec3(rand.nextDouble()*2-1, rand.nextDouble()*2-1, rand.nextDouble()*2-1);
                if (p.dot(p) < 1) return p.normalize();
            }
        }

        static Vec3 reflect(Vec3 v, Vec3 n) {
            return v.sub(n.mul(2 * v.dot(n)));
        }

        static Vec3 refract(Vec3 uv, Vec3 n, double etaiOverEtat) {
            double cosTheta = Math.min(-uv.dot(n), 1.0);
            Vec3 rOutPerp = uv.add(n.mul(cosTheta)).mul(etaiOverEtat);
            Vec3 rOutPar = n.mul(-Math.sqrt(Math.abs(1.0 - rOutPerp.dot(rOutPerp))));
            return rOutPerp.add(rOutPar);
        }
    }

    static class Ray {
        Vec3 origin, dir;
        Ray(Vec3 o, Vec3 d) { origin = o; dir = d.normalize(); }
        Vec3 at(double t) { return origin.add(dir.mul(t)); }
    }

    static final int DIFFUSE = 0;
    static final int METAL = 1;
    static final int DIELECTRIC = 2;
    static final int EMISSIVE = 3;

    static class Sphere {
        Vec3 center, color, emission;
        double radius, fuzz;
        int material;

        Sphere(Vec3 c, double r, Vec3 col, int mat) {
            center = c; radius = r; color = col; material = mat; fuzz = 0; emission = new Vec3(0,0,0);
        }

        Sphere(Vec3 c, double r, Vec3 col, int mat, double fuzz) {
            center = c; radius = r; color = col; material = mat; this.fuzz = fuzz; emission = new Vec3(0,0,0);
        }

        Sphere setEmission(Vec3 e) { emission = e; return this; }

        double hit(Ray ray) {
            Vec3 oc = ray.origin.sub(center);
            double a = ray.dir.dot(ray.dir);
            double b = 2.0 * oc.dot(ray.dir);
            double c = oc.dot(oc) - radius * radius;
            double disc = b*b - 4*a*c;
            if (disc < 0) return -1;
            double t = (-b - Math.sqrt(disc)) / (2*a);
            return t > 0.001 ? t : -1;
        }

        Vec3 normalAt(Vec3 point) {
            return point.sub(center).normalize();
        }
    }

    static List<Sphere> spheres = new ArrayList<>();
    static int samplesPerPixel = 200;
    static int maxDepth = 10;

    public static void main(String[] args) throws IOException {
        int width = 800;
        int height = 600;

        Vec3 camPos = new Vec3(0, 0, 0);
        double fov = 90;
        double scale = Math.tan(Math.toRadians(fov / 2));
        double aspect = (double) width / height;

        spheres.add(new Sphere(new Vec3(0, 0, -5), 1, new Vec3(0.8, 0.2, 0.2), DIFFUSE));
        spheres.add(new Sphere(new Vec3(-2, 0, -4), 1, new Vec3(0.8, 0.8, 0.8), METAL, 0.0));
        spheres.add(new Sphere(new Vec3(2, 0, -6), 1, new Vec3(1.0, 1.0, 1.0), DIELECTRIC, 1.5));
        spheres.add(new Sphere(new Vec3(2, 0, -6), -0.9, new Vec3(1.0, 1.0, 1.0), DIELECTRIC, 1.5));
        spheres.add(new Sphere(new Vec3(0, -101, -5), 100, new Vec3(0.5, 0.5, 0.5), DIFFUSE));
        spheres.add(new Sphere(new Vec3(-1, 3, -3), 2, new Vec3(1,1,1), EMISSIVE).setEmission(new Vec3(2,2,2)));

        int[][] pixels = new int[height][width * 3];

        for (int j = 0; j < height; j++) {
            if (j % 100 == 0) System.out.println("Row " + j + "/" + height);

            for (int i = 0; i < width; i++) {
                Vec3 color = new Vec3(0, 0, 0);

                for (int s = 0; s < samplesPerPixel; s++) {
                    double x = (2 * (i + rand.nextDouble()) / width - 1) * aspect * scale;
                    double y = (1 - 2 * (j + rand.nextDouble()) / height) * scale;
                    Ray ray = new Ray(camPos, new Vec3(x, y, -1));
                    color = color.add(trace(ray, maxDepth));
                }

                color = color.mul(1.0 / samplesPerPixel);
                pixels[j][i*3]     = (int)(255 * Math.min(1, Math.sqrt(color.x)));
                pixels[j][i*3 + 1] = (int)(255 * Math.min(1, Math.sqrt(color.y)));
                pixels[j][i*3 + 2] = (int)(255 * Math.min(1, Math.sqrt(color.z)));
            }
        }

        writePPM("output.ppm", pixels, width, height);
        System.out.println("Complete sire! Your amazing super awesome cool ppm has been completed.");
    }

    static Vec3 trace(Ray ray, int depth) {
        if (depth <= 0) return new Vec3(0, 0, 0);

        double closest = Double.MAX_VALUE;
        Sphere hitSphere = null;

        for (Sphere s : spheres) {
            double t = s.hit(ray);
            if (t > 0 && t < closest) {
                closest = t;
                hitSphere = s;
            }
        }

        if (hitSphere != null) {
            Vec3 hitPoint = ray.at(closest);
            Vec3 normal = hitSphere.normalAt(hitPoint);

            if (hitSphere.material == EMISSIVE) {
                return hitSphere.emission;
            } else if (hitSphere.material == METAL) {
                Vec3 reflected = Vec3.reflect(ray.dir, normal);
                Vec3 fuzzed = reflected.add(Vec3.randomUnitVector().mul(hitSphere.fuzz));
                if (fuzzed.dot(normal) > 0) {
                    return hitSphere.color.mul(trace(new Ray(hitPoint, fuzzed.normalize()), depth - 1));
                }
                return new Vec3(0, 0, 0);
            } else if (hitSphere.material == DIELECTRIC) {
                double ior = hitSphere.fuzz;
                boolean frontFace = ray.dir.dot(normal) < 0;
                Vec3 n = frontFace ? normal : normal.mul(-1);
                double ratio = frontFace ? (1.0 / ior) : ior;

                double cosTheta = Math.min(-ray.dir.dot(n), 1.0);
                double sinTheta = Math.sqrt(1.0 - cosTheta * cosTheta);
                boolean cannotRefract = ratio * sinTheta > 1.0;

                double r0 = (1 - ratio) / (1 + ratio);
                r0 = r0 * r0;
                double schlick = r0 + (1 - r0) * Math.pow(1 - cosTheta, 5);

                Vec3 dir;
                if (cannotRefract || schlick > rand.nextDouble()) {
                    dir = Vec3.reflect(ray.dir, n);
                } else {
                    dir = Vec3.refract(ray.dir, n, ratio);
                }
                return trace(new Ray(hitPoint, dir), depth - 1);
            } else {
                Vec3 bounceDir = normal.add(Vec3.randomUnitVector()).normalize();
                return hitSphere.color.mul(trace(new Ray(hitPoint, bounceDir), depth - 1));
            }
        }

        double t = 0.5 * (ray.dir.y + 1);
        return new Vec3(1, 1, 1).mul(1 - t).add(new Vec3(0.5, 0.7, 1.0).mul(t));
    }

    static void writePPM(String filename, int[][] pixels, int w, int h) throws IOException {
        try (PrintWriter out = new PrintWriter(filename)) {
            out.println("P3");
            out.println(w + " " + h);
            out.println("255");
            for (int j = 0; j < h; j++) {
                for (int i = 0; i < w; i++) {
                    out.print(pixels[j][i*3] + " " + pixels[j][i*3+1] + " " + pixels[j][i*3+2] + " ");
                }
                out.println();
            }
        }
    }
}