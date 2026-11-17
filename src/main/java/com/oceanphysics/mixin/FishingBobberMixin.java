package com.oceanphysics.mixin;

import com.oceanphysics.WaveBuoyancy;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pelampung pancing ikut naik-turun dengan ombak. Pegas vanilla rata-rata 0.1 per blok. */
@Mixin(FishingBobberEntity.class)
public abstract class FishingBobberMixin {
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void oceanphysics$bob(CallbackInfo ci) {
        WaveBuoyancy.nudgeSpring((FishingBobberEntity) (Object) this, 0.1);
    }
}
