package com.oceanphysics.item;

import com.oceanphysics.ModContent;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.entity.FloatingBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** Klik kanan blok di tepi/atas air: jadi blok terapung. Klik kanan blok terapung: kembali jadi blok. */
public class FloatWandItem extends Item {
    public FloatWandItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World w = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        BlockState st = w.getBlockState(pos);
        PlayerEntity player = ctx.getPlayer();

        if (!WaveConfig.get().floatingBlocks) return ActionResult.PASS;
        if (st.isAir() || st.hasBlockEntity() || st.getBlock() instanceof FluidBlock
                || st.getHardness(w, pos) < 0) {
            return ActionResult.PASS;
        }

        boolean nearWater = st.getFluidState().isIn(FluidTags.WATER);
        for (Direction d : Direction.values()) {
            if (w.getFluidState(pos.offset(d)).isIn(FluidTags.WATER)) nearWater = true;
        }
        if (!nearWater) {
            if (player != null && !w.isClient) {
                player.sendMessage(Text.literal("Blok harus menyentuh air."), true);
            }
            return ActionResult.FAIL;
        }
        if (w.isClient) return ActionResult.SUCCESS;

        FloatingBlockEntity e = new FloatingBlockEntity(ModContent.FLOATING_BLOCK, w);
        e.setState(st);
        e.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
        w.setBlockState(pos, st.getFluidState().getBlockState(), Block.NOTIFY_ALL);
        w.spawnEntity(e);
        return ActionResult.SUCCESS;
    }
}
