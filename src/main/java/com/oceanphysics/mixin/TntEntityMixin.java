package com.oceanphysics.mixin;

import com.oceanphysics.WaterPhysics;
import net.minecraft.entity.TntEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TNT menyala mengapung dan terombang-ambing ombak (server). */
@Mixin(TntEntity.class)
public abstract class TntEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void oceanphysics$float(CallbackInfo ci) {
        WaterPhysics.body((TntEntity) (Object) this, true, 0.97, 1.0, -0.2);
    }
}
