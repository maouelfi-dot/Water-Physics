package com.oceanphysics.mixin;

import com.oceanphysics.WaterPhysics;
import com.oceanphysics.WaveConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pasir/kerikil yang jatuh ke air melambat oleh hambatan air dan memicu percikan. */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {
    @Unique private boolean oceanphysics$wet;
    @Unique private double oceanphysics$lastVy;

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void oceanphysics$drag(CallbackInfo ci) {
        Entity e = (Entity) (Object) this;
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || e.getWorld().isClient()) return;
        boolean now = e.isTouchingWater();
        if (now && !oceanphysics$wet) WaterPhysics.shock(e.getWorld(), e, Math.abs(oceanphysics$lastVy) * 1.5);
        oceanphysics$wet = now;
        if (now && cfg.fallingBlockDrag) {
            Vec3d v = e.getVelocity();
            e.setVelocity(v.x * 0.9, Math.max(v.y * 0.88, -0.14), v.z * 0.9);
            e.velocityModified = true;
        }
        oceanphysics$lastVy = e.getVelocity().y;
    }
}
