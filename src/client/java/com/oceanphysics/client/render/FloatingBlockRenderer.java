package com.oceanphysics.client.render;

import com.oceanphysics.WaveConfig;
import com.oceanphysics.WaveMath;
import com.oceanphysics.entity.FloatingBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class FloatingBlockRenderer extends EntityRenderer<FloatingBlockEntity> {
    public FloatingBlockRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.3f;
    }

    @Override
    public void render(FloatingBlockEntity e, float yaw, float tickDelta, MatrixStack m,
                       VertexConsumerProvider vcp, int light) {
        BlockState st = e.getState();
        m.push();

        WaveConfig cfg = WaveConfig.get();
        if (cfg.enabled && cfg.boatTilt) {
            double x = MathHelper.lerp(tickDelta, e.prevX, e.getX());
            double y = MathHelper.lerp(tickDelta, e.prevY, e.getY());
            double z = MathHelper.lerp(tickDelta, e.prevZ, e.getZ());
            double surface = WaveMath.findSurface(e.getWorld(), x, y + 0.5, z);
            if (!Double.isNaN(surface)) {
                double[] s = WaveMath.slope(e.getWorld(), x, surface, z, tickDelta);
                float max = (float) Math.toRadians(20);
                float pitch = MathHelper.clamp((float) -Math.atan(s[1] * 1.2), -max, max);
                float roll = MathHelper.clamp((float) Math.atan(s[0] * 1.2), -max, max);
                m.translate(0.0, 0.5, 0.0);
                m.multiply(RotationAxis.POSITIVE_X.rotation(pitch));
                m.multiply(RotationAxis.POSITIVE_Z.rotation(roll));
                m.translate(0.0, -0.5, 0.0);
            }
        }

        m.translate(-0.5, 0.0, -0.5);
        MinecraftClient.getInstance().getBlockRenderManager()
                .renderBlockAsEntity(st, m, vcp, light, OverlayTexture.DEFAULT_UV);
        m.pop();
        super.render(e, yaw, tickDelta, m, vcp, light);
    }

    @Override
    public Identifier getTexture(FloatingBlockEntity entity) {
        return SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE;
    }
}
