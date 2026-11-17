package com.oceanphysics.client.mixin;

import com.oceanphysics.WaveConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tampilan fisika item: item yang di udara berputar (tumbling), item yang mendarat tergeletak diam
 * (item pipih rebah rata di tanah, blok duduk di tanah), tanpa melayang naik-turun.
 * Semua injeksi require=0: jika signature berbeda, fitur ini diam saja dan game tetap jalan.
 */
@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    private static final String RENDER =
            "render(Lnet/minecraft/entity/ItemEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";

    @Unique private ItemEntity oceanphysics$cur;

    @Unique
    private static boolean oceanphysics$onGround(ItemEntity e) {
        return e.isOnGround() && !e.isTouchingWater();
    }

    @Inject(method = RENDER, at = @At("HEAD"), require = 0)
    private void oceanphysics$begin(ItemEntity e, float yaw, float tickDelta, MatrixStack m,
                                    VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        oceanphysics$cur = e;
    }

    /** Hilangkan efek melayang naik-turun saat item di tanah. */
    @Redirect(method = RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;sin(F)F"), require = 0)
    private float oceanphysics$noBob(float v) {
        ItemEntity e = oceanphysics$cur;
        WaveConfig cfg = WaveConfig.get();
        if (e != null && cfg.enabled && cfg.itemPhysics && oceanphysics$onGround(e)) return -1.0f; // k = 0
        return (float) Math.sin(v);
    }

    /** Putaran yaw: tetap saat di tanah, berputar cepat saat di udara. */
    @Redirect(method = RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/ItemEntity;getRotation(F)F"), require = 0)
    private float oceanphysics$yaw(ItemEntity e, float tickDelta) {
        WaveConfig cfg = WaveConfig.get();
        if (cfg.enabled && cfg.itemPhysics) {
            if (oceanphysics$onGround(e)) return e.uniqueOffset * 6.2831855f;
            if (!e.isTouchingWater()) return (e.getItemAge() + tickDelta) * 0.45f + e.uniqueOffset;
        }
        return (e.getItemAge() + tickDelta) / 20.0f + e.uniqueOffset;
    }

    /** Rebahkan item pipih di tanah / putar jungkir balik di udara. */
    @Inject(method = RENDER,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;multiply(Lorg/joml/Quaternionf;)V",
                    shift = At.Shift.AFTER), require = 0)
    private void oceanphysics$pose(ItemEntity e, float yaw, float tickDelta, MatrixStack m,
                                   VertexConsumerProvider vcp, int light, CallbackInfo ci) {
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || !cfg.itemPhysics) return;

        if (oceanphysics$onGround(e)) {
            MinecraftClient mc = MinecraftClient.getInstance();
            BakedModel model = mc.getItemRenderer().getModel(e.getStack(), e.getWorld(), null, e.getId());
            if (model != null && !model.hasDepth()) {
                m.translate(0.0f, -0.105f, 0.0f);
                m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
            }
        } else if (!e.isTouchingWater()) {
            float t = e.getItemAge() + tickDelta;
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(t * 23.0f + e.uniqueOffset * 57.0f));
            m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(t * 13.0f));
        }
    }
}
