package com.oceanphysics.client;

import com.oceanphysics.OceanPhysicsMod;
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

import java.util.HashMap;

/**
 * Menggambar permukaan air bergelombang (mesh dinamis) di sekitar kamera, di atas air vanilla.
 * Dilindungi try/catch: jika ada error, fitur ini mati sendiri dan game tidak crash.
 * Default tidak aktif saat shader dipakai (shader punya pipeline air sendiri).
 */
public final class WaterMeshRenderer {
    private static final class Col {
        float surface, depth;
        int light, r, g, b;
        boolean valid;
        long stamp = Long.MIN_VALUE / 2;
    }

    private static final HashMap<Long, Col> CACHE = new HashMap<>();
    private static ClientWorld lastWorld;
    private static boolean failed;
    private static long lastTime = Long.MIN_VALUE, lastNano;
    private static long shaderCheckTime = Long.MIN_VALUE / 2;
    private static boolean shaderCached;
    private static int refillBudget;

    private static float[] ys = new float[0], nxs = new float[0], nzs = new float[0];
    private static Col[] cols = new Col[0];

    private WaterMeshRenderer() {}

    public static void render(WorldRenderContext ctx) {
        if (failed) return;
        WaveConfig cfg = WaveConfig.get();
        if (!cfg.enabled || !cfg.renderWaterMesh) return;
        ClientWorld world = ctx.world();
        if (world == null || ctx.consumers() == null || ctx.matrixStack() == null) return;
        if (!cfg.meshWithShaders && shadersActive(world.getTime())) return;
        try {
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
            shaderCached = true; // tidak yakin: anggap shader aktif, lebih aman
        }
        return shaderCached;
    }

    private static void draw(WorldRenderContext ctx, ClientWorld world, WaveConfig cfg) {
        int cell = Math.max(1, Math.min(4, cfg.meshCell));
        int half = Math.max(4, Math.min(48, cfg.meshRadius / cell));
        int n = 2 * half + 1;
        Vec3d cam = ctx.camera().getPos();

        if (world != lastWorld) {
            CACHE.clear();
            lastWorld = world;
        }
        if (CACHE.size() > 12000) CACHE.clear();

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
            cols = new Col[n * n];
        }

        int gx0 = (int) Math.floor(cam.x / cell) - half;
        int gz0 = (int) Math.floor(cam.z / cell) - half;
        refillBudget = 160;
        double lift = cfg.waveSize.amplitude * cfg.waveLift;
        double edge1 = half * cell;
        double edge0 = edge1 * 0.7;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int idx = i * n + j;
                int gx = gx0 + i, gz = gz0 + j;
                Col c = column(world, gx, gz, cell, time);
                cols[idx] = c;
                if (c == null || !c.valid) {
                    ys[idx] = Float.NaN;
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
                double h = (WaveMath.rawHeight(x, z, t) + lift) * c.depth * fade;
                ys[idx] = (float) (c.surface + h + 0.01);
            }
        }

        // normal per titik dari selisih tinggi tetangga
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int idx = i * n + j;
                float y = ys[idx];
                if (Float.isNaN(y)) continue;
                float xl = (i > 0 && !Float.isNaN(ys[idx - n])) ? ys[idx - n] : y;
                float xr = (i < n - 1 && !Float.isNaN(ys[idx + n])) ? ys[idx + n] : y;
                float zl = (j > 0 && !Float.isNaN(ys[idx - 1])) ? ys[idx - 1] : y;
                float zr = (j < n - 1 && !Float.isNaN(ys[idx + 1])) ? ys[idx + 1] : y;
                float nx = -(xr - xl) / (2f * cell);
                float nz = -(zr - zl) / (2f * cell);
                float len = (float) Math.sqrt(nx * nx + 1f + nz * nz);
                nxs[idx] = nx / len;
                nzs[idx] = nz / len;
            }
        }

        Sprite sprite = MinecraftClient.getInstance().getBakedModelManager()
                .getBlockModels().getModel(Blocks.WATER.getDefaultState()).getParticleSprite();
        float u0 = sprite.getMinU(), u1 = sprite.getMaxU(), v0 = sprite.getMinV(), v1 = sprite.getMaxV();

        VertexConsumerProvider consumers = ctx.consumers();
        RenderLayer layer = RenderLayer.getEntityTranslucent(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        VertexConsumer vc = consumers.getBuffer(layer);
        Matrix4f mat = ctx.matrixStack().peek().getPositionMatrix();

        for (int i = 0; i < n - 1; i++) {
            for (int j = 0; j < n - 1; j++) {
                int a = i * n + j;      // (x, z)
                int b = a + 1;          // (x, z+1)
                int c2 = a + n + 1;     // (x+1, z+1)
                int d2 = a + n;         // (x+1, z)
                if (Float.isNaN(ys[a]) || Float.isNaN(ys[b]) || Float.isNaN(ys[c2]) || Float.isNaN(ys[d2])) continue;
                Col col = cols[a];
                float wx0 = (float) ((double) (gx0 + i) * cell - cam.x);
                float wz0 = (float) ((double) (gz0 + j) * cell - cam.z);
                float wx1 = wx0 + cell, wz1 = wz0 + cell;
                float cy = (float) -cam.y;
                put(vc, mat, wx0, ys[a] + cy, wz0, u0, v0, col, nxs[a], nzs[a]);
                put(vc, mat, wx0, ys[b] + cy, wz1, u0, v1, col, nxs[b], nzs[b]);
                put(vc, mat, wx1, ys[c2] + cy, wz1, u1, v1, col, nxs[c2], nzs[c2]);
                put(vc, mat, wx1, ys[d2] + cy, wz0, u1, v0, col, nxs[d2], nzs[d2]);
            }
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(layer);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                            float u, float v, Col c, float nx, float nz) {
        float ny = (float) Math.sqrt(Math.max(0.0001, 1.0 - nx * nx - nz * nz));
        vc.vertex(mat, x, y, z)
                .color(c.r, c.g, c.b, 190)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(c.light)
                .normal(nx, ny, nz);
    }

    private static Col column(ClientWorld world, int gx, int gz, int cell, long time) {
        long key = ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
        Col c = CACHE.get(key);
        if (c == null || time - c.stamp > 100) {
            if (refillBudget > 0) {
                refillBudget--;
                if (c == null) {
                    c = new Col();
                    CACHE.put(key, c);
                }
                fill(world, c, gx * cell, gz * cell, time);
            }
        }
        return c;
    }

    private static void fill(ClientWorld world, Col c, int x, int z, long time) {
        c.stamp = time;
        c.valid = false;
        if (!world.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) return;
        double s = WaveMath.topWaterSurface(world, x, z);
        if (Double.isNaN(s)) return;
        c.surface = (float) s;
        c.depth = (float) WaveMath.depthFactor(world, x, s, z);
        BlockPos above = new BlockPos(x, (int) Math.floor(s) + 1, z);
        c.light = WorldRenderer.getLightmapCoordinates(world, above);
        int rgb = BiomeColors.getWaterColor(world, above.down());
        c.r = (rgb >> 16) & 255;
        c.g = (rgb >> 8) & 255;
        c.b = rgb & 255;
        c.valid = true;
    }
}
