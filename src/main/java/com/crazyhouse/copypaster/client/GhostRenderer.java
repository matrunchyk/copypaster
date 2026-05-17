package com.crazyhouse.copypaster.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;

/**
 * In-world overlays via vanilla {@link Gizmos} (fill only, no wireframes).
 */
@Environment(EnvType.CLIENT)
public final class GhostRenderer {

    static volatile boolean ghostActive = false;
    static volatile int ghostX, ghostY, ghostZ;
    static volatile int ghostSX, ghostSY, ghostSZ;

    private static final int GHOST_FILL = ARGB.color(64, 64, 216, 255);
    private static final float PADDING = 0.002f;

    static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(GhostRenderer::emitGizmos);
    }

    private static void emitGizmos(Minecraft client) {
        if (client.level == null) return;
        if (!SelectionPreview.shouldRenderWorld() && !ghostActive) return;

        try (Gizmos.TemporaryCollection ignored = client.collectPerTickGizmos()) {
            if (SelectionPreview.shouldRenderWorld()) {
                AABB box = SelectionPreview.selectionBounds();
                if (box != null) {
                    Gizmos.cuboid(box.inflate(PADDING), GizmoStyle.fill(CopyPasterConfig.selectionFillArgb()))
                            .setAlwaysOnTop();
                }
            }

            if (ghostActive) {
                AABB ghost = new AABB(ghostX, ghostY, ghostZ,
                        ghostX + ghostSX, ghostY + ghostSY, ghostZ + ghostSZ).inflate(PADDING);
                Gizmos.cuboid(ghost, GizmoStyle.fill(GHOST_FILL)).setAlwaysOnTop();
            }
        }
    }

    private GhostRenderer() {}
}
