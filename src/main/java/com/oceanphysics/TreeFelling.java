package com.oceanphysics;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Pohon roboh: saat satu batang kayu pohon ditebang, seluruh batang di atasnya (yang tersambung) menjadi
 * balok jatuh yang terdorong menjauhi pemain (makin tinggi makin jauh = rebah), daun ikut berjatuhan.
 * Balok jatuh memakai fisika air/ombak yang sama, dan mendarat jadi balok lagi atau menjatuhkan item.
 * Hanya dianggap pohon jika kumpulan batang punya cukup daun (rumah kayu aman).
 */
public final class TreeFelling {
    private TreeFelling() {}

    public static void register() {
        PlayerBlockBreakEvents.AFTER.register(TreeFelling::onBreak);
    }

    private static void onBreak(World w, PlayerEntity pl, BlockPos pos, BlockState state, BlockEntity be) {
        WaveConfig cfg = WaveConfig.get();
        if (w.isClient() || !cfg.enabled || !cfg.treeFall || !state.isIn(BlockTags.LOGS)) return;
        try {
            fell(w, pl, pos);
        } catch (Throwable t) {
            OceanPhysicsMod.LOGGER.error("Pohon roboh error (dilewati)", t);
        }
    }

    private static void fell(World w, PlayerEntity pl, BlockPos origin) {
        WaveConfig cfg = WaveConfig.get();
        ArrayList<BlockPos> logs = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        ArrayList<BlockPos> queue = new ArrayList<>();
        queue.add(origin);
        seen.add(origin.asLong());
        int leaves = 0;
        for (int qi = 0; qi < queue.size() && logs.size() < cfg.treeMaxLogs; qi++) {
            BlockPos p = queue.get(qi);
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dy == 0 && dz == 0) continue;
                BlockPos n = p.add(dx, dy, dz);
                if (Math.abs(n.getX() - origin.getX()) > 8 || Math.abs(n.getZ() - origin.getZ()) > 8) continue;
                if (!seen.add(n.asLong())) continue;
                BlockState ns = w.getBlockState(n);
                if (ns.isIn(BlockTags.LOGS)) {
                    logs.add(n);
                    queue.add(n);
                } else if (ns.isIn(BlockTags.LEAVES)) {
                    leaves++;
                }
            }
        }
        if (logs.isEmpty() || leaves < 6) return;

        double dirX = origin.getX() + 0.5 - pl.getX(), dirZ = origin.getZ() + 0.5 - pl.getZ();
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 0.01) { dirX = 1; dirZ = 0; len = 1; }
        dirX /= len;
        dirZ /= len;

        logs.sort((a, b) -> Integer.compare(a.getY(), b.getY()));
        for (BlockPos p : logs) topple(w, p, origin, dirX, dirZ, 0.03);

        // daun di sekitar batang ikut jatuh (dibatasi), sisanya luruh biasa
        int budget = cfg.treeMaxLeaves;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (BlockPos lp : logs) {
            for (int dx = -2; dx <= 2 && budget > 0; dx++) for (int dy = -1; dy <= 2 && budget > 0; dy++) for (int dz = -2; dz <= 2 && budget > 0; dz++) {
                m.set(lp.getX() + dx, lp.getY() + dy, lp.getZ() + dz);
                BlockState ls = w.getBlockState(m);
                if (ls.isIn(BlockTags.LEAVES) && w.random.nextInt(3) == 0) {
                    topple(w, m.toImmutable(), origin, dirX, dirZ, 0.02);
                    budget--;
                }
            }
        }
    }

    private static void topple(World w, BlockPos p, BlockPos origin, double dirX, double dirZ, double base) {
        BlockState s = w.getBlockState(p);
        if (s.isAir()) return;
        FallingBlockEntity fe = FallingBlockEntity.spawnFromBlock(w, p, s);
        double up = Math.max(0, p.getY() - origin.getY());
        double sp = Math.min(0.5, base + 0.028 * up); // makin tinggi, makin kencang rebah
        double jx = (w.random.nextDouble() - 0.5) * 0.04, jz = (w.random.nextDouble() - 0.5) * 0.04;
        fe.setVelocity(dirX * sp + jx, 0.04, dirZ * sp + jz);
        fe.velocityModified = true;
    }
}
