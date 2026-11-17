package com.oceanphysics.mixin;

import com.oceanphysics.WaterPhysics;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kereta tambang di air: hambatan dan hanyut oleh arus ombak (server). */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartEntityMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void oceanphysics$drag(CallbackInfo ci) {
        WaterPhysics.body((AbstractMinecartEntity) (Object) this, false, 0.92, 0.9, -0.12);
    }
}
