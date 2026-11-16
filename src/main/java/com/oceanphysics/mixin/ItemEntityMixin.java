package com.oceanphysics.mixin;

import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item yang jatuh ke laut mengapung dan ikut naik-turun/hanyut mengikuti ombak (server). */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void oceanphysics$float(CallbackInfo ci) {
        ItemEntity e = (ItemEntity) (Object) this;
        WaveConfig cfg = WaveConfig.get();
        World w = e.getWorld();
        if (w.isClient() || !cfg.enabled || !cfg.itemBobbing || !e.isTouchingWater()) return;

        double surface = WaveMath.findSurface(w, e.getX(), e.getY(), e.getZ());
        if (Double.isNaN(surface)) return;
        double h = WaveMath.height(w, e.getX(), surface, e.getZ(), 0f);
        double dy = (surface + h - 0.1) - e.getY();
        if (dy < -0.8 || dy > 1.5) return; // hanya item yang dekat permukaan

        double[] s = WaveMath.slope(w, e.getX(), surface, e.getZ(), 0f);
        Vec3d v = e.getVelocity();
        double vy = v.y + (dy * 0.2 - v.y) * 0.35;
        e.setVelocity(v.x - s[0] * 0.004, vy, v.z - s[1] * 0.004);
        e.velocityModified = true;
    }
}
