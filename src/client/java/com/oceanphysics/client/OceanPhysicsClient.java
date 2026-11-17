package com.oceanphysics.client;

import com.oceanphysics.OceanPhysicsMod;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaterForces;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
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
        WorldRenderEvents.AFTER_TRANSLUCENT.register(FluidSim::render);
        ClientTickEvents.END_CLIENT_TICK.register(FluidSim::tick);
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack st = player.getStackInHand(hand);
            if (!world.isClient()) return TypedActionResult.pass(st);
            BlockPos t = com.oceanphysics.SpillRules.target(player, world, st);
            if (t == null) return TypedActionResult.pass(st);
            FluidSim.spill((ClientWorld) world, t, WaveConfig.get().spillPerBucket);
            return TypedActionResult.success(st);
        });
    }

    /**
     * Pemain (client menentukan posisinya): ikut permukaan ombak, didorong arus ombak, dan mengapung.
     * Saat badai, ombak bisa lebih tinggi dari air vanila: pemain di dalam badan ombak tetap diperlakukan di air.
     */
    private static void swim(ClientPlayerEntity p, World w) {
        WaveConfig cfg = WaveConfig.get();
        if (p == null || w == null || !cfg.enabled || !cfg.swimBobbing) return;
        if (p.hasVehicle() || p.getAbilities().flying || p.isSpectator()) return;
        boolean touching = p.isTouchingWater();
        if (!touching && !cfg.extendedWet) return;

        WaterForces.Sample s = WaterForces.sample(w, p.getX(), p.getY() + 0.9, p.getZ());
        if (s == null) return;
        double depthFeet = s.surfaceY - p.getY(); // kaki di bawah puncak ombak
        if (!touching && depthFeet <= 0.0) return;
        if (touching && p.getY() < s.vanillaY - 1.7) return; // sedang menyelam dalam

        double f = Math.max(0.0, Math.min(1.0, depthFeet / 1.8));
        double k = cfg.swellStrength;
        Vec3d v = p.getVelocity();
        double ax = 0.0, az = 0.0, ay = s.uy * 0.9 * k;
        if (cfg.waterCurrent) {
            ax = s.ux * 0.06 * k * f;
            az = s.uz * 0.06 * k * f;
        }
        // daya apung: pegas-redam menuju garis air saat pemain diam di permukaan (Sneak = menyelam)
        if (cfg.playerFloat && !p.isSneaking() && !p.isSwimming()) {
            double target = s.surfaceY - 1.15;
            double dy = target - p.getY();
            if (dy > -0.9 && dy < 0.9) ay += dy * 0.035 - v.y * 0.08;
        }
        if (!touching) ay += 0.08 * f; // di dalam badan ombak tapi di atas air vanila: tahan gravitasi
        p.addVelocity(ax, ay, az);
    }
}
