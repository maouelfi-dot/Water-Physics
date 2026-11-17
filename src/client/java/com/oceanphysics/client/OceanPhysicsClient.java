package com.oceanphysics.client;

import com.oceanphysics.OceanPhysicsMod;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.world.World;

public class OceanPhysicsClient implements ClientModInitializer {
    private static boolean foamSpawnerFailed = false;
    private static boolean rippleFailed = false;

    @Override
    public void onInitializeClient() {
        // riak client ikut mempengaruhi fisika entity di dekat pemain (kapal, pemain, item, mob)
        com.oceanphysics.RippleHook.provider = (x, z) -> {
            com.oceanphysics.WaterGrid g = WaterMeshRenderer.GRID;
            int c = g.cell;
            int i = g.lookup((int) Math.floor(x / c), (int) Math.floor(z / c));
            return i < 0 ? 0.0 : g.h[i];
        };
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            swim(client.player, client.world);
            if (!rippleFailed) {
                try {
                    RippleClient.tick(client);
                } catch (Throwable t) {
                    rippleFailed = true;
                    OceanPhysicsMod.LOGGER.error("Riak dimatikan karena error", t);
                }
            }
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

        double k = cfg.swellStrength;
        double vy = WaveMath.verticalVelocityPerTick(w, p.getX(), surface, p.getZ());
        double[] s = WaveMath.slope(w, p.getX(), surface, p.getZ(), 0f);
        double h = WaveMath.height(w, p.getX(), surface, p.getZ(), 0f);
        double ax = -s[0] * 0.006 * k, az = -s[1] * 0.006 * k, ay = vy * 0.9 * k;

        // daya apung: pegas-redam menuju garis air saat pemain diam di permukaan (Sneak = menyelam)
        if (cfg.playerFloat && !p.isSneaking() && !p.isSwimming()) {
            double target = surface + h - 1.15;
            double dy = target - p.getY();
            if (dy > -0.9 && dy < 0.9) ay += dy * 0.035 - p.getVelocity().y * 0.08;
        }
        p.addVelocity(ax, ay, az);
    }
}
