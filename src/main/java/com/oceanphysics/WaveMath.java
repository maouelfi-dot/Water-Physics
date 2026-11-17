package com.oceanphysics;

import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

import java.util.Random;

/**
 * Jumlah gelombang sinus dengan dispersi air dalam (omega = sqrt(g*k)),
 * arah/fase acak dari seed, ditambah modulasi lambat supaya polanya tidak berulang.
 * Murni matematika, tanpa simulasi fluida, jadi ringan dan IDENTIK di server dan client.
 *
 * v8: tinggi gelombang bergantung pada "badai" (hujan/petir dunia), dan ada medan kecepatan air
 * (orbital + drift Stokes) yang dipakai semua fisika benda. Hanya AMPLITUDO yang berubah saat badai
 * (bukan panjang gelombang/frekuensi), supaya fase tidak melompat ketika cuaca berganti.
 */
public final class WaveMath {
    private static final int N = 8;
    private static final double G = 9.81;
    private static final double[] KX = new double[N], KZ = new double[N], KK = new double[N];
    private static final double[] AMP = new double[N], OMEGA = new double[N], PHASE = new double[N];
    private static double envPhase1, envPhase2;
    /** Drift Stokes aslinya sangat kecil; diperbesar supaya terlihat di game. */
    private static final double DRIFT_GAIN = 1.5;

    private WaveMath() {}

    public static void init(long seed) {
        Random r = new Random(seed);
        WaveConfig cfg = WaveConfig.get();
        double base = Math.toRadians(cfg.windAngleDegrees);
        double lambda = 72.0, sum = 0;
        for (int i = 0; i < N; i++) {
            double ang = base + Math.toRadians((r.nextDouble() - 0.5) * 120.0);
            double k = 2 * Math.PI / lambda;
            KX[i] = Math.cos(ang) * k;
            KZ[i] = Math.sin(ang) * k;
            KK[i] = k;
            OMEGA[i] = Math.sqrt(G * k);
            PHASE[i] = r.nextDouble() * Math.PI * 2;
            AMP[i] = Math.pow(lambda, 0.8);
            sum += AMP[i];
            lambda /= 1.55;
        }
        for (int i = 0; i < N; i++) AMP[i] /= sum; // total amplitudo = 1
        envPhase1 = r.nextDouble() * 6.28;
        envPhase2 = r.nextDouble() * 6.28;
    }

    // ------------------------------------------------------------------ badai

    /** Level badai 0..1 dari cuaca dunia (hujan 0.55 + petir 0.45). Sama di server dan client. */
    public static double storm(World w) {
        if (!WaveConfig.get().stormWaves) return 0.0;
        float r = w.getRainGradient(1.0f);
        float t = w.getThunderGradient(1.0f);
        double s = r * 0.55 + t * 0.45;
        return s < 0.0 ? 0.0 : (s > 1.0 ? 1.0 : s);
    }

    /** Pengali amplitudo total pada level badai tertentu. */
    public static double boost(double storm) {
        return 1.0 + WaveConfig.get().stormWaveBoost * storm;
    }

    /** Tinggi puncak ombak maksimum (blok) pada badai penuh, dibulatkan ke atas. */
    public static int maxWaveHeight() {
        WaveConfig c = WaveConfig.get();
        return (int) Math.ceil(c.waveSize.amplitude * boost(c.stormWaves ? 1.0 : 0.0));
    }

    private static double envelope(double x, double z, double tt) {
        return 0.65 + 0.35 * Math.sin(0.011 * x + 0.007 * z + 0.05 * tt + envPhase1)
                * Math.sin(0.009 * z - 0.013 * x - 0.03 * tt + envPhase2);
    }

    // ------------------------------------------------------------------ medan gelombang

    /** Tinggi gelombang (blok) tanpa faktor kedalaman. t dalam detik. */
    public static double rawHeight(double x, double z, double t) {
        return rawHeight(x, z, t, 0.0);
    }

