package com.oceanphysics.mixin;

import com.oceanphysics.WaveConfig;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Aliran air nyata: air mengalir lebih cepat (tick lebih sering) dan mencari turunan lebih jauh. Server. */
@Mixin(WaterFluid.class)
public abstract class WaterFluidMixin {
    @Inject(method = "getTickRate", at = @At("HEAD"), cancellable = true, require = 0)
    private void oceanphysics$tick(WorldView world, CallbackInfoReturnable<Integer> cir) {
        WaveConfig c = WaveConfig.get();
        if (c.enabled && c.fastFlow) cir.setReturnValue(c.flowTickRate);
    }

    @Inject(method = "getMaxFlowDistance", at = @At("HEAD"), cancellable = true, require = 0)
    private void oceanphysics$dist(WorldView world, CallbackInfoReturnable<Integer> cir) {
        WaveConfig c = WaveConfig.get();
        if (c.enabled && c.fastFlow) cir.setReturnValue(c.flowDistance);
    }
}
