package com.oceanphysics.client;

import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;

import java.util.Random;

/** Busa di puncak ombak dan di perairan dangkal, murah: hanya beberapa sampel per tick. */
public final class FoamSpawner {
    private static final Random R = new Random();

    private FoamSpawner() {}

    public static void tick(MinecraftClient mc) {
        WaveConfig cfg = WaveConfig.get();
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        if (w == null || p == null || !cfg.enabled || !cfg.foam || cfg.foamSamples <= 0) return;

        double ref = WaveMath.findSurface(w, p.getX(), p.getY(), p.getZ());
        if (Double.isNaN(ref)) ref = WaveMath.findSurface(w, p.getX(), p.getY() - 1, p.getZ());
        if (Double.isNaN(ref)) return;

        double t = WaveMath.timeSeconds(w, 0f);
        double amp = cfg.waveSize.amplitude;

        for (int i = 0; i < cfg.foamSamples; i++) {
            double ang = R.nextDouble() * Math.PI * 2;
            double d = 3 + Math.sqrt(R.nextDouble()) * cfg.foamRadius;
            double x = p.getX() + Math.cos(ang) * d;
            double z = p.getZ() + Math.sin(ang) * d;

            double s = WaveMath.findSurface(w, x, ref - 0.5, z);
            if (Double.isNaN(s)) continue;

            double df = WaveMath.depthFactor(w, x, s, z);
            double raw = WaveMath.rawHeight(x, z, t);
            double h = raw * df;
            double crest = h / amp;

            double chance = 0;
            if (crest > 0.55) chance = (crest - 0.55) / 0.45;         // puncak ombak
            if (df < 0.5) {                                           // dekat pantai
                double shore = (0.5 - df) * 2.0;
                chance = Math.max(chance, raw / amp > 0.1 ? shore : shore * 0.3);
            }
            if (R.nextDouble() < chance * 0.35 * cfg.foamIntensity) {
                w.addParticle(ParticleTypes.CLOUD, x, s + h + 0.02, z, 0.0, 0.0, 0.0);
            }
        }
    }
}
