#version 330

#moj_import <minecraft:fog.glsl>

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec3 viewRay;

out vec4 fragColor;

const vec3 SKY_COLOR = vec3(0.47333336, 0.65591097, 1.0);
//const vec3 SKY_COLOR = vec3(0.4392156863, 0.6235294118, 1.0);
const vec3 HAZE_COLOR = vec3(0.75, 0.84, 1.0);

const float HORIZON_FALLOFF = 0.17;

void main() {
    if (gl_FrontFacing) {
        discard;
    }

    vec3 ray = normalize(viewRay);

    float haze = clamp(HORIZON_FALLOFF / max(ray.y, 0.001), 0.0, 1.0);
    vec4 color = vec4(mix(SKY_COLOR, HAZE_COLOR, haze) * vertexColor.rgb, 1.0);

    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
