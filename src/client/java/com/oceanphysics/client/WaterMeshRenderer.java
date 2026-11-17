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
import net.minecraft.text.Text;
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

    private static float[] ys = new float[0], sh = new float[0];
    private static float[] hy = new float[0], hs = new float[0], hnx = new float[0], hnz = new float[0];
    private static int[] ids = new int[0];
    private static boolean announced;

    // konstanta per frame untuk warna badai dan silau (diisi di draw())
    private static float fTint, fGlare, fLx, fLy, fLz;
    private static final float TINT_R = 52f, TINT_G = 74f, TINT_B = 78f;

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
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc.player != null) mc.player.sendMessage(Text.literal("Water Physics: mesh air MATI karena error: " + t), false);
            } catch (Throwable ignored) {}
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

    private static double soft(double x) {
        final double L = 0.06; // angkat sedikit di atas air vanila; lembah dimampatkan, tidak pernah menembus vanila
        return x >= 0 ? L + x : L * Math.exp(x / L);
    }

    private static void draw(WorldRenderContext ctx, ClientWorld world, WaveConfig cfg) {
        final int cell = GRID.cell;
        final int half = Math.max(4, Math.min(30, cfg.meshRadius / cell));
        final int n = 2 * half + 1, m = n + 1;
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
            sh = new float[n * n];
            ids = new int[n * n];
            hy = new float[m * m];
            hs = new float[m * m];
            hnx = new float[m * m];
            hnz = new float[m * m];
        }

        int gx0 = (int) Math.floor(cam.x / cell) - half;
        int gz0 = (int) Math.floor(cam.z / cell) - half;
        refillBudget = 220;
        double edge1 = half * (double) cell;
        double edge0 = edge1 * 0.65;
        int baseAlpha = Math.max(0, Math.min(255, cfg.meshAlpha));
        final double storm = WaveMath.storm(world);
        double amp = Math.max(0.05, cfg.waveSize.amplitude * WaveMath.boost(storm));
        setupFrame(world, cfg, storm);

        // 1) tinggi tiap sel (pusat blok)
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
                double wave = WaveMath.rawHeight(x, z, t, storm) * GRID.depth[id];
                double total = (wave + GRID.h[id]) * fade;
                ys[k] = (float) (GRID.surface[id] + BASE_OFFSET + soft(total));
                float cr = (float) Math.max(0.0, Math.min(1.0, (total / amp - (0.5 - 0.2 * storm)) * 2.0));
                float dk = (float) Math.max(0.0, Math.min(1.0, -total / 0.25));
                sh[k] = cr - dk;
            }
        }

        // 2) titik sudut = rata-rata sel yang menyentuhnya (permukaan halus, rata dengan dinding/pantai)
        for (int ci = 0; ci < m; ci++) {
            for (int cj = 0; cj < m; cj++) {
                float sy = 0, ss = 0;
                int c = 0;
                for (int di = -1; di <= 0; di++) {
                    for (int dj = -1; dj <= 0; dj++) {
                        int i = ci + di, j = cj + dj;
                        if (i < 0 || j < 0 || i >= n || j >= n) continue;
                        int k = i * n + j;
                        if (Float.isNaN(ys[k])) continue;
                        sy += ys[k];
                        ss += sh[k];
                        c++;
                    }
                }
                int q = ci * m + cj;
                if (c == 0) hy[q] = Float.NaN;
                else {
                    hy[q] = sy / c;
                    hs[q] = ss / c;
                }
            }
        }
        for (int ci = 0; ci < m; ci++) {
            for (int cj = 0; cj < m; cj++) {
                int q = ci * m + cj;
                float y = hy[q];
                if (Float.isNaN(y)) continue;
                float xl = (ci > 0 && !Float.isNaN(hy[q - m])) ? hy[q - m] : y;
                float xr = (ci < m - 1 && !Float.isNaN(hy[q + m])) ? hy[q + m] : y;
                float zl = (cj > 0 && !Float.isNaN(hy[q - 1])) ? hy[q - 1] : y;
                float zr = (cj < m - 1 && !Float.isNaN(hy[q + 1])) ? hy[q + 1] : y;
                float nx = -(xr - xl) / (2f * cell);
                float nz = -(zr - zl) / (2f * cell);
                float len = (float) Math.sqrt(nx * nx + 1f + nz * nz);
                hnx[q] = nx / len;
                hnz[q] = nz / len;
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
        int quads = 0;

        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int k = i * n + j;
                if (Float.isNaN(ys[k])) continue;
                int a = i * m + j, b = a + 1, c2 = a + m + 1, d2 = a + m;
                if (Float.isNaN(hy[a]) || Float.isNaN(hy[b]) || Float.isNaN(hy[c2]) || Float.isNaN(hy[d2])) continue;
                int id = ids[k];
                float wx0 = (float) ((double) (gx0 + i) * cell - cam.x);
                float wz0 = (float) ((double) (gz0 + j) * cell - cam.z);
                float wx1 = wx0 + cell, wz1 = wz0 + cell;
                double mx = wx0 + cell * 0.5, mz = wz0 + cell * 0.5;
                double dm = Math.sqrt(mx * mx + mz * mz);
                float af = dm <= edge0 ? 1f : (dm >= edge1 ? 0f : (float) ((edge1 - dm) / (edge1 - edge0)));
                int alpha = (int) (baseAlpha * af);
                if (alpha <= 2) continue;
                put(vc, mat, wx0, hy[a] + cy, wz0, u0, v0, id, alpha, hnx[a], hnz[a], hs[a]);
                put(vc, mat, wx0, hy[b] + cy, wz1, u0, v1, id, alpha, hnx[b], hnz[b], hs[b]);
                put(vc, mat, wx1, hy[c2] + cy, wz1, u1, v1, id, alpha, hnx[c2], hnz[c2], hs[c2]);
                put(vc, mat, wx1, hy[d2] + cy, wz0, u1, v0, id, alpha, hnx[d2], hnz[d2], hs[d2]);
                quads++;
            }
        }

        if (consumers instanceof VertexConsumerProvider.Immediate imm) {
            imm.draw(layer);
        }
        if (!announced && quads > 0) {
            announced = true;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.sendMessage(Text.literal("Water Physics: mesh air aktif (" + quads + " petak)"), true);
        }
    }

    /** Warna badai dan arah cahaya (matahari/bulan) untuk silau, sekali per frame. */
    private static void setupFrame(ClientWorld world, WaveConfig cfg, double storm) {
        fTint = cfg.stormTint ? (float) (0.55 * storm) : 0f;
        fGlare = 0f;
        if (!cfg.glare) return;
        double d = world.getTimeOfDay() / 24000.0 - 0.25;
        d = d - Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        double a = ((d * 2.0 + e) / 3.0) * Math.PI * 2.0; // sudut langit (rad); siang = 0
        double sx = -Math.sin(a), sy = Math.cos(a);
        double rain = world.getRainGradient(1.0f);
        double calm = 1.0 - 0.85 * rain; // awan/hujan meredam silau
        double inten;
        if (sy > -0.12) {
            inten = Math.max(0.0, Math.min(1.0, (sy + 0.12) / 0.3));
            fLx = (float) sx;
            fLy = (float) sy;
        } else { // malam: pantulan bulan, lebih redup
            inten = 0.3 * Math.max(0.0, Math.min(1.0, (-sy - 0.05) / 0.3));
            fLx = (float) -sx;
            fLy = (float) -sy;
        }
        fLz = 0f;
        fGlare = (float) (inten * calm * cfg.glareStrength);
    }

    private static void put(VertexConsumer vc, Matrix4f mat, float x, float y, float z,
                            float u, float v, int id, int alpha, float nx, float nz, float shade) {
        float ny = (float) Math.sqrt(Math.max(0.0001, 1.0 - nx * nx - nz * nz));
        int c = GRID.rgb[id];
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        if (shade > 0f) {          // puncak ombak memutih (buih)
            float f = Math.min(1f, shade) * 0.6f;
            r = (int) (r + (255 - r) * f);
            g = (int) (g + (255 - g) * f);
            b = (int) (b + (255 - b) * f);
        } else if (shade < 0f) {   // lembah/riak tekanan menggelap
            float f = 1f + Math.max(-1f, shade) * 0.45f;
            r = (int) (r * f);
            g = (int) (g * f);
            b = (int) (b * f);
        }
        if (fTint > 0f) { // badai: air kelabu gelap
            r = (int) (r + (TINT_R - r) * fTint);
            g = (int) (g + (TINT_G - g) * fTint);
            b = (int) (b + (TINT_B - b) * fTint);
        }
        if (fGlare > 0f) { // silau: pantulan cahaya (Phong) dari normal ombak; x,y,z sudah relatif kamera
            float len = (float) Math.sqrt(x * x + y * y + z * z);
            if (len > 0.05f) {
                float vx = -x / len, vy = -y / len, vz = -z / len;
                float ndv = nx * vx + ny * vy + nz * vz;
                if (ndv > 0f) {
                    float rx = 2f * ndv * nx - vx, ry = 2f * ndv * ny - vy, rz = 2f * ndv * nz - vz;
                    float rl = rx * fLx + ry * fLy + rz * fLz;
                    if (rl > 0.6f) {
                        float p2 = rl * rl, p4 = p2 * p2, p8 = p4 * p4, p16 = p8 * p8, p32 = p16 * p16;
                        float spec = p32 * p16 * fGlare * (1.6f - ndv); // lebih terang di sudut landai (Fresnel kasar)
                        if (spec > 0.003f) {
                            float f = Math.min(1f, spec);
                            r = (int) (r + (255 - r) * f);
                            g = (int) (g + (250 - g) * f);
                            b = (int) (b + (235 - b) * f);
                            shade = Math.max(shade, f * 0.5f);
                        }
                    }
                }
            }
        }
        int a = Math.min(255, alpha + (int) (Math.max(0f, shade) * 40f));
        vc.vertex(mat, x, y, z)
                .color(r, g, b, a)
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
        GRID.depth[id] = (float) WaveMath.depthFactor(world, x, s, z); // sudah termasuk faktor air mengalir
        BlockPos above = new BlockPos(x, (int) Math.floor(s) + 1, z);
        GRID.light[id] = WorldRenderer.getLightmapCoordinates(world, above);
        GRID.rgb[id] = BiomeColors.getWaterColor(world, above.down());
        GRID.valid[id] = true;
    }
}
