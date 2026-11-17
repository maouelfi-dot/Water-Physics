package com.oceanphysics.mixin;

import com.oceanphysics.WaterPhysics;
import com.oceanphysics.WaveConfig;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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

        WaterPhysics.mob(e);
    }
}
