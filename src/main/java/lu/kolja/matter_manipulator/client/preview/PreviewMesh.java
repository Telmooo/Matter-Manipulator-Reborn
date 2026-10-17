package lu.kolja.matter_manipulator.client.preview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.nio.ByteBuffer;

final class PreviewMesh implements AutoCloseable {
    private static final BufferBuilder UPLOAD = new BufferBuilder(256);
    private static int liveBuffers;
    private final VertexBuffer buffer;
    final RenderType type;
    final AABB bounds;
    private final int vertices;
    private final BufferBuilder.SortState sortState;
    private Vec3 sortedFrom;
    private boolean closed;

    private PreviewMesh(VertexBuffer buffer, RenderType type, AABB bounds, int vertices,
            BufferBuilder.SortState sortState, Vec3 sortedFrom) {
        this.buffer = buffer;
        this.type = type;
        this.bounds = bounds;
        this.vertices = vertices;
        this.sortState = sortState;
        this.sortedFrom = sortedFrom;
    }

    static PreviewMesh upload(RenderType type, AABB bounds, ByteBuffer bytes, Vec3 viewpoint) {
        RenderSystem.assertOnRenderThread();
        UPLOAD.begin(type.mode(), type.format());
        UPLOAD.putBulkData(bytes);
        boolean compileSorted = PreviewRenderTypes.compileSorted(type);
        BufferBuilder.SortState sortState = null;
        if (compileSorted) {
            UPLOAD.setQuadSorting(sorting(viewpoint));
            sortState = UPLOAD.getSortState();
        }
        var rendered = UPLOAD.end();
        int vertices = rendered.drawState().vertexCount();
        VertexBuffer buffer = null;
        boolean uploading = false;
        try {
            buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            uploading = true;
            buffer.upload(rendered);
            liveBuffers++;
            PreviewDebugStats.meshUploaded(compileSorted);
            return new PreviewMesh(buffer, type, bounds, vertices, sortState, compileSorted ? viewpoint : null);
        } catch (RuntimeException | Error exception) {
            if (buffer != null) {
                buffer.close();
            }
            throw exception;
        } finally {
            if (!uploading) {
                rendered.release();
            }
            VertexBuffer.unbind();
        }
    }

    void draw(Matrix4f modelView) {
        if (closed) {
            return;
        }
        buffer.bind();
        buffer.drawWithShader(modelView, RenderSystem.getProjectionMatrix(), RenderSystem.getShader());
    }

    boolean needsResort(Vec3 viewpoint) {
        return sortState != null && PreviewRenderTypes.TRANSLUCENT_SORTING.shouldResort(
                viewpoint.distanceToSqr(sortedFrom), viewpoint.distanceToSqr(bounds.getCenter()));
    }

    void resort(Vec3 viewpoint) {
        RenderSystem.assertOnRenderThread();
        long start = PreviewDebugStats.now();
        UPLOAD.begin(type.mode(), type.format());
        UPLOAD.restoreSortState(sortState);
        UPLOAD.setQuadSorting(sorting(viewpoint));
        var rendered = UPLOAD.end();
        try {
            buffer.bind();
            buffer.upload(rendered);
        } finally {
            VertexBuffer.unbind();
        }
        sortedFrom = viewpoint;
        PreviewDebugStats.translucentResorted(vertices / 4, PreviewDebugStats.now() - start);
    }

    int vertices() {
        return vertices;
    }

    static int liveBuffers() {
        return liveBuffers;
    }

    private static VertexSorting sorting(Vec3 viewpoint) {
        return VertexSorting.byDistance((float) viewpoint.x, (float) viewpoint.y, (float) viewpoint.z);
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        if (!closed) {
            closed = true;
            buffer.close();
            liveBuffers--;
        }
    }
}
