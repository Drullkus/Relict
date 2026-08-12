package us.drullk.relict.client.renderer;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import us.drullk.relict.Relict;

import java.util.Optional;

public class RelictRenderPipelines {

    /**
     * Erases a body's outline from the celestial layer, so that a moon reads as solid.
     * <p>
     * {@code CELESTIAL} blends with {@code OVERLAY}, which is {@code src * srcAlpha + dst} — purely
     * additive, so it can only ever brighten. {@code TRANSLUCENT} is {@code src * srcAlpha + dst * (1 -
     * srcAlpha)}, so drawing black at the mask's alpha leaves {@code dst * (1 - alpha)}: whatever the layer
     * had accumulated there is erased in proportion to coverage, back to the black it was cleared to.
     * <p>
     * This has to be its own draw rather than a black quad sitting among the additive ones, because blend
     * state belongs to the pipeline: one draw gets one blend mode, and a body needs both. It does not need
     * its own buffer, though — the mask lives in the same vertex buffer as the phase quads, one base vertex
     * along, which is also what keeps it wound identically to them.
     * <p>
     * No custom shader is involved. {@code core/position_tex} already computes {@code texture *
     * ColorModulator} and discards fully transparent texels, so feeding it a black modulator and the
     * occlusion mask does the whole job. Only the mask's alpha is read.
     */
    public static final RenderPipeline CELESTIAL_OCCLUDER = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Relict.id("pipeline/celestial_occluder"))
            .withVertexShader("core/position_tex")
            .withFragmentShader("core/position_tex")
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(
                    Optional.of(BlendFunction.TRANSLUCENT), ColorTargetState.DEFAULT.format(), ColorTargetState.WRITE_COLOR))
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    /**
     * Composites the finished celestial layer over the sky.
     * <p>
     * Additive, which is the whole point of drawing the bodies into a layer of their own: black contributes
     * nothing, so everywhere a moon erased something — or nothing was ever drawn — the sky and its fog come
     * through untouched. Reconstructing that by tinting the erase with the sky colour would need the fog
     * colour too, and would still be wrong near the horizon where the fogged sky shader diverges from the
     * flat attribute value.
     * <p>
     * A clone of vanilla's {@code ENTITY_OUTLINE_BLIT} apart from the blend — that one is alpha-over, which
     * would let a moon's unlit limb replace the sky with black. {@code core/screenquad} builds a fullscreen
     * triangle from {@code gl_VertexID}, so this draws three vertices and binds no vertex buffer.
     */
    public static final RenderPipeline CELESTIAL_COMPOSITE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
            .withLocation(Relict.id("pipeline/celestial_composite"))
            .withVertexShader("core/screenquad")
            .withFragmentShader("core/blit_screen")
            .withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
            .withColorTargetState(new ColorTargetState(
                    Optional.of(BlendFunction.ADDITIVE), ColorTargetState.DEFAULT.format(), ColorTargetState.WRITE_COLOR))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .build();

    public static void onRegisterPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CELESTIAL_OCCLUDER);
        event.registerPipeline(CELESTIAL_COMPOSITE);
    }

}
