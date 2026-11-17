package com.oceanphysics.mixin;

import com.oceanphysics.RealFluid;
import com.oceanphysics.WaveConfig;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.WaterFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mengganti logika aliran air vanila dengan RealFluid (air bermassa terbatas). */
@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {
    @Inject(method = "onScheduledTick", at = @At("HEAD"), cancellable = true, require = 0)
    private void oceanphysics$real(World world, BlockPos pos, FluidState state, CallbackInfo ci) {
        if (!((Object) this instanceof WaterFluid)) return;
        WaveConfig c = WaveConfig.get();
        if (!c.enabled || !c.realFluid) return;
        RealFluid.tick(world, pos);
        ci.cancel();
    }
}
