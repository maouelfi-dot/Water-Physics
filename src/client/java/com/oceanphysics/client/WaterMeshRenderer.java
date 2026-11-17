package com.oceanphysics.client;

import com.oceanphysics.OceanPhysicsMod;
import com.oceanphysics.WaterGrid;
import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Lapisan air dinamis di atas air vanilla: ombak (sinus) + riak (simulasi gelombang).
 * Selalu berada >= 0.04 blok DI ATAS permukaan vanilla, jadi tidak ada z-fighting/kedip.
 * Data kolom air disimpan di WaterGrid (array, tanpa HashMap) agar ringan di HP.
 * Dilindungi try/catch: jika ada error fitur ini mati sendiri dan game tidak crash.
 */
public final class WaterMeshRenderer {
    public static final WaterGrid GRID = new WaterGrid();
    private static final float BASE_OFFSET = 0.04f;

    private static ClientWorld lastWorld;
    private static boolean failed;
    private static long lastTime = Long.MIN_VALUE, lastNano;
    private static long shaderCheckTime = Long.MIN_VALUE / 2;
    private static boolean shaderCached;
    private static int refillBudget;

    private static float[] ys = new float[0], nxs = new float[0], nzs = new float[0];
    private static int[] ids = new int[0];

    private WaterMeshRenderer() {}

    /** Dipanggil dari tick client: samakan ukuran sel dan bersihkan saat ganti dunia. */
    public static void syncWorld(ClientWorld world, WaveConfig cfg) {
        if (world != lastWorld) {
            GRID.clear();
            lastWorld = world;
        }
        int cell = Math.max(1, Math.min(4, cfg.meshCell));
        if (GRID.cell != cell) {
            GRID.cell = cell;
            GRID.clear();
        }
    }

