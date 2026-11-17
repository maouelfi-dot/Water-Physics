package com.oceanphysics;

/**
 * Jembatan antara simulasi riak (client) dan fisika (common).
 * Di dedicated server provider kosong, jadi tinggi riak = 0 dan tidak ada efek.
 */
public final class RippleHook {
    public interface Provider {
        double height(double x, double z);
    }

    public static volatile Provider provider;

    private RippleHook() {}

    public static double height(double x, double z) {
        Provider p = provider;
        return p == null ? 0.0 : p.height(x, z);
    }
}
