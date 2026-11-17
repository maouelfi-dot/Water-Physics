package com.oceanphysics.client.mixin;

import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import com.oceanphysics.WaveTilt;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.BoatEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Memiringkan kapal mengikuti lereng ombak. require=0 agar tidak crash jika signature berbeda. */
@Mixin(BoatEntityRenderer.class)
public abstract class BoatEntityRendererMixin {
    @Inject(
        method = "render(Lnet/minecraft/entity/vehicle/BoatEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
        at = @At("HEAD"), require = 0)
    private void oceanphysics$tilt(BoatEntity boat, float yaw, float tickDelta, MatrixStack m,
                                   VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || !cfg.boatTilt) return;
        // goyang fisika pegas-peredam (BoatRig dari BoatEntityMixin): oleng, memantul, lalu tenang
        if (cfg.boatRig && ((Object) boat) instanceof WaveTilt) {
            WaveTilt wt = (WaveTilt) (Object) boat;
            if (wt.oceanphysics$hasRig()) {
                m.multiply(RotationAxis.POSITIVE_X.rotation(wt.oceanphysics$rotX(tickDelta)));
                m.multiply(RotationAxis.POSITIVE_Z.rotation(wt.oceanphysics$rotZ(tickDelta)));
                return;
            }
        }
        if (!boat.isTouchingWater()) return;
        double x = MathHelper.lerp(tickDelta, boat.prevX, boat.getX());
        double y = MathHelper.lerp(tickDelta, boat.prevY, boat.getY());
        double z = MathHelper.lerp(tickDelta, boat.prevZ, boat.getZ());
        double surface = WaveMath.findSurface(boat.getWorld(), x, y, z);
        if (Double.isNaN(surface)) return;
        double[] s = WaveMath.slope(boat.getWorld(), x, surface, z, tickDelta);
        float max = (float) Math.toRadians(25);
        float pitch = MathHelper.clamp((float) -Math.atan(s[1] * 1.5), -max, max);
        float roll = MathHelper.clamp((float) Math.atan(s[0] * 1.5), -max, max);
        m.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
        m.multiply(RotationAxis.POSITIVE_Z.rotation(roll));
    }
}