    public static double rawHeight(double x, double z, double t, double storm) {
        WaveConfig cfg = WaveConfig.get();
        double tt = t * cfg.waveSpeed;
        double ch = cfg.stormChoppy * storm;
        double s = 0;
        for (int i = 0; i < N; i++) {
            // badai menguatkan gelombang pendek (kasar/berombak), hanya amplitudo, fase tetap
            double wgt = AMP[i] * (1.0 + ch * i / (N - 1.0));
            s += wgt * Math.sin(KX[i] * x + KZ[i] * z - OMEGA[i] * tt + PHASE[i]);
        }
        double env = envelope(x, z, tt);
        // 0..1, puncak dipertajam (chop) lalu x amplitudo; selalu >= sedikit di atas air vanilla
        double u = 0.5 + 0.5 * Math.max(-1.0, Math.min(1.0, s * (1.8 + 0.9 * storm) * env));
        u = Math.pow(Math.max(0.0, Math.min(1.0, u)), 1.0 + cfg.chop * (1.0 + 0.6 * storm));
        return (0.12 + 0.88 * u) * cfg.waveSize.amplitude * boost(storm);
    }

    /**
     * Kecepatan partikel air akibat gelombang, blok/DETIK (belum dikali faktor kedalaman).
     * out[0], out[1] = ux, uz (orbital + drift Stokes); out[2] = komponen orbital bertanda searah rambat
     * (positif di puncak = maju, negatif di lembah = mundur) untuk gerak hempas pantai/undertow.
     * Teori linear: u_i = a_i * omega_i * sin(theta_i) searah k_i; drift = a_i^2 * k_i * omega_i.
     */
    public static void orbital(double x, double z, double t, double storm, double[] out) {
        WaveConfig cfg = WaveConfig.get();
        double tt = t * cfg.waveSpeed;
        double ch = cfg.stormChoppy * storm;
        double a0 = cfg.waveSize.amplitude * boost(storm) * 0.79 * envelope(x, z, tt);
        double ux = 0, uz = 0, us = 0, dx = 0, dz = 0;
        for (int i = 0; i < N; i++) {
            double wgt = AMP[i] * (1.0 + ch * i / (N - 1.0));
            double th = KX[i] * x + KZ[i] * z - OMEGA[i] * tt + PHASE[i];
            double a = a0 * wgt;
            double om = OMEGA[i] * cfg.waveSpeed;
            double kk = KK[i];
            double orb = a * om * Math.sin(th);
            ux += orb * KX[i] / kk;
            uz += orb * KZ[i] / kk;
            us += orb;
            double dr = a * a * kk * om;
            dx += dr * KX[i] / kk;
            dz += dr * KZ[i] / kk;
        }
        double dg = DRIFT_GAIN * cfg.stokesDrift;
        out[0] = ux + dx * dg;
        out[1] = uz + dz * dg;
        out[2] = us;
    }

    public static double timeSeconds(World w, float partial) {
        return (w.getTime() + partial) / 20.0;
    }

    // ------------------------------------------------------------------ mencari permukaan

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

    /**
     * Seperti findSurface, tapi juga menemukan air di BAWAH sampai `reach` blok (benda yang sedang
     * menunggangi puncak ombak yang tingginya di atas permukaan vanila). Berhenti di blok padat.
     */
    public static double findSurfaceTall(World w, double x, double y, double z, int reach) {
        double s = findSurface(w, x, y, z);
        if (!Double.isNaN(s) || reach <= 0) return s;
        BlockPos.Mutable p = new BlockPos.Mutable(Math.floor(x), Math.floor(y) - 2, Math.floor(z));
        for (int i = 0; i <= reach; i++) {
            FluidState fs = w.getFluidState(p);
            if (fs.isIn(FluidTags.WATER)) return p.getY() + fs.getHeight(w, p);
            if (!w.getBlockState(p).getCollisionShape(w, p).isEmpty()) return Double.NaN;
            p.move(0, -1, 0);
        }
        return Double.NaN;
    }

    // ------------------------------------------------------------------ faktor kedalaman

    /** 0..1, kecil di perairan dangkal, kolam tertutup, dan air mengalir. */
    public static double depthFactor(World w, double x, double surfaceY, double z) {
        return depthParts(w, x, surfaceY, z, null);
    }

