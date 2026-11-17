package com.oceanphysics.client;

import com.oceanphysics.WaterGrid;
import com.oceanphysics.WaveConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.List;
import java.util.Random;

/** Menggerakkan simulasi riak: langkah fisika tiap tick + gangguan dari entity, percikan, dan hujan. */
public final class RippleClient {
    private static final Random R = new Random();
    private static HashSet<Integer> inWater = new HashSet<>();
    private static HashSet<Integer> nowInWater = new HashSet<>();

    private RippleClient() {}

    public static void tick(MinecraftClient mc) {
        WaveConfig cfg = WaveConfig.get();
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null || mc.isPaused() || !cfg.enabled || !cfg.ripples || !cfg.renderWaterMesh) return;

        WaterMeshRenderer.syncWorld(w, cfg);
        WaterGrid g = WaterMeshRenderer.GRID;
        int cell = g.cell;
        int half = Math.max(4, Math.min(30, cfg.meshRadius / cell));
        int cx = (int) Math.floor(p.getX() / cell);
        int cz = (int) Math.floor(p.getZ() / cell);

        // 1) fisika riak
        g.step(cx, cz, half, (float) cfg.rippleSpeed, (float) cfg.rippleDamping);

        // 2) gangguan dari entity yang menyentuh air
        float k = (float) cfg.rippleStrength;
        double reach = Math.min(40.0, cfg.meshRadius);
        List<Entity> list = w.getEntitiesByClass(Entity.class, p.getBoundingBox().expand(reach),
                e -> e.isTouchingWater() && !e.isSpectator());
        nowInWater.clear();
        int count = 0;
        for (Entity e : list) {
            if (++count > 60) break;
            int id = e.getId();
            nowInWater.add(id);
            int i = g.lookup((int) Math.floor(e.getX() / cell), (int) Math.floor(e.getZ() / cell));
            if (i < 0) continue;
            double rel = e.getY() - g.surface[i];
            if (rel > 1.3 || rel < -1.6) continue; // hanya yang dekat permukaan
            Vec3d v = e.getVelocity();
            double sp = v.horizontalLength();
            boolean entered = !inWater.contains(id);

            float push = 0f;
            if (entered) push = -(float) Math.min(0.55, 0.12 + Math.abs(v.y) * 0.9);
            else if (sp > 0.02) push = -(float) Math.min(0.14, sp * 0.55);
            if (push != 0f) {
                float size = (float) Math.min(1.6, 0.6 + e.getWidth() * 0.6);
                g.splat(e.getX(), e.getZ(), push * k * size);
            }
        }
        HashSet<Integer> tmp = inWater;
        inWater = nowInWater;
        nowInWater = tmp;

        // 3) tetesan hujan
        if (cfg.rainRipples && w.isRaining()) {
            for (int n = 0; n < 10; n++) {
                int gx = cx + R.nextInt(2 * half + 1) - half;
                int gz = cz + R.nextInt(2 * half + 1) - half;
                int i = g.lookup(gx, gz);
                if (i >= 0) g.h[i] -= 0.045f * k;
            }
        }
    }
}
