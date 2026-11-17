package com.oceanphysics;

/**
 * Jendela 64x64 sel permukaan air (ring buffer, ikut pemain) berisi data kolom air dan
 * simulasi riak: persamaan gelombang 2D  v += c2 * laplacian(h);  h += v.
 * Kelas ini murni Java (tanpa Minecraft) supaya mudah diuji dan sangat ringan.
 */
public final class WaterGrid {
    public static final int N = 64;
    private static final int MASK = N - 1;
    public static final long NEVER = Long.MIN_VALUE / 2;

    private final int[] ownX = new int[N * N], ownZ = new int[N * N];
    private final boolean[] used = new boolean[N * N];
    public final boolean[] valid = new boolean[N * N];
    public final float[] surface = new float[N * N], depth = new float[N * N];
    public final float[] h = new float[N * N], v = new float[N * N];
    public final int[] light = new int[N * N], rgb = new int[N * N];
    public final long[] stamp = new long[N * N];

    public int cell = 1;

    public static int idx(int gx, int gz) {
        return ((gz & MASK) << 6) | (gx & MASK);
    }

    public void clear() {
        java.util.Arrays.fill(used, false);
        java.util.Arrays.fill(valid, false);
        java.util.Arrays.fill(h, 0f);
        java.util.Arrays.fill(v, 0f);
    }

    /** Ambil indeks sel; jika sel itu milik koordinat lain, direset. */
    public int acquire(int gx, int gz) {
        int i = idx(gx, gz);
        if (!used[i] || ownX[i] != gx || ownZ[i] != gz) {
            used[i] = true;
            ownX[i] = gx;
            ownZ[i] = gz;
            valid[i] = false;
            h[i] = 0f;
            v[i] = 0f;
            stamp[i] = NEVER;
        }
        return i;
    }

    public boolean stale(int i, long time) {
        return time - stamp[i] > 100;
    }

    /** Indeks sel air valid di (gx,gz), atau -1. */
    public int lookup(int gx, int gz) {
        int i = idx(gx, gz);
        if (used[i] && ownX[i] == gx && ownZ[i] == gz && valid[i]) return i;
        return -1;
    }

    private float nb(int gx, int gz, float fallback) {
        int j = lookup(gx, gz);
        return j >= 0 ? h[j] : fallback; // tepi/pantai memantulkan gelombang
    }

    /** Satu langkah simulasi (1 tick) untuk sel di sekitar (cx,cz). */
    public void step(int cx, int cz, int half, float c2, float damping) {
        for (int gz = cz - half; gz <= cz + half; gz++) {
            for (int gx = cx - half; gx <= cx + half; gx++) {
                int i = lookup(gx, gz);
                if (i < 0) continue;
                float hc = h[i];
                float lap = nb(gx - 1, gz, hc) + nb(gx + 1, gz, hc)
                        + nb(gx, gz - 1, hc) + nb(gx, gz + 1, hc) - 4f * hc;
                v[i] = (v[i] + c2 * lap) * damping;
            }
        }
        for (int gz = cz - half; gz <= cz + half; gz++) {
            for (int gx = cx - half; gx <= cx + half; gx++) {
                int i = lookup(gx, gz);
                if (i < 0) continue;
                float nh = (h[i] + v[i]) * 0.9985f;
                if (nh > 0.6f) nh = 0.6f; else if (nh < -0.6f) nh = -0.6f;
                h[i] = nh;
            }
        }
    }

    /** Dorong permukaan di posisi dunia (wx,wz): amount negatif = tekan ke bawah. */
    public void splat(double wx, double wz, float amount) {
        double fx = wx / cell - 0.5, fz = wz / cell - 0.5;
        int gx = (int) Math.floor(fx), gz = (int) Math.floor(fz);
        float tx = (float) (fx - gx), tz = (float) (fz - gz);
        add(gx, gz, amount * (1 - tx) * (1 - tz));
        add(gx + 1, gz, amount * tx * (1 - tz));
        add(gx, gz + 1, amount * (1 - tx) * tz);
        add(gx + 1, gz + 1, amount * tx * tz);
    }

    private void add(int gx, int gz, float a) {
        int i = lookup(gx, gz);
        if (i >= 0) {
            float nh = h[i] + a;
            h[i] = nh > 0.6f ? 0.6f : (nh < -0.6f ? -0.6f : nh);
        }
    }
}
