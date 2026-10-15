package lock.render;

import lock.Core;
import lock.module.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Night-sky atmosphere drawn after the world: aurora curtains, wide nebula ribbons, a sunset horizon glow and stars.
 * Everything is geometry on a dome around the camera, drawn additively with the depth test on, so terrain and clouds
 * correctly cover it and it glows over the existing sky. Stars are seeded once; ribbons are rebuilt per frame from
 * a few sines (cheap) and quality only changes segment counts.
 */
public final class Sky {
    private Sky() {}

    private static final double R = 230;
    private static float[] starAz, starEl, starSize, starPhase;
    private static int starCount;

    private static void ensureStars(int n) {
        if (starAz != null && starCount >= n) return;
        Random r = new Random(1337);
        starCount = n;
        starAz = new float[n]; starEl = new float[n]; starSize = new float[n]; starPhase = new float[n];
        for (int i = 0; i < n; i++) {
            starAz[i] = (float) (r.nextDouble() * Math.PI * 2);
            starEl[i] = (float) Math.asin(0.05 + 0.95 * r.nextDouble());       // uniform over the upper hemisphere, none below the horizon
            starSize[i] = 0.25f + r.nextFloat() * r.nextFloat() * 0.9f;
            starPhase[i] = r.nextFloat() * 6.28f;
        }
    }

    /** 0 in daylight, 1 deep in the night, smooth through dusk and dawn. */
    static float night(long dayTime) {
        double t = dayTime % 24000L;
        double dusk = Mth.clamp((t - 12000.0) / 1800.0, 0, 1), dawn = Mth.clamp((24000.0 - t) / 1800.0, 0, 1);
        if (t < 12000) return 0;
        double a = Math.min(dusk, dawn);
        return (float) (a * a * (3 - 2 * a));
    }

    private static int accentRgb(Ambience a) {
        return switch (a.colors.get()) {
            case "Natural" -> 0x4DFF9A;
            case "Ice" -> 0xBFE9FF;
            case "Ember" -> 0xFF8A4D;
            default -> Core.modules.general.accent.get();
        };
    }

    private static int secondRgb(Ambience a) {
        return switch (a.colors.get()) {
            case "Natural" -> 0x2EC4B6;
            case "Ice" -> 0x7AA8FF;
            case "Ember" -> 0xFF4D9A;
            default -> Colors.lerp(Core.modules.general.accent.get(), 0x9B7BFF, 0.75f);
        };
    }

    public static void render(Camera camera, float pt, Matrix4f view) {
        Minecraft mc = Minecraft.getInstance();
        Ambience a = Core.modules.ambience;
        if (!a.sky.get() || mc.level == null || mc.level.dimension() != net.minecraft.world.level.Level.OVERWORLD) return;
        float night = night(mc.level.getDayTime());
        boolean sunset = a.mode.is("Sunset Haze");
        double t = mc.level.getGameTime() + pt;
        double tod = mc.level.getDayTime() % 24000L;
        float dusk = sunset ? (float) Math.max(0, 1 - Math.abs(tod - 12800) / 1800.0) + (float) Math.max(0, 1 - Math.abs(tod - 23300) / 1500.0) : 0;
        float vis = Math.max(night, Math.min(1f, dusk));
        if (vis < 0.01f) return;

        // heavy rain / thunder hides the sky; fade with it
        vis *= 1f - mc.level.getRainLevel(pt) * 0.85f;
        if (vis < 0.01f) return;

        float inten = a.intensity.f(), soft = a.softness.f(), speed = a.speed.f() * 0.02f;
        int q = a.quality.is("Low") ? 0 : a.quality.is("Medium") ? 1 : 2;

        Draw.beginSky(view);
        try {
            if (a.stars.f() > 0.01f && night > 0.01f) stars(a, night * (1f - mc.level.getRainLevel(pt) * 0.85f), t, q);
            switch (a.mode.get()) {
                case "Aurora" -> ribbons(a, vis, t * speed, q, false, inten, soft);
                case "Nebula" -> ribbons(a, vis, t * speed * 0.6, q, true, inten, soft);
                case "Sunset Haze" -> haze(vis * inten, soft);
                default -> {}
            }
        } finally {
            Draw.endSky();
        }
    }

    private static double px(double az, double el) { return Math.cos(el) * Math.sin(az) * R; }
    private static double py(double el) { return Math.sin(el) * R; }
    private static double pz(double az, double el) { return -Math.cos(el) * Math.cos(az) * R; }

    private static void stars(Ambience a, float vis, double t, int q) {
        int n = q == 0 ? 260 : q == 1 ? 620 : 1100;
        ensureStars(1100);
        float density = a.stars.f();
        for (int i = 0; i < n; i++) {
            if (i / (float) n > density) break;
            double az = starAz[i], el = starEl[i];
            float tw = 0.65f + 0.35f * (float) Math.sin(t * 0.06 + starPhase[i]);
            float al = vis * tw * (0.35f + 0.65f * starSize[i]);
            double cx = px(az, el), cy = py(el), cz = pz(az, el);
            double s = starSize[i] * 0.9;
            // tiny camera-facing quad: spanned by the local east/up directions of the point
            double ex = Math.cos(az), ez = Math.sin(az);
            int c = Colors.argb(0xDDEBFF, al);
            Draw.v(cx - ex * s, cy - s, cz - ez * s, c); Draw.v(cx + ex * s, cy - s, cz + ez * s, c);
            Draw.v(cx + ex * s, cy + s, cz + ez * s, c); Draw.v(cx - ex * s, cy + s, cz - ez * s, c);
        }
    }

