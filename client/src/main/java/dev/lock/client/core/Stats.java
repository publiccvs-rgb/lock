package dev.lock.client.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/** Frame timing, click rate and session counters, all derived from what the client observes locally. */
public final class Stats {
    private final Minecraft mc = Minecraft.getInstance();
    private final long[] frameNanos = new long[600];
    private int frameIdx, frameCount;
    private long lastFrame;
    private final Deque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
    public final long sessionStart = System.currentTimeMillis();
    public long worldJoin = System.currentTimeMillis();
    private Object lastLevel;
    public double distance, sprintDistance;
    public int jumps, deaths;
    private boolean wasOnGround = true, wasDead;
    private Vec3 lastPos;
    public double speed; // blocks per second (horizontal)
    private long lastStatCalc;
    private double cachedAvgFps, cachedLow, cachedFrameMs;

    public void frame() {
        long now = System.nanoTime();
        if (lastFrame != 0) {
            frameNanos[frameIdx] = now - lastFrame;
            frameIdx = (frameIdx + 1) % frameNanos.length;
            if (frameCount < frameNanos.length) frameCount++;
        }
        lastFrame = now;
    }

    /** Frame times in ms, oldest first (at most {@code n}). */
    public float[] recentFrameMs(int n) {
        int count = Math.min(n, frameCount);
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            int idx = Math.floorMod(frameIdx - count + i, frameNanos.length);
            out[i] = frameNanos[idx] / 1_000_000f;
        }
        return out;
    }

    private void calc() {
        long now = System.currentTimeMillis();
        if (now - lastStatCalc < 250) return;
        lastStatCalc = now;
        if (frameCount == 0) return;
        long[] copy = new long[frameCount];
        for (int i = 0; i < frameCount; i++) copy[i] = frameNanos[Math.floorMod(frameIdx - frameCount + i, frameNanos.length)];
        long sum = 0;
        for (long v : copy) sum += v;
        cachedFrameMs = sum / (double) frameCount / 1e6;
        cachedAvgFps = cachedFrameMs > 0 ? 1000.0 / cachedFrameMs : 0;
        Arrays.sort(copy);
        int k = Math.max(1, frameCount / 100);
        long worst = 0;
        for (int i = frameCount - k; i < frameCount; i++) worst += copy[i];
        double worstMs = worst / (double) k / 1e6;
        cachedLow = worstMs > 0 ? 1000.0 / worstMs : 0;
    }

    public double avgFps() { calc(); return cachedAvgFps; }
    public double lowFps() { calc(); return cachedLow; }
    public double frameMs() { calc(); return cachedFrameMs; }

    public void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) left.addLast(now);
        else if (button == 1) right.addLast(now);
    }

    public int cps(int button) {
        Deque<Long> d = button == 0 ? left : right;
        long now = System.currentTimeMillis();
        while (!d.isEmpty() && now - d.peekFirst() > 1000) d.removeFirst();
        return d.size();
    }

    public void tick() {
        LocalPlayer p = mc.player;
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            worldJoin = System.currentTimeMillis();
            lastPos = null;
        }
        if (p == null) return;
        Vec3 pos = p.position();
        if (lastPos != null) {
            double dx = pos.x - lastPos.x, dz = pos.z - lastPos.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < 10) { // ignore teleports
                distance += d;
                if (p.isSprinting()) sprintDistance += d;
                speed = d * 20;
            }
        }
        lastPos = pos;
        boolean onGround = p.onGround();
        if (wasOnGround && !onGround && p.getDeltaMovement().y > 0.2 && !p.getAbilities().flying) jumps++;
        wasOnGround = onGround;
        boolean dead = p.isDeadOrDying();
        if (dead && !wasDead) deaths++;
        wasDead = dead;
    }
}
