package com.oceanphysics.client;

import com.oceanphysics.OceanPhysicsMod;
import com.oceanphysics.SpillRules;
import com.oceanphysics.WaveConfig;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.fluid.FluidState;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;

/**
 * Simulasi cairan berbasis partikel (SPH sederhana) untuk air tumpah:
 * gravitasi, tolakan/tekanan antar partikel, viskositas, kohesi, tabrakan dengan blok dunia,
 * didorong entity, menyatu saat masuk kolam. Dirender sebagai lapisan genangan halus
 * (medan ketebalan dari kernel partikel) + tetesan di udara. Murni client.
 */
public final class FluidSim {
    private static final int CAP = 1200;
    private static final double H = 0.45;   // radius interaksi
    private static final double S = 0.30;   // jarak istirahat antar partikel
    private static final Random R = new Random();

    private static int n;
    private static final double[] px = new double[CAP], py = new double[CAP], pz = new double[CAP];
    private static final float[] vx = new float[CAP], vy = new float[CAP], vz = new float[CAP];
    private static final int[] life = new int[CAP], ground = new int[CAP], next = new int[CAP];
    private static final Long2IntOpenHashMap head = new Long2IntOpenHashMap();
    static { head.defaultReturnValue(-1); }

    private static ClientWorld lastWorld;
    private static boolean failed;
    private static double lastTop;

    // medan ketebalan untuk render
    private static final int CELLS = CAP * 25;
    private static final Long2IntOpenHashMap cellIdx = new Long2IntOpenHashMap();
    static { cellIdx.defaultReturnValue(-1); }
    private static final int[] cci = new int[CELLS], ccj = new int[CELLS], clight = new int[CELLS];
    private static final float[] cw = new float[CELLS], csy = new float[CELLS], cth = new float[CELLS];
    private static final double[] cmin = new double[CELLS];

    private FluidSim() {}

    public static int count() { return n; }

    private static void removeAt(int i) {
        int l = n - 1;
        if (i != l) {
            px[i] = px[l]; py[i] = py[l]; pz[i] = pz[l];
            vx[i] = vx[l]; vy[i] = vy[l]; vz[i] = vz[l];
            life[i] = life[l]; ground[i] = ground[l];
        }
        n--;
    }

    /** Tumpahkan air di blok kosong t. */
    public static void spill(ClientWorld w, BlockPos t, int count) {
        if (failed) return;
        WaveConfig cfg = WaveConfig.get();
        int cap = Math.max(50, Math.min(CAP, cfg.spillMax));
        double cx = t.getX() + 0.5, cy = t.getY() + 0.4, cz = t.getZ() + 0.5;
        for (int k = 0; k < count; k++) {
            if (n >= cap) removeAt(0);
            int i = n++;
            px[i] = cx + (R.nextDouble() - 0.5) * 0.5;
            py[i] = cy + R.nextDouble() * 0.5;
            pz[i] = cz + (R.nextDouble() - 0.5) * 0.5;
            vx[i] = (float) ((R.nextDouble() - 0.5) * 0.10);
            vy[i] = (float) (R.nextDouble() * 0.05);
            vz[i] = (float) ((R.nextDouble() - 0.5) * 0.10);
            life[i] = cfg.spillLifeSeconds * 20 + R.nextInt(80);
            ground[i] = 0;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("Air tumpah: " + n + " partikel"), true);
    }

