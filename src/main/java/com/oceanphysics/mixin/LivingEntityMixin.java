package com.oceanphysics.mixin;

import com.oceanphysics.WaterPhysics;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mob di air ikut naik-turun dan hanyut oleh ombak; mob besar yang masuk air memicu gelombang kejut. Hanya di server. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Unique private boolean oceanphysics$wet;
    @Unique private double oceanphysics$lastVy;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void oceanphysics$swell(CallbackInfo ci) {
        LivingEntity e = (LivingEntity) (Object) this;
        WaveConfig cfg = WaveConfig.get();
        World w = e.getWorld();
        if (w.isClient() || !cfg.enabled || e instanceof PlayerEntity) return;

        boolean now = e.isTouchingWater();
        if (now && !oceanphysics$wet) {
            WaterPhysics.shock(w, e, Math.abs(oceanphysics$lastVy) * Math.min(1.6, 0.6 + e.getWidth()));
        }
        oceanphysics$wet = now;
        oceanphysics$lastVy = e.getVelocity().y;

        if (!cfg.mobBobbing || e.hasVehicle() || !now) return;
        double surface = WaveMath.findSurface(w, e.getX(), e.getY() + e.getHeight() * 0.5, e.getZ());
        if (Double.isNaN(surface)) return;
        if (e.getY() < surface - 1.5) return; // hanya yang dekat permukaan

        double vy = WaveMath.verticalVelocityPerTick(w, e.getX(), surface, e.getZ());
        double[] s = WaveMath.slope(w, e.getX(), surface, e.getZ(), 0f);
        Vec3d v = e.getVelocity();
        double k = cfg.swellStrength;
        e.setVelocity(v.x - s[0] * 0.004 * k, v.y + vy * 0.9 * k, v.z - s[1] * 0.004 * k);
    }
}
