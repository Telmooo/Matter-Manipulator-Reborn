package lu.kolja.matter_manipulator.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import lu.kolja.matter_manipulator.client.integration.ClientIntegrationDispatcher;
import lu.kolja.matter_manipulator.api.client.PreviewDetail;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.RenderTypeHelper;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

final class PreviewStaticGeometry {
    private static final int STRIDE = 8;

    private final Map<RenderType, Recorder> layers;

    private PreviewStaticGeometry(Map<RenderType, Recorder> layers) {
        this.layers = layers;
    }

    static @Nullable PreviewStaticGeometry record(Level level, PreviewContentLevel view, PreviewVisual visual,
            BlockPos pos, BlockState state, PreviewDetail detail) {
        var recorders = new HashMap<RenderType, Recorder>();
        var drawn = PreviewModelData.renderStatic(level, view, visual, pos, state, detail, new PoseStack(),
                layer -> recorders.computeIfAbsent(layer, ignored -> new Recorder()));
        if (drawn != ClientIntegrationDispatcher.StaticRender.DRAWN) {
            return null;
        }
        recorders.values().removeIf(recorder -> recorder.vertices == 0);
        return recorders.isEmpty() ? null : new PreviewStaticGeometry(Map.copyOf(recorders));
    }

    void render(PoseStack pose, MultiBufferSource buffers) {
        var last = pose.last();
        layers.forEach((layer, recorded) -> {
            var consumer = buffers.getBuffer(RenderTypeHelper.getEntityRenderType(layer, false));
            var data = recorded.data;
            for (int vertex = 0; vertex < recorded.vertices; vertex++) {
                int i = vertex * STRIDE;
                int color = recorded.colors[vertex];
                consumer.vertex(last.pose(), data[i], data[i + 1], data[i + 2])
                        .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
                        .uv(data[i + 3], data[i + 4])
                        .overlayCoords(OverlayTexture.NO_OVERLAY)
                        .uv2(LightTexture.FULL_BRIGHT)
                        .normal(last.normal(), data[i + 5], data[i + 6], data[i + 7])
                        .endVertex();
            }
        });
    }

    private static final class Recorder implements VertexConsumer {
        private float[] data = new float[STRIDE * 64];
        private int[] colors = new int[64];
        private int vertices;
        private final float[] vertex = new float[STRIDE];
        private int color = -1;

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            vertex[0] = (float) x;
            vertex[1] = (float) y;
            vertex[2] = (float) z;
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            color = alpha << 24 | red << 16 | green << 8 | blue;
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            vertex[3] = u;
            vertex[4] = v;
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            vertex[5] = x;
            vertex[6] = y;
            vertex[7] = z;
            return this;
        }

        @Override
        public void endVertex() {
            if (vertices == colors.length) {
                data = Arrays.copyOf(data, data.length * 2);
                colors = Arrays.copyOf(colors, colors.length * 2);
            }
            System.arraycopy(vertex, 0, data, vertices * STRIDE, STRIDE);
            colors[vertices++] = color;
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {}

        @Override
        public void unsetDefaultColor() {}
    }
}