    // 0 bebas, 1 padat (lastTop = permukaan), 2 air/fluida (partikel menyatu), 3 chunk belum dimuat
    private static int classify(ClientWorld w, double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        if (!w.getChunkManager().isChunkLoaded(bx >> 4, bz >> 4)) return 3;
        BlockPos.Mutable mp = new BlockPos.Mutable(bx, by, bz);
        BlockState bs = w.getBlockState(mp);
        FluidState fs = bs.getFluidState();
        if (!fs.isEmpty() && y < by + Math.max(0.1f, fs.getHeight(w, mp))) return 2;
        VoxelShape sh = bs.getCollisionShape(w, mp);
        if (sh.isEmpty()) return 0;
        double top = by + sh.getMax(Direction.Axis.Y);
        if (y < top) {
            lastTop = top;
            return 1;
        }
        return 0;
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld w = mc.world;
        if (w != lastWorld) {
            n = 0;
            lastWorld = w;
        }
        WaveConfig cfg = WaveConfig.get();
        if (w == null || failed || n == 0 || mc.isPaused()) return;
        if (!cfg.enabled || !cfg.spill) {
            n = 0;
            return;
        }
        try {
            for (int i = n - 1; i >= 0; i--) {
                if (--life[i] <= 0) removeAt(i);
            }
            pushByEntities(mc, w);
            for (int sub = 0; sub < 2; sub++) {
                interact();
                integrate(w, 0.5f);
            }
            for (int i = 0; i < n; i++) if (ground[i] > 0) ground[i]--;
        } catch (Throwable t) {
            failed = true;
            n = 0;
            OceanPhysicsMod.LOGGER.error("Simulasi cairan dimatikan karena error", t);
            if (mc.player != null) mc.player.sendMessage(Text.literal("Water Physics: simulasi air tumpah MATI karena error: " + t), false);
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (long) (z & 0x1FFFFF);
    }

    private static void interact() {
        head.clear();
        for (int i = 0; i < n; i++) {
            long k = key((int) Math.floor(px[i] / H), (int) Math.floor(py[i] / H), (int) Math.floor(pz[i] / H));
            next[i] = head.put(k, i);
        }
        for (int i = 0; i < n; i++) {
            int ix = (int) Math.floor(px[i] / H), iy = (int) Math.floor(py[i] / H), iz = (int) Math.floor(pz[i] / H);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        int j = head.get(key(ix + dx, iy + dy, iz + dz));
                        while (j >= 0) {
                            if (j > i) pair(i, j);
                            j = next[j];
                        }
                    }
                }
            }
        }
    }

    private static void pair(int i, int j) {
        double dx = px[j] - px[i], dy = py[j] - py[i], dz = pz[j] - pz[i];
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 >= H * H || d2 < 1e-8) return;
        double d = Math.sqrt(d2);
        double nx = dx / d, ny = dy / d, nz = dz / d;
        if (d < S) {
            double corr = (S - d) * 0.25;
            px[i] -= nx * corr; py[i] -= ny * corr * 0.7; pz[i] -= nz * corr;
            px[j] += nx * corr; py[j] += ny * corr * 0.7; pz[j] += nz * corr;
            float pr = (float) ((S - d) * 0.03);
            vx[i] -= nx * pr; vy[i] -= ny * pr; vz[i] -= nz * pr;
            vx[j] += nx * pr; vy[j] += ny * pr; vz[j] += nz * pr;
        } else {
            float co = (float) ((H - d) * 0.0015);
            vx[i] += nx * co; vy[i] += ny * co; vz[i] += nz * co;
            vx[j] -= nx * co; vy[j] -= ny * co; vz[j] -= nz * co;
        }
        float vis = 0.05f * (float) (1.0 - d / H);
        float ax = (vx[j] - vx[i]) * vis, ay = (vy[j] - vy[i]) * vis, az = (vz[j] - vz[i]) * vis;
        vx[i] += ax; vy[i] += ay; vz[i] += az;
        vx[j] -= ax; vy[j] -= ay; vz[j] -= az;
    }

    private static void pushByEntities(MinecraftClient mc, ClientWorld w) {
        if (mc.player == null) return;
        List<Entity> list = w.getEntitiesByClass(Entity.class, mc.player.getBoundingBox().expand(24.0),
                e -> !e.isSpectator() && e.isAlive());
        int c = 0;
        for (Entity e : list) {
            if (++c > 24) break;
            Box b = e.getBoundingBox().expand(0.12, 0.0, 0.12);
            Vec3d ev = e.getVelocity();
            double sp = ev.horizontalLength();
            for (int i = 0; i < n; i++) {
                if (!b.contains(px[i], py[i], pz[i])) continue;
                double dx = px[i] - e.getX(), dz = pz[i] - e.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 0.01) { dx = R.nextDouble() - 0.5; dz = R.nextDouble() - 0.5; len = Math.sqrt(dx * dx + dz * dz) + 1e-4; }
                float push = (float) (0.05 + sp * 0.9);
                vx[i] += (float) (dx / len * push + ev.x * 0.4);
                vz[i] += (float) (dz / len * push + ev.z * 0.4);
                vy[i] += (float) (0.04 + sp * 0.5);
                ground[i] = 0;
            }
        }
    }

    private static void kill(int i, boolean splash) {
        if (splash) {
            WaterMeshRenderer.GRID.splat(px[i], pz[i], -0.05f);
        }
        removeAt(i);
    }

    private static void integrate(ClientWorld w, float dt) {
        for (int i = n - 1; i >= 0; i--) {
            int c = classify(w, px[i], py[i], pz[i]);
            if (c == 3) { removeAt(i); continue; }
            if (c == 2) { kill(i, true); continue; }
            if (c == 1) { py[i] = lastTop + 0.002; if (vy[i] < 0) vy[i] = 0; ground[i] = 3; }

            vy[i] -= 0.07f * dt;
            float drag = 1f - 0.01f * dt;
            vx[i] *= drag; vy[i] *= drag; vz[i] *= drag;

            double nx = px[i] + vx[i] * dt;
            c = classify(w, nx, py[i], pz[i]);
            if (c == 1) vx[i] *= -0.15f;
            else if (c == 2) { kill(i, true); continue; }
            else if (c == 3) { removeAt(i); continue; }
            else px[i] = nx;

            double nz = pz[i] + vz[i] * dt;
            c = classify(w, px[i], py[i], nz);
            if (c == 1) vz[i] *= -0.15f;
            else if (c == 2) { kill(i, true); continue; }
            else if (c == 3) { removeAt(i); continue; }
            else pz[i] = nz;

            double ny = py[i] + vy[i] * dt;
            c = classify(w, px[i], ny, pz[i]);
            if (c == 1) {
                if (vy[i] < 0) {
                    py[i] = lastTop + 0.002;
                    ground[i] = 3;
                    vy[i] = vy[i] < -0.25f ? -vy[i] * 0.12f : 0f;
                } else {
                    vy[i] = -vy[i] * 0.1f;
                }
            } else if (c == 2) { kill(i, true); continue; }
            else if (c == 3) { removeAt(i); continue; }
            else py[i] = ny;

            if (ground[i] > 0) { vx[i] *= 0.86f; vz[i] *= 0.86f; }
        }
    }

    // ---------------- render ----------------

    private static long ckey(int i, int j) {
        return ((long) i << 32) ^ (j & 0xFFFFFFFFL);
    }

    private static float smooth(float x) {
        x = x < 0 ? 0 : (x > 1 ? 1 : x);
        return x * x * (3 - 2 * x);
    }

    public static void render(WorldRenderContext ctx) {
        if (failed || n == 0) return;
        WaveConfig cfg = WaveConfig.get();
        ClientWorld world = ctx.world();
        if (!cfg.enabled || !cfg.spill || world == null || ctx.consumers() == null || ctx.matrixStack() == null) return;
        try {
            draw(ctx, world);
        } catch (Throwable t) {
            failed = true;
            OceanPhysicsMod.LOGGER.error("Render cairan dimatikan karena error", t);
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.sendMessage(Text.literal("Water Physics: render air tumpah MATI karena error: " + t), false);
        }
    }

    private static void draw(WorldRenderContext ctx, ClientWorld world) {
        final double C = 0.25, RR = 0.5, RR2 = RR * RR;
        Vec3d cam = ctx.camera().getPos();
        int rgb = BiomeColors.getWaterColor(world, BlockPos.ofFloored(cam.x, cam.y, cam.z));
        int cr = (rgb >> 16) & 255, cg = (rgb >> 8) & 255, cb = rgb & 255;

        // 1) medan ketebalan dari partikel yang menempel di tanah
        cellIdx.clear();
        int cnt = 0;
        for (int i = 0; i < n; i++) {
            if (ground[i] <= 0) continue;
            int cx0 = (int) Math.floor(px[i] / C), cz0 = (int) Math.floor(pz[i] / C);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int ci = cx0 + dx, cj = cz0 + dz;
                    double ddx = (ci + 0.5) * C - px[i], ddz = (cj + 0.5) * C - pz[i];
                    double d2 = ddx * ddx + ddz * ddz;
                    if (d2 >= RR2) continue;
                    float wgt = (float) (1.0 - d2 / RR2);
                    wgt *= wgt;
                    long k = ckey(ci, cj);
                    int idx = cellIdx.get(k);
                    if (idx < 0) {
                        if (cnt >= CELLS) continue;
                        idx = cnt++;
                        cellIdx.put(k, idx);
                        cci[idx] = ci; ccj[idx] = cj;
                        cw[idx] = 0f;
                        cmin[idx] = py[i];
                    }
                    cw[idx] += wgt;
                    if (py[i] < cmin[idx]) cmin[idx] = py[i];
                }
            }
        }
        for (int idx = 0; idx < cnt; idx++) {
            float edge = smooth((cw[idx] - 0.12f) / 0.5f);
            float th = Math.min(0.5f, 0.012f + cw[idx] * 0.04f) * edge;
            cth[idx] = th;
            if (cw[idx] < 0.12f) { csy[idx] = Float.NaN; continue; }
            csy[idx] = (float) (cmin[idx] + th);
            clight[idx] = WorldRenderer.getLightmapCoordinates(world,
                    BlockPos.ofFloored((cci[idx] + 0.5) * C, cmin[idx] + 0.1, (ccj[idx] + 0.5) * C));
        }

        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getBlockModels().getModel(Blocks.WATER.getDefaultState()).getParticleSprite();
        float u0 = sprite.getMinU(), u1 = sprite.getMaxU(), v0 = sprite.getMinV(), v1 = sprite.getMaxV();
        VertexConsumerProvider consumers = ctx.consumers();
        RenderLayer layer = RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        VertexConsumer vc = consumers.getBuffer(layer);
        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();

        // 2) permukaan genangan: sudut dirata-ratakan agar halus dan menipis di tepi
        float[] cy4 = new float[4];
        for (int idx = 0; idx < cnt; idx++) {
            if (Float.isNaN(csy[idx])) continue;
            int ci = cci[idx], cj = ccj[idx];
            for (int a = 0; a < 2; a++) {
                for (int b = 0; b < 2; b++) {
                    float sum = 0f;
                    int c = 0;
                    for (int da = -1; da <= 0; da++) {
                        for (int db = -1; db <= 0; db++) {
                            int o = cellIdx.get(ckey(ci + a + da, cj + b + db));
                            if (o < 0 || Float.isNaN(csy[o])) continue;
                            sum += csy[o];
                            c++;
                        }
                    }
                    cy4[a * 2 + b] = c == 0 ? csy[idx] : sum / c;
                }
            }
            float ya = cy4[0], yb = cy4[1], yc = cy4[3], yd = cy4[2]; // (0,0) (0,1) (1,1) (1,0)
            float sx = (float) (((yd - ya) + (yc - yb)) / (2.0 * C));
            float sz = (float) (((yb - ya) + (yc - yd)) / (2.0 * C));
            float len = (float) Math.sqrt(sx * sx + 1f + sz * sz);
            float nnx = -sx / len, nny = 1f / len, nnz = -sz / len;
            float x0 = (float) (ci * C - cam.x), z0 = (float) (cj * C - cam.z);
            float x1 = (float) (x0 + C), z1 = (float) (z0 + C);
            float lift = 0.003f - (float) cam.y;
            int alpha = 120 + (int) (100f * Math.min(1f, cth[idx] / 0.1f));
            int lt = clight[idx];
            vert(vc, mat, x0, ya + lift, z0, u0, v0, cr, cg, cb, alpha, lt, nnx, nny, nnz);
            vert(vc, mat, x0, yb + lift, z1, u0, v1, cr, cg, cb, alpha, lt, nnx, nny, nnz);
            vert(vc, mat, x1, yc + lift, z1, u1, v1, cr, cg, cb, alpha, lt, nnx, nny, nnz);
            vert(vc, mat, x1, yd + lift, z0, u1, v0, cr, cg, cb, alpha, lt, nnx, nny, nnz);
        }

        // 3) tetesan di udara
        for (int i = 0; i < n; i++) {
            if (ground[i] > 0) continue;
            double dxp = px[i] - cam.x, dzp = pz[i] - cam.z;
            double len = Math.sqrt(dxp * dxp + dzp * dzp);
            float rx = 1f, rz = 0f;
            if (len > 0.05) { rx = (float) (-dzp / len); rz = (float) (dxp / len); }
            float hw = 0.055f, hh = 0.07f + Math.min(0.25f, Math.abs(vy[i]) * 0.6f);
            float cxp = (float) dxp, cyp = (float) (py[i] - cam.y), czp = (float) dzp;
            int lt = WorldRenderer.getLightmapCoordinates(world, BlockPos.ofFloored(px[i], py[i], pz[i]));
            vert(vc, mat, cxp - rx * hw, cyp - hh, czp - rz * hw, u0, v0, cr, cg, cb, 210, lt, 0f, 1f, 0f);
            vert(vc, mat, cxp - rx * hw, cyp + hh, czp - rz * hw, u0, v1, cr, cg, cb, 210, lt, 0f, 1f, 0f);
            vert(vc, mat, cxp + rx * hw, cyp + hh, czp + rz * hw, u1, v1, cr, cg, cb, 210, lt, 0f, 1f, 0f);
            vert(vc, mat, cxp + rx * hw, cyp - hh, czp + rz * hw, u1, v0, cr, cg, cb, 210, lt, 0f, 1f, 0f);
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(layer);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float u, float v,
                             int r, int g, int b, int a, int light, float nx, float ny, float nz) {
        vc.vertex(mat, x, y, z)
                .color(r, g, b, a)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(nx, ny, nz);
    }
}
