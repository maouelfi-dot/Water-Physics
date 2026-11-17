package com.oceanphysics;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * Ombak badai menyapu blok rapuh di tepi air (obor, bunga, tebu, redstone, rumput, dll.).
 * Blok pecah dan jatuh sebagai item, lalu item itu ikut terseret ombak dan arus hempas pantai.
 * Hanya blok non-padat yang rapuh (kekerasan <= 0.25); bangunan padat tidak disentuh.
 * Murah: hanya beberapa sampel acak per detik per pemain, dan hanya saat level badai cukup tinggi.
 */
public final class WaveErosion {
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private WaveErosion() {}

    public static boolean fragile(ServerWorld w, BlockPos pos, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        if (!s.getCollisionShape(w, pos).isEmpty()) return false;
        float hard = s.getHardness(w, pos);
        return hard >= 0.0f && hard <= 0.25f;
    }

    public static void tick(ServerWorld w) {
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || !cfg.washAway || cfg.washPerSecond <= 0) return;
        if (w.getTime() % 10 != 0) return;
        double storm = WaveMath.storm(w);
        if (storm < cfg.washStormMin) return;
        double strength = Math.min(1.0, (storm - cfg.washStormMin) / Math.max(0.05, 1.0 - cfg.washStormMin) + 0.2);

        int n = Math.max(1, cfg.washPerSecond / 2); // dipanggil 2x per detik
        double t = WaveMath.timeSeconds(w, 0f);
        BlockPos.Mutable q = new BlockPos.Mutable();
        for (PlayerEntity pl : w.getPlayers()) {
            for (int i = 0; i < n; i++) {
                int x = (int) Math.floor(pl.getX()) + w.random.nextInt(57) - 28;
                int z = (int) Math.floor(pl.getZ()) + w.random.nextInt(57) - 28;
                q.set(x, 0, z);
                if (!w.isChunkLoaded(q)) continue;
                double surf = WaveMath.topWaterSurface(w, x, z);
                if (Double.isNaN(surf)) continue;
                double df = WaveMath.depthFactor(w, x + 0.5, surf, z + 0.5);
                double reach = WaveMath.heightAt(x + 0.5, z + 0.5, t, storm, df); // seberapa tinggi ombak di sini
                int yw = (int) Math.floor(surf - 0.001);
                for (Direction d : SIDES) {
                    for (int dy = 0; dy <= 2; dy++) {
                        BlockPos bp = new BlockPos(x + d.getOffsetX(), yw + dy, z + d.getOffsetZ());
                        if (!w.isChunkLoaded(bp)) break;
                        BlockState bs = w.getBlockState(bp);
                        if (!fragile(w, bp, bs)) continue;
                        // ombak harus cukup tinggi untuk mencapai dasar blok itu
                        if (bp.getY() > surf + reach * 0.9) continue;
                        if (w.random.nextDouble() > 0.5 * strength) continue;
                        w.spawnParticles(ParticleTypes.SPLASH, bp.getX() + 0.5, bp.getY() + 0.3, bp.getZ() + 0.5,
                                8, 0.3, 0.2, 0.3, 0.1);
                        w.breakBlock(bp, true);
                    }
                }
            }
        }
    }
}
