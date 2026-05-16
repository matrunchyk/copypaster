package com.crazyhouse.copypaster.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Renders two in-world overlays (MC 26.1.2 / LevelRenderEvents API):
 *
 *  1. Selection box  — yellow wireframe around the two corners the player
 *     selected with [ / ] keybinds.
 *
 *  2. Paste ghost    — cyan wireframe at the destination of the next /paste,
 *     populated by the S2C GhostPayload from the server.
 */
@Environment(EnvType.CLIENT)
public final class GhostRenderer {

    // ── Paste ghost state (set by packet handler) ─────────────────────────────
    static volatile boolean ghostActive = false;
    static volatile int ghostX, ghostY, ghostZ;
    static volatile int ghostSX, ghostSY, ghostSZ;

    // ── Selection corners (set by KeyHandler) ─────────────────────────────────
    static volatile BlockPos selPos1 = null;
    static volatile BlockPos selPos2 = null;

    // ── ARGB colours ──────────────────────────────────────────────────────────
    private static final int GHOST_COLOR = ARGB.color(230, 64,  216, 255); // cyan
    private static final int POS1_COLOR  = ARGB.color(230, 255, 100,  20); // orange
    private static final int POS2_COLOR  = ARGB.color(230,  50, 200,  80); // green
    private static final int SEL_COLOR   = ARGB.color(230, 255, 220,  30); // yellow

    static void register() {
        LevelRenderEvents.BEFORE_GIZMOS.register(GhostRenderer::render);
    }

    private static void render(LevelRenderContext ctx) {
        MultiBufferSource.BufferSource buf = ctx.bufferSource();
        VertexConsumer lines = buf.getBuffer(RenderTypes.lines());
        PoseStack matrices   = ctx.poseStack();
        Vec3 cam             = ctx.levelState().cameraRenderState.pos;

        boolean drew = false;

        if (ghostActive) {
            drawBox(matrices, lines, cam, ghostX, ghostY, ghostZ, ghostSX, ghostSY, ghostSZ, GHOST_COLOR);
            drew = true;
        }

        BlockPos p1 = selPos1, p2 = selPos2;
        if (p1 != null && p2 != null) {
            int minX = Math.min(p1.getX(), p2.getX()), maxX = Math.max(p1.getX(), p2.getX()) + 1;
            int minY = Math.min(p1.getY(), p2.getY()), maxY = Math.max(p1.getY(), p2.getY()) + 1;
            int minZ = Math.min(p1.getZ(), p2.getZ()), maxZ = Math.max(p1.getZ(), p2.getZ()) + 1;
            drawBox(matrices, lines, cam, minX, minY, minZ, maxX-minX, maxY-minY, maxZ-minZ, SEL_COLOR);
            drew = true;
        } else if (p1 != null) {
            drawBox(matrices, lines, cam, p1.getX(), p1.getY(), p1.getZ(), 1, 1, 1, POS1_COLOR);
            drew = true;
        } else if (p2 != null) {
            drawBox(matrices, lines, cam, p2.getX(), p2.getY(), p2.getZ(), 1, 1, 1, POS2_COLOR);
            drew = true;
        }

        if (drew) buf.endBatch(RenderTypes.lines());
    }

    private static void drawBox(PoseStack matrices, VertexConsumer consumer, Vec3 cam,
                                 int ox, int oy, int oz, int sx, int sy, int sz, int color) {
        VoxelShape shape = Shapes.create(0, 0, 0, sx, sy, sz);
        ShapeRenderer.renderShape(matrices, consumer, shape,
                ox - cam.x, oy - cam.y, oz - cam.z, color, 1.0f);
    }

    private GhostRenderer() {}
}
