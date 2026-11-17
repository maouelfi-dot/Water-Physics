package com.oceanphysics.mixin;

import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jalan di client DAN server (client pengendara menentukan posisi kapal);
 * deterministik karena hanya memakai world time + seed.
 *
 * Vanilla (IN_WATER): v.y = (v.y - 0.04 + 0.0615 * e) * 0.75, e = (waterLevel - y) / 0.5625.
 * Menambah 0.1093 * h per tick menggeser titik setimbang naik tepat h blok.
 */
@Mixin(BoatEntity.class)
public abstract class BoatEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void oceanphysics$waves(CallbackInfo ci) {
        BoatEntity boat = (BoatEntity) (Object) this;
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || cfg.boatFollowStrength <= 0 || !boat.isTouchingWater()) return;

        World w = boat.getWorld();
        double surface = WaveMath.findSurface(w, boat.getX(), boat.getY(), boat.getZ());
        if (Double.isNaN(surface)) return;

        double h = WaveMath.height(w, boat.getX(), surface, boat.getZ(), 0f);
        double[] s = WaveMath.slope(w, boat.getX(), surface, boat.getZ(), 0f);

        double kv = 0.1093 * cfg.boatFollowStrength;
        Vec3d v = boat.getVelocity();
        boat.setVelocity(v.x - s[0] * cfg.boatSlide, v.y + kv * h, v.z - s[1] * cfg.boatSlide);
    }
}