    /**
     * Sama seperti depthFactor, tetapi mengisi parts (jika tidak null):
     * [0] = faktor kedalaman saja, [1] = keterbukaan perairan 0..1, [2] = 1 untuk air tenang, 0.12 untuk air mengalir.
     */
    public static double depthParts(World w, double x, double surfaceY, double z, double[] parts) {
        double full = WaveConfig.get().fullDepth;
        BlockPos.Mutable p = new BlockPos.Mutable(Math.floor(x), Math.floor(surfaceY) - 1, Math.floor(z));
        int depth = 0;
        int max = (int) Math.ceil(full);
        while (depth < max && w.getFluidState(p).isIn(FluidTags.WATER)) {
            depth++;
            p.move(0, -1, 0);
        }
        double f = Math.min(1.0, depth / full);
        double m = WaveConfig.get().minDepthFactor;
        double dep = m + (1.0 - m) * (f * f * (3 - 2 * f)); // smoothstep dengan batas bawah
        // kolam tertutup tidak boleh menggembung seperti laut: kurangi ombak jika air di sekitar sedikit
        double open = openness(w, x, surfaceY, z);
        p.set((int) Math.floor(x), (int) Math.floor(surfaceY - 0.001), (int) Math.floor(z));
        FluidState top = w.getFluidState(p);
        double still = (top.isEmpty() || top.isStill()) ? 1.0 : 0.12; // air mengalir hampir datar
        if (parts != null) {
            parts[0] = dep;
            parts[1] = open;
            parts[2] = still;
        }
        return dep * (0.25 + 0.75 * open) * still;
    }

    /** 0..1: seberapa luas air terbuka di sekeliling titik (8 sampel pada jarak 5 blok). */
    public static double openness(World w, double x, double surfaceY, double z) {
        int y = (int) Math.floor(surfaceY - 0.01);
        BlockPos.Mutable p = new BlockPos.Mutable();
        int cnt = 0;
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4.0;
            p.set((int) Math.floor(x + Math.cos(a) * 5.0), y, (int) Math.floor(z + Math.sin(a) * 5.0));
            if (w.getFluidState(p).isIn(FluidTags.WATER)) cnt++;
        }
        return cnt / 8.0;
    }

    // ------------------------------------------------------------------ tinggi total

    /** Tinggi total di atas permukaan vanilla di titik yang faktor kedalamannya sudah diketahui. */
    public static double heightAt(double x, double z, double t, double storm, double df) {
        double h = rawHeight(x, z, t, storm) * df;
        if (WaveConfig.get().ripples) h += RippleHook.height(x, z) * Math.max(df, 0.3);
        return h;
    }

    /** Tinggi total di atas permukaan vanilla (sudah termasuk faktor kedalaman dan badai). */
    public static double height(World w, double x, double surfaceY, double z, float partial) {
        double d = depthFactor(w, x, surfaceY, z);
        return heightAt(x, z, timeSeconds(w, partial), storm(w), d);
    }

    /** Permukaan air paling atas di kolom (x,z), termasuk air mengalir. NaN jika tidak ada atau air jatuh. */
    public static double topWaterSurface(World w, int x, int z) {
        int top = w.getTopY(Heightmap.Type.WORLD_SURFACE, x, z) - 1;
        BlockPos.Mutable p = new BlockPos.Mutable(x, top, z);
        for (int i = 0; i < 5; i++) {
            FluidState fs = w.getFluidState(p);
            if (fs.isIn(FluidTags.WATER)) {
                if (fs.contains(FlowableFluid.FALLING) && fs.get(FlowableFluid.FALLING)) return Double.NaN;
                return p.getY() + fs.getHeight(w, p);
            }
            if (w.getBlockState(p).isOpaque()) return Double.NaN; // tanah/batu padat: berhenti
            p.move(0, -1, 0);
        }
        return Double.NaN;
    }

    /** Turunan tinggi terhadap x dan z (kemiringan), hasil [dhdx, dhdz]. */
    public static double[] slope(World w, double x, double surfaceY, double z, float partial) {
        double t = timeSeconds(w, partial);
        double storm = storm(w);
        double e = 0.5;
        double d = depthFactor(w, x, surfaceY, z);
        double dx = (rawHeight(x + e, z, t, storm) - rawHeight(x - e, z, t, storm)) / (2 * e) * d;
        double dz = (rawHeight(x, z + e, t, storm) - rawHeight(x, z - e, t, storm)) / (2 * e) * d;
        if (WaveConfig.get().ripples) {
            double r = 1.0;
            dx += (RippleHook.height(x + r, z) - RippleHook.height(x - r, z)) / (2 * r);
            dz += (RippleHook.height(x, z + r) - RippleHook.height(x, z - r)) / (2 * r);
        }
        return new double[]{dx, dz};
    }

    /** Kecepatan vertikal permukaan, blok per tick. */
    public static double verticalVelocityPerTick(World w, double x, double surfaceY, double z) {
        double t = timeSeconds(w, 0);
        double dt = 0.05;
        double d = depthFactor(w, x, surfaceY, z);
        double storm = storm(w);
        return (rawHeight(x, z, t + dt, storm) - rawHeight(x, z, t - dt, storm)) / (2 * dt) / 20.0 * d;
    }
}
