package com.oceanphysics;

import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;

/**
 * Satu model air untuk semua benda: di titik (x,z) ada PERMUKAAN gelombang (puncak lokal) dan KECEPATAN air
 * (orbital gelombang + drift Stokes + hempas pantai). Benda yang berada di dalam badan ombak (walau sudah
 * di atas permukaan air vanila) diperlakukan sebagai di dalam air, lalu ditarik/didorong oleh kecepatan air itu.
 * Dipakai bersama oleh server (mob, item, blok jatuh, TNT, kereta, kapal) dan client (pemain).
 */
public final class WaterForces {
    /** Arus ombak asli sangat lembut; dikali ini supaya terasa di game. */
    public static final double GAME_GAIN = 3.0;

    private WaterForces() {}

    public static final class Sample {
        public double vanillaY;   // permukaan air vanila (atas kolom air)
        public double h;          // tinggi ombak di atas vanila
        public double surfaceY;   // vanillaY + h = puncak ombak lokal
        public double ux, uy, uz; // kecepatan air, blok/tick
        public double df;         // faktor ukuran ombak 0..1
        public double storm;
    }

    private static final class ShoreInfo {
        float dx, dz, prox;
        long stamp;
    }

    private static final ThreadLocal<HashMap<Long, ShoreInfo>> SHORE = ThreadLocal.withInitial(HashMap::new);
    private static final double[] COS8 = new double[8], SIN8 = new double[8];

    static {
        for (int i = 0; i < 8; i++) {
            COS8[i] = Math.cos(i * Math.PI / 4.0);
            SIN8[i] = Math.sin(i * Math.PI / 4.0);
        }
    }

    /** Jangkauan pencarian air ke bawah (blok) untuk benda yang menunggangi puncak ombak. */
    public static int reach() {
        return WaveMath.maxWaveHeight() + 1;
    }

    /** Kolom murah: permukaan air vanila di bawah (x,y,z) atau NaN. */
    public static double column(World w, double x, double y, double z) {
        return WaveMath.findSurfaceTall(w, x, y, z, reach());
    }

    /** Sampel lengkap (permukaan ombak + kecepatan air). null jika tidak ada air di kolom itu. */
    public static Sample sample(World w, double x, double y, double z) {
        double base = column(w, x, y, z);
        if (Double.isNaN(base)) return null;
        return sampleAt(w, x, z, base);
    }

    public static Sample sampleAt(World w, double x, double z, double base) {
        WaveConfig cfg = WaveConfig.get();
        double storm = WaveMath.storm(w);
        double t = WaveMath.timeSeconds(w, 0f);
        double[] parts = new double[3];
        double df = WaveMath.depthParts(w, x, base, z, parts);

        Sample s = new Sample();
        s.vanillaY = base;
        s.storm = storm;
        s.df = df;
        s.h = WaveMath.heightAt(x, z, t, storm, df);
        s.surfaceY = base + s.h;

        double dt = 0.05;
        s.uy = (WaveMath.rawHeight(x, z, t + dt, storm) - WaveMath.rawHeight(x, z, t - dt, storm))
                / (2 * dt) / 20.0 * df;

        if (cfg.waterCurrent) {
            double[] o = new double[3];
            WaveMath.orbital(x, z, t, storm, o);
            // kecepatan orbital sebanding dengan tinggi ombak: dikali df supaya kolam/sungai tenang juga tenang
            double g = GAME_GAIN * cfg.currentStrength / 20.0;
            s.ux = o[0] * g * df;
            s.uz = o[1] * g * df;
            if (cfg.shoreSurge) {
                ShoreInfo sh = shore(w, x, base, z);
                if (sh.prox > 0.01f) {
                    double shoal = 1.0 + (1.0 - parts[0]) * 1.2;        // ombak "naik" di perairan dangkal
                    double surge = o[2] * g * cfg.surgeStrength * sh.prox * parts[1] * parts[2]
                            * (1.0 + storm * 1.5) * shoal;
                    s.ux += sh.dx * surge;
                    s.uz += sh.dz * surge;
                }
            }
        }
        // batas kecepatan arus (blok/tick) supaya badai penuh tetap masuk akal
        double sp = Math.sqrt(s.ux * s.ux + s.uz * s.uz), cap = 0.12;
        if (sp > cap) {
            s.ux *= cap / sp;
            s.uz *= cap / sp;
        }
        return s;
    }

    /** Arah ke daratan terdekat dan kedekatannya (0..1), di-cache per 2x2 blok selama 20 tick. */
    private static ShoreInfo shore(World w, double x, double surfaceY, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        long key = ((long) (bx >> 1) << 32) ^ ((long) (bz >> 1) & 0xFFFFFFFFL);
        HashMap<Long, ShoreInfo> m = SHORE.get();
        long now = w.getTime();
        ShoreInfo c = m.get(key);
        if (c != null && now - c.stamp < 20 && now >= c.stamp) return c;
        if (m.size() > 600) m.clear();
        if (c == null) c = new ShoreInfo();
        c.stamp = now;

        final int R = 6;
        int by = (int) Math.floor(surfaceY - 0.01);
        BlockPos.Mutable p = new BlockPos.Mutable();
        double sx = 0, sz = 0, best = 0;
        for (int a = 0; a < 8; a++) {
            for (int d = 1; d <= R; d++) {
                p.set(bx + (int) Math.round(COS8[a] * d), by, bz + (int) Math.round(SIN8[a] * d));
                boolean land;
                if (!w.isChunkLoaded(p)) land = true;
                else land = !w.getFluidState(p).isIn(FluidTags.WATER);
                if (land) {
                    double wgt = 1.0 - (d - 1) / (double) R;
                    sx += COS8[a] * wgt;
                    sz += SIN8[a] * wgt;
                    if (wgt > best) best = wgt;
                    break;
                }
            }
        }
        double len = Math.sqrt(sx * sx + sz * sz);
        if (len < 1e-3) {
            c.dx = 0;
            c.dz = 0;
            c.prox = 0;
        } else {
            c.dx = (float) (sx / len);
            c.dz = (float) (sz / len);
            c.prox = (float) best;
        }
        m.put(key, c);
        return c;
    }

    // ------------------------------------------------------------------ helper benda

    /** Bagian tubuh (0..1) yang terendam di bawah puncak ombak. */
    public static double submerged(Entity e, Sample s) {
        double hgt = Math.max(0.2, e.getHeight());
        double f = (s.surfaceY - e.getY()) / hgt;
        return f < 0 ? 0 : (f > 1 ? 1 : f);
    }

    /** Apakah benda di dalam air: menyentuh air vanila, atau berada di dalam badan ombak di atasnya. */
    public static boolean wet(Entity e, Sample s) {
        if (s == null) return false;
        if (e.isTouchingWater()) return true;
        return WaveConfig.get().extendedWet && e.getY() < s.surfaceY;
    }

    /** Seret ke kecepatan air (benda pasif: item, blok, TNT). k per tick. */
    public static void drag(Entity e, Sample s, double k) {
        Vec3d v = e.getVelocity();
        e.setVelocity(v.x + (s.ux - v.x) * k, v.y, v.z + (s.uz - v.z) * k);
        e.velocityModified = true;
    }

    /** Dorong searah arus (benda yang punya tenaga sendiri: mob, pemain, kapal). */
    public static void push(Entity e, Sample s, double k) {
        Vec3d v = e.getVelocity();
        e.setVelocity(v.x + s.ux * k, v.y, v.z + s.uz * k);
        e.velocityModified = true;
    }
}