    public static void render(WorldRenderContext ctx) {
        if (failed) return;
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || !cfg.renderWaterMesh) return;
        ClientWorld world = ctx.world();
        if (world == null || ctx.consumers() == null || ctx.matrixStack() == null) return;
        if (!cfg.meshWithShaders && shadersActive(world.getTime())) return;
        try {
            syncWorld(world, cfg);
            draw(ctx, world, cfg);
        } catch (Throwable t) {
            failed = true;
            OceanPhysicsMod.LOGGER.error("Mesh air dimatikan karena error", t);
        }
    }

    private static boolean shadersActive(long time) {
        if (time - shaderCheckTime < 20) return shaderCached;
        shaderCheckTime = time;
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            shaderCached = false;
            return false;
        }
        try {
            Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object api = c.getMethod("getInstance").invoke(null);
            shaderCached = (Boolean) c.getMethod("isShaderPackInUse").invoke(api);
        } catch (Throwable t) {
            shaderCached = true;
        }
        return shaderCached;
    }

    private static void draw(WorldRenderContext ctx, ClientWorld world, WaveConfig cfg) {
        final int cell = GRID.cell;
        final int half = Math.max(4, Math.min(30, cfg.meshRadius / cell));
        final int n = 2 * half + 1;
        Vec3d cam = ctx.camera().getPos();

        long time = world.getTime();
        long now = System.nanoTime();
        if (time != lastTime) {
            lastTime = time;
            lastNano = now;
        }
        float partial = Math.min(1f, (now - lastNano) / 50_000_000f);
        double t = (time + partial) / 20.0;

        if (ys.length != n * n) {
            ys = new float[n * n];
            nxs = new float[n * n];
            nzs = new float[n * n];
            ids = new int[n * n];
        }

        int gx0 = (int) Math.floor(cam.x / cell) - half;
        int gz0 = (int) Math.floor(cam.z / cell) - half;
        refillBudget = 220;
        double edge1 = half * (double) cell;
        double edge0 = edge1 * 0.65;
        int baseAlpha = Math.max(0, Math.min(255, cfg.meshAlpha));

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int k = i * n + j;
                int gx = gx0 + i, gz = gz0 + j;
                int id = GRID.acquire(gx, gz);
                if (GRID.stale(id, time) && refillBudget > 0) {
                    refillBudget--;
                    fill(world, id, gx * cell, gz * cell, time);
                }
                ids[k] = id;
                if (!GRID.valid[id]) {
                    ys[k] = Float.NaN;
                    continue;
                }
                double x = (double) gx * cell, z = (double) gz * cell;
                double dx = x - cam.x, dz = z - cam.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                double fade;
                if (d <= edge0) fade = 1;
                else if (d >= edge1) fade = 0;
                else {
                    double f = (edge1 - d) / (edge1 - edge0);
                    fade = f * f * (3 - 2 * f);
                }
                double wave = WaveMath.rawHeight(x, z, t) * GRID.depth[id];
                double total = Math.max(0.0, wave + GRID.h[id]) * fade;
                ys[k] = (float) (GRID.surface[id] + BASE_OFFSET + total);
            }
        }

        // normal per titik dari selisih tinggi tetangga
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int k = i * n + j;
                float y = ys[k];
                if (Float.isNaN(y)) continue;
                float xl = (i > 0 && !Float.isNaN(ys[k - n])) ? ys[k - n] : y;
                float xr = (i < n - 1 && !Float.isNaN(ys[k + n])) ? ys[k + n] : y;
                float zl = (j > 0 && !Float.isNaN(ys[k - 1])) ? ys[k - 1] : y;
                float zr = (j < n - 1 && !Float.isNaN(ys[k + 1])) ? ys[k + 1] : y;
                float nx = -(xr - xl) / (2f * cell);
                float nz = -(zr - zl) / (2f * cell);
                float len = (float) Math.sqrt(nx * nx + 1f + nz * nz);
                nxs[k] = nx / len;
                nzs[k] = nz / len;
            }
        }

        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getBlockModels().getModel(Blocks.WATER.getDefaultState()).getParticleSprite();
        float u0 = sprite.getMinU(), u1 = sprite.getMaxU(), v0 = sprite.getMinV(), v1 = sprite.getMaxV();

        VertexConsumerProvider consumers = ctx.consumers();
        RenderLayer layer = RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        VertexConsumer vc = consumers.getBuffer(layer);
        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();
        float cy = (float) -cam.y;

        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1; j++) {
                int a = i * n + j;      // (x, z)
                int b = a + 1;          // (x, z+1)
                int c2 = a + n + 1;     // (x+1, z+1)
                int d2 = a + n;         // (x+1, z)
                if (Float.isNaN(ys[a]) || Float.isNaN(ys[b]) || Float.isNaN(ys[c2]) || Float.isNaN(ys[d2])) continue;
                int id = ids[a];
                float wx0 = (float) ((double) (gx0 + i) * cell - cam.x);
                float wz0 = (float) ((double) (gz0 + j) * cell - cam.z);
                float wx1 = wx0 + cell, wz1 = wz0 + cell;
                // alpha memudar ke tepi mesh
                double mx = wx0 + cell * 0.5, mz = wz0 + cell * 0.5;
                double dm = Math.sqrt(mx * mx + mz * mz);
                float af = dm <= edge0 ? 1f : (dm >= edge1 ? 0f : (float) ((edge1 - dm) / (edge1 - edge0)));
                int alpha = (int) (baseAlpha * af);
                if (alpha <= 2) continue;
                put(vc, mat, wx0, ys[a] + cy, wz0, u0, v0, id, alpha, nxs[a], nzs[a]);
                put(vc, mat, wx0, ys[b] + cy, wz1, u0, v1, id, alpha, nxs[b], nzs[b]);
                put(vc, mat, wx1, ys[c2] + cy, wz1, u1, v1, id, alpha, nxs[c2], nzs[c2]);
                put(vc, mat, wx1, ys[d2] + cy, wz0, u1, v0, id, alpha, nxs[d2], nzs[d2]);
            }
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(layer);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                            float u, float v, int id, int alpha, float nx, float nz) {
        float ny = (float) Math.sqrt(Math.max(0.0001, 1.0 - nx * nx - nz * nz));
        int c = GRID.rgb[id];
        vc.vertex(mat, x, y, z)
                .color((c >> 16) & 255, (c >> 8) & 255, c & 255, alpha)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(GRID.light[id])
                .normal(nx, ny, nz);
    }

    private static void fill(ClientWorld world, int id, int x, int z, long time) {
        GRID.stamp[id] = time;
        GRID.valid[id] = false;
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) {
            GRID.stamp[id] = time - 90; // coba lagi sebentar lagi
            return;
        }
        double s = WaveMath.topWaterSurface(world, x, z);
        if (Double.isNaN(s)) return;
        GRID.surface[id] = (float) s;
        GRID.depth[id] = (float) WaveMath.depthFactor(world, x, s, z);
        BlockPos above = new BlockPos(x, (int) Math.floor(s) + 1, z);
        GRID.light[id] = WorldRenderer.getLightmapCoordinates(world, above);
        GRID.rgb[id] = BiomeColors.getWaterColor(world, above.down());
        GRID.valid[id] = true;
    }
}
