package com.drehverschluss.stormcaller.fx.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;

/**
 * Render type of the lightning visuals: the vanilla lightning shader (position + color, full bright, no texture),
 * additive blending like vanilla lightning bolts, no depth write (overlapping glow layers add up instead of hiding each
 * other) and no backface culling (the strips face the camera anyway).
 */
public final class FxRenderTypes extends RenderType {
    public static final RenderType ARC = create("stormcaller_lightning_arc", DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS, 16384, false, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setOutputState(WEATHER_TARGET)
                    .createCompositeState(false));

    private FxRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling,
                          boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
