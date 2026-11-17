package com.oceanphysics;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Pengganti logika aliran air vanila (server): air = massa terbatas, satuan 1/8 blok per sel.
 *  - jatuh: air turun ke sel di bawahnya sampai penuh
 *  - menyebar: air meratakan ketinggian dengan tetangga (bejana berhubungan), volume TETAP
 *  - tidak ada sumber tak terbatas: ember air = 8 satuan yang menyebar dan mengisi cekungan
 * Disimpan langsung sebagai blok air vanila (level 0 = penuh, 1..7 = 7/8..1/8), jadi tersimpan di dunia
 * dan renderer/arus/entity vanila tetap bekerja. Tiap tick dibatasi supaya tidak lag.
 */
public final class RealFluid {
    private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static long lastTime = Long.MIN_VALUE;
    private static World lastWorld;
    private static int used;

    private RealFluid() {}

    private static int amount(BlockState s) {
        int l = s.get(FluidBlock.LEVEL);
        return (l == 0 || l >= 8) ? 8 : 8 - l;
    }

    /** -1 = tidak bisa menerima air; selain itu jumlah air saat ini (0..8). */
    private static int accept(World w, BlockPos p) {
        if (w.isOutOfHeightLimit(p) || !w.isChunkLoaded(p)) return -1;
        BlockState s = w.getBlockState(p);
        if (s.isOf(Blocks.WATER)) return amount(s);
        if (!s.getFluidState().isEmpty()) return -1; // lava, waterlogged
        if (s.isAir() || s.isReplaceable()) return 0;
        return -1;
    }

    private static void set(World w, BlockPos p, int a) {
        BlockState old = w.getBlockState(p);
        if (a > 0 && !old.isAir() && !old.isOf(Blocks.WATER)) w.breakBlock(p, true); // tanaman dll. hanyut
        BlockState ns;
        if (a >= 8) ns = Blocks.WATER.getDefaultState();
        else if (a <= 0) ns = Blocks.AIR.getDefaultState();
        else ns = Blocks.WATER.getDefaultState().with(FluidBlock.LEVEL, 8 - a);
        if (ns != old) w.setBlockState(p, ns);
    }

    public static void tick(World w, BlockPos pos) {
        WaveConfig cfg = WaveConfig.get();
        if (w.isClient()) return;
        BlockState bs = w.getBlockState(pos);
        if (!bs.isOf(Blocks.WATER)) return;

        long t = w.getTime();
        if (t != lastTime || w != lastWorld) {
            lastTime = t;
            lastWorld = w;
            used = 0;
        }
        int rate = Math.max(1, cfg.flowTickRate);
        if (used++ >= cfg.realFluidPerTick) {
            w.scheduleFluidTick(pos, Fluids.WATER, 1);
            return;
        }

        int a = amount(bs);
        boolean changed = false;

        // 1) jatuh
        BlockPos down = pos.down();
        int ad = accept(w, down);
        if (ad >= 0 && ad < 8) {
            int tr = Math.min(a, 8 - ad);
            a -= tr;
            set(w, down, ad + tr);
            changed = true;
        }

        // 2) meratakan dengan tetangga (selisih >= 2 supaya tidak bolak-balik)
        if (a > 0) {
            int start = w.random.nextInt(4);
            for (int k = 0; k < 4 && a > 0; k++) {
                BlockPos np = pos.offset(DIRS[(start + k) & 3]);
                int an = accept(w, np);
                if (an < 0) continue;
                int d = a - an;
                if (d >= 2) {
                    int tr = d / 2;
                    a -= tr;
                    set(w, np, an + tr);
                    changed = true;
                }
            }
        }

        if (changed) {
            set(w, pos, a);
            if (a > 0) w.scheduleFluidTick(pos, Fluids.WATER, rate);
        }
    }
}
