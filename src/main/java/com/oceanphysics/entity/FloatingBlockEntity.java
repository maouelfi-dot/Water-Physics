package com.oceanphysics.entity;

import com.oceanphysics.ModContent;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Blok yang mengapung dan bergoyang mengikuti ombak. Bisa diinjak pemain. */
public class FloatingBlockEntity extends Entity {
    private static final TrackedData<Integer> STATE_ID =
            DataTracker.registerData(FloatingBlockEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private int noWaterTicks = 0;

    public FloatingBlockEntity(EntityType<?> type, World world) {
        super(type, world);
    }

    public void setState(BlockState state) {
        this.dataTracker.set(STATE_ID, Block.getRawIdFromState(state));
    }

    public BlockState getState() {
        return Block.getStateFromRawId(this.dataTracker.get(STATE_ID));
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STATE_ID, Block.getRawIdFromState(Blocks.OAK_PLANKS.getDefaultState()));
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        WaveConfig cfg = WaveConfig.get();
        World w = this.getWorld();
        double surface = WaveMath.findSurface(w, getX(), getY() + 0.5, getZ());
        if (Double.isNaN(surface)) {
            if (++noWaterTicks > 20) placeBack();
            return;
        }
        noWaterTicks = 0;

        double h = 0, sx = 0, sz = 0;
        if (cfg.enabled) {
            h = WaveMath.height(w, getX(), surface, getZ(), 0f);
            double[] s = WaveMath.slope(w, getX(), surface, getZ(), 0f);
            sx = s[0];
            sz = s[1];
        }
        double target = surface + h - 0.6; // sekitar 60% terendam
        double dy = MathHelper.clamp((target - getY()) * 0.3, -0.3, 0.3);
        this.setVelocity(Vec3d.ZERO);
        this.move(MovementType.SELF, new Vec3d(-sx * cfg.blockSlide, dy, -sz * cfg.blockSlide));
        this.setVelocity(Vec3d.ZERO);
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        if (player.getStackInHand(hand).isOf(ModContent.FLOAT_WAND)) {
            if (!this.getWorld().isClient) placeBack();
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    /** Ubah kembali jadi blok biasa. */
    public void placeBack() {
        World w = this.getWorld();
        BlockState state = getState();
        BlockPos pos = BlockPos.ofFloored(getX(), getY() + 0.5, getZ());
        BlockState there = w.getBlockState(pos);
        if (there.isAir() || there.isReplaceable()) {
            w.setBlockState(pos, state);
        } else {
            this.dropStack(new ItemStack(state.getBlock()));
        }
        this.discard();
    }

    @Override
    public boolean canHit() {
        return !this.isRemoved();
    }

    @Override
    public boolean isCollidable() {
        return true;
    }

    @Override
    public boolean isPushedByFluids() {
        return false;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        if (nbt.contains("State")) {
            setState(NbtHelper.toBlockState(
                    this.getWorld().createCommandRegistryWrapper(RegistryKeys.BLOCK), nbt.getCompound("State")));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.put("State", NbtHelper.fromBlockState(getState()));
    }
}