    /** Curtains: vertical strips along a wavy path around the sky, bright at the base and fading upward. */
    private static void ribbons(Ambience a, float vis, double tt, int q, boolean nebula, float inten, float soft) {
        int count = nebula ? 3 : (q == 0 ? 2 : q == 1 ? 3 : 4);
        int segs = q == 0 ? 36 : q == 1 ? 64 : 110;
        int c1 = accentRgb(a), c2 = secondRgb(a);
        if (nebula) { c1 = Colors.lerp(c1, 0x9B7BFF, 0.6f); c2 = Colors.lerp(c2, 0xFF6BD6, 0.5f); }
        float height = nebula ? 0.75f : 0.35f + 0.25f * soft;
        for (int r = 0; r < count; r++) {
            double baseAz = r * (Math.PI * 2 / count) + 0.6 + Math.sin(tt * 0.3 + r) * 0.08;
            double span = nebula ? 1.9 : 1.5;                         // radians of sky the ribbon covers
            double e0 = Math.toRadians(nebula ? 9 : 7 + r * 2.5);
            for (int i = 0; i < segs; i++) {
                double u0 = i / (double) segs, u1 = (i + 1) / (double) segs;
                double[] w0 = wave(u0, r, tt, nebula), w1 = wave(u1, r, tt, nebula);
                double az0 = baseAz + (u0 - 0.5) * span + w0[0], az1 = baseAz + (u1 - 0.5) * span + w1[0];
                double top0 = e0 + height * w0[1], top1 = e0 + height * w1[1];
                float fade0 = edge(u0), fade1 = edge(u1);
                float bright0 = (0.55f + 0.45f * (float) w0[2]) * fade0, bright1 = (0.55f + 0.45f * (float) w1[2]) * fade1;
                int ca0 = Colors.lerp(c1, c2, (float) (0.5 + 0.5 * Math.sin(u0 * 3.2 + tt + r)));
                int ca1 = Colors.lerp(c1, c2, (float) (0.5 + 0.5 * Math.sin(u1 * 3.2 + tt + r)));
                float amp = (nebula ? 0.16f : 0.34f) * inten * vis;
                // 4 vertical rows: faint foot, bright lower body, fading upper body, nothing at the top
                double[] rows = {0.0, 0.18, 0.55, 1.0};
                float[] alp = {0.25f, 1.0f, 0.45f, 0.0f};
                for (int k = 0; k < 3; k++) {
                    double ea0 = e0 + (top0 - e0) * rows[k], eb0 = e0 + (top0 - e0) * rows[k + 1];
                    double ea1 = e0 + (top1 - e0) * rows[k], eb1 = e0 + (top1 - e0) * rows[k + 1];
                    Draw.v(px(az0, ea0), py(ea0), pz(az0, ea0), Colors.argb(ca0, amp * bright0 * alp[k]));
                    Draw.v(px(az1, ea1), py(ea1), pz(az1, ea1), Colors.argb(ca1, amp * bright1 * alp[k]));
                    Draw.v(px(az1, eb1), py(eb1), pz(az1, eb1), Colors.argb(ca1, amp * bright1 * alp[k + 1]));
                    Draw.v(px(az0, eb0), py(eb0), pz(az0, eb0), Colors.argb(ca0, amp * bright0 * alp[k + 1]));
                }
            }
        }
    }

    /** {azimuth wobble, height factor, brightness}. */
    private static double[] wave(double u, int r, double tt, boolean nebula) {
        double ph = r * 1.7;
        double az = Math.sin(u * 5.0 + tt * 0.9 + ph) * 0.07 + Math.sin(u * 11.0 - tt * 1.4 + ph * 2) * 0.025;
        double h = 0.62 + 0.38 * Math.sin(u * 3.0 + tt * 0.5 + ph) * Math.sin(u * 7.0 - tt * 0.7);
        double b = 0.5 + 0.5 * Math.sin(u * 9.0 + tt * 1.8 + ph * 3);
        return new double[] {az, Math.max(0.25, h), b};
    }

    private static float edge(double u) { double e = Math.min(u, 1 - u) * 6; return (float) Math.min(1, e * e * (3 - 2 * Math.min(1, e))); }

    /** Warm band hugging the horizon. */
    private static void haze(float amp, float soft) {
        int segs = 72;
        double e0 = Math.toRadians(-2), e1 = Math.toRadians(10 + 14 * soft), e2 = Math.toRadians(28 + 20 * soft);
        for (int i = 0; i < segs; i++) {
            double a0 = i / (double) segs * Math.PI * 2, a1 = (i + 1) / (double) segs * Math.PI * 2;
            int warm = 0xFF8A4D, rose = 0xFF4D7A;
            float k = amp * 0.35f;
            Draw.v(px(a0, e0), py(e0), pz(a0, e0), Colors.argb(warm, k)); Draw.v(px(a1, e0), py(e0), pz(a1, e0), Colors.argb(warm, k));
            Draw.v(px(a1, e1), py(e1), pz(a1, e1), Colors.argb(rose, k * 0.55f)); Draw.v(px(a0, e1), py(e1), pz(a0, e1), Colors.argb(rose, k * 0.55f));
            Draw.v(px(a0, e1), py(e1), pz(a0, e1), Colors.argb(rose, k * 0.55f)); Draw.v(px(a1, e1), py(e1), pz(a1, e1), Colors.argb(rose, k * 0.55f));
            Draw.v(px(a1, e2), py(e2), pz(a1, e2), Colors.argb(rose, 0f)); Draw.v(px(a0, e2), py(e2), pz(a0, e2), Colors.argb(rose, 0f));
        }
    }
}
