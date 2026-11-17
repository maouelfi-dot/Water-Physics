package com.oceanphysics.mixin;

import com.oceanphysics.BoatRig;
import com.oceanphysics.WaterForces;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import com.oceanphysics.WaveTilt;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Kapal: jalan di client DAN server (client pengendara menentukan posisi kapal); deterministik karena hanya
 * memakai world time + seed + cuaca dunia.
 *
 * Vertikal: tinggi ombak dirata-rata dari 5 titik (tengah + 4 sisi). Saat masih menyentuh air vanila,
 * setimbang digeser (+0.1093 * h per tick). Saat sudah naik ke dalam badan ombak di atas air vanila
 * (ombak badai), diterapkan fisika air vanila dengan "permukaan" = puncak ombak:
 * v.y = (v.y + 0.0615 * e) * 0.75, e = (waterLevel - y) / 0.5625.
 * Horizontal: didorong arus ombak + meluncur turun lereng.
 * Goyang: sudut pitch/roll dikejar dengan pegas-peredam (BoatRig) supaya kapal oleng dan memantul balik.
 */
@Mixin(BoatEntity.class)
public abstract class BoatEntityMixin implements WaveTilt {
    @Unique private BoatRig oceanphysics$rig;

    @Unique
    private BoatRig oceanphysics$rig() {
        if (oceanphysics$rig == null) oceanphysics$rig = new BoatRig();
        return oceanphysics$rig;
    }

    @Override
    public boolean oceanphysics$hasRig() {
        return oceanphysics$rig != null;
    }

    @Override
    public float oceanphysics$rotX(float tickDelta) {
        return oceanphysics$rig().lerpX(tickDelta);
    }

    @Override
    public float oceanphysics$rotZ(float tickDelta) {
        return oceanphysics$rig().lerpZ(tickDelta);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void oceanphysics$waves(CallbackInfo ci) {
        BoatEntity boat = (BoatEntity) (Object) this;
        WaveConfig cfg = WaveConfig.get();
        World w = boat.getWorld();
        boolean client = w.isClient();
        boolean rigOn = client && cfg.boatTilt && cfg.boatRig;
        float max = (float) Math.toRadians(28);

        if (!cfg.enabled || cfg.boatFollowStrength <= 0) {
            if (rigOn) oceanphysics$rig().step(0f, 0f, cfg.boatRockStiffness, cfg.boatRockDamping, max);
            return;
        }
        WaterForces.Sample s = WaterForces.sample(w, boat.getX(), boat.getY() + 0.2, boat.getZ());
        boolean touching = boat.isTouchingWater();
        if (s == null || !(touching || (cfg.extendedWet && boat.getY() < s.surfaceY))) {
            if (rigOn) oceanphysics$rig().step(0f, 0f, cfg.boatRockStiffness, cfg.boatRockDamping, max);
            return;
        }

        // tinggi ombak di 5 titik (lambung ~ 1 blok dari pusat)
        final double R = 0.9;
        double t = WaveMath.timeSeconds(w, 0f);
        double x = boat.getX(), z = boat.getZ();
        double hE = WaveMath.heightAt(x + R, z, t, s.storm, s.df);
        double hW = WaveMath.heightAt(x - R, z, t, s.storm, s.df);
        double hS = WaveMath.heightAt(x, z + R, t, s.storm, s.df);
        double hN = WaveMath.heightAt(x, z - R, t, s.storm, s.df);
        double hAvg = (s.h * 2.0 + hE + hW + hS + hN) / 6.0;
        double dhdx = (hE - hW) / (2 * R), dhdz = (hS - hN) / (2 * R);

        Vec3d v = boat.getVelocity();
        double vy = v.y;
        if (touching) {
            vy += 0.1093 * cfg.boatFollowStrength * hAvg;
        } else {
            double e = (s.vanillaY + hAvg - boat.getY()) / 0.5625;
            if (e > 0) vy = (vy + 0.0615 * e * cfg.boatFollowStrength) * 0.75;
        }
        double vx = v.x - dhdx * cfg.boatSlide;
        double vz = v.z - dhdz * cfg.boatSlide;
        if (cfg.waterCurrent) {
            vx += s.ux * 0.07 * cfg.boatFollowStrength;
            vz += s.uz * 0.07 * cfg.boatFollowStrength;
        }
        boat.setVelocity(vx, vy, vz);

        if (rigOn) {
            float lim = (float) Math.toRadians(25);
            float tz = clampF((float) Math.atan(dhdx * 1.5), lim);
            float tx = clampF((float) -Math.atan(dhdz * 1.5), lim);
            oceanphysics$rig().step(tx, tz, cfg.boatRockStiffness, cfg.boatRockDamping, max);
        }
    }

    @Unique
    private static float clampF(float v, float lim) {
        return v < -lim ? -lim : (v > lim ? lim : v);
    }
}
