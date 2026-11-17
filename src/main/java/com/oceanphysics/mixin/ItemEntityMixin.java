package com.oceanphysics.mixin;

import com.oceanphysics.WaterPhysics;
import com.oceanphysics.WaveConfig;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fisika item (server): berat tenggelam / ringan mengapung di air, memantul di tanah,
 * gelombang kejut saat jatuh ke air, dan hanya bisa diambil saat pemain jongkok di dekatnya.
 */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {
    @Unique private boolean oceanphysics$wet;
    @Unique private boolean oceanphysics$ground;
    @Unique private double oceanphysics$lastVy;
    @Unique private int oceanphysics$bounces;

    @Inject(method = "tick", at = @At("TAIL"))
    private void oceanphysics$physics(CallbackInfo ci) {
        ItemEntity e = (ItemEntity) (Object) this;
        WaveConfig cfg = WaveConfig.get();
        boolean now = e.isTouchingWater();
        if (now && !oceanphysics$wet) WaterPhysics.shock(e.getWorld(), e, Math.abs(oceanphysics$lastVy) * 1.2);
        oceanphysics$wet = now;
        WaterPhysics.item(e);

        boolean ground = e.isOnGround();
        if (cfg.enabled && cfg.itemBounce && !e.getWorld().isClient() && !now) {
            if (ground && !oceanphysics$ground && oceanphysics$lastVy < -0.16 && oceanphysics$bounces < 4) {
                Vec3d v = e.getVelocity();
                e.setVelocity(v.x * 0.85, -oceanphysics$lastVy * 0.38, v.z * 0.85);
                e.velocityModified = true;
                oceanphysics$bounces++;
            } else if (ground && Math.abs(oceanphysics$lastVy) < 0.05) {
                oceanphysics$bounces = 0;
            }
        }
        oceanphysics$ground = ground && e.getVelocity().y <= 0.0;
        oceanphysics$lastVy = e.getVelocity().y;
    }

    /** Ambil item hanya saat jongkok (Sneak) di dekat item. */
    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void oceanphysics$sneakPickup(PlayerEntity player, CallbackInfo ci) {
        WaveConfig cfg = WaveConfig.get();
        if (cfg.enabled && cfg.sneakPickup && !player.isSneaking()) ci.cancel();
    }
}
