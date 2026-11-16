package com.oceanphysics.client;

import com.oceanphysics.ModContent;
import com.oceanphysics.OceanPhysicsMod;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import com.oceanphysics.client.render.FloatingBlockRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;

public class OceanPhysicsClient implements ClientModInitializer {
    private static boolean foamSpawnerFailed = false;

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModContent.FLOATING_BLOCK, FloatingBlockRenderer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            swim(client.player, client.world);
            try {
                FoamClient.tick(client);
            } catch (Throwable t) {
                if (!FoamClient.failed) {
                    FoamClient.failed = true;
                    OceanPhysicsMod.LOGGER.error("Buih kapal dimatikan karena error", t);
                }
            }
            if (!foamSpawnerFailed && !client.isPaused()) {
                try {
                    FoamSpawner.tick(client);
                } catch (Throwable t) {
                    foamSpawnerFailed = true;
                    OceanPhysicsMod.LOGGER.error("Busa ombak dimatikan karena error", t);
                }
            }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(WaterMeshRenderer::render);
    }

    private static void swim(ClientPlayerEntity p, World w) {
        WaveConfig cfg = WaveConfig.get();
        if (p == null || w == null || !cfg.enabled || !cfg.swimBobbing) return;
        if (p.hasVehicle() || !p.isTouchingWater() || p.getAbilities().flying) return;

        double surface = WaveMath.findSurface(w, p.getX(), p.getY() + 0.9, p.getZ());
        if (Double.isNaN(surface)) return;
        if (p.getY() < surface - 1.7) return; // sedang menyelam dalam

        double vy = WaveMath.verticalVelocityPerTick(w, p.getX(), surface, p.getZ());
        double[] s = WaveMath.slope(w, p.getX(), surface, p.getZ(), 0f);
        p.addVelocity(-s[0] * 0.003, vy * 0.6, -s[1] * 0.003);
    }
}
