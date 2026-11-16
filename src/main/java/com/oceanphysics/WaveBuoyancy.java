package com.oceanphysics;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Helper dorongan ombak untuk entity biasa (hanya di server). */
public final class WaveBuoyancy {
    private WaveBuoyancy() {}

    /** Menggeser kesetimbangan pegas vanilla (mis. pelampung pancing). */
    public static void nudgeSpring(Entity e, double k) {
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || !cfg.itemBobbing || e.getWorld().isClient || !e.isTouchingWater()) return;
        World w = e.getWorld();
        double surface = WaveMath.findSurface(w, e.getX(), e.getY(), e.getZ());
        if (Double.isNaN(surface)) return;
        double h = WaveMath.height(w, e.getX(), surface, e.getZ(), 0f);
        Vec3d v = e.getVelocity();
        e.setVelocity(v.x, v.y + k * h, v.z);
    }

    /** Menambah kecepatan vertikal permukaan (mis. item yang mengapung). */
    public static void nudgeFeedForward(Entity e, double factor) {
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || !cfg.itemBobbing || e.getWorld().isClient || !e.isTouchingWater()) return;
        World w = e.getWorld();
        double surface = WaveMath.findSurface(w, e.getX(), e.getY(), e.getZ());
        if (Double.isNaN(surface)) return;
        double vy = WaveMath.verticalVelocityPerTick(w, e.getX(), surface, e.getZ());
        Vec3d v = e.getVelocity();
        e.setVelocity(v.x, v.y + vy * factor, v.z);
    }
}
