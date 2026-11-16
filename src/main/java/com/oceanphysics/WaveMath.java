package com.oceanphysics;

import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Jumlah gelombang sinus dengan dispersi air dalam (omega = sqrt(g*k)),
 * arah/fase acak dari seed, ditambah modulasi lambat supaya polanya tidak berulang.
 * Murni matematika, tanpa simulasi fluida, jadi ringan.
 */
public final class WaveMath {
    private static final int N = 8;
    private static final double G = 9.81;
    private static final double[] KX = new double[N], KZ = new double[N];
    private static final double[] AMP = new double[N], OMEGA = new double[N], PHASE = new double[N];
    private static double envPhase1, envPhase2;

    private WaveMath() {}

    public static void init(long seed) {
        Random r = new Random(seed);
        WaveConfig cfg = WaveConfig.get();
        double base = Math.toRadians(cfg.windAngleDegrees);
        double lambda = 64.0, sum = 0;
        for (int i = 0; i < N; i++) {
            double ang = base + Math.toRadians((r.nextDouble() - 0.5) * 120.0);
            double k = 2 * Math.PI / lambda;
            KX[i] = Math.cos(ang) * k;
            KZ[i] = Math.sin(ang) * k;
            OMEGA[i] = Math.sqrt(G * k);
            PHASE[i] = r.nextDouble() * Math.PI * 2;
            AMP[i] = Math.pow(lambda, 0.8);
            sum += AMP[i];
            lambda /= 1.618;
        }
        for (int i = 0; i < N; i++) AMP[i] /= sum; // total amplitudo = 1
        envPhase1 = r.nextDouble() * 6.28;
        envPhase2 = r.nextDouble() * 6.28;
    }

    /** Tinggi gelombang (blok) tanpa faktor kedalaman. t dalam detik. */
    public static double rawHeight(double x, double z, double t) {
        WaveConfig cfg = WaveConfig.get();
        double tt = t * cfg.waveSpeed;
        double s = 0;
        for (int i = 0; i < N; i++) {
            s += AMP[i] * Math.sin(KX[i] * x + KZ[i] * z - OMEGA[i] * tt + PHASE[i]);
        }
        double env = 0.65 + 0.35 * Math.sin(0.011 * x + 0.007 * z + 0.05 * tt + envPhase1)
                * Math.sin(0.009 * z - 0.013 * x - 0.03 * tt + envPhase2);
        return s * env * cfg.waveSize.amplitude;
    }

    public static double timeSeconds(World w, float partial) {
        return (w.getTime() + partial) / 20.0;
    }

    /** Cari permukaan air di sekitar (x,y,z). NaN jika tidak ada. */
    public static double findSurface(World w, double x, double y, double z) {
        BlockPos.Mutable p = new BlockPos.Mutable(Math.floor(x), Math.floor(y), Math.floor(z));
        // naik sampai air habis (maks 4 blok)
        for (int i = 0; i < 4; i++) {
            if (w.getFluidState(p.up()).isIn(FluidTags.WATER)) p.move(0, 1, 0); else break;
        }
        FluidState fs = w.getFluidState(p);
        if (!fs.isIn(FluidTags.WATER)) {
            // mungkin y sedikit di atas air
            p.move(0, -1, 0);
            fs = w.getFluidState(p);
            if (!fs.isIn(FluidTags.WATER)) return Double.NaN;
        }
        return p.getY() + fs.getHeight(w, p);
    }

    /** 0..1, kecil di perairan dangkal. */
    public static double depthFactor(World w, double x, double surfaceY, double z) {
        double full = WaveConfig.get().fullDepth;
        BlockPos.Mutable p = new BlockPos.Mutable(Math.floor(x), Math.floor(surfaceY) - 1, Math.floor(z));
        int depth = 0;
        int max = (int) Math.ceil(full);
        while (depth < max && w.getFluidState(p).isIn(FluidTags.WATER)) {
            depth++;
            p.move(0, -1, 0);
        }
        double f = Math.min(1.0, depth / full);
        return f * f * (3 - 2 * f); // smoothstep
    }

    /** Tinggi total (sudah termasuk pengangkatan rata-rata dan faktor kedalaman). */
    public static double height(World w, double x, double surfaceY, double z, float partial) {
        WaveConfig cfg = WaveConfig.get();
        double lift = cfg.waveSize.amplitude * cfg.waveLift;
        return (rawHeight(x, z, timeSeconds(w, partial)) + lift) * depthFactor(w, x, surfaceY, z);
    }

    /** Permukaan air diam paling atas di kolom (x,z) memakai heightmap. NaN jika bukan air diam. */
    public static double topWaterSurface(World w, int x, int z) {
        int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        BlockPos pos = new BlockPos(x, top, z);
        FluidState fs = w.getFluidState(pos);
        if (!fs.isIn(FluidTags.WATER) || !fs.isStill()) return Double.NaN;
        return top + fs.getHeight(w, pos);
    }

    /** Turunan tinggi terhadap x dan z (kemiringan), hasil [dhdx, dhdz]. */
    public static double[] slope(World w, double x, double surfaceY, double z, float partial) {
        double t = timeSeconds(w, partial);
        double e = 0.5;
        double d = depthFactor(w, x, surfaceY, z);
        double dx = (rawHeight(x + e, z, t) - rawHeight(x - e, z, t)) / (2 * e) * d;
        double dz = (rawHeight(x, z + e, t) - rawHeight(x, z - e, t)) / (2 * e) * d;
        return new double[]{dx, dz};
    }

    /** Kecepatan vertikal permukaan, blok per tick. */
    public static double verticalVelocityPerTick(World w, double x, double surfaceY, double z) {
        double t = timeSeconds(w, 0);
        double dt = 0.05;
        double d = depthFactor(w, x, surfaceY, z);
        return (rawHeight(x, z, t + dt) - rawHeight(x, z, t - dt)) / (2 * dt) / 20.0 * d;
    }
}
