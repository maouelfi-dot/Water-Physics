package com.oceanphysics.client;

import com.oceanphysics.WaveConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.Random;

/** Buih dan percikan di belakang kapal yang bergerak. (Busa ombak/pantai ada di FoamSpawner.) */
public final class FoamClient {
    public static boolean failed = false;
    private static final Random RNG = new Random();

    private FoamClient() {}

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        WaveConfig cfg = WaveConfig.get();
        if (failed || w == null || p == null || mc.isPaused() || !cfg.enabled || !cfg.foam) return;

        Box box = p.getBoundingBox().expand(48);
        List<BoatEntity> boats = w.getEntitiesByClass(BoatEntity.class, box, b -> b.isTouchingWater());
        for (BoatEntity b : boats) {
            double sp = b.getVelocity().horizontalLength();
            if (sp < 0.06) continue;
            double yaw = Math.toRadians(b.getYaw());
            double fx = -Math.sin(yaw), fz = Math.cos(yaw);
            for (int k = 0; k < 2; k++) {
                double px = b.getX() - fx * 0.9 + (RNG.nextDouble() - 0.5) * 1.2;
                double pz = b.getZ() - fz * 0.9 + (RNG.nextDouble() - 0.5) * 1.2;
                w.addParticle(ParticleTypes.SPLASH, px, b.getY() + 0.15, pz,
                        -fx * sp * 0.3 + (RNG.nextDouble() - 0.5) * 0.05, 0.1,
                        -fz * sp * 0.3 + (RNG.nextDouble() - 0.5) * 0.05);
            }
            if (sp > 0.2) {
                w.addParticle(ParticleTypes.CLOUD, b.getX() + fx * 1.2, b.getY() + 0.2, b.getZ() + fz * 1.2, 0, 0, 0);
            }
        }
    }
}
