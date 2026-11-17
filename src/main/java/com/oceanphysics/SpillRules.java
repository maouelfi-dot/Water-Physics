package com.oceanphysics;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsage;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Aturan tumpah ember air (dipakai server dan client agar keputusannya sama). */
public final class SpillRules {
    private SpillRules() {}

    /** Posisi blok kosong tempat air tumpah, atau null jika ember dipakai normal (vanila). */
    public static BlockPos target(PlayerEntity p, World w, ItemStack s) {
        WaveConfig c = WaveConfig.get();
        if (!c.enabled || !c.spill || !s.isOf(Items.WATER_BUCKET)) return null;
        if (!c.spillAlways && !p.isSneaking()) return null;
        HitResult h = p.raycast(5.0, 1.0f, true);
        if (h.getType() != HitResult.Type.BLOCK) return null;
        BlockHitResult b = (BlockHitResult) h;
        if (w.getFluidState(b.getBlockPos()).isIn(FluidTags.WATER)) return null; // ke kolam: vanila
        return b.getBlockPos().offset(b.getSide());
    }

    /** Server: ember air jadi ember kosong, suara tumpah. Client menggambar cairannya sendiri. */
    public static void register() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack st = player.getStackInHand(hand);
            if (world.isClient()) return TypedActionResult.pass(st);
            BlockPos t = target(player, world, st);
            if (t == null) return TypedActionResult.pass(st);
            world.playSound(null, t.getX() + 0.5, t.getY() + 0.5, t.getZ() + 0.5,
                    SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1.0f, 1.0f);
            return TypedActionResult.success(ItemUsage.exchangeStack(st, player, new ItemStack(Items.BUCKET)));
        });
    }
}
